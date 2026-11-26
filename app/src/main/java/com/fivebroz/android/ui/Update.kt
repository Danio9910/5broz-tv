package com.fivebroz.android.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.fivebroz.android.BuildConfig
import com.fivebroz.android.data.Api
import kotlinx.coroutines.delay
import java.io.File

private var checkedThisRun = false

/**
 * App updates through Android's own Download Manager: the download keeps going
 * with the screen off or the app closed, resumes after network drops, and shows
 * progress in the notification shade. Tapping the finished notification (or
 * coming back to the app) opens the installer.
 */
private object Updater {
    private const val TAG = "5broz"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("5broz-update", Context.MODE_PRIVATE)

    fun installedCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Exception) {
        BuildConfig.VERSION_CODE.toLong()
    }

    private fun file(ctx: Context, code: Int) =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "5broz-$code.apk")

    data class State(val status: Int, val progress: Float) // DownloadManager.STATUS_*

    /** Existing download for this version, if any. */
    fun state(ctx: Context, code: Int): State? {
        val p = prefs(ctx)
        if (p.getInt("code", -1) != code) return null
        val id = p.getLong("id", -1)
        if (id < 0) return null
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val prog = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            if (status == DownloadManager.STATUS_SUCCESSFUL && !file(ctx, code).exists()) return null
            return State(status, prog)
        }
    }

    fun start(ctx: Context, v: Api.AppVersion) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val p = prefs(ctx)
        p.getLong("id", -1).takeIf { it >= 0 }?.let { runCatching { dm.remove(it) } }
        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()
            ?.filter { it.name.startsWith("5broz-") }?.forEach { it.delete() }

        val req = DownloadManager.Request(Uri.parse(v.url))
            .setTitle("تحديث 5broz ${v.name}")
            .setDescription("جارٍ تنزيل الإصدار الجديد")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "5broz-${v.code}.apk")
        val id = dm.enqueue(req)
        p.edit().putLong("id", id).putInt("code", v.code).apply()
        Log.i(TAG, "update download enqueued id=$id code=${v.code}")
    }

    fun canInstall(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(ctx: Context, code: Int) {
        val f = file(ctx, code)
        Log.i(TAG, "update install ${f.name} size=${f.length()}")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Checks once per launch whether a newer build is published, and offers it. */
@Composable
fun UpdateCheck() {
    val ctx = LocalContext.current
    val view = LocalView.current
    var offer by remember { mutableStateOf<Api.AppVersion?>(null) }
    var state by remember { mutableStateOf<Updater.State?>(null) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (checkedThisRun) return@LaunchedEffect
        checkedThisRun = true
        // Test builds only check when the emulator test asks them to.
        if (BuildConfig.DEBUG && !ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE).getBoolean("test_update", false)) {
            return@LaunchedEffect
        }
        var v = Api.appVersion() ?: return@LaunchedEffect
        if (BuildConfig.DEBUG) {
            // The emulator test can point the download at a deliberately slow file.
            ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE).getString("test_update_url", null)
                ?.let { v = v.copy(url = it) }
        }
        if (v.code > Updater.installedCode(ctx) && v.url.isNotBlank()) {
            offer = v
            state = Updater.state(ctx, v.code) // a download may already be running or finished
        }
    }

    val v = offer ?: return

    // Follow the system download while the dialog is open.
    LaunchedEffect(v) {
        while (true) {
            val s = Updater.state(ctx, v.code)
            state = s
            if (s?.status == android.app.DownloadManager.STATUS_FAILED) {
                error = "تعذّر التنزيل — تأكد من الإنترنت ثم اضغط «تحديث الآن» مرة أخرى."
            }
            delay(600)
        }
    }
    val downloading = state?.status.let {
        it == android.app.DownloadManager.STATUS_RUNNING ||
            it == android.app.DownloadManager.STATUS_PENDING ||
            it == android.app.DownloadManager.STATUS_PAUSED
    }
    val ready = state?.status == android.app.DownloadManager.STATUS_SUCCESSFUL
    // Keep a TV from dimming while the user watches the bar (the download itself doesn't need it).
    view.keepScreenOn = downloading

    // Install as soon as it finishes (and when the user returns after granting permission).
    LaunchedEffect(ready) {
        if (ready && Updater.canInstall(ctx)) Updater.install(ctx, v.code)
    }

    val fr = remember { FocusRequester() }
    LaunchedEffect(downloading, ready) { runCatching { fr.requestFocus() } }

    Dialog(onDismissRequest = { offer = null }) {
        Column(
            Modifier.background(C.Ink900, RoundedCornerShape(18.dp)).padding(26.dp).widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (ready) "التحديث جاهز للتثبيت" else "تحديث جديد متوفر", style = MaterialTheme.typography.titleLarge)
            Text(
                "الإصدار ${v.name}" + (v.notes?.let { "\n$it" } ?: ""),
                color = C.Muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                downloading -> {
                    val p = state?.progress ?: 0f
                    LinearProgressIndicator(progress = { p }, color = C.Gold, trackColor = C.Ink700, modifier = Modifier.fillMaxWidth())
                    Text("جارٍ التنزيل… ${(p * 100).toInt()}٪", color = C.Paper, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "يمكنك إطفاء الشاشة أو الخروج من التطبيق — التنزيل يكمل في الخلفية ويظهر في الإشعارات.",
                        color = C.Muted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    GoldButton("إخفاء", { offer = null }, filled = false, focusRequester = fr)
                }
                ready -> {
                    if (!Updater.canInstall(ctx)) {
                        Text(
                            "خطوة أخيرة: اسمح لـ 5broz بتثبيت التحديثات، ثم ارجع هنا.",
                            color = C.Muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        GoldButton("فتح الإعدادات", { Updater.askInstallPermission(ctx) }, focusRequester = fr)
                    } else {
                        GoldButton("تثبيت الآن", { Updater.install(ctx, v.code) }, focusRequester = fr)
                    }
                }
                else -> {
                    if (error.isNotEmpty()) Text(error, color = C.Danger, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("تحديث الآن", {
                            error = ""
                            try {
                                Updater.start(ctx, v)
                                state = Updater.State(android.app.DownloadManager.STATUS_PENDING, 0f)
                            } catch (e: Exception) {
                                error = "تعذّر بدء التنزيل."
                            }
                        }, focusRequester = fr)
                        GoldButton("لاحقاً", { offer = null }, filled = false)
                    }
                }
            }
        }
    }
}
