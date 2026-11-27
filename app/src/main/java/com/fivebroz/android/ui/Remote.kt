package com.fivebroz.android.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Device
import com.fivebroz.android.data.Household
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A TV id handed over by a scanned code (fivebroz://remote/<id>); the main screen opens its remote. */
object PendingRemote {
    var tv by mutableStateOf<String?>(null)
}

/** Speech to text (Arabic first). Returns a launcher; the result goes to [onText]. */
@Composable
fun rememberVoice(onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "قل اسم فيلم أو وش تبي تشوف")
        if (i.resolveActivity(ctx.packageManager) != null) launcher.launch(i)
    }
}

/** Whether this device can listen (has a speech recognizer). */
@Composable
fun canListen(): Boolean {
    val ctx = LocalContext.current
    return remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }
}

/** The phone as the TV's remote: arrows, OK, back, play/pause, and typing or speaking a search. */
@Composable
fun RemoteScreen(preselect: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tvs by remember { mutableStateOf<List<Device>?>(null) }
    var tv by remember { mutableStateOf<Device?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) {
        tvs = null
        val list = Household.tvs(ctx)
        tvs = list
        tv = list.firstOrNull { it.id == preselect } ?: preselect?.let { Household.device(it) } ?: list.singleOrNull()
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun send(kind: String, payload: JSONObject) {
        val t = tv ?: return
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        scope.launch { if (!Household.send(t.id, kind, payload)) note = "ما وصل — تأكد من النت" }
    }
    fun key(k: String) = send("key", JSONObject().put("key", k))
    val voice = rememberVoice { said -> send("text", JSONObject().put("text", said)); note = "«$said» على التلفزيون" }

    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Focusable(onBack, shape = CircleShape, lift = 1f, ring = false) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Back, 22.dp) }
                }
                Text("ريموت التلفزيون", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
            }
            VSpace(14.dp)
            val list = tvs
            when {
                list == null && tv == null -> Text("نبحث عن تلفزيون مفتوح في البيت…", color = C.Muted)
                tv == null && list.isNullOrEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ما فيه تلفزيون مفتوح الحين.", style = MaterialTheme.typography.titleMedium)
                    Text("افتح 5broz على التلفزيون، وبعدها اضغط «حدّث».", color = C.Muted, textAlign = TextAlign.Center)
                    VSpace(14.dp)
                    GoldButton("حدّث", { tick++ })
                }
                tv == null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر التلفزيون", style = MaterialTheme.typography.titleMedium)
                    list!!.forEach { d -> GoldButton(d.name, { tv = d }, Modifier.fillMaxWidth(), filled = false, fill = true, icon = Icons.Tv) }
                }
                else -> {
                    val t = tv!!
                    Text(t.name, color = C.Gold, style = MaterialTheme.typography.titleMedium)
                    VSpace(22.dp)
                    // The D-pad
                    Box(Modifier.size(260.dp).background(C.Ink800, CircleShape).border(1.dp, C.Paper.copy(alpha = 0.1f), CircleShape)) {
                        PadKey(Icons.Up, Modifier.align(Alignment.TopCenter)) { key("up") }
                        PadKey(Icons.Down, Modifier.align(Alignment.BottomCenter)) { key("down") }
                        // RTL: the visual right is "right" on the TV too
                        PadKey(Icons.Right, Modifier.align(Alignment.CenterStart)) { key("right") }
                        PadKey(Icons.Left, Modifier.align(Alignment.CenterEnd)) { key("left") }
                        Focusable({ key("ok") }, Modifier.align(Alignment.Center), CircleShape, lift = 0.96f, ring = false) {
                            Box(Modifier.size(96.dp).background(C.Paper, CircleShape), contentAlignment = Alignment.Center) {
                                Text("OK", color = C.Ink950, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    VSpace(22.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoundKey(Icons.Back, "رجوع") { key("back") }
                        RoundKey(Icons.Rewind, "-10") { key("rew") }
                        RoundKey(Icons.Play, "تشغيل/إيقاف") { key("playpause") }
                        RoundKey(Icons.Forward, "+10") { key("ff") }
                    }
                    VSpace(24.dp)
                    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(text, { text = it.take(80) }, "اكتب للبحث في التلفزيون…", {
                            if (text.isNotBlank()) { send("text", JSONObject().put("text", text)); note = "«$text» على التلفزيون"; text = "" }
                        }, Modifier.weight(1f), center = false)
                        if (canListen()) RoundKey(Icons.Mic, null) { voice() }
                    }
                    note?.let { VSpace(10.dp); Text(it, color = C.Muted) }
                    if ((list?.size ?: 0) > 1) {
                        VSpace(18.dp)
                        GoldButton("تلفزيون آخر", { tv = null }, filled = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick, modifier.padding(6.dp), CircleShape, lift = 0.94f, ring = false) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Ic(icon, 30.dp) }
    }
}

@Composable
private fun RoundKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(onClick, shape = CircleShape, lift = 0.94f, ring = false) {
            Box(Modifier.size(58.dp).background(C.Ink800, CircleShape), contentAlignment = Alignment.Center) { Ic(icon, 24.dp) }
        }
        label?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

/** On the TV: the code a phone scans to become this TV's remote. */
@Composable
fun TvRemoteCode(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val id = remember { Household.myId(ctx) }
    val url = "https://www.5broz.com/r.html?d=$id"
    val qr = remember(url) { qrBitmap(url, 420) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Row(
            Modifier.background(C.Ink900, RoundedCornerShape(20.dp)).padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Column(Modifier.widthIn(max = 320.dp)) {
                Text("جوالك ريموت", style = MaterialTheme.typography.headlineMedium)
                VSpace(10.dp)
                Text("امسح الرمز بكاميرا جوالك، أو افتح 5broz في الجوال ← صورتك ← «ريموت التلفزيون».", color = C.Muted)
                VSpace(10.dp)
                Text("تكتب البحث وتتنقل من جوالك.", color = C.Paper)
                VSpace(18.dp)
                GoldButton("تم", onClose)
            }
            Box(Modifier.size(220.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Image(qr.asImageBitmap(), "رمز الريموت", Modifier.fillMaxSize())
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): android.graphics.Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}
