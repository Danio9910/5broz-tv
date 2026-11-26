package com.fivebroz.android.player

import com.fivebroz.android.data.Stream

/** What a stream looks like to a person, extracted from the addon's text. */
data class StreamInfo(
    val stream: Stream,
    val quality: String, // "4K", "1080p", "720p", "SD"
    val cached: Boolean, // instantly playable from Real-Debrid
    val size: String?,
    val hdr: Boolean,
    val score: Int,
)

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
    var score = 0
    if (cached) score += 100
    if (uncached) score -= 200
    score += when (quality) {
        "1080p" -> 40
        "4K" -> if (isTv) 30 else 5
        "720p" -> 20
        else -> 0
    }
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
        score = score,
    )
}

fun rank(list: List<Stream>, isTv: Boolean): List<StreamInfo> =
    list.map { describe(it, isTv) }.sortedByDescending { it.score }
