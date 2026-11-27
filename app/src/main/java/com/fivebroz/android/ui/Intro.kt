package com.fivebroz.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Opening moment (about 0.8 s, once per launch): the gold "5" is traced as an outline from the
 * bottom up, fills with gold as a glint passes over it, "broz" fades in, then everything lifts away.
 */
@Composable
fun LaunchIntro(onDone: () -> Unit) {
    val trace = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        trace.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        launch { word.animateTo(1f, tween(220)) }
        fill.animateTo(1f, tween(240))
        kotlinx.coroutines.delay(60)
        out.animateTo(0f, tween(200))
        onDone()
    }
    val gold = Brush.linearGradient(listOf(Color(0xFFF6DFA0), C.Gold, Color(0xFFA8792A)), Offset.Zero, Offset(0f, 400f))
    val five = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 150.sp)
    Box(
        Modifier.fillMaxSize().alpha(out.value).background(C.Ink950)
            .background(Brush.radialGradient(listOf(C.Gold.copy(alpha = 0.12f * fill.value), Color.Transparent), radius = 700f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.scale(1f + 0.06f * (1f - out.value)), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // outline, revealed from the bottom up
                Text(
                    "5",
                    Modifier.drawWithContent {
                        clipRect(top = size.height * (1f - trace.value)) { this@drawWithContent.drawContent() }
                    },
                    style = five.copy(brush = gold, drawStyle = Stroke(width = 3f)),
                )
                // the fill, with a glint sweeping across
                Text(
                    "5",
                    Modifier.alpha(fill.value).drawWithContent {
                        drawContent()
                        val x = size.width * (fill.value * 1.6f - 0.3f)
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                Offset(x - 60f, 0f), Offset(x + 60f, size.height),
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                        )
                    },
                    style = five.copy(brush = gold),
                )
            }
            Text(
                "broz",
                Modifier.alpha(word.value),
                fontFamily = Cormorant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                color = C.Paper.copy(alpha = 0.85f),
                letterSpacing = 6.sp,
            )
        }
    }
}
