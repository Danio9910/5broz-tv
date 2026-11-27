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
        entry = Entry(key, System.currentTimeMillis(), scope.async { Api.streams(type, videoId) })
        warmEngine(ctx)
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
            try {
                Torrents.get(app).warmUp()
            } catch (t: Throwable) {
                Log.w("5broz", "engine warm-up failed: $t")
            }
        }
    }
}
