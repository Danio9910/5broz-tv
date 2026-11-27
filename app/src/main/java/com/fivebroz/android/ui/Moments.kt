package com.fivebroz.android.ui

import android.icu.util.IslamicCalendar
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.Prefs
import kotlinx.coroutines.launch
import java.util.Calendar

/** Days the family marks: the app dresses up for them (Hijri dates from the device's Islamic calendar). */
enum class Season(val greeting: String, val line: String) {
    Ramadan("رمضان كريم", "سهرات رمضان مع العائلة — مسلسلات الموسم بانتظاركم."),
    EidFitr("عيد فطر سعيد", "كل عام وأنتم بخير — عيدكم مبارك."),
    EidAdha("عيد أضحى مبارك", "كل عام وأنتم بخير — عيدكم مبارك."),
    NationalDay("اليوم الوطني", "دام عزك يا وطن 💚"),
    FoundingDay("يوم التأسيس", "يوم بدينا 💚"),
    ;

    val green get() = this == NationalDay || this == FoundingDay

    companion object {
        fun now(): Season? {
            val g = Calendar.getInstance()
            val m = g.get(Calendar.MONTH)
            val d = g.get(Calendar.DAY_OF_MONTH)
            if (m == Calendar.SEPTEMBER && d in 22..24) return NationalDay
            if (m == Calendar.FEBRUARY && d == 22) return FoundingDay
            return runCatching {
                val h = IslamicCalendar(java.util.Locale("ar", "SA")).apply {
                    calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
                    timeInMillis = System.currentTimeMillis()
                }
                val hm = h.get(IslamicCalendar.MONTH)
                val hd = h.get(IslamicCalendar.DAY_OF_MONTH)
                when {
                    hm == IslamicCalendar.RAMADAN -> Ramadan
                    hm == IslamicCalendar.SHAWWAL && hd <= 3 -> EidFitr
                    hm == IslamicCalendar.DHU_AL_HIJJAH && hd in 9..13 -> EidAdha
                    else -> null
                }
            }.getOrNull()
        }

        /** For screenshots and testing: a debug preference can force one. */
        fun current(): Season? = Prefs.forcedSeason?.let { f -> entries.firstOrNull { it.name == f } } ?: now()
    }
}

/** The seasonal ribbon at the top of home: a crescent and swaying lanterns, or green for national days. */
@Composable
fun SeasonBanner(g: Dp) {
    val s = remember { Season.current() } ?: return
    // The lanterns sway a few times when home opens, then rest (no endless redrawing).
    val swing = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(3) {
            swing.animateTo(6f, tween(1200))
            swing.animateTo(-6f, tween(1200))
        }
        swing.animateTo(0f, tween(900))
    }
    val a = swing.value
    val tint = if (s.green) Color(0xFF1F7A4D) else C.Gold
    Box(
        Modifier.padding(horizontal = g, vertical = 8.dp).fillMaxWidth().height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.28f), C.Ink900.copy(alpha = 0.6f))))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
    ) {
        Canvas(Modifier.align(Alignment.CenterEnd).width(130.dp).fillMaxSize()) {
            if (s.green) palm(tint) else {
                crescent(Offset(size.width * 0.75f, size.height * 0.42f), size.height * 0.26f, C.Gold)
                lantern(Offset(size.width * 0.32f, 0f), size.height * 0.52f, a, C.Gold)
                lantern(Offset(size.width * 0.52f, 0f), size.height * 0.34f, -a * 0.7f, C.Gold.copy(alpha = 0.8f))
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp)) {
            Text(s.greeting, fontFamily = Amiri, fontSize = 24.sp, color = C.Paper)
            Text(s.line, color = C.Paper.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun DrawScope.crescent(c: Offset, r: Float, color: Color) {
    val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.42f, c.y - r * 0.18f), r * 0.86f)) }
    drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outer, inner), color)
}

private fun DrawScope.lantern(top: Offset, len: Float, angle: Float, color: Color) {
    rotate(angle, pivot = top) {
        drawLine(color.copy(alpha = 0.6f), top, Offset(top.x, top.y + len), 2f)
        val w = 16.dp.toPx()
        val h = 24.dp.toPx()
        val y = top.y + len
        drawRoundRect(color, Offset(top.x - w / 2, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 3))
        drawRoundRect(Color(0xFFFFF1C9).copy(alpha = 0.85f), Offset(top.x - w / 4, y + h * 0.22f), Size(w / 2, h * 0.56f), androidx.compose.ui.geometry.CornerRadius(w / 6))
        drawCircle(color, w / 5, Offset(top.x, y - 2f))
    }
}

private fun DrawScope.palm(color: Color) {
    val base = Offset(size.width * 0.6f, size.height * 0.95f)
    val crown = Offset(size.width * 0.6f, size.height * 0.28f)
    drawLine(color, base, crown, 5f)
    for (k in 0 until 6) {
        val ang = Math.toRadians((200 + k * 28).toDouble())
        val tip = Offset(crown.x + (Math.cos(ang) * size.height * 0.42f).toFloat(), crown.y + (Math.sin(ang) * size.height * 0.32f).toFloat() + size.height * 0.1f)
        drawLine(color, crown, tip, 4f)
    }
}

