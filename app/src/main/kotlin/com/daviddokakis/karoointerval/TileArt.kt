package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * A data tile's header and number drawn as one image, in pixels, with the Karoo's own rules
 * (KarooType), so every tile matches a native Karoo field 1:1 at any size. Layout dp rounding and
 * text-view font padding can't creep in. The tile's background stays the layout's.
 *
 * Geometry, in Karoo 3 px (× k off a Karoo 3):
 * - Label: Plex Sans Condensed capitals, 20 px tall (23 on tiles 150 px and up), their top
 *   KarooType.headerCapsTop below the tile's top, right-aligned 7 px in.
 * - Icons: the Karoo's own field glyphs (KarooType.Glyph, drawGlyph) filling boxes 25 × caps / 23,
 *   the first 12 px from the left, bottoms on the label's baseline, 4 px apart.
 * - Number: Relative, −0.04, auto-sized so its line fits the tile less 34 px and its width less
 *   2 × 8 px, its ink right-aligned 9 px in (like the label), the digits' ink 12 px above the bottom. Calibrated on a native
 *   236 × 140 field: 64 px digits, 24 px under the label.
 */
object TileArt {
    /** A drawable icon (the top row's, [TopRowArt]). */
    class Icon(val res: Int, val color: Int)

    /** A header icon: one of the Karoo's own field glyphs ([KarooType.Glyph]). */
    class Glyph(val path: String, val color: Int) {
        companion object {
            /** The red Karoo heart (KarooType.drawRedHeart's), our pick for heart rate. */
            val RED_HEART = Glyph(KarooType.HEART_FILLED, KarooType.HEART_RED)
        }
    }

    fun draw(
        context: Context, w: Int, h: Int, k: Float, icons: List<Glyph>, label: String, value: String,
        labelColor: Int, valueColor: Int,
        /** Where a jump would land ("250"): drawn small after the number, with an arrow centred on it. */
        landing: String? = null, arrowColor: Int = 0,
        /** The Karoo's empty state ("No Sensor", "Searching…") instead of the number. */
        empty: String? = null,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val tileH = (h / k).roundToInt()   // in Karoo 3 px, for the size rules
        val caps = KarooType.headerCapsPx(tileH) * k
        val capsTop = KarooType.headerCapsTop(tileH, fullWidth = false) * k
        val baseline = capsTop + caps

        val box = KarooType.headerIconBoxPx(KarooType.headerCapsPx(tileH)) * k
        var x = KarooType.HEADER_ICON_LEFT_PX * k
        val glyphPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        for (g in icons) {
            glyphPaint.color = g.color
            KarooType.drawGlyph(c, g.path, x, baseline, box, glyphPaint)
            x += box + 4 * k
        }

        // Label, shrunk only if it would run into the icons
        val labelPaint = KarooType.labelPaint(caps.roundToInt()).apply { color = labelColor }
        val right = w - KarooType.HEADER_RIGHT_INSET_PX * k
        val room = right - x
        val lw = labelPaint.measureText(label)
        if (lw > room && room > 0) labelPaint.textSize *= room / lw
        c.drawText(label, right, baseline, labelPaint)

        if (empty != null) {
            val p = KarooType.emptyPaint(empty, (w / k).roundToInt()).apply { color = valueColor; textSize *= k }
            c.drawText(empty, w / 2f, KarooType.emptyBaseline(tileH) * k, p)
            return bmp
        }

        // Number
        val valuePaint = KarooType.valuePaint().apply { color = valueColor }
        val margin = KarooType.VALUE_MARGIN_PX * k
        var size = KarooType.valueSize(valuePaint, value, (w - 2 * margin).toInt(), (h - 34 * k).toInt())
        // A jump's landing target: "→ 250" at 45 % of the number's size, right-aligned after it; the
        // number gives up the width it needs
        var landingW = 0f
        val landPaint = KarooType.valuePaint().apply { color = valueColor; textAlign = android.graphics.Paint.Align.LEFT }
        val arrowPaint = KarooType.labelPaint(10).apply { color = arrowColor; textAlign = android.graphics.Paint.Align.LEFT }
        if (landing != null) {
            fun measure(sz: Float): Float {
                landPaint.textSize = sz * 0.45f; arrowPaint.textSize = sz * 0.45f
                return arrowPaint.measureText("→\u2009") + landPaint.measureText(landing) + 8 * k
            }
            landingW = measure(size)
            size = KarooType.valueSize(valuePaint, value, (w - 2 * margin - landingW).toInt(), (h - 34 * k).toInt())
            landingW = measure(size)
        }
        valuePaint.textSize = size
        val ink = Rect(); valuePaint.getTextBounds("8", 0, 1, ink)
        // Right-aligned by its ink, 9 px in like the label's glyphs (Relative's digits carry a few
        // px of side bearing that would otherwise push the number further in)
        val textInk = Rect(); valuePaint.getTextBounds(value, 0, value.length, textInk)
        val textW = valuePaint.measureText(value)
        val inkRightGap = textW - textInk.right
        val valueBase = h - 12 * k - ink.bottom
        if (landing != null) {
            var lx = w - 9 * k - landingW + 8 * k
            // The arrow in the number's colour, its middle on the middle of the small digits
            arrowPaint.color = valueColor
            val ab = Rect(); arrowPaint.getTextBounds("→", 0, 1, ab)
            val db = Rect(); landPaint.getTextBounds("8", 0, 1, db)
            val arrowBase = valueBase + (db.top + db.bottom) / 2f - (ab.top + ab.bottom) / 2f
            c.drawText("→\u2009", lx, arrowBase, arrowPaint)
            lx += arrowPaint.measureText("→\u2009")
            c.drawText(landing, lx, valueBase, landPaint)
        }
        c.drawText(value, w - 9 * k - landingW + inkRightGap, valueBase, valuePaint)
        return bmp
    }
}

