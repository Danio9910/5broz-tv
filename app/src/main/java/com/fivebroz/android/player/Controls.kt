package com.fivebroz.android.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.fivebroz.android.ui.C
import com.fivebroz.android.ui.Focusable
import com.fivebroz.android.ui.VSpace

/** Observable state of the player screen, owned by PlayerActivity. */
class PlayerUi {
    var visible by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var flash by mutableStateOf<String?>(null)
    var subsOpen by mutableStateOf(false)
    var audioOpen by mutableStateOf(false)
    var speedOpen by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var inPip by mutableStateOf(false)
    /** Zoomed to fill the screen (pinch out) instead of letterboxed. */
    var fill by mutableStateOf(false)
    var showSkipIntro by mutableStateOf(false)
    var skipLabel by mutableStateOf("تخطي المقدمة")
    /** "1080p · H.264 · 5.1" for what's playing. */
    var badges by mutableStateOf<String?>(null)
    var nightMode by mutableStateOf(false)
    var held2x by mutableStateOf(false)
    var moreOpen by mutableStateOf(false)
    var episodesOpen by mutableStateOf(false)
    /** Intro / credits stretches on the timeline, as fractions of the film. */
    var marks by mutableStateOf<List<Pair<Float, Float>>>(emptyList())
    /** Brightness / volume swipe feedback: which one, and level 0..1. */
    var gesture by mutableStateOf<Pair<androidx.compose.ui.graphics.vector.ImageVector, Float>?>(null)
    var chatOpen by mutableStateOf(false)
    var partyCode by mutableStateOf("")
    var bubble by mutableStateOf<String?>(null)
    val messages = androidx.compose.runtime.mutableStateListOf<com.fivebroz.android.data.PartyMessage>()
    /** Emoji reactions floating up the screen in a watch party (id to emoji). */
    val floaters = androidx.compose.runtime.mutableStateListOf<Pair<Long, String>>()
    var lastPoke = 0L
}

data class SubOption(val label: String, val selected: Boolean, val select: () -> Unit)

interface PlayerActions {
    fun togglePlay()
    fun seekTo(ms: Long)
    fun seekBy(sec: Int)
    fun back()
    fun poke()
    fun subtitleOptions(): List<SubOption>
    fun audioOptions(): List<SubOption>
    fun setSubtitleSize(scale: Float)
    val subtitleOffset: Float
    fun shiftSubtitles(deltaSec: Float)
    fun resetSubtitleDelay()
    fun applySubStyle()
    val secondLanguage: Boolean
    fun setSecondLanguage(on: Boolean)
    fun otherSource()
    val hasNextEpisode: Boolean
    fun nextEpisode()
    val inParty: Boolean
    val partyIsHost: Boolean
    fun startParty()
    fun sendChat(text: String)
    fun setSpeed(speed: Float)
    fun skipIntro()
    fun setFill(fill: Boolean)
    val canPip: Boolean
    fun enterPip()
    /** Swipe on the left/right half: brightness / volume. Returns the new level 0..1. */
    fun adjustBrightness(delta: Float): Float
    fun adjustVolume(delta: Float): Float
    fun hold2x(on: Boolean)
    fun setNightMode(on: Boolean)
    fun openExternal()
    /** Episodes of this series (empty for films), for the in-player picker. */
    val episodes: List<com.fivebroz.android.data.Episode>
    val currentVideoId: String
    fun playEpisode(e: com.fivebroz.android.data.Episode)
}

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Custom transport: big centre play/pause with ±10s, a gold scrubber,
 * subtitle and source shortcuts. Tap / double-tap on touch, D-pad on TV.
 */
