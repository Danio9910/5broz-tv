package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Prefs
import kotlinx.coroutines.launch

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(50), lift = 1.05f, ring = false) { f ->
        Text(
            label,
            Modifier.background(if (f) C.Paper else if (on) C.Gold.copy(alpha = 0.22f) else C.Ink800)
                .then(if (on && !f) Modifier.border(1.dp, C.Gold, RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (f) C.Ink950 else C.Paper,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium) }
        VSpace(10.dp)
        content()
    }
}

/** Everything the family can tune, in one place (per profile unless noted). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAddons: () -> Unit) {
    val tv = LocalIsTv.current
    var tick by remember { mutableIntStateOf(0) } // redraw after a change
    if (tick < 0) return // reading it makes the screen redraw after each change
    fun changed() { tick++ }
    val gap = Arrangement.spacedBy(8.dp)

    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .then(if (tv) Modifier.padding(48.dp) else Modifier.statusBarsPadding().padding(horizontal = 16.dp)),
        ) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Focusable(onBack, shape = CircleShape, lift = 1f, ring = false) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Back, 22.dp) }
                }
                Text("الإعدادات", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
            }

            Section("المظهر", "لكل ملف ألوانه.") {
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    Look.accents.forEachIndexed { i, (name, color) ->
                        Focusable({ Prefs.accent = i; Look.load(); changed() }, shape = RoundedCornerShape(50), lift = 1.08f, ring = false) { f ->
                            Row(
                                Modifier.background(if (f) C.Paper else C.Ink800).padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(16.dp).background(color, CircleShape).then(if (Prefs.accent == i) Modifier.border(2.dp, C.Paper, CircleShape) else Modifier))
                                Text(name, color = if (f) C.Ink950 else C.Paper, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                VSpace(8.dp)
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    Choice("أسود كامل (لشاشات OLED)", Prefs.amoled) { Prefs.amoled = !Prefs.amoled; Look.load(); changed() }
                    Choice("بطاقات طولية", Prefs.cardStyle == "poster") { Prefs.cardStyle = "poster"; changed() }
                    Choice("بطاقات عرضية", Prefs.cardStyle == "wide") { Prefs.cardStyle = "wide"; changed() }
                }
            }

            Section("صفوف الرئيسية", "أخفِ ما لا يهمك ورتّب الباقي. تظهر هنا بعد فتح الرئيسية مرة.") {
                val hidden = Prefs.hiddenRows
                val order = Prefs.rowOrder
                val titles = lastRowTitles.sortedBy { t -> order.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it } }
                titles.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t, Modifier.weight(1f), color = if (t in hidden) C.Muted else C.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Choice(if (t in hidden) "مخفي" else "ظاهر", t !in hidden) {
                            Prefs.hiddenRows = if (t in hidden) hidden - t else hidden + t
                            changed()
                        }
                        if (i > 0) {
                            VSpace(0.dp)
                            Choice("↑", false) {
                                val list = titles.toMutableList()
                                list.removeAt(i)
                                list.add(i - 1, t)
                                Prefs.rowOrder = list
                                changed()
                            }
                        }
                    }
                }
            }

            Section("التشغيل") {
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    Choice("يبدأ أسرع مصدر تلقائياً", Prefs.streamMode == "auto") { Prefs.streamMode = "auto"; changed() }
                    Choice("أختار المصدر بنفسي دائماً", Prefs.streamMode == "manual") { Prefs.streamMode = "manual"; changed() }
                    Choice("الوضع الليلي للصوت", Prefs.nightMode) { Prefs.nightMode = !Prefs.nightMode; changed() }
                    Choice("الإعلان خلفية لصفحة العنوان", Prefs.trailerBackground) { Prefs.trailerBackground = !Prefs.trailerBackground; changed() }
                    if (tv) {
                        Choice("مطابقة معدّل الإطارات", Prefs.matchFrameRate) { Prefs.matchFrameRate = !Prefs.matchFrameRate; changed() }
                        Choice("شاشة التوقف", Prefs.screensaver) { Prefs.screensaver = !Prefs.screensaver; changed() }
                    }
                }
            }

            Section("الترجمة", "تُطبّق في كل الأفلام. التوقيت يُضبط تلقائياً، ويُحفظ أي تعديل يدوي لكل فيديو.") {
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    Choice("أبيض", Prefs.subColor == "white") { Prefs.subColor = "white"; changed() }
                    Choice("أصفر", Prefs.subColor == "yellow") { Prefs.subColor = "yellow"; changed() }
                    Choice("خلفية داكنة", Prefs.subBox) { Prefs.subBox = !Prefs.subBox; changed() }
                    listOf("صغير" to 0.85f, "متوسط" to 1.1f, "كبير" to 1.4f).forEach { (l, v) ->
                        Choice(l, Prefs.subScale == v) { Prefs.subScale = v; changed() }
                    }
                    listOf("أسفل" to 0.08f, "أعلى قليلاً" to 0.18f, "أعلى" to 0.3f).forEach { (l, v) ->
                        Choice(l, Prefs.subPos == v) { Prefs.subPos = v; changed() }
                    }
                    Choice("الإنجليزية تحت العربية", Prefs.subSecond) { Prefs.subSecond = !Prefs.subSecond; changed() }
                }
            }

            Section("الحلقات") {
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    Choice("إخفاء صور الحلقات غير المشاهدة (بدون حرق)", Prefs.spoilerBlur) { Prefs.spoilerBlur = !Prefs.spoilerBlur; changed() }
                }
            }

            if (!Prefs.profileKids) {
                Section("الإضافات", "مصادر إضافية لكل البيت، مثل Stremio وNuvio.") {
                    GoldButton("إدارة الإضافات", onAddons, filled = false, icon = Icons.Sources)
                }
            }

            if (!Prefs.profileKids) {
                Section("Trakt", "مزامنة ما تشاهده، وقائمتك وتوصياتك من Trakt تظهر في الرئيسية. لكل ملف حسابه.") { TraktLink() }
            }

            Section("عن التطبيق") {
                Text("5broz — الإصدار ${com.fivebroz.android.BuildConfig.VERSION_NAME}", color = C.Muted)
            }
            VSpace(40.dp)
        }
    }
}

@Composable
private fun TraktLink() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var code by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tick) { status = com.fivebroz.android.data.Api.trakt("status") }
    val st = status
    when {
        st == null -> Text("…", color = C.Muted)
        !st.optBoolean("configured") -> Text("Trakt غير مفعّل بعد على الخادم.", color = C.Muted)
        st.optBoolean("connected") -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("مرتبط: ${st.optString("username")}", color = C.Paper)
            Choice("فصل", false) {
                scope.launch { com.fivebroz.android.data.Api.trakt("disconnect"); tick++ }
            }
        }
        code != null -> Column {
            Text("افتح trakt.tv/activate من أي جهاز واكتب الرمز:", color = C.Muted)
            Text(code!!.optString("user_code"), style = MaterialTheme.typography.displayLarge, color = C.Gold)
            Text("ننتظر التأكيد…", color = C.Muted)
            androidx.compose.runtime.LaunchedEffect(code) {
                val c = code ?: return@LaunchedEffect
                val every = c.optLong("interval", 5) * 1000
                val until = System.currentTimeMillis() + c.optLong("expires_in", 600) * 1000
                while (System.currentTimeMillis() < until) {
                    kotlinx.coroutines.delay(every)
                    val r = com.fivebroz.android.data.Api.trakt("poll", org.json.JSONObject().put("device_code", c.optString("device_code")))
                    if (r?.optBoolean("ok") == true) break
                }
                code = null
                tick++
            }
        }
        else -> GoldButton("ربط بحساب Trakt", { scope.launch { code = com.fivebroz.android.data.Api.trakt("code") } }, filled = false)
    }
}
