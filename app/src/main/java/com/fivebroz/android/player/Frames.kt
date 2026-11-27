package com.fivebroz.android.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import java.io.File

/**
 * "Continue watching" shows the frame where you stopped, not the poster (like Netflix).
 * A small copy of the video surface is saved per title, at most once a minute while playing.
 */
object Frames {
    private var lastAt = 0L

    fun file(ctx: Context, metaId: String): File = File(File(ctx.filesDir, "frames"), metaId.replace(Regex("[^A-Za-z0-9_.:-]"), "_") + ".jpg")

    /** The saved frame as an image URL, or null. The time keeps image caches fresh. */
    fun url(ctx: Context, metaId: String): String? = file(ctx, metaId).takeIf { it.exists() && it.length() > 2000 }?.let { "file://${it.absolutePath}?t=${it.lastModified()}" }

    fun capture(ctx: Context, surface: SurfaceView?, metaId: String, force: Boolean = false) {
        val s = surface ?: return
        if (!s.holder.surface.isValid || s.width == 0 || s.height == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastAt < 60_000) return
        lastAt = now
        val w = 480
        val h = (w * s.height / s.width.toFloat()).toInt().coerceIn(120, 480)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val app = ctx.applicationContext
        runCatching {
            PixelCopy.request(s, bmp, { result ->
                if (result != PixelCopy.SUCCESS) return@request
                Thread {
                    runCatching {
                        // A black frame (fade, credits, protected video) is worse than the poster.
                        if (dark(bmp)) return@Thread
                        val f = file(app, metaId)
                        f.parentFile?.mkdirs()
                        val tmp = File(f.parentFile, f.name + ".tmp")
                        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }
                        tmp.renameTo(f)
                    }
                }.start()
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun dark(b: Bitmap): Boolean {
        var sum = 0L
        var n = 0
        val stepX = maxOf(1, b.width / 16)
        val stepY = maxOf(1, b.height / 9)
        for (y in 0 until b.height step stepY) for (x in 0 until b.width step stepX) {
            val c = b.getPixel(x, y)
            sum += ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)
            n++
        }
        return n == 0 || sum / (n * 3) < 18
    }
}
