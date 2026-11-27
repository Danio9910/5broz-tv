package com.fivebroz.android.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fivebroz.android.data.Preview
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV screensaver (Nuvio's "Ambient mode"): after a few idle minutes, a slow slideshow
 * of film backdrops with the time. Any key brings the app back.
 */
@Composable
fun Screensaver(items: List<Preview>) {
    val list = remember(items) { items.filter { it.background != null }.shuffled().take(30) }
    var i by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(list) {
        while (list.isNotEmpty()) {
            delay(12_000)
            i = (i + 1) % list.size
            now = Date()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(list.getOrNull(i), animationSpec = tween(1600), label = "saver") { p ->
            Box(Modifier.fillMaxSize()) {
                Img(p?.background?.replace("/w780/", "/w1280/"), Modifier.fillMaxSize().kenBurns())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Column(Modifier.align(Alignment.BottomStart).padding(48.dp)) {
                    Text(p?.name ?: "", style = MaterialTheme.typography.headlineMedium)
                    p?.year?.let { Text(it, color = C.Muted) }
                }
            }
        }
        Text(
            SimpleDateFormat("h:mm", Locale.US).format(now),
            Modifier.align(Alignment.TopEnd).padding(40.dp),
            fontSize = 44.sp,
            color = C.Paper.copy(alpha = 0.85f),
        )
    }
}

/**
 * A title's trailer playing silently behind its page (Settings → الإعلان خلفية).
 * Fades in after a short pause so the page itself appears first.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerBackground(key: String?, modifier: Modifier) {
    if (key == null || !com.fivebroz.android.data.Prefs.trailerBackground) return
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(3500)
        show = true
    }
    AnimatedVisibility(show, modifier, enter = fadeIn(tween(1200)), exit = fadeOut()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    isFocusable = false
                    val html = """
                        <html><body style="margin:0;background:#000;overflow:hidden">
                        <iframe width="100%" height="100%" frameborder="0" allow="autoplay; encrypted-media"
                          src="https://www.youtube-nocookie.com/embed/$key?autoplay=1&mute=1&controls=0&loop=1&playlist=$key&modestbranding=1&playsinline=1&rel=0&iv_load_policy=3"></iframe>
                        </body></html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.5broz.com", html, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