@Composable
fun PlayerControls(ui: PlayerUi, title: String, isTv: Boolean, a: PlayerActions) {
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (isTv) Modifier else Modifier.playerGestures(ui, a).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            ui.visible = !ui.visible
                            a.poke()
                        },
                        onDoubleTap = { o ->
                            // Physical sides: left goes back, right goes forward.
                            if (o.x < size.width / 2f) a.seekBy(-10) else a.seekBy(10)
                        },
                        // Hold anywhere: 2× while the finger stays down (like Nuvio / YouTube).
                        onLongPress = { a.hold2x(true) },
                        onPress = {
                            tryAwaitRelease()
                            if (ui.held2x) a.hold2x(false)
                        },
                    )
                },
            ),
    ) {
        // Buffering spinner even while controls are hidden.
        if (ui.buffering && !ui.visible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = C.Paper, strokeWidth = 2.dp)
        }
        ui.flash?.let { f ->
            Text(
                f,
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 22.dp, vertical = 10.dp),
                color = C.Paper,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        ui.gesture?.let { (icon, level) -> GestureHud(icon, level) }
        if (!isTv && ui.visible) {
            com.fivebroz.android.ui.OneTimeTip("hold2x", "اضغط مطوّلاً على الفيديو لتسريعه 2×، واسحب يميناً أو يساراً للصوت والإضاءة.", Modifier.align(Alignment.TopCenter).padding(top = 76.dp))
        }
        if (ui.held2x) {
            Text(
                "2× ▶▶",
                Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
                color = C.Paper,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        AnimatedVisibility(ui.visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.75f),
                            0.25f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                )
                // Title bar (reads right-to-left).
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Focusable(a::back, shape = CircleShape, lift = 1.08f, ring = false) { f ->
                        Box(Modifier.size(40.dp).background(if (f) C.Paper else Color.Transparent), contentAlignment = Alignment.Center) {
                            com.fivebroz.android.ui.Ic(com.fivebroz.android.ui.Icons.Back, 22.dp, if (f) C.Ink950 else C.Paper)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ui.badges?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                    if (!isTv) {
                        TopIcon(com.fivebroz.android.ui.Icons.Expand, ui.fill) { a.setFill(!ui.fill) }
                        if (a.canPip) {
                            Spacer(Modifier.width(6.dp))
                            TopIcon(com.fivebroz.android.ui.Icons.Pip, false) { a.enterPip() }
                        }
                    }
                }

                // Transport — always left-to-right, like the timeline it controls.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val playFr = remember { FocusRequester() }
                    LaunchedEffect(Unit) { if (isTv) runCatching { playFr.requestFocus() } }
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 48.dp else 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(com.fivebroz.android.ui.Icons.Rewind, "10", 56.dp) { a.seekBy(-10) }
                        if (ui.buffering) {
                            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(52.dp), color = C.Paper, strokeWidth = 2.dp)
                            }
                        } else {
                            RoundButton(if (ui.playing) com.fivebroz.android.ui.Icons.Pause else com.fivebroz.android.ui.Icons.Play, null, 78.dp, big = true, fr = playFr) { a.togglePlay() }
                        }
                        RoundButton(com.fivebroz.android.ui.Icons.Forward, "10", 56.dp) { a.seekBy(10) }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp)) {
                        Scrubber(ui, a)
                        VSpace(14.dp)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val I = com.fivebroz.android.ui.Icons
                                Pill("الترجمة والصوت", I.Subtitles) { ui.subsOpen = true }
                                if (!a.inParty) Pill(if (ui.speed == 1f) "السرعة" else speedLabel(ui.speed), I.Speed) { ui.speedOpen = true }
                                if (a.episodes.isNotEmpty() && !a.inParty) Pill("الحلقات", I.Episodes) { ui.episodesOpen = true }
                                if (a.hasNextEpisode && (!a.inParty || a.partyIsHost)) Pill("الحلقة التالية", I.Next) { a.nextEpisode() }
                                Pill("المزيد", I.More) { ui.moreOpen = true }
                            }
                        }
                    }
                }
            }
        }

        ui.bubble?.let { b ->
            if (!ui.chatOpen) {
                Text(
                    b,
                    Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 420.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    color = C.Paper,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
            }
        }
        if (ui.subsOpen) SubtitlePanel(ui, a)
        if (ui.audioOpen) AudioPanel(ui, a)
        if (ui.speedOpen) SpeedPanel(ui, a)
        if (ui.moreOpen) MorePanel(ui, a, isTv)
        if (ui.episodesOpen) EpisodesPanel(ui, a)
        // Above the controls so it stays reachable; on TV the OK key takes it while controls are hidden.
        if (ui.showSkipIntro && !ui.subsOpen && !ui.audioOpen && !ui.speedOpen && !ui.chatOpen) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = if (ui.visible) 118.dp else 40.dp)) {
                Focusable({ a.skipIntro() }, shape = RoundedCornerShape(10.dp), lift = 1.05f, ring = false) { f ->
                    Row(
                        Modifier.background(if (f || isTv) C.Paper else Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, C.Paper.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val fg = if (f || isTv) C.Ink950 else C.Paper
                        Text(ui.skipLabel, color = fg, style = MaterialTheme.typography.labelLarge)
                        com.fivebroz.android.ui.Ic(com.fivebroz.android.ui.Icons.SkipIntro, 16.dp, fg)
                    }
                }
            }
        }
        // Watch party: tap an emoji, everyone in the room sees it float up.
        if (ui.partyCode.isNotEmpty() && ui.visible && !ui.chatOpen && !ui.subsOpen && !ui.moreOpen) {
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                androidx.compose.foundation.layout.Column {
                    REACTIONS.forEach { e ->
                        Focusable({ a.sendChat(e) }, shape = androidx.compose.foundation.shape.CircleShape, lift = 1.2f, ring = false) {
                            Text(e, Modifier.padding(7.dp), fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        ui.floaters.forEach { (id, e) -> androidx.compose.runtime.key(id) { Floater(id, e) { ui.floaters.removeAll { it.first == id } } } }
        if (ui.chatOpen) ChatPanel(ui, a)
    }
}

val REACTIONS = listOf("❤️", "😂", "😮", "👏", "🔥", "😢")

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Floater(id: Long, emoji: String, onDone: () -> Unit) {
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    val lane = remember { ((id % 5) - 2) * 18f }
    LaunchedEffect(Unit) {
        t.animateTo(1f, androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        onDone()
    }
    Text(
        emoji,
        Modifier.align(Alignment.BottomEnd).padding(end = 70.dp, bottom = 90.dp).graphicsLayer {
            val v = t.value
            translationY = -v * 520f * density / 2.6f
            translationX = (lane + kotlin.math.sin(v * 9f) * 14f) * density
            val sc = if (v < 0.15f) v / 0.15f * 1.3f else 1.3f - (v - 0.15f) * 0.35f
            scaleX = sc
            scaleY = sc
            alpha = if (v > 0.7f) (1f - v) / 0.3f else 1f
        },
        fontSize = 34.sp,
    )
}

@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Focusable(onClick, shape = CircleShape, lift = 1.08f, ring = false) { f ->
        Box(
            Modifier.size(40.dp).background(if (f) C.Paper else if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { com.fivebroz.android.ui.Ic(icon, 20.dp, if (f) C.Ink950 else C.Paper) }
    }
}

fun speedLabel(s: Float): String =
    if (s == 1f) "عادية" else (if (s % 1f == 0f) "%.0f".format(java.util.Locale.US, s) else "%s".format(java.util.Locale.US, s.toString().trimEnd('0'))) + "×"

/**
 * Touch gestures beyond tap: vertical swipe on the left half = brightness,
 * right half = volume (physical sides), pinch = fill/fit.
 */
private fun Modifier.playerGestures(ui: PlayerUi, a: PlayerActions): Modifier = this
    .pointerInput(Unit) {
        // Pinch: two fingers apart = fill the screen, together = fit.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            do {
                val e = awaitPointerEvent()
                if (e.changes.count { it.pressed } >= 2) {
                    zoom *= e.calculateZoom()
                    if (zoom > 1.15f && !ui.fill) { a.setFill(true); zoom = 1f }
                    if (zoom < 0.87f && ui.fill) { a.setFill(false); zoom = 1f }
                    e.changes.forEach { it.consume() }
                }
            } while (e.changes.any { it.pressed })
        }
    }
    .pointerInput(Unit) {
        var left = false
        var hideJob: kotlinx.coroutines.Job? = null
        coroutineScope {
            detectVerticalDragGestures(
                onDragStart = { o ->
                    left = o.x < size.width / 2f
                    hideJob?.cancel()
                },
                onDragEnd = {
                    hideJob = launch { kotlinx.coroutines.delay(700); ui.gesture = null }
                },
                onDragCancel = { ui.gesture = null },
            ) { change, dy ->
                if (ui.subsOpen || ui.audioOpen || ui.speedOpen || ui.chatOpen || ui.moreOpen || ui.episodesOpen) return@detectVerticalDragGestures
                change.consume()
                // A full-height swipe moves the level by ~120%.
                val delta = -dy / size.height * 1.2f
                ui.gesture = if (left) {
                    com.fivebroz.android.ui.Icons.Brightness to a.adjustBrightness(delta)
                } else {
                    com.fivebroz.android.ui.Icons.Volume to a.adjustVolume(delta)
                }
            }
        }
    }

@Composable
private fun androidx.compose.foundation.layout.BoxScope.GestureHud(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Float) {
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.fivebroz.android.ui.Ic(icon, 20.dp, C.Paper)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.width(140.dp).height(4.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(C.Paper, RoundedCornerShape(2.dp)))
            }
        }
        Text("${(level.coerceIn(0f, 1f) * 100).toInt()}%", color = C.Paper, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 40.dp))
    }
}

