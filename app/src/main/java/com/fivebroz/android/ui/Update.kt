package com.fivebroz.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.fivebroz.android.BuildConfig
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

private var checkedThisRun = false

/** Checks once per launch whether a newer build is published, and offers it. */
@Composable
fun UpdateCheck() {
    val ctx = LocalContext.current
    var offer by remember { mutableStateOf<Api.AppVersion?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (checkedThisRun) return@LaunchedEffect
        checkedThisRun = true
        val v = Api.appVersion() ?: return@LaunchedEffect
        if (v.code > BuildConfig.VERSION_CODE && v.url.isNotBlank()) offer = v
    }

    val v = offer ?: return
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    Dialog(onDismissRequest = { if (progress == null) offer = null }) {
        Column(
            Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(26.dp).widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("تحديث جديد متوفر", style = MaterialTheme.typography.titleLarge)
            Text(
                "الإصدار ${v.name}" + (v.notes?.let { "\n$it" } ?: ""),
                color = C.Muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = { p }, color = C.Gold, trackColor = C.Ink700, modifier = Modifier.fillMaxWidth())
                Text("جارٍ التنزيل… ${(p * 100).toInt()}٪", color = C.Muted, style = MaterialTheme.typography.labelMedium)
            } else {
                if (error.isNotEmpty()) Text(error, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("تحديث الآن", {
                        error = ""
                        progress = 0f
                        scope.launch {
                            val file = download(ctx, v.url) { progress = it }
                            progress = null
                            if (file == null) error = "تعذّر التنزيل، حاول مرة أخرى."
                            else if (!install(ctx, file)) error = "اسمح بالتثبيت من الإعدادات، ثم اضغط «تحديث الآن» مرة أخرى."
                        }
                    }, focusRequester = fr)
                    GoldButton("لاحقاً", { offer = null }, filled = false)
                }
            }
        }
    }
}

private suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "5broz.apk")
            Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null
                val total = body.contentLength().toFloat()
                var done = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress(done / total) }
                        }
                    }
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

private fun install(ctx: Context, file: File): Boolean {
    // Android asks once for permission to install from this app.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return false
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}
