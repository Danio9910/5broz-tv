package com.fivebroz.android.player

import android.content.Context
import android.util.Log
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Gets a title's sources while its page is open, so pressing play doesn't
 * start with a network round-trip. Also wakes the torrent engine early.
 */
object StreamPrefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Entry(val key: String, val at: Long, val job: Deferred<List<Stream>>)
    @Volatile private var entry: Entry? = null

    fun warm(ctx: Context, type: String, videoId: String) {
        val key = "$type/$videoId"
        val e = entry
        if (e != null && e.key == key && System.currentTimeMillis() - e.at < 10 * 60_000) return
        val app = ctx.applicationContext
        val isTv = app.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val job = scope.async { Api.streams(type, videoId) }
        entry = Entry(key, System.currentTimeMillis(), job)
        scope.launch {
            try {
                // Look up the best sources' file lists now, so play only waits for the video itself.
                val top = rank(job.await(), isTv).map { it.stream }.filter { it.url == null && it.infoHash != null }.take(3)
                if (top.isNotEmpty()) {
                    Torrents.get(app).prefetch(top.map { com.fivebroz.torrent.TorrentEngine.magnet(it.infoHash!!, it.trackers) })
                }
            } catch (t: Throwable) {
                Log.w("5broz", "prefetch failed: $t")
            }
        }
    }

    /** The prefetched list (possibly empty), or null if it failed or isn't for this video. */
    suspend fun result(type: String, videoId: String): List<Stream>? {
        val e = entry ?: return null
        if (e.key != "$type/$videoId") return null
        return runCatching { e.job.await() }.getOrNull()
    }

    /** Sources fetched for this exact video in the last 10 minutes, if any. */
    suspend fun take(type: String, videoId: String): List<Stream>? {
        val e = entry ?: return null
        if (e.key != "$type/$videoId" || System.currentTimeMillis() - e.at > 10 * 60_000) return null
        return runCatching { e.job.await() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Starts the BitTorrent session and its DHT in the background. */
    fun warmEngine(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            // after the first screens have drawn — it competes for the CPU on modest phones
            kotlinx.coroutines.delay(4000)
            try {
                Torrents.get(app).warmUp()
            } catch (t: Throwable) {
                Log.w("5broz", "engine warm-up failed: $t")
            }
        }
    }
}
