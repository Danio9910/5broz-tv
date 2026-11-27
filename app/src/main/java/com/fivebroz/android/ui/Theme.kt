package com.fivebroz.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fivebroz.android.R

object C {
    val Ink950 = Color(0xFF0A0A0B)
    val Ink900 = Color(0xFF111113)
    val Ink800 = Color(0xFF1A1A1D)
    val Ink700 = Color(0xFF26262B)
    val Ink600 = Color(0xFF3A3A42)
    val Paper = Color(0xFFF4F1EA)
    val Muted = Color(0xFF8A8A93)
    val Gold = Color(0xFFC8A96A)
    val Danger = Color(0xFFE07A6B)
}

@OptIn(ExperimentalTextApi::class)
private fun cairo(w: Int) = Font(
    R.font.cairo,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun cormorant(w: Int) = Font(
    R.font.cormorant,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Cairo = FontFamily(cairo(400), cairo(500), cairo(600), cairo(700))
val Cormorant = FontFamily(cormorant(500), cormorant(600))

/** Whether we are running on a television (D-pad, 10-foot UI). */
val LocalIsTv = staticCompositionLocalOf { false }

/** Touch device with a large screen (tablet / foldable open). */
val LocalIsTablet = staticCompositionLocalOf { false }

@Composable
fun FiveBrozTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = Cairo, color = C.Paper)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Gold,
            onPrimary = C.Ink950,
            background = C.Ink950,
            surface = C.Ink900,
            onSurface = C.Paper,
            onBackground = C.Paper,
        ),
        typography = Typography(
            // Two weights only (regular + semibold): quieter, more expensive-looking.
            displayLarge = base.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 46.sp),
            headlineMedium = base.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
            titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
            bodyLarge = base.copy(fontSize = 15.sp, lineHeight = 25.sp),
            bodyMedium = base.copy(fontSize = 13.sp, lineHeight = 21.sp),
            labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            labelMedium = base.copy(fontSize = 12.sp),
        ),
        content = content,
    )
}
