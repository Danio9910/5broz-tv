package com.fivebroz.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A film or episode kept on this device for watching without internet (on a flight, say). */
data class OfflineItem(
    val videoId: String,
    val metaId: String,
    val type: String,
    val name: String,
    val label: String,
    val poster: String?,
    /** "waiting", "finding" (looking for a source), "downloading", "done", "failed" */
    val status: String,
    val progress: Float = 0f,
    val bytes: Long = 0,
    val total: Long = 0,
    val path: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val url: String? = null,
    val error: String? = null,
)

/** The list of downloads, kept in this device's settings (downloads are per device, not per profile). */
object Offline {
    private const val KEY = "offline.items"

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "offline").apply { mkdirs() }

    @Synchronized
    fun all(ctx: Context): List<OfflineItem> {
        val raw = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray()).mapObjects { o ->
            OfflineItem(
                videoId = o.str("videoId") ?: return@mapObjects null,
                metaId = o.str("metaId") ?: "",
                type = o.str("type") ?: "movie",
                name = o.str("name") ?: "",
                label = o.str("label") ?: "",
                poster = o.str("poster"),
                status = o.str("status") ?: "waiting",
                progress = o.optDouble("progress", 0.0).toFloat(),
                bytes = o.optLong("bytes"),
                total = o.optLong("total"),
                path = o.str("path"),
                infoHash = o.str("infoHash"),
                fileIdx = o.int("fileIdx"),
                url = o.str("url"),
                error = o.str("error"),
            )
        }
    }

    @Synchronized
    private fun save(ctx: Context, list: List<OfflineItem>) {
        val a = JSONArray()
        list.forEach { i ->
            a.put(
                JSONObject().put("videoId", i.videoId).put("metaId", i.metaId).put("type", i.type).put("name", i.name)
                    .put("label", i.label).put("poster", i.poster ?: JSONObject.NULL).put("status", i.status)
                    .put("progress", i.progress.toDouble()).put("bytes", i.bytes).put("total", i.total)
                    .put("path", i.path ?: JSONObject.NULL).put("infoHash", i.infoHash ?: JSONObject.NULL)
                    .put("fileIdx", i.fileIdx ?: JSONObject.NULL).put("url", i.url ?: JSONObject.NULL)
                    .put("error", i.error ?: JSONObject.NULL),
            )
        }
        ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    @Synchronized
    fun put(ctx: Context, item: OfflineItem) {
        val list = all(ctx).filter { it.videoId != item.videoId } + item
        save(ctx, list)
    }

    @Synchronized
    fun update(ctx: Context, videoId: String, f: (OfflineItem) -> OfflineItem) {
        save(ctx, all(ctx).map { if (it.videoId == videoId) f(it) else it })
    }

    @Synchronized
    fun remove(ctx: Context, videoId: String) {
        val it = all(ctx).firstOrNull { x -> x.videoId == videoId }
        it?.path?.let { p -> runCatching { File(p).delete() } }
        save(ctx, all(ctx).filter { x -> x.videoId != videoId })
    }

    /** The finished file for this video, if it's on the device. */
    fun fileFor(ctx: Context, videoId: String): File? =
        all(ctx).firstOrNull { it.videoId == videoId && it.status == "done" }?.path?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
}
