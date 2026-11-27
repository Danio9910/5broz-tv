package com.fivebroz.android.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * One coherent icon family: 24-unit grid, 1.6 rounded strokes (a few solid
 * glyphs where weight helps, like play). Replaces every emoji / text symbol.
 */
object Icons {
    private fun line(name: String, vararg d: String, width: Float = 1.6f) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        d.forEach {
            addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun solid(name: String, d: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)).build()

    // navigation
    val Home = line("home", "M3 10.8 12 3l9 7.8M5.5 9.5V21h4.2v-6h4.6v6h4.2V9.5")
    val Search = line("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4")
    val Library = line("library", "M6 3h12v18l-6-4-6 4z")
    val Tonight = line("tonight", "M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5a8.5 8.5 0 1 0 11 11z")
    val Back = line("back", "M5 12h14", "M13 6l6 6-6 6") // RTL: back points right
    val Close = line("close", "M6 6l12 12", "M18 6 6 18")
    val ChevronDown = line("chevron", "M6 9l6 6 6-6")
    val Join = line("join", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4", "M10 17l5-5-5-5", "M15 12H3")

    // actions
    val Play = solid("play", "M7.5 4.6v14.8a.8.8 0 0 0 1.2.7l12-7.4a.8.8 0 0 0 0-1.4l-12-7.4a.8.8 0 0 0-1.2.7z")
    val Pause = line("pause", "M8 5v14", "M16 5v14", width = 2.6f)
    val Plus = line("plus", "M12 5v14", "M5 12h14")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5")
    val ThumbUp = line(
        "up",
        "M7 10v11",
        "M15 5.9 14 10h5.8a2 2 0 0 1 1.9 2.6l-2.3 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.8a2 2 0 0 0 1.8-1.1L12 2a3.1 3.1 0 0 1 3 3.9z",
    )
    val ThumbDown = line(
        "down",
        "M17 14V3",
        "M9 18.1 10 14H4.2a2 2 0 0 1-1.9-2.6l2.3-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.8a2 2 0 0 0-1.8 1.1L12 22a3.1 3.1 0 0 1-3-3.9z",
    )
    val Sources = line("sources", "M12 2l10 5-10 5L2 7z", "M2 17l10 5 10-5", "M2 12l10 5 10-5")
    val Trailer = line("trailer", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M10 8.5l5.5 3.5-5.5 3.5z")
    val Lock = line("lock", "M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Edit = line("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Trash = line("trash", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6")
    val Vote = line("vote", "M12 4l7 9h-4v7H9v-7H5z")
    val More = line("more", "M5 12h.01", "M12 12h.01", "M19 12h.01")
    val Star = line("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val Episodes = line("episodes", "M3 5h18", "M3 12h18", "M3 19h12")
    val Info = line("info", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 16v-4", "M12 8h.01")

    // player
    val Rewind = line("rewind", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5")
    val Forward = line("forward", "M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5")
    val Subtitles = line("subs", "M3 5h18v14H3z", "M7 15h4", "M14 15h3", "M7 11h2", "M12 11h5")
    val Audio = line("audio", "M11 5 6 9H2v6h4l5 4z", "M15.5 8.5a5 5 0 0 1 0 7", "M19 5a10 10 0 0 1 0 14")
    val Speed = line("speed", "M12 14l4-4", "M3.3 19a10 10 0 1 1 17.4 0")
    val Party = line(
        "party",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
        "M22 21v-2a4 4 0 0 0-3-3.9",
        "M16 3.1a4 4 0 0 1 0 7.8",
    )
    val Next = line("next", "M5 4l10 8-10 8z", "M19 5v14")
    val Pip = line("pip", "M21 9V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4", "M13 13h8v8h-8z")
    val Brightness = line(
        "sun",
        "M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z",
        "M12 1v2", "M12 21v2", "M4.2 4.2l1.4 1.4", "M18.4 18.4l1.4 1.4",
        "M1 12h2", "M21 12h2", "M4.2 19.8l1.4-1.4", "M18.4 5.6l1.4-1.4",
    )
    val Volume = line("volume", "M11 5 6 9H2v6h4l5 4z", "M15.5 8.5a5 5 0 0 1 0 7")
    val Expand = line("expand", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")
    val SkipIntro = line("skip", "M5 4l7 8-7 8", "M12 4l7 8-7 8")
}

/** Draws one of [Icons] at a size and tint. */
@Composable
fun Ic(icon: ImageVector, size: Dp = 22.dp, tint: Color = C.Paper, modifier: Modifier = Modifier) {
    Icon(icon, null, modifier.size(size), tint = tint)
}
