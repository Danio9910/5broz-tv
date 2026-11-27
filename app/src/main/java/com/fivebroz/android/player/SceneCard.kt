package com.fivebroz.android.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.fivebroz.android.R

/** The picture people share: the frame, the line being said, the title, and a small 5broz mark. */
object SceneCard {
    fun draw(ctx: Context, frame: Bitmap, line: String, title: String, label: String): Bitmap {
        val w = frame.width
        val h = frame.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(frame, 0f, 0f, null)
        // dark band at the bottom for the words
        val shade = Paint().apply {
            shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(220, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), shade)
        val arabic = runCatching { ResourcesCompat.getFont(ctx, R.font.amiri_bold) }.getOrNull()
        val pad = w * 0.05f
        if (line.isNotBlank()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = w * 0.045f
                typeface = arabic
                setShadowLayer(8f, 0f, 2f, Color.BLACK)
            }
            val lay = StaticLayout.Builder.obtain("«$line»", 0, line.length + 2, tp, (w - pad * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
            c.save()
            c.translate(pad, h - pad * 1.9f - lay.height)
            lay.draw(c)
            c.restore()
        }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = w * 0.026f
            typeface = arabic
        }
        val caption = if (label.isNotBlank()) "$title — $label" else title
        val capLay = StaticLayout.Builder.obtain(caption, 0, caption.length, small, (w - pad * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(1).build()
        c.save()
        c.translate(pad, h - pad * 1.2f)
        capLay.draw(c)
        c.restore()
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xD4, 0xAF, 0x6A)
            textSize = w * 0.032f
            typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.cormorant) }.getOrNull()
        }
        c.drawText("5broz", pad, pad + mark.textSize, mark)
        return out
    }
}
