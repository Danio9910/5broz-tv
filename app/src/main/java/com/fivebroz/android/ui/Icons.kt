package com.fivebroz.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons — the same 24×24 paths the website's navigation uses. */
object Icons {
    private fun line(d: String, name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    val Home = line("M3 10.8 12 3l9 7.8M5.5 9.5V21h4.2v-6h4.6v6h4.2V9.5", "home")
    val Search = line("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4", "search")
    val Library = line("M6 3h12v18l-6-4-6 4z", "library")
}
