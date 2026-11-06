package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the power gauge: a grey arc with the target range in green, a needle at the current
 * watts, and the watts number inside the arc. Everything is one bitmap so it scales as a whole
 * (the ImageView uses fitCenter), which keeps the number inside the arc when Karoo makes the
 * field shorter (a loaded route) without telling the app.
 *
 * The arc spans the target ± 4 × the range's half width, and at least ± 10 % of the target, so
 * the green band takes about a quarter of the arc. The needle stops at the ends.
 */
class GaugeRenderer(private val context: Context) {

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    /**
     * @param width bitmap width in px; the height follows from [ASPECT].
     * @param numberColor colour of the watts digits (plain text colour, or a range colour).
     * @param karooStyle Karoo native styling: Karoo green band and Karoo's condensed font.
     */
    fun render(width: Int, watts: Int?, target: Int?, range: IntRange?, numberColor: Int, karooStyle: Boolean = false): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = (w * ASPECT).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Geometry from the approved sketch (gauge-b4.html), in units of the width.
        val cx = w / 2f
        val r = w * 0.4286f
        val sw = w * 0.0759f
        val cy = w * 0.491f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)

        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            strokeCap = Paint.Cap.ROUND
        }
        // Android angles run clockwise from 3 o'clock: the arc goes from 200° to 340° (160° → 20°
        // in maths angles, i.e. a 140° bow over the top).
        arc.color = color(R.color.gauge_track)
        c.drawArc(oval, START_DEG, SWEEP_DEG, false, arc)

        val center = target ?: range?.let { (it.first + it.last) / 2 }
        if (center != null && center > 0) {
            val bandHalf = range?.let { (it.last - it.first) / 2f } ?: 0f
            val half = max(center * 0.10f, bandHalf * 4f)
            val min = center - half
            val max = center + half
            fun fraction(v: Float) = ((v - min) / (max - min)).coerceIn(0f, 1f)

            if (range != null) {
                arc.color = color(if (karooStyle) R.color.karoo_green else R.color.gauge_band)
                val a = fraction(range.first.toFloat())
                val b = fraction(range.last.toFloat())
                c.drawArc(oval, START_DEG + a * SWEEP_DEG, (b - a) * SWEEP_DEG, false, arc)
            }

            if (watts != null) {
                // Needle across the arc: a transparent gap first (so whatever is behind the field
                // shows through), then the solid bar.
                val deg = START_DEG + fraction(watts.toFloat()) * SWEEP_DEG
                val rad = Math.toRadians(deg.toDouble())
                val r0 = r - sw / 2f - w * 0.0125f
                val r1 = r + sw / 2f + w * 0.0125f
                val x0 = cx + r0 * cos(rad).toFloat()
                val y0 = cy + r0 * sin(rad).toFloat()
                val x1 = cx + r1 * cos(rad).toFloat()
                val y1 = cy + r1 * sin(rad).toFloat()
                val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                needle.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                needle.strokeWidth = w * 0.036f
                c.drawLine(x0, y0, x1, y1, needle)
                needle.xfermode = null
                needle.color = color(R.color.text)
                needle.strokeWidth = w * 0.02f
                c.drawLine(x0, y0, x1, y1, needle)
            }
        }

        // Watts, centred, as big as fits inside the arc.
        val text = watts?.toString() ?: "--"
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (karooStyle) KAROO_TYPEFACE else Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = numberColor
            textSize = w * 0.335f
            if (karooStyle) {
                // Like Karoo's own numbers: 90% wide, letters a little closer.
                textScaleX = 0.9f
                letterSpacing = -0.05f
            }
        }
        val maxWidth = (r - sw / 2f) * 2f * 0.82f
        val measured = p.measureText(text)
        if (measured > maxWidth) p.textSize *= maxWidth / measured
        c.drawText(text, cx, w * 0.487f, p)
        return bmp
    }

    companion object {
        /** Height / width of the gauge bitmap (448 × 232 px in the sketch). */
        const val ASPECT = 0.518f
        private const val START_DEG = 200f
        private const val SWEEP_DEG = 140f

        /** Karoo's number font: on the Karoo, sans-serif-light is IBM Plex Sans Regular. */
        private val KAROO_TYPEFACE: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
}
