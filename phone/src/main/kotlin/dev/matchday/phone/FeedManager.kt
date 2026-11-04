package dev.matchday.phone

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.matchday.core.FeedCodec
import dev.matchday.core.FeedEndpointUrls
import dev.matchday.core.FeedTransfer
import dev.matchday.core.LiveCodec
import dev.matchday.core.mergeLive
import dev.matchday.core.SportsFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS = "matchday_settings"
private const val KEY_FEED = "cached_feed"
private const val KEY_URL = "feed_url"
private const val KEY_LIVE = "cached_live_overlay"
private const val KEY_LAST_FULL = "last_successful_full_sync"

/** Local cached JSON is authoritative until a new, validated HTTPS response is received. */
class FeedManager(private val context: Context) {
    companion object { private val syncMutex = Mutex() }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun demoJson(): String = context.assets.open("demo_feed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun readJson(): String = prefs.getString(KEY_FEED, null) ?: demoJson()
    private fun applyStoredLive(base: SportsFeed): SportsFeed =
        prefs.getString(KEY_LIVE, null)?.let { json ->
            runCatching { base.mergeLive(LiveCodec.decode(json)) }.getOrNull()
        } ?: base
    fun load(): SportsFeed = runCatching { applyStoredLive(FeedCodec.decode(readJson())) }
        .getOrElse { FeedCodec.decode(demoJson()) }
    fun url(): String = prefs.getString(KEY_URL, "").orEmpty()
    fun setUrl(value: String) {
        val normalized = value.trim()
        if (normalized != url()) {
            prefs.edit().putString(KEY_URL, normalized)
                .remove(KEY_LIVE).remove(KEY_LAST_FULL).apply()
        }
    }
    fun isFullRefreshDue(intervalMillis: Long): Boolean {
        val last = prefs.getLong(KEY_LAST_FULL, 0L)
        return last <= 0L || System.currentTimeMillis() - last >= intervalMillis
    }
    fun resetDemo() { prefs.edit().remove(KEY_FEED).remove(KEY_LIVE).remove(KEY_URL).remove(KEY_LAST_FULL).apply(); sendToWatch(demoJson()) }
    fun save(json: String) {
        FeedCodec.decode(json) // Reject invalid feeds before persisting or syncing.
        prefs.edit().putString(KEY_FEED, json).apply()
        sendToWatch(FeedCodec.encode(applyStoredLive(FeedCodec.decode(json))))
    }
    suspend fun refresh(): Result<SportsFeed> = withContext(Dispatchers.IO) {
        syncMutex.withLock { runCatching {
            val link = url()
            require(link.startsWith("https://", ignoreCase = true)) { "HTTPS 데이터 URL을 설정해 주세요" }
            val parsedUri = Uri.parse(link)
            require(parsedUri.host != null && parsedUri.userInfo == null) { "올바른 HTTPS URL이 아닙니다" }
            require(parsedUri.port == -1 || parsedUri.port == 443) { "표준 HTTPS 포트(443)만 허용합니다" }
            val conn = URL(link).openConnection() as HttpURLConnection
            try {
                // The complete fixture feed is expensive on a cold Worker cache.
                // This longer timeout applies ONLY to /feed.json, not /live.json.
                conn.connectTimeout = 10000
                conn.readTimeout = 45000
                // The URL contains the private feed key in its query string. Do not
                // forward it to a different host through an HTTP redirect.
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")
                val status = conn.responseCode
                if (status == 403) error(FeedEndpointUrls.AUTH_HELP)
                require(status in 200..299) { "전체 피드 HTTP $status" }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().also { data -> require(data.length <= 2_000_000) { "JSON 파일이 너무 큽니다" } }
                }
                FeedCodec.decode(body).also {
                    save(body)
                    prefs.edit().putLong(KEY_LAST_FULL, System.currentTimeMillis()).apply()
                }
            } finally { conn.disconnect() }
        }
    }
    }
    /** Derive /live.json from the user's existing authenticated /feed.json URL.
     * The API key remains on the exact same host and is never put in GitHub. */
    fun liveUrl(): String? = FeedEndpointUrls.liveFromFeed(url())

    /** Cheap foreground-only score polling, without downloading the full season. */
    suspend fun refreshLive(): Result<SportsFeed> = withContext(Dispatchers.IO) {
        syncMutex.withLock { runCatching {
            val link = liveUrl() ?: error("실시간 지원 Cloudflare 피드 URL이 아닙니다")
            val conn = URL(link).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 7000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = false // Prevent access key redirect leakage.
                conn.setRequestProperty("Accept", "application/json")
                val status = conn.responseCode
                if (status == 403) error(FeedEndpointUrls.AUTH_HELP)
                require(status in 200..299) { "스코어 API HTTP $status" }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { stream ->
                    stream.readText().also { require(it.length <= 300_000) { "스코어 피드 용량 초과" } }
                }
                val overlay = LiveCodec.decode(body)
                // Start with load(), not the static full-feed cache: it includes the
                // previous live overlay, so a sparse next response cannot erase scores.
                val base = load()
                if (overlay.games.isEmpty() || base.isDemo) return@runCatching base
                val updated = base.mergeLive(overlay)
                // Persist the merged snapshot itself. Live endpoints are intentionally
                // sparse and may stop returning a game after it leaves their time window.
                val mergedJson = FeedCodec.encode(updated)
                prefs.edit().putString(KEY_FEED, mergedJson).remove(KEY_LIVE).apply()
                sendToWatch(mergedJson)
                updated
            } finally { conn.disconnect() }
        }
    }

    }
    /** Both APKs have the same applicationId/signing key; Data Layer handles nearby transfer. */
    fun sendToWatch(json: String) {
        val put = PutDataMapRequest.create("/matchday/feed").apply {
            // Compress real-season data before transmitting to Wear OS Data Layer.
            dataMap.putString("payloadGzip", FeedTransfer.compress(json))
            dataMap.putString("theme", prefs.getString("theme", "SYSTEM") ?: "SYSTEM")
            dataMap.putLong("revision", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(put)
            .addOnFailureListener { Log.w("Matchday", "Watch sync pending", it) }
    }
    fun shareCurrent() { sendToWatch(readJson()) }
}
