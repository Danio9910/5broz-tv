package com.fivebroz.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Same palette and glyphs as the website, so profiles look identical everywhere. */
val AVATARS = listOf(
    "linear-gradient(135deg,#c8a96a,#8a6d3b)",
    "linear-gradient(135deg,#6a89c8,#3b4e8a)",
    "linear-gradient(135deg,#c86a6a,#8a3b3b)",
    "linear-gradient(135deg,#6ac89a,#3b8a63)",
    "linear-gradient(135deg,#9a6ac8,#5e3b8a)",
    "linear-gradient(135deg,#c8936a,#8a5a3b)",
)
val SYMBOLS = listOf("🎬", "🍿", "⭐", "🎭", "🎞️", "👑", "🦁", "🚀", "🐱", "🌙", "🎮", "⚽")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(onChosen: () -> Unit) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) } // id "" = new
    var attempt by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf<Profile?>(null) } // the chosen profile, on its spotlight
    val first = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    val size = if (isTv) 112.dp else 96.dp

    LaunchedEffect(attempt) {
        failed = false
        try {
            profiles = Db.profiles()
        } catch (_: Exception) {
            failed = true
        }
    }
    LaunchedEffect(profiles) { if (!profiles.isNullOrEmpty()) runCatching { first.requestFocus() } }
    BackHandler(enabled = manage && editing == null) { manage = false }

    fun choose(p: Profile) {
        if (manage) {
            editing = p
            return
        }
        if (!p.pin.isNullOrBlank()) pinFor = p
        else stage = p
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تعذّر تحميل الملفات. تأكد من الاتصال بالإنترنت.", color = C.Muted)
                VSpace(16.dp)
                GoldButton("إعادة المحاولة", { attempt++ })
            }
            profiles == null -> Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(3) { Skeleton(Modifier.size(size), RoundedCornerShape(size * 0.22f)) }
            }
            else -> Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Wordmark(34)
                VSpace(18.dp)
                Text(if (manage) "إدارة الملفات" else "من يشاهد الآن؟", style = MaterialTheme.typography.headlineMedium)
                VSpace(30.dp)
                FlowRow(
                    Modifier.widthIn(max = 900.dp).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val list = profiles!!
                    list.forEachIndexed { i, p ->
                        Column(Modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Focusable(
                                onClick = { choose(p) },
                                shape = RoundedCornerShape(size * 0.22f),
                                lift = 1.08f,
                                focusRequester = if (i == 0) first else null,
                            ) {
                                Box {
                                    Avatar(p, size)
                                    if (manage) {
                                        Box(Modifier.size(size).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                            Ic(Icons.Edit, 28.dp)
                                        }
                                    } else if (!p.pin.isNullOrBlank()) {
                                        Ic(Icons.Lock, 15.dp, C.Paper, Modifier.align(Alignment.BottomEnd).padding(7.dp))
                                    }
                                }
                            }
                            VSpace(10.dp)
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.isKids) Text("أطفال", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                            else if (p.family) Text("عائلي", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (list.size < 8) {
                        Column(Modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Focusable(
                                onClick = { editing = Profile("", "", AVATARS[list.size % AVATARS.size], SYMBOLS[list.size % SYMBOLS.size], false, false, null) },
                                shape = RoundedCornerShape(size * 0.22f),
                                lift = 1.08f,
                                focusRequester = if (list.isEmpty()) first else null,
                            ) { f ->
                                Box(
                                    Modifier.size(size).border(1.5.dp, if (f) C.Paper else C.Paper.copy(alpha = 0.25f), RoundedCornerShape(size * 0.22f)),
                                    contentAlignment = Alignment.Center,
                                ) { Ic(Icons.Plus, 34.dp, if (f) C.Paper else C.Muted) }
                            }
                            VSpace(10.dp)
                            Text("ملف جديد", color = C.Muted, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                VSpace(36.dp)
                GoldButton(if (manage) "تم" else "إدارة الملفات", { manage = !manage }, filled = manage)
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(p, onDismiss = { pinFor = null }, onOk = {
            pinFor = null
            stage = p
        })
    }
    stage?.let { p ->
        ProfileStage(p, size) {
            Prefs.choose(p)
            onChosen()
        }
    }
    editing?.let { p ->
        ProfileEditor(
            p,
            onClose = { editing = null },
            onSaved = {
                editing = null
                attempt++
            },
        )
    }
}

@Composable
private fun PinDialog(p: Profile, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    fun submit() {
        if (pin == p.pin) onOk() else error = "الرقم السري غير صحيح."
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(24.dp).widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(p, 64.dp)
            Text("الرقم السري لملف ${p.name}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Field(pin, {
                pin = it.filter(Char::isDigit).take(6)
                error = ""
                if (pin.length == (p.pin ?: "").length) submit()
            }, "••••", ::submit, Modifier.fillMaxWidth(), secret = true, numeric = true, focusRequester = fr)
            if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            GoldButton("دخول", ::submit, Modifier.fillMaxWidth(), fill = true)
        }
    }
}

/** Resize a picked photo to a 320px square JPEG data URL — stored inline like the website does. */
private suspend fun photoToAvatar(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(crop, 320, 320, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(start: Profile, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(start) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = start.id.isEmpty()
    val nameFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv) runCatching { nameFr.requestFocus() } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val av = photoToAvatar(ctx, uri)
            if (av != null) p = p.copy(avatar = av) else error = "تعذّر قراءة الصورة."
        }
    }

    fun save() {
        if (p.name.isBlank()) {
            error = "اكتب اسماً للملف."
            return
        }
        busy = true
        scope.launch {
            val clean = p.copy(name = p.name.trim(), pin = p.pin?.takeIf { it.length == 4 })
            val ok = if (isNew) Db.createProfile(clean) != null else Db.updateProfile(clean)
            busy = false
            if (ok) {
                if (clean.id == Prefs.profileId) Prefs.choose(clean)
                onSaved()
            } else error = "تعذّر الحفظ. حاول مرة أخرى."
        }
    }

    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Ink950), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .statusBarsPadding().imePadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (isNew) "ملف جديد" else "تعديل الملف", style = MaterialTheme.typography.headlineMedium)
                Avatar(p, 104.dp)

                Field(
                    p.name, { p = p.copy(name = it.take(24)); error = "" }, "الاسم — بأي لغة أو رموز 😊", {},
                    Modifier.fillMaxWidth(), plainText = true, focusRequester = nameFr,
                )

                Text("الخلفية", color = C.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AVATARS.forEach { g ->
                        val on = p.avatar == g
                        Focusable({ p = p.copy(avatar = g) }, shape = CircleShape, lift = 1.1f) {
                            Box(Modifier.size(44.dp).border(if (on) 3.dp else 0.dp, C.Paper, CircleShape)) {
                                Avatar(Profile("", " ", g, "", false, false, null), 44.dp)
                            }
                        }
                    }
                    if (!isTv) {
                        Focusable({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(50), lift = 1.05f) {
                            Text(
                                "صورة من جهازك",
                                Modifier.background(C.Ink800).padding(horizontal = 14.dp, vertical = 11.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                Text("الرمز", color = C.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("") + SYMBOLS).forEach { s ->
                        val on = (p.symbol ?: "") == s
                        Focusable({ p = p.copy(symbol = s.ifEmpty { null }) }, shape = RoundedCornerShape(10.dp), lift = 1.1f) {
                            Box(
                                Modifier.size(42.dp).background(if (on) C.Paper.copy(alpha = 0.2f) else C.Ink800),
                                contentAlignment = Alignment.Center,
                            ) { Text(s.ifEmpty { "أ" }, fontSize = 20.sp, color = C.Paper) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Toggle("ملف أطفال", p.isKids, Modifier.weight(1f)) { p = p.copy(isKids = it, family = if (it) false else p.family) }
                    Toggle("محتوى عائلي", p.family, Modifier.weight(1f)) { p = p.copy(family = it, isKids = if (it) false else p.isKids) }
                }

                Field(
                    p.pin ?: "", { p = p.copy(pin = it.filter(Char::isDigit).take(4).ifEmpty { null }) },
                    "رقم سري من 4 أرقام (اختياري)", {}, Modifier.fillMaxWidth(), numeric = true,
                )

                if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
                GoldButton(if (busy) "…" else "حفظ", ::save, Modifier.fillMaxWidth(), fill = true)
                GoldButton("إلغاء", onClose, Modifier.fillMaxWidth(), fill = true, filled = false)
                if (!isNew) {
                    Spacer(Modifier.size(6.dp))
                    Focusable({ confirmDelete = true }, shape = RoundedCornerShape(8.dp), lift = 1f, ring = false) {
                        Text("حذف هذا الملف", Modifier.padding(10.dp), color = C.Danger, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(24.dp).widthIn(max = 340.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("حذف ملف ${p.name}؟", style = MaterialTheme.typography.titleMedium)
                Text("سيُحذف سجل مشاهدته وقائمته نهائياً، ولا يمكن التراجع.", color = C.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                GoldButton("حذف نهائياً", {
                    scope.launch {
                        if (Db.deleteProfile(start.id)) {
                            if (start.id == Prefs.profileId) Prefs.clearProfile()
                            confirmDelete = false
                            onSaved()
                        } else error = "تعذّر الحذف."
                    }
                }, Modifier.fillMaxWidth(), fill = true)
                GoldButton("تراجع", { confirmDelete = false }, Modifier.fillMaxWidth(), fill = true, filled = false)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Focusable({ onChange(!on) }, modifier, RoundedCornerShape(12.dp), lift = 1.03f) {
        Row(
            Modifier.fillMaxWidth().background(if (on) C.Gold.copy(alpha = 0.18f) else C.Ink800).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(width = 38.dp, height = 22.dp).background(if (on) C.Gold else C.Ink600, RoundedCornerShape(50)),
                contentAlignment = if (on) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(Modifier.padding(3.dp).size(16.dp).background(C.Paper, CircleShape))
            }
        }
    }
}


/** The chosen profile steps into a spotlight, grows, and is greeted by name before the app opens. */
@Composable
private fun ProfileStage(p: Profile, size: androidx.compose.ui.unit.Dp, onDone: () -> Unit) {
    val grow = remember { androidx.compose.animation.core.Animatable(1f) }
    val light = remember { androidx.compose.animation.core.Animatable(0f) }
    val words = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(p.id) {
        launch { light.animateTo(1f, androidx.compose.animation.core.tween(420)) }
        grow.animateTo(1.55f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f))
        words.animateTo(1f, androidx.compose.animation.core.tween(360))
        kotlinx.coroutines.delay(650)
        onDone()
    }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f * light.value))
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(C.Gold.copy(alpha = 0.30f * light.value), Color.Transparent),
                    radius = 900f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(grow.value)) { Avatar(p, size) }
            Spacer(Modifier.size(size * 0.45f))
            Text(
                "${Season.current()?.greeting ?: greeting()} يا ${p.name}",
                Modifier.alpha(words.value),
                fontFamily = Amiri,
                fontSize = 30.sp,
                color = C.Paper,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Modifier.scale(s: Float) = this.then(androidx.compose.ui.draw.scale(s))
private fun Modifier.alpha(a: Float) = this.then(androidx.compose.ui.draw.alpha(a))
