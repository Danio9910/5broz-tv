package com.fivebroz.android.ui

import android.content.Context
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Preview
import com.fivebroz.android.player.PlayArgs
import com.fivebroz.android.player.PlayerActivity
import com.fivebroz.android.player.StreamPrefetch

/**
 * "Play" straight from a banner or card: works out what to play (the episode in progress,
 * else the next unwatched one) and starts it. Returns false when the title needs its page
 * (not playable here, blocked for this profile, or "always choose the source" is on).
 */
suspend fun quickPlay(ctx: Context, p: Preview): Boolean {
    if (com.fivebroz.android.data.Prefs.streamMode == "manual") return false
    val d = Api.detail(p.type, p.id) ?: return false
    if (!d.base.id.startsWith("tt") || blockedForProfile(d)) return false
    val series = d.base.type == "series"
    val eps = d.episodes.filter { it.season > 0 }
    val resume = Db.continueWatching().firstOrNull { it.item.id == d.base.id }?.videoId
    val target = if (!series) null else {
        val finished = Db.finishedIds(d.base.id)
        eps.firstOrNull { it.id == resume } ?: eps.firstOrNull { it.id !in finished } ?: eps.firstOrNull() ?: return false
    }
    val video = target?.id ?: d.base.id
    StreamPrefetch.warm(ctx, d.base.type, video)
    val after = if (series) eps.dropWhile { it.id != video }.drop(1).take(30) else emptyList()
    PlayerActivity.start(
        ctx,
        PlayArgs(
            type = d.base.type,
            metaId = d.base.id,
            name = d.base.name,
            poster = d.base.poster,
            videoId = video,
            label = target?.let(::epLabel) ?: "",
            queueIds = ArrayList(after.map { it.id }),
            queueLabels = ArrayList(after.map(::epLabel)),
        ),
    )
    return true
}