/** After 9 pm: tonight's top-voted suggestion, ready to start. */
@Composable
fun MovieNightCard(g: Dp, onOpen: (Preview) -> Unit) {
    val h = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    if (!(h >= 21 || h < 2 || Prefs.forceMovieNight)) return
    var pick by remember { mutableStateOf<com.fivebroz.android.data.Suggestion?>(null) }
    LaunchedEffect(Unit) { pick = runCatching { Db.suggestions() }.getOrNull()?.firstOrNull { it.votes > 0 } }
    val s = pick ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }
    Box(
        Modifier.padding(horizontal = g, vertical = 8.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)).background(C.Ink800),
    ) {
        Img(s.item.background ?: s.item.poster, Modifier.fillMaxSize().kenBurns())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.88f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
            Text("ليلة الفيلم 🍿", color = C.Gold, style = MaterialTheme.typography.labelLarge)
            Text(s.item.name, fontFamily = Amiri, fontSize = 26.sp, color = C.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("الأعلى صوتاً الليلة · ${s.votes} ${if (s.votes == 1) "صوت" else "أصوات"}", color = C.Paper.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton(if (starting) "لحظة…" else "نبدأ؟", {
                    if (!starting) scope.launch {
                        starting = true
                        if (!quickPlay(ctx, s.item)) onOpen(s.item)
                        starting = false
                    }
                }, icon = Icons.Play)
                GoldButton("التفاصيل", { onOpen(s.item) }, filled = false)
            }
        }
    }
}

/** "ملخص 5broz": a card with this profile's year, shown in December (and any time from settings). */
@Composable
fun YearRecapCard(g: Dp, always: Boolean = false, onOpen: (Preview) -> Unit = {}) {
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) }
    if (!always && month != Calendar.DECEMBER && !Prefs.forceRecap) return
    var r by remember { mutableStateOf<Db.YearRecap?>(null) }
    LaunchedEffect(Unit) { r = Db.yearRecap() }
    val y = r ?: return
    if (y.titles == 0 && !always) return
    // one gold glint runs along the border, twice
    val glint = remember { androidx.compose.animation.core.Animatable(-0.4f) }
    LaunchedEffect(Unit) {
        repeat(2) { glint.snapTo(-0.4f); glint.animateTo(1.4f, tween(3200)) }
    }
    val x = glint.value
    Box(
        Modifier.padding(horizontal = g, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1F0E), C.Ink900, Color(0xFF1A1426))))
            .border(1.dp, Brush.linearGradient(listOf(C.Gold.copy(alpha = 0.1f), C.Gold, C.Gold.copy(alpha = 0.1f)), Offset(x * 1000f, 0f), Offset(x * 1000f + 400f, 300f)), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Column {
            Text("ملخص 5broz ${y.year}", fontFamily = Amiri, fontSize = 26.sp, color = C.Gold)
            Text("سنة ${Prefs.profileName} في المشاهدة", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            if (y.titles == 0) {
                Text("لا مشاهدات مسجّلة هذه السنة بعد.", color = C.Paper)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Stat("${y.hours}", "ساعة")
                    Stat("${y.titles}", "عنوان")
                    if (y.episodes > 0) Stat("${y.episodes}", "حلقة")
                    if (y.films > 0) Stat("${y.films}", "فيلم")
                }
                y.top?.let { t ->
                    androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                    Focusable({ onOpen(t) }, shape = RoundedCornerShape(10.dp), lift = 1.03f, ring = false) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Img(t.poster, Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(6.dp)))
                            Column {
                                Text("الأكثر مشاهدة عندك", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (t.type == "series" && y.topCount > 1) Text("${y.topCount} حلقة", color = C.Paper.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(n: String, label: String) {
    Column {
        Text(n, fontFamily = Cormorant, fontSize = 40.sp, color = C.Paper)
        Text(label, color = C.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** A film series as a shelf: its posters stacked and fanned like boxed sets, then the full row. */
@Composable
fun CollectionShelf(name: String, items: List<Preview>, onOpen: (Preview) -> Unit, g: Dp) {
    Column {
        Row(Modifier.padding(horizontal = g, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp).height(96.dp)) {
                items.take(3).reversed().forEachIndexed { i, p ->
                    val k = items.take(3).size - 1 - i // 0 = front
                    Img(
                        p.poster,
                        Modifier.align(Alignment.BottomStart).padding(start = (k * 22).dp).width(56.dp).height(84.dp)
                            .rotate((k - 1) * 7f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp)),
                    )
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(name, fontFamily = Amiri, fontSize = 22.sp, color = C.Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${items.size} ${if (items.size in 3..10) "أفلام" else "فيلم"} في السلسلة", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PosterRow("كل الأجزاء بالترتيب", items, onOpen, g)
    }
}

