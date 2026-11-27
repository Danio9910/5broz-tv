package com.fivebroz.android.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.zIndex
import androidx.compose.runtime.staticCompositionLocalOf
import com.fivebroz.android.data.Prefs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.Profile

/**
 * The one interactive surface used everywhere.
 *  - TV: the focused item lifts and gets a soft gold ring + glow.
 *  - Touch: a press sinks slightly with a light haptic tick.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    lift: Float = 1.06f,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    ring: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val tv = LocalIsTv.current
    val target = when {
        pressed -> 0.96f
        focused -> lift
        else -> 1f
    }
    val s by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lift")
    val fr = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val showRing = focused && ring
    Box(
        modifier
            .scale(s)
            .then(fr)
            .onFocusChanged {
                // a soft tick as the remote moves (follows the TV's own sound setting)
                if (tv && it.isFocused && !focused) view.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN)
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clip(shape)
            // a crisp white edge on focus (no glow: a soft shadow is costly to redraw on TVs)
            .border(if (showRing) 3.dp else 0.dp, if (showRing) C.Paper else Color.Transparent, shape)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(source, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (tv) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        onClick()
                    }
                } else {
                    Modifier
                        // The remote's menu key opens the same options as a long press.
                        .onPreviewKeyEvent { e ->
                            if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) onLongClick()
                                true
                            } else {
                                false
                            }
                        }
                        .combinedClickable(
                            interactionSource = source,
                            indication = null,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                },
            ),
    ) { content(focused) }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    filled: Boolean = true,
    fill: Boolean = false, // stretch to the width the caller gave
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    // Primary actions are solid white (the way premium players do it); gold is
    // kept for accents only. Secondary actions are frosted glass.
    Focusable(onClick, modifier, RoundedCornerShape(10.dp), lift = 1.05f, focusRequester = focusRequester, ring = false) { f ->
        val bg = when {
            filled -> if (f || !LocalIsTv.current) C.Paper else C.Paper.copy(alpha = 0.88f)
            f -> C.Paper
            else -> Color.White.copy(alpha = 0.14f)
        }
        val fg = if (filled || f) C.Ink950 else C.Paper
        Row(
            (if (fill) Modifier.fillMaxWidth() else Modifier).background(bg).padding(horizontal = 24.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Ic(icon, 18.dp, fg)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/** Remote images fade in instead of popping. */
@Composable
fun Img(url: String?, modifier: Modifier = Modifier, desc: String? = null) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(180).build() },
        contentDescription = desc,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** Long-press (or the remote's menu key) on any poster opens the quick-actions menu. */
val LocalItemMenu = androidx.compose.runtime.staticCompositionLocalOf<((Preview) -> Unit)?> { null }

/** A soft breathing placeholder while content loads. */
@Composable
fun Skeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    // A slow gold glint sweeping across, instead of a grey pulse.
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600)), label = "x")
    Box(
        modifier.clip(shape).background(C.Ink800).background(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to C.Gold.copy(alpha = 0.10f),
                1f to Color.Transparent,
                start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
                end = androidx.compose.ui.geometry.Offset(x * 600f + 400f, 200f),
            ),
        ),
    )
}

@Composable
fun SkeletonRows(gutter: Dp, rows: Int = 3) {
    val w = posterWidth()
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        repeat(rows) {
            Skeleton(Modifier.padding(horizontal = gutter).size(140.dp, 16.dp))
            Row(
                Modifier.padding(start = gutter, top = 12.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(6) { Skeleton(Modifier.width(w).aspectRatio(2f / 3f)) }
            }
        }
    }
}