/** Playback speed, 0.5× – 2×. */
@Composable
private fun SpeedPanel(ui: PlayerUi, a: PlayerActions) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { ui.speedOpen = false } },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp).background(C.Ink900)
                .verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("سرعة التشغيل", style = MaterialTheme.typography.titleLarge)
            VSpace(6.dp)
            speeds.forEach { sp ->
                val o = SubOption(speedLabel(sp), ui.speed == sp) { a.setSpeed(sp) }
                Focusable(
                    { o.select(); ui.speedOpen = false },
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(10.dp),
                    lift = 1.02f,
                    focusRequester = if (ui.speed == sp) first else null,
                    ring = false,
                ) { f -> OptionRow(o, f) }
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    size: androidx.compose.ui.unit.Dp,
    big: Boolean = false,
    fr: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(onClick, shape = CircleShape, lift = 1.1f, focusRequester = fr, ring = false) { f ->
        val tint = if (f) C.Ink950 else C.Paper
        Box(
            Modifier.size(size).background(if (f) C.Paper else if (big) Color.White.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            com.fivebroz.android.ui.Ic(icon, if (big) 34.dp else 36.dp, tint)
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 10.sp,
                    color = tint,
                    style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(50), lift = 1.05f, ring = false) { f ->
        val fg = if (f) C.Ink950 else C.Paper
        Row(
            Modifier.background(if (f) C.Paper else Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) com.fivebroz.android.ui.Ic(icon, 17.dp, fg)
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Gold timeline. Drag/tap on touch; on TV focus it and use ◀ ▶. */
@Composable
private fun Scrubber(ui: PlayerUi, a: PlayerActions) {
    var focused by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val dur = ui.duration.coerceAtLeast(1L)
    val frac = dragFrac ?: (ui.position.toFloat() / dur).coerceIn(0f, 1f)
    val bufFrac = (ui.buffered.toFloat() / dur).coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmt(dragFrac?.let { (it * dur).toLong() } ?: ui.position), color = C.Paper, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 54.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(28.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { a.seekBy(-10); true }
                        Key.DirectionRight -> { a.seekBy(10); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(dur) {
                    detectTapGestures { o -> a.seekTo((o.x / size.width * dur).toLong()) }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f); a.poke() },
                        onDragEnd = { dragFrac?.let { a.seekTo((it * dur).toLong()) }; dragFrac = null },
                        onDragCancel = { dragFrac = null },
                        onHorizontalDrag = { ch, _ -> dragFrac = (ch.position.x / size.width).coerceIn(0f, 1f); a.poke() },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val h = if (focused || dragFrac != null) 6.dp else 4.dp
            widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            Box(Modifier.fillMaxWidth().height(h).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)))
            Box(Modifier.fillMaxWidth(bufFrac).height(h).background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50)))
            // Intro and credits, from IntroDB: a brighter stretch on the line.
            ui.marks.forEach { (s, e) ->
                Box(Modifier.offset(x = maxWidth * s).width(maxWidth * (e - s).coerceAtLeast(0.004f)).height(h).background(C.Paper.copy(alpha = 0.55f)))
            }
            Box(Modifier.fillMaxWidth(frac).height(h).background(C.Gold, RoundedCornerShape(50)))
            val thumb = if (focused || dragFrac != null) 18.dp else 13.dp
            Box(
                Modifier.offset(x = maxWidth * frac - thumb / 2).size(thumb).background(if (focused) C.Paper else C.Gold, CircleShape),
            )
        }
        Text(
            "-" + fmt(dur - (dragFrac?.let { (it * dur).toLong() } ?: ui.position)),
            color = C.Muted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 60.dp),
        )
    }
}

