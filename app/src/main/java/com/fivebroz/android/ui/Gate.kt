package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Prefs
import kotlinx.coroutines.launch

/** A single-line input that looks like the rest of the app and works with a remote. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    numeric: Boolean = false,
    plainText: Boolean = false, // names: any script, emoji, symbols
    focusRequester: FocusRequester? = null,
    center: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val own = remember { FocusRequester() }
    val req = focusRequester ?: own
    val keyboard = LocalSoftwareKeyboardController.current
    val fr = Modifier.focusRequester(req)
    Box(
        modifier
            // Tapping anywhere on the box focuses the input and opens the keyboard.
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                runCatching { req.requestFocus() }
                keyboard?.show()
            }
            .background(C.Ink800, RoundedCornerShape(12.dp))
            .border(1.dp, if (focused) C.Gold else C.Paper.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = C.Muted, style = MaterialTheme.typography.bodyLarge)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            cursorBrush = SolidColor(C.Gold),
            textStyle = TextStyle(
                color = C.Paper,
                fontSize = 17.sp,
                fontFamily = Cairo,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            ),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    numeric -> KeyboardType.NumberPassword
                    plainText -> KeyboardType.Text
                    else -> KeyboardType.Password
                },
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().then(fr).onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
fun GateScreen(onPass: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }

    fun submit() {
        if (code.isBlank() || busy) return
        busy = true
        error = ""
        scope.launch {
            val ok = Api.checkCode(code.trim())
            busy = false
            if (ok) {
                Prefs.gatePassed = true
                onPass()
            } else error = "رمز غير صحيح."
        }
    }

    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Wordmark(52)
            Text("اكتب رمز الدخول للمتابعة", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            Field(code, { code = it; error = "" }, "رمز الدخول", ::submit, Modifier.fillMaxWidth(), focusRequester = fr)
            if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            GoldButton(if (busy) "…" else "دخول", ::submit, Modifier.fillMaxWidth(), fill = true)
        }
    }
}

/** Asks for the household access code (used to leave a kids profile). */
@Composable
fun CodeDialog(title: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    fun submit() {
        scope.launch {
            if (Api.checkCode(code.trim())) onOk() else error = "رمز غير صحيح."
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(24.dp).widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("اكتب رمز الدخول للمتابعة", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            Field(code, { code = it; error = "" }, "رمز الدخول", ::submit, Modifier.fillMaxWidth(), secret = true, focusRequester = fr)
            if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            GoldButton("تأكيد", ::submit, Modifier.fillMaxWidth(), fill = true)
        }
    }
}

/** Enter a room code and jump into that watch party (works with rooms made on the website too). */
@Composable
fun JoinPartyDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    fun submit() {
        if (code.length < 4 || busy) return
        busy = true
        error = ""
        scope.launch {
            val p = com.fivebroz.android.data.Parties.join(code)
            if (p?.videoId == null || p.metaId == null) {
                busy = false
                error = "لا توجد غرفة بهذا الرمز."
                return@launch
            }
            val type = if (p.type == "series") "series" else "movie"
            val d = runCatching { Api.detail(type, p.metaId) }.getOrNull()
            busy = false
            onDismiss()
            com.fivebroz.android.player.PlayerActivity.start(
                ctx,
                com.fivebroz.android.player.PlayArgs(
                    type = type,
                    metaId = d?.base?.id ?: p.metaId,
                    name = d?.base?.name ?: "غرفة ${p.code}",
                    poster = d?.base?.poster,
                    videoId = p.videoId,
                    label = "",
                    partyId = p.id,
                    partyHost = false,
                ),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(24.dp).widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("انضمام لغرفة مشاهدة", style = MaterialTheme.typography.titleMedium)
            Text("اكتب الرمز الذي أرسله المضيف", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
            Field(
                code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6); error = "" }, "ABC123", ::submit,
                Modifier.fillMaxWidth(), plainText = true, focusRequester = fr,
            )
            if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            GoldButton(if (busy) "…" else "دخول", ::submit, Modifier.fillMaxWidth(), fill = true)
        }
    }
}
