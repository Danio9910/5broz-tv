package com.fivebroz.android.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.Profile

/**
 * A focusable, clickable surface. On TV the focused item lifts slightly and
 * gets a thin gold edge — the only "highlight" language in the app.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    lift: Float = 1.06f,
    focusRequester: FocusRequester? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) lift else 1f, tween(160), label = "lift")
    val fr = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier
            .scale(s)
            .then(fr)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(if (focused) 2.dp else 0.dp, if (focused) C.Gold else Color.Transparent, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) { content(focused) }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    filled: Boolean = true,
    fill: Boolean = false, // stretch to the width the caller gave (e.g. full-width form buttons)
) {
    Focusable(onClick, modifier, RoundedCornerShape(50), lift = 1.05f, focusRequester = focusRequester) { f ->
        val bg = when {
            filled -> C.Gold
            f -> C.Paper.copy(alpha = 0.16f)
            else -> C.Paper.copy(alpha = 0.08f)
        }
        Box(
            (if (fill) Modifier.fillMaxWidth() else Modifier).background(bg).padding(horizontal = 26.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) C.Ink950 else C.Paper,
            )
        }
    }
}

@Composable
fun Poster(
    item: Preview,
    width: Dp,
    onClick: () -> Unit,
    progress: Float? = null,
    rank: Int? = null,
) {
    Column(Modifier.width(width)) {
        Focusable(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { focused ->
            Box(Modifier.fillMaxSize().background(C.Ink800)) {
                if (item.poster != null) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        item.name,
                        modifier = Modifier.align(Alignment.Center).padding(10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (rank != null) {
                    Text(
                        "$rank",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, C.Ink950)))
                            .padding(horizontal = 10.dp),
                        fontFamily = Cormorant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 56.sp,
                        color = C.Gold,
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                            .background(C.Paper.copy(alpha = 0.2f)),
                    ) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(C.Gold))
                    }
                }
                if (!focused) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.06f)))
            }
        }
    }
}

/** Card width that suits the screen: generous on TV, compact on phones. */
@Composable
fun posterWidth(): Dp = if (LocalIsTv.current) 138.dp else 112.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = C.Paper,
    )
}

@Composable
fun PosterRow(
    title: String,
    items: List<Preview>,
    onOpen: (Preview) -> Unit,
    gutter: Dp,
    ranked: Boolean = false,
    progress: ((Int) -> Float?)? = null,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = gutter, vertical = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { i, it -> "${it.id}#$i" }) { i, it ->
                Poster(
                    it,
                    posterWidth(),
                    onClick = { onOpen(it) },
                    progress = progress?.invoke(i),
                    rank = if (ranked) i + 1 else null,
                )
            }
        }
    }
}

// ---------- profile avatars ----------

private val HEX = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b")

private fun parseHex(h: String): Color {
    val s = h.removePrefix("#")
    val full = if (s.length == 3) s.map { "$it$it" }.joinToString("") else s
    return Color(0xFF000000 or full.toLong(16))
}

@Composable
fun Avatar(p: Profile, size: Dp) {
    val av = p.avatar ?: ""
    val bmp = remember(av) {
        if (av.startsWith("data:image")) {
            try {
                val bytes = Base64.decode(av.substringAfter(","), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }
    val shape = RoundedCornerShape(size * 0.22f)
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val colors = HEX.findAll(av).map { parseHex(it.value) }.toList()
                .ifEmpty { listOf(C.Ink600, C.Ink800) }
            val brush = if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0]))
            else Brush.linearGradient(colors)
            Box(Modifier.fillMaxSize().background(brush))
            Text(
                p.symbol?.takeIf { it.isNotBlank() } ?: p.name.take(1),
                fontSize = (size.value * 0.38f).sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun Meta(parts: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filterNotNull().filter { it.isNotBlank() }.forEach {
            Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Dot() = Box(Modifier.size(4.dp).clip(CircleShape).background(C.Muted))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