@Composable
private fun SubtitlePanel(ui: PlayerUi, a: PlayerActions) {
    val options = remember { a.subtitleOptions() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { ui.subsOpen = false } },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp).background(C.Ink900)
                .verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val audio = remember { a.audioOptions() }
            if (audio.isNotEmpty()) {
                Text("الصوت", style = MaterialTheme.typography.titleLarge)
                VSpace(6.dp)
                audio.forEach { o ->
                    Focusable({ o.select(); ui.subsOpen = false }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), lift = 1.02f, ring = false) { f -> OptionRow(o, f) }
                }
                VSpace(14.dp)
            }
            Pill(if (ui.nightMode) "✓ الوضع الليلي" else "الوضع الليلي") { a.setNightMode(!ui.nightMode) }
            VSpace(14.dp)
            Text("الترجمة", style = MaterialTheme.typography.titleLarge)
            VSpace(6.dp)
            options.forEachIndexed { i, o ->
                Focusable(
                    { o.select(); ui.subsOpen = false },
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(10.dp),
                    lift = 1.02f,
                    focusRequester = if (i == 0) first else null,
                    ring = false,
                ) { f ->
                    OptionRow(o, f)
                }
            }
            VSpace(14.dp)
            Text("توقيت الترجمة (يُحفظ لهذا الفيديو)", color = C.Muted, style = MaterialTheme.typography.labelLarge)
            var off by remember { mutableFloatStateOf(a.subtitleOffset) }
            Text(
                (if (off > 0) "+" else "") + "%.1f ث".format(java.util.Locale.US, off),
                color = C.Paper,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("−1") { a.shiftSubtitles(-1f); off = a.subtitleOffset }
                Pill("−0.1") { a.shiftSubtitles(-0.1f); off = a.subtitleOffset }
                Pill("+0.1") { a.shiftSubtitles(0.1f); off = a.subtitleOffset }
                Pill("+1") { a.shiftSubtitles(1f); off = a.subtitleOffset }
            }
            if (off != 0f) Pill("رجوع للتوقيت التلقائي") { a.resetSubtitleDelay(); off = 0f }
            VSpace(14.dp)
            Text("لغة ثانية", color = C.Muted, style = MaterialTheme.typography.labelLarge)
            var second by remember { mutableStateOf(a.secondLanguage) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (second) "✓ الإنجليزية تحت العربية" else "الإنجليزية تحت العربية") {
                    second = !second
                    a.setSecondLanguage(second)
                }
            }
            VSpace(14.dp)
            Text("الشكل", color = C.Muted, style = MaterialTheme.typography.labelLarge)
            var color by remember { mutableStateOf(com.fivebroz.android.data.Prefs.subColor) }
            var box by remember { mutableStateOf(com.fivebroz.android.data.Prefs.subBox) }
            var pos by remember { mutableFloatStateOf(com.fivebroz.android.data.Prefs.subPos) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (color == "white") "✓ أبيض" else "أبيض") { color = "white"; com.fivebroz.android.data.Prefs.subColor = color; a.applySubStyle() }
                Pill(if (color == "yellow") "✓ أصفر" else "أصفر") { color = "yellow"; com.fivebroz.android.data.Prefs.subColor = color; a.applySubStyle() }
                Pill(if (box) "✓ خلفية" else "خلفية") { box = !box; com.fivebroz.android.data.Prefs.subBox = box; a.applySubStyle() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("أسفل" to 0.08f, "أعلى قليلاً" to 0.18f, "أعلى" to 0.3f).forEach { (l, v) ->
                    Pill(if (pos == v) "✓ $l" else l) { pos = v; com.fivebroz.android.data.Prefs.subPos = v; a.applySubStyle() }
                }
            }
            VSpace(14.dp)
            Text("حجم الخط", color = C.Muted, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("صغير" to 0.85f, "متوسط" to 1.1f, "كبير" to 1.4f).forEach { (l, s) ->
                    Pill(l) { a.setSubtitleSize(s); ui.subsOpen = false }
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(ui: PlayerUi, a: PlayerActions) {
    var text by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { ui.chatOpen = false } },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(360.dp).background(C.Ink900)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("غرفة المشاهدة", style = MaterialTheme.typography.titleLarge)
            if (ui.partyCode.isNotEmpty()) {
                Text("الرمز", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    ui.partyCode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        letterSpacing = 6.sp,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                    ),
                    color = C.Gold,
                )
                Text(
                    if (a.partyIsHost) "شارك الرمز — من يدخل به يشاهد معك بنفس اللحظة." else "أنت تشاهد مع المضيف.",
                    color = C.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.messages.isEmpty()) Text("لا رسائل بعد.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                ui.messages.forEach { m ->
                    Column(Modifier.fillMaxWidth().background(C.Ink800, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(m.sender ?: "ضيف", color = C.Gold, style = MaterialTheme.typography.labelMedium)
                        Text(m.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                REACTIONS.forEach { e ->
                    Focusable({ a.sendChat(e) }, shape = androidx.compose.foundation.shape.CircleShape, lift = 1.2f, ring = false) {
                        Text(e, Modifier.padding(6.dp), fontSize = 22.sp)
                    }
                }
            }
            com.fivebroz.android.ui.Field(
                text, { text = it.take(300) }, "اكتب رسالة…",
                { a.sendChat(text); text = "" },
                Modifier.fillMaxWidth(), plainText = true, center = false,
            )
            Pill("إرسال") { a.sendChat(text); text = "" }
        }
    }
}

@Composable
private fun OptionRow(o: SubOption, f: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (f) C.Paper else C.Ink800).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp)) {
            if (o.selected) com.fivebroz.android.ui.Ic(com.fivebroz.android.ui.Icons.Check, 18.dp, if (f) C.Ink950 else C.Gold)
        }
        Text(o.label, color = if (f) C.Ink950 else C.Paper, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Choose the soundtrack — dubbed Arabic, original, commentary… */
@Composable
private fun AudioPanel(ui: PlayerUi, a: PlayerActions) {
    val options = remember { a.audioOptions() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { ui.audioOpen = false } },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp).background(C.Ink900)
                .verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("الصوت", style = MaterialTheme.typography.titleLarge)
            VSpace(6.dp)
            Pill(if (ui.nightMode) "✓ الوضع الليلي (يرفع الكلام ويخفّض الانفجارات)" else "الوضع الليلي (يرفع الكلام ويخفّض الانفجارات)") {
                a.setNightMode(!ui.nightMode)
            }
            VSpace(6.dp)
            if (options.isEmpty()) Text("هذا الملف فيه مسار صوت واحد.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            options.forEachIndexed { i, o ->
                Focusable(
                    { o.select(); ui.audioOpen = false },
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(10.dp),
                    lift = 1.02f,
                    focusRequester = if (i == 0) first else null,
                    ring = false,
                ) { f -> OptionRow(o, f) }
            }
        }
    }
}

