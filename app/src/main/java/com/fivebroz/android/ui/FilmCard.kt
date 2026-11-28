package com.fivebroz.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.fivebroz.android.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tonight's clean cut-outs (made each night for the films on the home hero): the character
 * can step out of the top of the card. Anything without one shows its poster inside the card.
 */
object Cuts {
    data class Cut(val poster: String, val cut: String)

    private val lock = Mutex()
    @Volatile private var map: Map<String, Cut>? = null
    private var at = 0L

    fun now(id: String): Cut? = map?.get(id)

    suspend fun get(id: String): Cut? {
        lock.withLock {
            if (map == null || System.currentTimeMillis() - at > 3 * 3600_000L) {
                map = runCatching {
                    val o = Net.getObj("${Net.SITE}/cuts/index.json")
                    o.keys().asSequence().associateWith { k ->
                        val v = o.getJSONObject(k)
                        Cut(Net.SITE + v.getString("poster"), Net.SITE + v.getString("cut"))
                    }
                }.getOrElse { map ?: emptyMap() }
                at = System.currentTimeMillis()
            }
        }
        return map?.get(id)
    }
}

/** The film's own colour, kept deep enough for white text on top: the glow of the card. */
@Composable
fun rememberGlow(url: String?): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(Color(0xFF3A2A40)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url.replace("/w780/", "/w185/").replace("/w500/", "/w185/"))
            .allowHardware(false).size(160).build()
        val bmp = (coil.Coil.imageLoader(ctx).execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return@LaunchedEffect
        // the top three quarters only: title text usually sits at the bottom of a poster
        val top = withContext(Dispatchers.Default) {
            android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, (bmp.height * 0.75f).toInt())
        }
        val p = androidx.palette.graphics.Palette.from(top).generate()
        val sw = p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch ?: return@LaunchedEffect
        val base = Color(sw.rgb)
        val l = 0.3f * base.red + 0.59f * base.green + 0.11f * base.blue
        val f = if (l > 0.42f) 0.42f / l else 1f
        c = Color(base.red * f, base.green * f, base.blue * f)
    }
    val a by animateColorAsState(c, tween(700), label = "glow")
    return a
}

val CardInk = Color(0xFF141217)

/**
 * The page's hero: the film framed in a rounded card. When there is a clean cut-out, the
 * character stands out above the card's top edge (and steps up once as the page opens).
 * Below the art: a big round play button glowing in the film's colour, then [body].
 */
@Composable
fun FilmCard(
    id: String,
    poster: String?,
    glow: Color,
    playLabel: String?,
    playSub: String?,
    onPlay: (() -> Unit)?,
    playIcon: ImageVector = Icons.Play,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    topRoom: Dp = 64.dp,
    artModifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    var cut by remember(id) { mutableStateOf(Cuts.now(id)) }
    var lift by remember(id) { mutableStateOf<Float?>(null) } // where the character starts, 0..1 of the poster's height
    LaunchedEffect(id) {
        val c = Cuts.get(id) ?: return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(c.cut).allowHardware(false).size(40, 60).build()
        val bmp = (coil.Coil.imageLoader(ctx).execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return@LaunchedEffect
        val t = withContext(Dispatchers.Default) {
            var found = -1f
            for (y in 0 until bmp.height) {
                var n = 0
                for (x in 0 until bmp.width) if ((bmp.getPixel(x, y) ushr 24) > 140) n++
                if (n >= 2) { found = y / bmp.height.toFloat(); break }
            }
            found
        }
        if (t in 0f..0.2f) { cut = c; lift = t }
    }
    val stepUp = remember(id) { Animatable(0f) }
    LaunchedEffect(lift) { if (lift != null) stepUp.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val w = minOf(maxWidth, this.maxWidth)
        val artH = w * 1.02f
        val imgH = w * 1.5f // posters are 2:3
        val up: Dp = lift?.let { (imgH * it + 56.dp).coerceAtMost(150.dp) } ?: 0.dp
        val room = if (cut != null && lift != null) maxOf(topRoom, up + 8.dp) else topRoom
        val art = if (cut != null && lift != null) cut!!.poster else poster

        Column(Modifier.widthIn(max = w).padding(top = room), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(artH)) {
                // the poster, framed
                Box(
                    artModifier.fillMaxWidth().height(artH)
                        .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                        .background(CardInk),
                ) {
                    Img(art, Modifier.fillMaxWidth().requiredHeight(imgH).offset(y = (imgH - artH) / 2 - up))
                    Box(
                        Modifier.fillMaxWidth().height(artH).background(
                            Brush.verticalGradient(0.55f to Color.Transparent, 0.86f to CardInk.copy(alpha = 0.7f), 1f to CardInk),
                        ),
                    )
                }
                // the character, unclipped, standing out of the top edge
                if (cut != null && lift != null) {
                    Box(
                        Modifier.fillMaxWidth().height(artH + up).offset(y = -up)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                translationY = (1f - stepUp.value) * 26.dp.toPx()
                                alpha = stepUp.value
                            }
                            .drawWithContent {
                                drawContent()
                                // melt the lower part of the figure back into the card
                                drawRect(
                                    Brush.verticalGradient(0.62f to Color.Black, 0.9f to Color.Transparent),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        Img(cut!!.cut, Modifier.fillMaxWidth().requiredHeight(imgH).offset(y = (imgH - artH - up) / 2), fade = false)
                    }
                }
            }
            // the card's body
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
                    .background(CardInk)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onPlay != null) {
                    // the orb sits half over the art
                    Column(Modifier.fillMaxWidth().pullUp(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayOrb(onPlay, glow, playIcon)
                        playLabel?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        playSub?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = C.Muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        VSpace(16.dp)
                        Box(
                            Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(1.dp)
                                .background(Brush.horizontalGradient(0f to Color.Transparent, 0.5f to glow.copy(alpha = 0.9f), 1f to Color.Transparent)),
                        )
                    }
                } else {
                    VSpace(18.dp)
                }
                body()
            }
        }
    }
}

/** A big round button glowing in the film's colour: the one thing to press. */
@Composable
fun PlayOrb(onClick: () -> Unit, glow: Color, icon: ImageVector = Icons.Play, modifier: Modifier = Modifier) {
    Focusable(onClick, modifier, CircleShape, lift = 1.08f, ring = false) { f ->
        Box(
            Modifier.size(92.dp).background(glow.copy(alpha = if (f) 0.45f else 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(76.dp)
                    .shadow(22.dp, CircleShape, ambientColor = glow, spotColor = glow)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(minOf(1f, glow.red * 1.35f + 0.08f), minOf(1f, glow.green * 1.35f + 0.08f), minOf(1f, glow.blue * 1.35f + 0.08f)),
                                glow,
                            ),
                        ),
                        CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = if (f) 0.7f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Ic(icon, 30.dp, Color.White)
            }
        }
    }
}

/** Draws the content [by] higher and takes that much less room (an overlap without a gap). */
private fun Modifier.pullUp(by: Dp) = this.then(
    Modifier.layout { m, c ->
        val p = m.measure(c)
        val d = by.roundToPx()
        layout(p.width, (p.height - d).coerceAtLeast(0)) { p.place(0, -d) }
    },
)
