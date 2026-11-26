package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Profile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(onChosen: () -> Unit) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    val first = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    val size = if (isTv) 112.dp else 92.dp

    LaunchedEffect(Unit) {
        try {
            profiles = Db.profiles()
        } catch (_: Exception) {
            failed = true
        }
    }
    LaunchedEffect(profiles) { if (!profiles.isNullOrEmpty()) runCatching { first.requestFocus() } }

    fun choose(p: Profile) {
        if (!p.pin.isNullOrBlank()) pinFor = p
        else {
            Prefs.choose(p)
            onChosen()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failed -> Centered("تعذّر تحميل الملفات. تأكد من الاتصال بالإنترنت.")
            profiles == null -> Centered("…")
            else -> Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Wordmark(34)
                VSpace(18.dp)
                Text("من يشاهد الآن؟", style = MaterialTheme.typography.headlineMedium)
                VSpace(30.dp)
                FlowRow(
                    Modifier.widthIn(max = 900.dp).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    val list = profiles!!
                    if (list.isEmpty()) {
                        Text(
                            "لا توجد ملفات بعد — أنشئ ملفك من الموقع 5broz.com",
                            color = C.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                    list.forEachIndexed { i, p ->
                        Column(Modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Focusable(
                                onClick = { choose(p) },
                                shape = RoundedCornerShape(size * 0.22f),
                                lift = 1.08f,
                                focusRequester = if (i == 0) first else null,
                            ) { Avatar(p, size) }
                            VSpace(10.dp)
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (p.isKids) Text("أطفال", color = C.Gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(p, onDismiss = { pinFor = null }, onOk = {
            pinFor = null
            Prefs.choose(p)
            onChosen()
        })
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
            Field(pin, { pin = it.filter(Char::isDigit).take(6); error = "" }, "••••", ::submit,
                Modifier.fillMaxWidth(), secret = true, numeric = true, focusRequester = fr)
            if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            GoldButton("دخول", ::submit, Modifier.fillMaxWidth())
        }
    }
}
