package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Offline
import com.fivebroz.android.data.OfflineItem
import com.fivebroz.android.player.DownloadService
import com.fivebroz.android.player.PlayArgs
import com.fivebroz.android.player.PlayerActivity
import kotlinx.coroutines.delay

/** What's kept on this device for watching without internet. */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Offline.all(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            items = Offline.all(ctx)
            delay(1500)
        }
    }
    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Focusable(onBack, shape = CircleShape, lift = 1f, ring = false) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Back, 22.dp) }
                    }
                    Text("التنزيلات", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
                }
                VSpace(6.dp)
                Text("تشوفها بدون نت — في الطيارة أو السفر. من صفحة أي فيلم: «المزيد» ← «تنزيل».", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                VSpace(14.dp)
            }
            if (items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ic(Icons.Download, 40.dp, C.Muted)
                    VSpace(10.dp)
                    Text("ما فيه تنزيلات بعد.", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(items, key = { it.videoId }) { it -> DownloadRow(it) { items = Offline.all(ctx) } }
        }
    }
}

@Composable
private fun DownloadRow(d: OfflineItem, changed: () -> Unit) {
    val ctx = LocalContext.current
    val done = d.status == "done"
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Focusable(
            {
                if (done) PlayerActivity.start(ctx, PlayArgs(d.type, d.metaId, d.name, d.poster, d.videoId, d.label))
                else if (d.status == "failed") DownloadService.enqueue(ctx, d)
            },
            shape = RoundedCornerShape(8.dp), lift = 1.03f, ring = false,
        ) {
            Img(d.poster, Modifier.width(64.dp).height(96.dp).clip(RoundedCornerShape(8.dp)))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.label.isNotBlank()) Text(d.label, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VSpace(6.dp)
            when (d.status) {
                "done" -> Text("جاهز بدون نت · ${size(d.total)}", color = C.Gold, style = MaterialTheme.typography.labelLarge)
                "failed" -> Text("تعذّر — اضغط الصورة لإعادة المحاولة", color = C.Muted, style = MaterialTheme.typography.labelLarge)
                "waiting" -> Text("في الانتظار…", color = C.Muted, style = MaterialTheme.typography.labelLarge)
                "finding" -> Text("نختار أفضل نسخة…", color = C.Muted, style = MaterialTheme.typography.labelLarge)
                else -> {
                    LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = C.Gold, trackColor = C.Ink700)
                    VSpace(4.dp)
                    Text("${(d.progress * 100).toInt()}٪ · ${size(d.bytes)} من ${size(d.total)}", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (done) {
            GoldButton("شغّل", { PlayerActivity.start(ctx, PlayArgs(d.type, d.metaId, d.name, d.poster, d.videoId, d.label)) }, icon = Icons.Play)
        }
        Focusable({ DownloadService.cancel(ctx, d.videoId); changed() }, shape = CircleShape, lift = 1.05f, ring = false) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Ic(Icons.Trash, 20.dp, C.Muted) }
        }
    }
}

private fun size(b: Long): String = when {
    b <= 0 -> "—"
    b >= 1_000_000_000 -> "%.1f غ.ب".format(b / 1_000_000_000f)
    else -> "${b / 1_000_000} م.ب"
}

/** Starts keeping a film or episode on the device (asks nothing: picks a phone-sized copy). */
fun downloadForOffline(ctx: android.content.Context, type: String, metaId: String, name: String, poster: String?, videoId: String, label: String) {
    com.fivebroz.android.Notify.askWhenUseful(ctx)
    DownloadService.enqueue(ctx, OfflineItem(videoId = videoId, metaId = metaId, type = type, name = name, label = label, poster = poster, status = "waiting"))
}
