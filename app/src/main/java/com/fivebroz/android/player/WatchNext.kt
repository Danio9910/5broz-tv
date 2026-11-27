package com.fivebroz.android.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import java.net.URLEncoder

/**
 * Android TV / Google TV "Continue watching" row on the home screen:
 * what a profile is in the middle of shows up there and opens straight into the title.
 */
object WatchNext {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("watchnext", Context.MODE_PRIVATE)

    fun deepLink(type: String, metaId: String): Uri =
        Uri.parse("fivebroz://title/$type/${URLEncoder.encode(metaId, "UTF-8")}")

    fun update(
        ctx: Context,
        type: String,
        metaId: String,
        name: String,
        episodeLabel: String,
        poster: String?,
        positionSec: Long,
        durationSec: Long,
    ) {
        try {
            val finished = durationSec > 0 && positionSec >= durationSec * 0.92
            val sp = prefs(ctx)
            val existing = sp.getLong(metaId, -1L)
            if (finished && type != "series") {
                remove(ctx, metaId)
                return
            }
            val b = WatchNextProgram.Builder()
                .setType(if (type == "series") TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setWatchNextType(
                    if (finished) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                    else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
                )
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(name)
                .setIntentUri(deepLink(type, metaId))
                .setInternalProviderId(metaId)
            if (episodeLabel.isNotBlank()) b.setEpisodeTitle(episodeLabel)
            if (!finished && durationSec > 0) {
                b.setLastPlaybackPositionMillis((positionSec * 1000).toInt())
                b.setDurationMillis((durationSec * 1000).toInt())
            }
            poster?.let {
                b.setPosterArtUri(Uri.parse(it))
                b.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            }
            val helper = PreviewChannelHelper(ctx)
            val program = b.build()
            if (existing > 0) {
                helper.updateWatchNextProgram(program, existing)
            } else {
                val id = helper.publishWatchNextProgram(program)
                if (id > 0) sp.edit().putLong(metaId, id).apply()
            }
        } catch (e: Exception) {
            Log.w("5broz", "watch next: ${e.message}")
        }
    }

    fun remove(ctx: Context, metaId: String) {
        val sp = prefs(ctx)
        val id = sp.getLong(metaId, -1L)
        if (id <= 0) return
        runCatching { ctx.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) }
        sp.edit().remove(metaId).apply()
    }
}
