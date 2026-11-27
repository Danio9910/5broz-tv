package com.fivebroz.android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Preview
import kotlinx.coroutines.launch

/** Screens re-read their data when an item menu changed it. */
object ItemEvents {
    var resumeVersion by mutableIntStateOf(0)
    var libraryVersion by mutableIntStateOf(0)
}

/** Gives every poster under it a long-press menu. */
@Composable
fun ItemMenuHost(onOpen: (Preview) -> Unit, content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<Preview?>(null) }
    CompositionLocalProvider(LocalItemMenu provides { p: Preview -> target = p }) { content() }
    target?.let { ItemMenu(it, onOpen) { target = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemMenu(item: Preview, onOpen: (Preview) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<Boolean?>(null) }
    var resume by remember { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        resume = Db.hasResume(item.id)
        saved = Db.inLibrary(item.id)
    }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(C.Ink800)) {
                    Img(item.poster, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                }
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.year?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(14.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            Box(Modifier.fillMaxWidth()) {
                Focusable({ onDismiss(); onOpen(item) }, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), lift = 1f, ring = false, focusRequester = first) { f ->
                    MenuLine(Icons.Info, "عرض التفاصيل", f)
                }
            }
            saved?.let { s ->
                MenuRow(if (s) Icons.Check else Icons.Plus, if (s) "إزالة من قائمتي" else "إضافة إلى قائمتي") {
                    scope.launch {
                        Db.setInLibrary(item, !s)
                        ItemEvents.libraryVersion++
                        toast(if (s) "أُزيل من قائمتك" else "أُضيف إلى قائمتك")
                    }
                    onDismiss()
                }
            }
            MenuRow(Icons.Tonight, "اقترح لليلة") {
                scope.launch { toast(if (Db.suggest(item)) "أُضيف إلى اقتراحات الليلة" else "تعذّر الاقتراح — حاول مجدداً") }
                onDismiss()
            }
            if (resume) {
                MenuRow(Icons.Close, "إزالة من متابعة المشاهدة") {
                    scope.launch {
                        if (Db.clearResume(item.id)) {
                            ItemEvents.resumeVersion++
                            val app = ctx.applicationContext
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.fivebroz.android.player.WatchNext.remove(app, item.id)
                            }
                            toast("أُزيل من متابعة المشاهدة")
                        } else {
                            toast("تعذّرت الإزالة — حاول مجدداً")
                        }
                    }
                    onDismiss()
                }
            }
        }
    }

    if (LocalIsTv.current) {
        Dialog(onDismissRequest = onDismiss) {
            Box(Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(18.dp)).background(C.Ink900).padding(top = 20.dp)) { body() }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = C.Ink900,
        ) { Box(Modifier.navigationBarsPadding()) { body() } }
    }
}

@Composable
private fun MenuLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, f: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (f) C.Ink700 else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ic(icon, 22.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