/** Everything that isn't needed every minute: another source, an external player, a watch party. */
@Composable
private fun MorePanel(ui: PlayerUi, a: PlayerActions, isTv: Boolean) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { ui.moreOpen = false } },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(320.dp).background(C.Ink900).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("المزيد", style = MaterialTheme.typography.titleLarge)
            VSpace(6.dp)
            val I = com.fivebroz.android.ui.Icons
            val rows = buildList {
                add(Triple(I.Sources, "مصدر آخر") { a.otherSource() })
                if (!isTv) add(Triple(I.Expand, "فتح في مشغّل آخر") { a.openExternal() })
                add(Triple(I.Party, if (a.inParty) "الغرفة ${ui.partyCode}" else "غرفة مشاهدة") { a.startParty() })
            }
            rows.forEachIndexed { i, (icon, label, go) ->
                Focusable({ ui.moreOpen = false; go() }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), lift = 1.02f, focusRequester = if (i == 0) first else null, ring = false) { f ->
                    Row(
                        Modifier.fillMaxWidth().background(if (f) C.Paper else C.Ink800).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.fivebroz.android.ui.Ic(icon, 20.dp, if (f) C.Ink950 else C.Paper)
                        Text(label, color = if (f) C.Ink950 else C.Paper, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Pick another episode without leaving the film. */
@Composable
private fun EpisodesPanel(ui: PlayerUi, a: PlayerActions) {
    val eps = a.episodes
    val current = eps.firstOrNull { it.id == a.currentVideoId }
    var season by remember { mutableStateOf(current?.season ?: eps.firstOrNull()?.season ?: 1) }
    val seasons = remember(eps) { eps.map { it.season }.distinct().sorted() }
    val list = eps.filter { it.season == season }
    val first = remember { FocusRequester() }
    LaunchedEffect(season) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { ui.episodesOpen = false } },
    ) {
        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(420.dp).background(C.Ink900).padding(22.dp)) {
            Text("الحلقات", style = MaterialTheme.typography.titleLarge)
            VSpace(8.dp)
            if (seasons.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { s -> Pill(if (s == season) "✓ الموسم $s" else "الموسم $s") { season = s } }
                }
                VSpace(10.dp)
            }
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list.size) { i ->
                    val e = list[i]
                    val now = e.id == a.currentVideoId
                    Focusable(
                        { ui.episodesOpen = false; if (!now) a.playEpisode(e) },
                        Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), lift = 1.02f,
                        focusRequester = if (now || (current?.season != season && i == 0)) first else null, ring = false,
                    ) { f ->
                        Row(
                            Modifier.fillMaxWidth().background(if (f) C.Paper else if (now) C.Ink700 else C.Ink800).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.width(112.dp).height(63.dp).background(C.Ink700, RoundedCornerShape(6.dp))) {
                                e.thumbnail?.let { com.fivebroz.android.ui.Img(it, Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${e.episode}. ${e.title}", color = if (f) C.Ink950 else C.Paper, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (now) Text("تشاهدها الآن", color = if (f) C.Ink950 else C.Gold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
