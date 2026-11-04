package dev.matchday.core

import java.net.URI
import java.net.URLDecoder

/**
 * Build the authenticated live endpoint from a validated full-feed URL.
 *
 * A single URI is the source of truth for both endpoints. Keep the *raw*
 * query bytes unchanged: percent-encoded +, &, = and Unicode in access
 * keys must not be decoded then accidentally encoded a second time.
 *
 * Never log or display the returned URL because it contains the secret.
 */
object FeedEndpointUrls {
    fun liveFromFeed(fullUrl: String): String? {
        val value = fullUrl.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawFragment != null || uri.rawPath != "/feed.json" ||
            (uri.port != -1 && uri.port != 443)) return null
        val keyParameters = uri.rawQuery?.split("&")?.filter {
            it.substringBefore("=") == "key"
        }.orEmpty()
        if (keyParameters.size != 1) return null
        val rawKey = keyParameters.single().substringAfter("=", "")
        val key = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrNull()
        if (key.isNullOrBlank() || key.any { it.isISOControl() }) return null
        val pathIndex = value.indexOf("/feed.json", value.indexOf("://") + 3)
        if (pathIndex < 0) return null
        return value.replaceRange(pathIndex, pathIndex + "/feed.json".length, "/live.json")
    }

    /** Safe to show in the UI; does not contain any value from the access key. */
    const val URL_HELP = "Cloudflare 주소는 https://.../feed.json?key=접근키 형식이어야 합니다"
    const val AUTH_HELP =
        "인증 실패(HTTP 403): Cloudflare FEED_ACCESS_KEY와 URL의 ?key=값이 같은지 확인하세요"
}