@Composable
fun Poster(
    item: Preview,
    width: Dp,
    onClick: () -> Unit,
    progress: Float? = null,
    onFocus: (() -> Unit)? = null,
) {
    val menu = LocalItemMenu.current
    val inst = remember { java.util.UUID.randomUUID().toString() }
    Focusable(
        { Shared.opened = inst; Shared.openedAt = System.currentTimeMillis(); onClick() },
        Modifier.width(width).aspectRatio(2f / 3f).sharedArt(inst.takeIf { Shared.opened == it }),
        RoundedCornerShape(if (Prefs.profileKids) 20.dp else 8.dp),
        onFocus = onFocus,
        onLongClick = menu?.let { m -> { m(item) } },
    ) {
        Box(Modifier.fillMaxSize().background(C.Ink800)) {
            if (item.poster != null) {
                Img(item.poster.replace("/w500/", "/w342/"), Modifier.fillMaxSize(), item.name)
            } else {
                Text(
                    item.name,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (progress != null && progress > 0f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            item.badge?.let { Badge(it, Modifier.align(Alignment.TopStart)) }
        }
    }
}

/** A small label in a card's corner: "جديد", "حلقة جديدة", "قريباً". */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(6.dp).background(if (text == "قريباً") C.Ink950.copy(alpha = 0.8f) else C.Gold, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = if (text == "قريباً") C.Paper else C.Ink950,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
    )
}

// ---------- poster → page motion (shared element) ----------

/** Which card was just opened, so its picture can grow into the title page. */
object Shared {
    var opened by androidx.compose.runtime.mutableStateOf<String?>(null)
    var openedAt = 0L

    /** The key for the page that's opening now (only right after a card was tapped). */
    fun fresh(): String? = opened.takeIf { System.currentTimeMillis() - openedAt < 1500 }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }
val LocalNavAnimScope = staticCompositionLocalOf<androidx.compose.animation.AnimatedVisibilityScope?> { null }

/** Marks a picture as the one that morphs between a card and the title page. */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedArt.sharedBounds(
            rememberSharedContentState("art-$key"),
            animatedVisibilityScope = anim,
            resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(C.Paper.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(C.Gold))
    }
}

/** Landscape card: backdrop + title (continue-watching and TV rows). */
@Composable
fun WideCard(
    item: Preview,
    width: Dp,
    onClick: () -> Unit,
    progress: Float? = null,
    subtitle: String? = null,
    onFocus: (() -> Unit)? = null,
) {
    val menu = LocalItemMenu.current
    val inst = remember { java.util.UUID.randomUUID().toString() }
    Column(Modifier.width(width)) {
        Focusable(
            { Shared.opened = inst; Shared.openedAt = System.currentTimeMillis(); onClick() }, Modifier.fillMaxWidth().aspectRatio(16f / 9f).sharedArt(inst.takeIf { Shared.opened == it }),
            RoundedCornerShape(8.dp), onFocus = onFocus,
            onLongClick = menu?.let { m -> { m(item) } },
        ) {
            Box(Modifier.fillMaxSize().background(C.Ink800)) {
                item.badge?.let { Badge(it, Modifier.align(Alignment.TopStart).zIndex(1f)) }
                val ctx = LocalContext.current
                // where you stopped, if we have the frame
                val frame = remember(item.id, progress) { if (progress != null) com.fivebroz.android.player.Frames.url(ctx, item.id) else null }
                Img(frame ?: item.background ?: item.poster, Modifier.fillMaxSize(), item.name)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)),
                    ),
                )
                Text(
                    item.name,
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress > 0f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.padding(top = 6.dp, start = 2.dp),
                color = C.Muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Netflix-style top-10 tile: a tall outlined numeral tucked behind the poster. */
@Composable
fun RankedPoster(rank: Int, item: Preview, width: Dp, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    // Fixed to the poster's height so the numeral never pushes the row taller.
    Box(Modifier.width(width * 1.55f).height(width * 1.5f)) {
        // Gold-foil outline numerals.
        Text(
            "$rank",
            Modifier.align(Alignment.BottomStart).offset(y = 18.dp),
            style = TextStyle(
                fontFamily = Cormorant,
                fontWeight = FontWeight.SemiBold,
                fontSize = (width.value * 1.05f).sp,
                brush = Brush.verticalGradient(listOf(Color(0xFFF3E3B5), C.Gold, Color(0xFF8A6A33))),
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            ),
        )
        Box(Modifier.align(Alignment.BottomEnd)) { Poster(item, width, onClick, onFocus = onFocus) }
    }
}

/** Card width that suits the screen. */
@Composable
fun posterWidth(): Dp = when {
    LocalIsTv.current -> 128.dp
    LocalIsTablet.current -> 138.dp
    else -> 108.dp
} * (if (Prefs.profileKids) 1.22f else 1f) // kids get bigger, easier cards

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, color = C.Paper)
}

