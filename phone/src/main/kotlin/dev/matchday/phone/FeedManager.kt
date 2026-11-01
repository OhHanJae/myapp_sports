package dev.matchday.phone

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.matchday.core.FeedCodec
import dev.matchday.core.FeedTransfer
import dev.matchday.core.SportsFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS = "matchday_settings"
private const val KEY_FEED = "cached_feed"
private const val KEY_URL = "feed_url"

/** Local cached JSON is authoritative until a new, validated HTTPS response is received. */
class FeedManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun demoJson(): String = context.assets.open("demo_feed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun readJson(): String = prefs.getString(KEY_FEED, null) ?: demoJson()
    fun load(): SportsFeed = runCatching { FeedCodec.decode(readJson()) }
        .getOrElse { FeedCodec.decode(demoJson()) }
    fun url(): String = prefs.getString(KEY_URL, "").orEmpty()
    fun setUrl(value: String) { prefs.edit().putString(KEY_URL, value.trim()).apply() }
    fun resetDemo() { prefs.edit().remove(KEY_FEED).remove(KEY_URL).apply(); sendToWatch(demoJson()) }
    fun save(json: String) {
        FeedCodec.decode(json) // Reject invalid feeds before persisting or syncing.
        prefs.edit().putString(KEY_FEED, json).apply()
        sendToWatch(json)
    }
    suspend fun refresh(): Result<SportsFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val link = url()
            require(link.startsWith("https://", ignoreCase = true)) { "HTTPS 데이터 URL을 설정해 주세요" }
            val parsedUri = Uri.parse(link)
            require(parsedUri.host != null && parsedUri.userInfo == null) { "올바른 HTTPS URL이 아닙니다" }
            require(parsedUri.port == -1 || parsedUri.port == 443) { "표준 HTTPS 포트(443)만 허용합니다" }
            val conn = URL(link).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 7000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "application/json")
                require(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().also { data -> require(data.length <= 2_000_000) { "JSON 파일이 너무 큽니다" } }
                }
                FeedCodec.decode(body).also { save(body) }
            } finally { conn.disconnect() }
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
