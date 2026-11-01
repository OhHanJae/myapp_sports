package dev.matchday.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Phone -> Wear Data Layer payload compression. A full KBO season may exceed
 * the ~100 KB DataItem payload threshold when sent as plain JSON.
 *
 * Keep the legacy uncompressed reader on watch for backwards compatibility.
 */
object FeedTransfer {
    private const val MAX_DECODED_BYTES = 2_000_000

    fun compress(json: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { zip ->
            zip.write(json.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    fun decompress(encoded: String): String {
        val binary = Base64.getDecoder().decode(encoded)
        GZIPInputStream(ByteArrayInputStream(binary)).use { zip ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_DECODED_BYTES) { "워치 데이터 용량 초과" }
                out.write(buffer, 0, count)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
