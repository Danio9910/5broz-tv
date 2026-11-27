package com.fivebroz.android.player

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import com.fivebroz.android.data.Stream

/** What this device's hardware can decode, so we pick files it can actually play. */
object Caps {
    private val decoders by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
    }
    private fun supports(mime: String, profile: Int? = null): Boolean = decoders.any { c ->
        c.supportedTypes.any { it.equals(mime, true) } && (profile == null || runCatching {
            c.getCapabilitiesForType(mime).profileLevels.any { it.profile == profile }
        }.getOrDefault(false))
    }
    val hevc by lazy { supports("video/hevc") }
    val hevc10 by lazy { supports("video/hevc", CodecProfileLevel.HEVCProfileMain10) }
}

/** What a stream looks like to a person, extracted from the addon's text. */
data class StreamInfo(
    val stream: Stream,
    val quality: String, // "4K", "1080p", "720p", "SD"
    val cached: Boolean, // instantly playable from Real-Debrid
    val size: String?,
    val hdr: Boolean,
    val seeders: Int?, // torrents only
    val score: Int,
) {
    val isTorrent get() = stream.url == null
}

private val SEEDS = Regex("""👤\s*(\d+)""")
private val SIZE = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)

fun describe(s: Stream, isTv: Boolean): StreamInfo {
    val text = "${s.name}\n${s.title}\n${s.filename ?: ""}"
    val low = text.lowercase()
    val quality = when {
        "2160" in low || "4k" in low -> "4K"
        "1080" in low -> "1080p"
        "720" in low -> "720p"
        else -> "SD"
    }
    val cached = "rd+" in low || "[rd+]" in low || "⚡" in text
    val uncached = "rd download" in low || "[rd download]" in low
    val hdr = Regex("""\b(hdr|hdr10|dv|dolby ?vision)\b""").containsMatchIn(low)
    val dv = Regex("""\b(dv|dolby ?vision)\b""").containsMatchIn(low)
    val m = SIZE.find(text)
    val gb = m?.let {
        val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
        if (it.groupValues[2].equals("MB", true)) v / 1024 else v
    }
    val seeders = SEEDS.find(text)?.groupValues?.get(1)?.toIntOrNull()
    var score = 0
    if (s.url == null) {
        // Torrent: what matters most is how many people share it.
        val seeds = seeders ?: 0
        score += when {
            seeds >= 200 -> 90
            seeds >= 50 -> 70
            seeds >= 15 -> 45
            seeds >= 5 -> 20
            else -> -60
        }
        // Big files start slowly and stutter on ordinary connections.
        if (gb != null) score -= when {
            gb > 12 -> 70
            gb > 6 -> 35
            gb > 4 -> 10
            else -> 0
        }
        if (quality == "4K") score -= 40
    }
    if (cached) score += 100
    if (uncached) score -= 200
    score += when (quality) {
        "1080p" -> 40
        "4K" -> if (isTv) 30 else 5
        "720p" -> 20
        else -> 0
    }
    // H.264 plays everywhere, instantly. HEVC (x265) often fails or stutters even on
    // devices that claim to support it — so it's only a fallback.
    val isHevc = Regex("""\b(x265|h\.?265|hevc)\b""").containsMatchIn(low)
    val is10 = Regex("""\b10[ -]?bit\b""").containsMatchIn(low)
    val isAvc = Regex("""\b(x264|h\.?264|avc)\b""").containsMatchIn(low) || "yts" in low || "yify" in low
    if (isAvc) score += 30
    if (isHevc) score -= if (Caps.hevc) 45 else 150
    if (isHevc && is10) score -= if (Caps.hevc10) 30 else 120
    if (dv) score -= 30 // Dolby Vision shows wrong colours on many devices
    if (Regex("""\b(cam|hdcam|ts|telesync)\b""").containsMatchIn(low)) score -= 150
    if (gb != null && gb > 25) score -= 20
    if (gb != null && gb < 0.4) score -= 10
    return StreamInfo(
        stream = s,
        quality = quality,
        cached = cached,
        size = m?.let { "${it.groupValues[1]} ${it.groupValues[2].uppercase()}" },
        hdr = hdr,
        seeders = seeders,
        score = score,
    )
}

fun rank(list: List<Stream>, isTv: Boolean): List<StreamInfo> =
    list.map { describe(it, isTv) }.sortedByDescending { it.score }
