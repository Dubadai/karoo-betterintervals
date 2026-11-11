package com.daviddokakis.karoointerval

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.dubadai.karoo.common.KarooType
import kotlin.math.ceil

/**
 * The ERG pill next to the gear (docs/design/indoor-erg, mock round 1): "● ERG" on green while the
 * trainer follows the workout, "ERG" grey while it's free, "ERG …" grey while connecting, "ERG ✕"
 * amber when the trainer is lost. Sizes are the mock's, in Karoo pixels, times [scale].
 */
object ErgPillArt {
    private const val H = 36f
    private const val PAD = 14f
    private const val DOT_R = 5f
    private const val DOT_W = 16f
    private const val TEXT = 24f
    private const val GLYPH = 22f
    private const val GAP = 8f
    private const val BASELINE = 26f

    fun draw(status: ErgStatus, bg: Int, fg: Int, scale: Float): Bitmap {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = KarooType.label   // Plex Sans Condensed Medium, the Karoo's header font
            textSize = TEXT * scale
            color = fg
        }
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textSize = GLYPH * scale
            color = fg
        }
        val glyph = when (status) { ErgStatus.CONNECTING -> "…"; ErgStatus.LOST -> "✕"; else -> null }
        val word = if (status == ErgStatus.OFF) "ERG off" else "ERG"
        val dot = if (status == ErgStatus.HOLDING) DOT_W * scale else 0f
        val textW = label.measureText(word)
        val glyphW = glyph?.let { GAP * scale + glyphPaint.measureText(it) } ?: 0f
        val w = ceil(PAD * scale * 2 + dot + textW + glyphW).toInt()
        val h = ceil(H * scale).toInt()
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, fill)
        var x = PAD * scale
        if (dot > 0) {
            c.drawCircle(x + DOT_R * scale, h / 2f, DOT_R * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg })
            x += dot
        }
        c.drawText(word, x, BASELINE * scale, label)
        if (glyph != null) c.drawText(glyph, x + textW + GAP * scale, BASELINE * scale, glyphPaint)
        return bmp
    }
}