@Composable
fun PosterRow(
    title: String,
    items: List<Preview>,
    onOpen: (Preview) -> Unit,
    gutter: Dp,
    ranked: Boolean = false,
    progress: ((Int) -> Float?)? = null,
    onFocusItem: ((Preview) -> Unit)? = null,
    wide: Boolean = false, // landscape cards (e.g. new episodes)
) {
    if (items.isEmpty()) return
    val w = posterWidth()
    Column(Modifier.fillMaxWidth().padding(bottom = if (LocalIsTv.current) 18.dp else 22.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = gutter, vertical = 6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (ranked) 4.dp else 10.dp),
        ) {
            itemsIndexed(items, key = { i, it -> "${it.id}#$i" }, contentType = { _, _ -> "poster" }) { i, it ->
                val focus = onFocusItem?.let { f -> { f(it) } }
                if (ranked) RankedPoster(i + 1, it, w, { onOpen(it) }, focus)
                else if (wide || com.fivebroz.android.data.Prefs.cardStyle == "wide") WideCard(it, w * 1.9f, { onOpen(it) }, progress = progress?.invoke(i), onFocus = focus)
                else Poster(it, w, onClick = { onOpen(it) }, progress = progress?.invoke(i), onFocus = focus)
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filterNotNull().filter { it.isNotBlank() }.forEach {
            Text(it, color = C.Paper.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/** Scroll only as much as needed to reveal the focused item (no TV "pivot" jump). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val MinimalScroll = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0f && end <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> end - containerSize
        }
    }
}


/** The film's own title artwork when it exists — text otherwise. */
@Composable
fun TitleArt(
    name: String,
    logo: String?,
    maxHeight: Dp,
    maxWidth: Dp,
    style: androidx.compose.ui.text.TextStyle,
    center: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = if (center) Alignment.Center else Alignment.CenterStart,
            modifier = modifier.height(maxHeight).androidxWidthIn(maxWidth),
        )
    } else {
        // No artwork: set the name in Amiri (Arabic) / Cormorant (Latin), like a film's title card.
        val arabic = name.any { it in '\u0600'..'\u06FF' }
        Text(
            name,
            modifier = modifier,
            style = style.copy(
                fontFamily = if (arabic) Amiri else Cormorant,
                fontWeight = if (arabic) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = style.fontSize * (if (arabic) 1.08f else 1.15f),
                lineHeight = style.fontSize * (if (arabic) 1.5f else 1.2f),
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 18f),
            ),
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.androidxWidthIn(max: Dp) = this.then(Modifier.widthIn(max = max).fillMaxWidth())

/**
 * A slow push-in on artwork when it appears — once, then still. (It used to breathe forever,
 * which redrew a full-screen picture every frame and made scrolling heavy on modest devices.)
 * Off entirely on low-memory devices.
 */
@Composable
fun Modifier.kenBurns(): Modifier {
    val ctx = LocalContext.current
    val light = remember {
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || mi.totalMem < 3_500_000_000L
    }
    if (light) return this
    val s = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { s.animateTo(1.05f, tween(9000, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    return this.graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

/** A deep tint taken from the artwork, used to colour the page around it. */
@Composable
fun rememberAmbient(url: String?): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(C.Ink950) }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url.replace("/w1280/", "/w300/").replace("/w780/", "/w300/"))
            .allowHardware(false).size(200).build()
        val r = coil.Coil.imageLoader(ctx).execute(req)
        val bmp = (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val p = androidx.palette.graphics.Palette.from(bmp).generate()
        val sw = p.darkMutedSwatch ?: p.mutedSwatch ?: p.darkVibrantSwatch ?: return@LaunchedEffect
        // Keep it dark and quiet: mix the colour heavily with ink.
        val base = Color(sw.rgb)
        c = Color(
            red = base.red * 0.35f + C.Ink950.red * 0.65f,
            green = base.green * 0.35f + C.Ink950.green * 0.65f,
            blue = base.blue * 0.35f + C.Ink950.blue * 0.65f,
        )
    }
    val animated by androidx.compose.animation.animateColorAsState(c, tween(700), label = "amb")
    return animated
}

/** "Browse by genre": picture tiles, one per mood. */
@Composable
fun GenreRow(genres: List<com.fivebroz.android.data.GenreTile>, gutter: Dp, onGenre: (com.fivebroz.android.data.GenreTile) -> Unit) {
    if (genres.isEmpty()) return
    val tv = LocalIsTv.current
    if (Prefs.profileKids) return KidsGenreRow(genres, gutter, onGenre)
    val w = if (tv) 230.dp else 168.dp
    Column(Modifier.fillMaxWidth().padding(bottom = if (tv) 18.dp else 22.dp)) {
        SectionTitle("تصفّح حسب النوع", Modifier.padding(horizontal = gutter, vertical = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(genres.size) { i ->
                val g = genres[i]
                Focusable({ onGenre(g) }, Modifier.width(w).aspectRatio(16f / 9f), RoundedCornerShape(10.dp)) {
                    Box(Modifier.fillMaxSize().background(C.Ink800)) {
                        Img(g.image, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.75f))))
                        Text(g.name, Modifier.align(Alignment.BottomStart).padding(12.dp), style = MaterialTheme.typography.titleMedium, color = C.Paper)
                    }
                }
            }
        }
    }
}

private val KidColors = listOf(
    listOf(Color(0xFFFF8A65), Color(0xFFFF5E8A)),
    listOf(Color(0xFF4FC3F7), Color(0xFF7C4DFF)),
    listOf(Color(0xFFFFD54F), Color(0xFFFF9800)),
    listOf(Color(0xFF81C784), Color(0xFF26A69A)),
    listOf(Color(0xFFBA68C8), Color(0xFFF06292)),
)

/** Kids' world: big, round, bright tiles with the picture in a bubble. */
@Composable
private fun KidsGenreRow(genres: List<com.fivebroz.android.data.GenreTile>, gutter: Dp, onGenre: (com.fivebroz.android.data.GenreTile) -> Unit) {
    val tv = LocalIsTv.current
    val w = if (tv) 190.dp else 136.dp
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text("عالم الصغار ✨", Modifier.padding(horizontal = gutter, vertical = 6.dp), fontFamily = Amiri, fontSize = 24.sp, color = C.Paper)
        LazyRow(contentPadding = PaddingValues(horizontal = gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(genres.size) { i ->
                val g = genres[i]
                Focusable({ onGenre(g) }, Modifier.width(w).aspectRatio(1f), RoundedCornerShape(28.dp), lift = 1.1f) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(KidColors[i % KidColors.size])), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Img(g.image, Modifier.size(w * 0.5f).clip(androidx.compose.foundation.shape.CircleShape).border(3.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
                            VSpace(8.dp)
                            Text(g.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A hint shown once ("hold for 2×"), dismissed with a tap. */
@Composable
fun OneTimeTip(key: String, text: String, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(!Prefs.tipSeen(key)) }
    if (!show) return
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(9000)
        Prefs.setTipSeen(key)
        show = false
    }
    Focusable({ Prefs.setTipSeen(key); show = false }, modifier, RoundedCornerShape(14.dp), lift = 1f, ring = false) {
        Row(
            Modifier.background(C.Paper).padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 340.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Ic(Icons.Info, 18.dp, C.Ink950)
            Text(text, color = C.Ink950, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            Text("حسناً", color = C.Ink950, style = MaterialTheme.typography.labelLarge)
        }
    }
}