/**
 * The workout screen's top row (heart rate, cadence, target, or power on a heart-rate step) drawn
 * as one image with BigWatts' rules for its heart-rate line, so both apps match: Relative at the
 * font's own spacing, as heavy as the Karoo's header (a thin outline, 2.8 % of the size), each icon
 * 1.05 × the size wide with its top 0.9 × the size above the baseline, 0.3 × the size before the
 * number. Two values sit 0.9 × the size apart. A jump's landing target ("→ 250") follows the first.
 */
object TopRowArt {
    /** [heart]: the red Karoo heart (KarooType.drawRedHeart, redHeartBox) instead of [icon]'s drawable, as BigWatts. */
    class Item(val icon: TileArt.Icon, val text: String, val heart: Boolean = false)

    private var c0: Canvas? = null

    fun draw(context: Context, items: List<Item>, sizePx: Float, h: Int, ink: Int, landing: String? = null): Bitmap {
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = KarooType.value; letterSpacing = 0f; textSize = sizePx; color = ink
            style = android.graphics.Paint.Style.FILL_AND_STROKE; strokeWidth = sizePx * 0.028f
        }
        val small = android.graphics.Paint(text).apply { textSize = sizePx * 0.7f; strokeWidth = sizePx * 0.7f * 0.028f }
        val arrow = KarooType.labelPaint(10).apply { color = ink; textSize = sizePx * 0.7f; textAlign = android.graphics.Paint.Align.LEFT }
        // Relative is monospaced: its space is a full digit wide, so words get a normal gap (as BigWatts)
        val wordGap = sizePx * 0.28f
        fun measure(t: String) = t.split(' ').let { ws -> ws.sumOf { text.measureText(it).toDouble() }.toFloat() + (ws.size - 1) * wordGap }
        fun drawWords(t: String, x0: Float, y: Float) {
            var xx = x0
            for (word in t.split(' ')) { c0?.drawText(word, xx, y, text); xx += text.measureText(word) + wordGap }
        }
        val iw = KarooType.heartBox(sizePx)
        val gap = KarooType.heartGap(sizePx)
        val between = sizePx * 0.9f
        val landW = landing?.let { gap + arrow.measureText("→\u2009") + small.measureText(it) } ?: 0f
        val widths = items.mapIndexed { i, it -> iw + gap + measure(it.text) + (if (i == 0) landW else 0f) }
        val w = (widths.sum() + between * (items.size - 1)).toInt().coerceAtLeast(1) + 2
        val bmp = Bitmap.createBitmap(w, h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c0 = c
        val digits = Rect(); text.getTextBounds("8", 0, 1, digits)
        val baseline = h / 2f + digits.height() / 2f
        var x = 1f
        items.forEachIndexed { i, it ->
            val top = KarooType.heartTop(baseline, sizePx)
            if (it.heart) {
                val rb = KarooType.redHeartBox(sizePx)
                KarooType.drawRedHeart(c, x + (iw - rb) / 2, baseline, rb, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
            } else {
                ContextCompat.getDrawable(context, it.icon.res)?.mutate()?.let { d ->
                    d.colorFilter = PorterDuffColorFilter(it.icon.color, PorterDuff.Mode.SRC_IN)
                    d.setBounds(x.roundToInt(), top.roundToInt(), (x + iw).roundToInt(), (top + iw).roundToInt())
                    d.draw(c)
                }
            }
            x += iw + gap
            drawWords(it.text, x, baseline)
            x += measure(it.text)
            if (i == 0 && landing != null) {
                x += gap
                c.drawText("→\u2009", x, baseline, arrow); x += arrow.measureText("→\u2009")
                c.drawText(landing, x, baseline, small); x += small.measureText(landing)
            }
            x += between
        }
        return bmp
    }
}

