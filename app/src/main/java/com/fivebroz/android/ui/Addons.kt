package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Net
import kotlinx.coroutines.launch

/** Turns what people paste (stremio://…, a page URL, …/manifest.json) into the addon's base URL. */
private fun normalize(raw: String): String {
    var u = raw.trim()
    if (u.startsWith("stremio://", ignoreCase = true)) u = "https://" + u.substring("stremio://".length)
    if (!u.startsWith("http", ignoreCase = true)) u = "https://$u"
    return u.removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/configure").removeSuffix("/")
}

/**
 * Household addons, the way Stremio and Nuvio do it: paste an addon's link once and
 * its sources appear for everyone in the house, on every device, next to the built-in ones.
 */
@Composable
fun AddonsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Db.Addon>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) { list = Db.addons() }

    fun add() {
        val base = normalize(input)
        if (input.isBlank() || busy) return
        busy = true
        msg = "جارٍ التحقق من الإضافة…"
        scope.launch {
            val m = runCatching { Net.getObj("$base/manifest.json") }.getOrNull()
            val res = m?.optJSONArray("resources")
            val streams = res != null && (0 until res.length()).any { i ->
                val r = res.opt(i)
                r == "stream" || (r is org.json.JSONObject && r.optString("name") == "stream")
            }
            msg = when {
                m == null -> "هذا الرابط لا يعمل — تأكد أنه رابط إضافة (ينتهي غالباً بـ manifest.json)."
                !streams -> "«${m.optString("name")}» لا توفّر مصادر تشغيل."
                Db.addAddon(base, m.optString("name").ifBlank { base }) -> {
                    input = ""
                    tick++
                    "أُضيفت «${m.optString("name")}» — مصادرها تظهر الآن لكل أفراد البيت."
                }
                else -> "تعذّر الحفظ — حاول مجدداً."
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .then(if (LocalIsTv.current) Modifier.padding(48.dp) else Modifier.statusBarsPadding().padding(horizontal = 16.dp)),
        ) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Focusable(onBack, shape = CircleShape, lift = 1f, ring = false) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Back, 22.dp) }
                }
                Text("الإضافات", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "الإضافات تجيب مصادر تشغيل إضافية، مثل Stremio وNuvio. الروابط المباشرة منها تبدأ فوراً. الإضافة تُحفظ لكل البيت مرة واحدة.",
                color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 640.dp),
            )
            VSpace(18.dp)
            Field(input, { input = it }, "الصق رابط الإضافة هنا", { add() }, Modifier.fillMaxWidth().widthIn(max = 640.dp), plainText = true, center = false)
            VSpace(10.dp)
            GoldButton(if (busy) "…" else "إضافة", { add() })
            msg?.let { Text(it, Modifier.padding(top = 10.dp), color = C.Paper.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
            VSpace(24.dp)
            SectionTitle("المثبّتة")
            VSpace(8.dp)
            AddonRow("Torrentio", "مدمجة — تورنت", null)
            list?.forEach { a ->
                AddonRow(a.name, a.url, if (com.fivebroz.android.data.Prefs.profileKids) null else ({
                    scope.launch { Db.removeAddon(a.url); tick++ }
                }))
            }
        }
    }
}

@Composable
private fun AddonRow(name: String, sub: String, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(vertical = 4.dp)
            .background(C.Ink800, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Focusable(onRemove, shape = CircleShape, lift = 1.05f, ring = false) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Trash, 20.dp, C.Danger) }
            }
        }
    }
}
