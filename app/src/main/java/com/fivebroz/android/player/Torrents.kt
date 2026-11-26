package com.fivebroz.android.player

import android.content.Context
import com.fivebroz.torrent.TorrentEngine
import java.io.File

/** One torrent session for the whole app (it is expensive to start). */
object Torrents {
    @Volatile private var engine: TorrentEngine? = null

    fun get(ctx: Context): TorrentEngine =
        engine ?: synchronized(this) {
            engine ?: TorrentEngine(File(ctx.applicationContext.cacheDir, "torrents")).also { engine = it }
        }
}
