package com.fivebroz.android.player

import com.fivebroz.android.data.Net
import com.fivebroz.android.data.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Subtitle AutoSync helpers. */
object SubSync {
    private const val CHUNK = 65536L

    /**
     * The OpenSubtitles "movie hash" of a file: its size plus the 64-bit sum of its
     * first and last 64 KB. Subtitle sites use it to find subtitles timed for this exact file.
     */
    suspend fun hash(url: String, size: Long, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        if (size < CHUNK * 2) return@withContext null
        try {
            var h = size
            for (start in listOf(0L, size - CHUNK)) {
                val b = Request.Builder().url(url).header("Range", "bytes=$start-${start + CHUNK - 1}")
                headers.forEach { (k, v) -> b.header(k, v) }
                val bytes = Net.client.newCall(b.build()).execute().use { r ->
                    if (r.code != 206 && r.code != 200) return@withContext null
                    r.body?.bytes() ?: return@withContext null
                }
                if (bytes.size < CHUNK) return@withContext null
                val buf = ByteBuffer.wrap(bytes, 0, CHUNK.toInt()).order(ByteOrder.LITTLE_ENDIAN)
                while (buf.remaining() >= 8) h += buf.long
            }
            "%016x".format(h)
        } catch (_: Exception) {
            null
        }
    }

    /** Size of a direct link's file (for its hash). */
    suspend fun size(url: String, headers: Map<String, String>): Long? = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url(url).header("Range", "bytes=0-0")
            headers.forEach { (k, v) -> b.header(k, v) }
            Net.client.newCall(b.build()).execute().use { r ->
                r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: r.header("Content-Length")?.toLongOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private val TAGS = listOf("bluray", "brrip", "bdrip", "web-dl", "webdl", "webrip", "web", "hdtv", "dvdrip", "hdrip", "yify", "yts", "rarbg", "720p", "1080p", "2160p", "x264", "x265", "hevc", "amzn", "nf", "dsnp", "hmax", "repack", "proper", "extended")

    /** How well a subtitle's release name matches the playing file's name. */
    fun nameScore(sub: Subtitle, filename: String?): Int {
        val f = filename?.lowercase() ?: return 0
        val s = sub.file?.lowercase() ?: return 0
        var n = TAGS.count { it in f && it in s } * 2
        val group = f.substringBeforeLast('.').substringAfterLast('-').trim()
        if (group.length >= 3 && group in s) n += 6
        return n
    }

    /** The subtitle to show in a language: one made for this file, else the closest release name. */
    fun best(list: List<Subtitle>, filename: String?, pick: (Subtitle) -> Boolean): Subtitle? =
        list.filter(pick).maxWithOrNull(compareBy<Subtitle>({ it.hash }, { nameScore(it, filename) }))
}
