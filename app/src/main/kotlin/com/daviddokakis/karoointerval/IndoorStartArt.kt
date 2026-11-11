package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * The indoor START box (docs/design/indoor-erg/indoor-start): a real Karoo 3, zoomed in on its
 * start / pause button (lower right side), edge to edge on the left of the yellow box; the button
 * flashes green ([flashOn] flips once a second); PRESS START TO BEGIN and how the button works on
 * the right. The box is taller than the usual START box ([HEIGHT_DP]); the big watts above give way. Drawn as one bitmap, since a field can't lay a photo and text out this way itself.
 */
object IndoorStartArt {
    private var photo: Bitmap? = null

    /** The button's outline in `karoo_start_button.png` (200 × 580, traced on the photo). */
    private val OUTLINE = floatArrayOf(
        140.3f, 225f, 112f, 201.7f, 103.7f, 205f, 88.7f, 346.7f, 92f, 358f, 118.7f, 370f, 122.7f, 365f,
    )
    private const val PHOTO_W = 200f
    private const val PHOTO_H = 580f
    /** The button's middle (photo pixels), kept in the middle of the box's height. */
    private const val BUTTON_Y = 286f
    /** Photo pixels per Karoo px: the zoom of the approved mock (290 photo px in a 232 px box). */
    private const val ZOOM = 232f / 290f
    /** The indoor START box's usual height: room for the title and the line under it at 24 px. */
    const val HEIGHT_DP = 124f

    fun draw(context: Context, w: Int, h: Int, radius: Float, u: Float, flashOn: Boolean): Bitmap {
        val res = context.resources
        val src = photo ?: BitmapFactory.decodeResource(res, R.drawable.karoo_start_button).also { photo = it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val box = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) }
        c.clipPath(box)
        c.drawColor(context.getColor(R.color.karoo_yellow))

        // The Karoo, the box's full height, from its left edge, always at the same zoom: a taller
        // box shows more of the photo above and below the button, not a bigger photo.
        val k = ZOOM * u
        val pw = PHOTO_W * k
        val srcH = (h / k).coerceAtMost(PHOTO_H)
        val srcTop = (BUTTON_Y - srcH / 2f).coerceIn(0f, PHOTO_H - srcH)
        c.drawBitmap(src, android.graphics.Rect(0, srcTop.toInt(), PHOTO_W.toInt(), (srcTop + srcH).toInt()), RectF(0f, 0f, pw, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

        // The start button, tinted green along its own outline
        val button = Path().apply {
            moveTo(OUTLINE[0] * k, (OUTLINE[1] - srcTop) * k)
            for (i in 2 until OUTLINE.size step 2) lineTo(OUTLINE[i] * k, (OUTLINE[i + 1] - srcTop) * k)
            close()
        }
        // The Karoo's own success green (success_green_600, its switches when on)
        val green = context.getColor(R.color.karoo_success_green)
        c.drawPath(button, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; alpha = if (flashOn) 204 else 64 })
        c.drawPath(
            button,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 6.5f * u; strokeJoin = Paint.Join.ROUND
                color = green; alpha = if (flashOn) 255 else 128
            },
        )

        // The words, in the Karoo's px (u = px per Karoo px): the title Plex SemiBold 36, the line
        // under it Plex Medium 24, never smaller (karoo-type README › The smallest readable text).
        // The block sits in the middle of the box's height.
        val ink = context.getColor(R.color.karoo_yellow_fg)
        val x = pw + 20 * u
        val textW = (w - x - 14 * u).toInt().coerceAtLeast(1)
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create("sans-serif", Typeface.BOLD); textSize = 36 * u
        }
        val lines = res.getString(R.string.start_indoor_title).split('\n')
        // Shrink the title only if it doesn't fit (a narrow field); the small line never shrinks
        while (lines.maxOf { title.measureText(it) } > textW && title.textSize > 26 * u) title.textSize -= 1f
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create("sans-serif", Typeface.NORMAL); textSize = 24 * u
        }
        val text = res.getString(R.string.start_indoor_sub)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, sub, textW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setMaxLines(4).build()
        val lineH = title.textSize * 1.08f
        val gap = 8 * u
        val total = lineH * lines.size + gap + layout.height
        var y = (h - total) / 2f
        lines.forEach { y += lineH; c.drawText(it, x, y - title.textSize * 0.2f, title) }
        c.save(); c.translate(x, y + gap); layout.draw(c); c.restore()
        return bmp
    }
}
