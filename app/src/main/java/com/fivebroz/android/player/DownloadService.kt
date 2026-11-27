package com.fivebroz.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fivebroz.android.MainActivity
import com.fivebroz.android.R
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Net
import com.fivebroz.android.data.Offline
import com.fivebroz.android.data.OfflineItem
import com.fivebroz.android.data.Prefs
import com.fivebroz.torrent.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps downloading while the phone is in a pocket: one download at a time, in order,
 * with a progress notification. Direct links are copied; torrents are fetched whole by the
 * built-in engine and kept on the device.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTE = 7311

        /** Queue a film/episode and make sure the service is working on the list. */
        fun enqueue(ctx: Context, item: OfflineItem) {
            Offline.put(ctx, item.copy(status = "waiting", progress = 0f, error = null))
            kick(ctx)
        }

        fun kick(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun cancel(ctx: Context, videoId: String) {
            val it = Offline.all(ctx).firstOrNull { x -> x.videoId == videoId }
            it?.infoHash?.let { h -> runCatching { Torrents.get(ctx).stopDownload(h, true) } }
            Offline.remove(ctx, videoId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.init(applicationContext)
        startForegroundCompat(note("نجهّز التنزيل…", 0, true))
        if (!running) {
            running = true
            scope.launch { work() }
        }
        return START_STICKY
    }

    private suspend fun work() {
        while (scope.isActive) {
            val next = Offline.all(this).firstOrNull { it.status == "waiting" || it.status == "finding" || it.status == "downloading" }
                ?: break
            runCatching { fetch(next) }.onFailure { e ->
                Log.w("5broz", "download failed ${next.videoId}", e)
                Offline.update(this, next.videoId) { it.copy(status = "failed", error = "تعذّر التنزيل") }
            }
        }
        running = false
        withContext(Dispatchers.Main) {
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private suspend fun fetch(item: OfflineItem) {
        var it = item
        if (it.url == null && it.infoHash == null) {
            Offline.update(this, it.videoId) { x -> x.copy(status = "finding") }
            show(it.name, "نختار أفضل نسخة…", 0, true)
            val streams = Api.streams(it.type, it.videoId)
            // Smaller, widely playable files suit a phone better than 4K remuxes.
            val best = rank(streams, isTv = false).map { s -> s.stream }
                .sortedBy { s -> if ((s.sizeBytes ?: 0L) in 300_000_000L..4_500_000_000L) 0 else 1 }
                .firstOrNull() ?: throw IllegalStateException("no source")
            it = it.copy(url = best.url, infoHash = best.infoHash, fileIdx = best.fileIdx)
            Offline.update(this, it.videoId) { _ -> it.copy(status = "downloading") }
            if (best.url == null && best.infoHash != null) {
                // remember trackers through the magnet only
                it = it.copy(url = null)
                magnets[it.videoId] = TorrentEngine.magnet(best.infoHash, TorrentEngine.trackersFrom(emptyList()) + best.trackers.filter { t -> !t.startsWith("dht:") }.map { t -> t.removePrefix("tracker:") })
            }
        }
        if (it.url != null) direct(it) else torrent(it)
    }

    private val magnets = HashMap<String, String>()

    private suspend fun direct(item: OfflineItem) {
        val dir = Offline.dir(this)
        val name = (item.name + " " + item.label).replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifBlank { item.videoId } + ".mp4"
        val f = File(dir, name)
        val tmp = File(dir, "$name.part")
        val req = okhttp3.Request.Builder().url(item.url!!).apply { if (tmp.exists()) header("Range", "bytes=${tmp.length()}-") }.build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val body = r.body ?: throw IllegalStateException("empty")
            val resumed = r.code == 206
            val total = (if (resumed) tmp.length() else 0L) + body.contentLength().coerceAtLeast(0)
            java.io.FileOutputStream(tmp, resumed).use { out ->
                val buf = ByteArray(256 * 1024)
                var done = if (resumed) tmp.length() else 0L
                var last = 0L
                body.byteStream().use { inp ->
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 1500) {
                            last = now
                            if (Offline.all(this).none { x -> x.videoId == item.videoId }) throw IllegalStateException("cancelled")
                            val p = if (total > 0) done.toFloat() / total else 0f
                            Offline.update(this, item.videoId) { x -> x.copy(status = "downloading", progress = p, bytes = done, total = total) }
                            show(item.name, "${(p * 100).toInt()}٪", (p * 100).toInt(), total <= 0)
                        }
                    }
                }
            }
        }
        tmp.renameTo(f)
        Offline.update(this, item.videoId) { x -> x.copy(status = "done", progress = 1f, path = f.absolutePath) }
        show(item.name, "جاهز للمشاهدة بدون نت ✓", 100, false, done = true)
    }

    private suspend fun torrent(item: OfflineItem) {
        val engine = Torrents.get(this)
        val hash = item.infoHash ?: throw IllegalStateException("no hash")
        val magnet = magnets[item.videoId] ?: TorrentEngine.magnet(hash, emptyList())
        engine.startDownload(magnet, File(Offline.dir(this), "t"))
        var stalled = 0
        while (true) {
            delay(2000)
            if (Offline.all(this).none { x -> x.videoId == item.videoId }) {
                engine.stopDownload(hash, true)
                return
            }
            val info = engine.downloadInfo(hash, item.fileIdx)
            if (info == null) { if (++stalled > 30) throw IllegalStateException("lost"); continue }
            val p = if (info.size > 0) info.done.toFloat() / info.size else 0f
            Offline.update(this, item.videoId) { x -> x.copy(status = "downloading", progress = p, bytes = info.done, total = info.size, path = info.path?.absolutePath) }
            val speed = if (info.bytesPerSec > 0) " · ${"%.1f".format(info.bytesPerSec / 1_048_576f)} م.ب/ث" else ""
            show(item.name, if (info.hasMetadata) "${(p * 100).toInt()}٪$speed" else "نبحث عن مصادر…", (p * 100).toInt(), !info.hasMetadata)
            if (info.finished) {
                // keep the file, let go of the torrent
                engine.stopDownload(hash, false)
                Offline.update(this, item.videoId) { x -> x.copy(status = "done", progress = 1f, path = info.path?.absolutePath) }
                show(item.name, "جاهز للمشاهدة بدون نت ✓", 100, false, done = true)
                return
            }
        }
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "التنزيلات", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun note(text: String, pct: Int, indeterminate: Boolean, title: String = "تنزيل للمشاهدة بدون نت", done: Boolean = false) =
        NotificationCompat.Builder(this, CHANNEL.also { channel() })
            .setSmallIcon(R.drawable.ic_stat_5)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)
            .setProgress(if (done) 0 else 100, pct, indeterminate && !done)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()

    private fun show(title: String, text: String, pct: Int, indeterminate: Boolean, done: Boolean = false) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(if (done) NOTE + title.hashCode() else NOTE, note(text, pct, indeterminate, title, done))
        }
    }

    private fun startForegroundCompat(n: android.app.Notification) {
        ServiceCompat.startForeground(
            this, NOTE, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
