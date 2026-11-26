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
    var chatOpen by mutableStateOf(false)
    var partyCode by mutableStateOf("")
    var bubble by mutableStateOf<String?>(null)
    val messages = androidx.compose.runtime.mutableStateListOf<com.fivebroz.android.data.PartyMessage>()
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
    fun setSubtitleSize(scale: Float)
    val subtitleOffset: Float
    fun shiftSubtitles(deltaSec: Float)
    fun otherSource()
    val hasNextEpisode: Boolean
    fun nextEpisode()
    val inParty: Boolean
    val partyIsHost: Boolean
    fun startParty()
    fun sendChat(text: String)
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
                if (isTv) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            ui.visible = !ui.visible
                            a.poke()
                        },
                        onDoubleTap = { o ->
                            // Physical sides: left goes back, right goes forward.
                            if (o.x < size.width / 2f) a.seekBy(-10) else a.seekBy(10)
                        },
                    )
                },
            ),
    ) {
        // Buffering spinner even while controls are hidden.
        if (ui.buffering && !ui.visible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = C.Gold, strokeWidth = 2.dp)
        }
        ui.flash?.let { f ->
            Text(
                f,
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 22.dp, vertical = 10.dp),
                color = C.Paper,
                style = MaterialTheme.typography.titleLarge,
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
                            Text("→", fontSize = 22.sp, color = if (f) C.Ink950 else C.Paper)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        RoundButton("−10", 54.dp) { a.seekBy(-10) }
                        if (ui.buffering) {
                            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(52.dp), color = C.Gold, strokeWidth = 2.dp)
                            }
                        } else {
                            RoundButton(if (ui.playing) "❚❚" else "▶", 76.dp, big = true, fr = playFr) { a.togglePlay() }
                        }
                        RoundButton("+10", 54.dp) { a.seekBy(10) }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp)) {
                        Scrubber(ui, a)
                        VSpace(14.dp)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill("الترجمة") { ui.subsOpen = true }
                                Pill("مصدر آخر") { a.otherSource() }
                                if (a.hasNextEpisode && (!a.inParty || a.partyIsHost)) Pill("الحلقة التالية") { a.nextEpisode() }
                                Pill(if (a.inParty) "الغرفة ${ui.partyCode}" else "غرفة مشاهدة") { a.startParty() }
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
        if (ui.chatOpen) ChatPanel(ui, a)
    }
}

@Composable
private fun RoundButton(label: String, size: androidx.compose.ui.unit.Dp, big: Boolean = false, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, shape = CircleShape, lift = 1.1f, focusRequester = fr, ring = false) { f ->
        Box(
            Modifier.size(size)
                .background(if (f) C.Paper else if (big) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .border(if (big || f) 0.dp else 1.5.dp, C.Paper.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (big) 26.sp else 15.sp,
                color = if (f) C.Ink950 else C.Paper,
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            )
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(50), lift = 1.05f, ring = false) { f ->
        Text(
            label,
            Modifier.background(if (f) C.Paper else Color.White.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 7.dp),
            color = if (f) C.Ink950 else C.Paper,
            style = MaterialTheme.typography.labelLarge,
        )
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
                    Text(
                        (if (o.selected) "✓  " else "    ") + o.label,
                        Modifier.fillMaxWidth().background(if (f) C.Paper else C.Ink800).padding(horizontal = 14.dp, vertical = 11.dp),
                        color = if (f) C.Ink950 else if (o.selected) C.Gold else C.Paper,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            VSpace(14.dp)
            Text("توقيت الترجمة", color = C.Muted, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("أبكر ٠٫٥") { a.shiftSubtitles(-0.5f) }
                Text(
                    (if (a.subtitleOffset > 0) "+" else "") + "%.1f ث".format(a.subtitleOffset),
                    color = C.Paper,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                Pill("أبطأ ٠٫٥") { a.shiftSubtitles(0.5f) }
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
            com.fivebroz.android.ui.Field(
                text, { text = it.take(300) }, "اكتب رسالة…",
                { a.sendChat(text); text = "" },
                Modifier.fillMaxWidth(), plainText = true, center = false,
            )
            Pill("إرسال") { a.sendChat(text); text = "" }
        }
    }
}
