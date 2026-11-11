package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import kotlin.math.ceil

/**
 * Draws the workout profile in zone colours: one bar per step
 * (width = duration, height = watts).
 *
 * Heart-rate steps (docs/design/hr-targets/) are in the Karoo's heart rate zone colours. A workout
 * with only heart-rate targets is drawn in bpm, from 80 bpm up (from 0 every step would look about
 * the same height); in a mixed workout a heart-rate step stands at the watts it's worth
 * (bpm / LTHR × FTP).
 *
 * - [Style.SCREEN] (interval screen): watt guide lines, the current step outlined, a "now"
 *   marker, the step a jump lands on in solid black (white in dark mode) with a ▼, time labels.
 *   Finished steps keep their zone colour, since you may jump back to them.
 *   Supports zooming into the current step.
 * - [Style.CARD] (workout list thumbnail): bars only, transparent background.
 */
class ChartRenderer(private val context: Context) {

    enum class Style { SCREEN, CARD }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    fun render(state: ScreenState, width: Int, height: Int, scale: Float, look: Style = Style.SCREEN): Bitmap {
        val card = look == Style.CARD
        // The screen chart has no panel: it's drawn on the page colour (follows light/dark mode).
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Transparent background: the Karoo page (screen) or the card colour (list) shows through.
        val plan = state.plan ?: return bmp
        if (plan.steps.isEmpty() || plan.totalSec <= 0) return bmp

        val d = context.resources.displayMetrics.density * scale
        // The step a jump lands on and its ▼: the text colour, which no zone uses (Karoo zone
        // colours include blue and purple, the old jump colours).
        val landColor = R.color.text
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.text_secondary)
            textSize = 10f * d
        }
        val top = if (card) 2f * d else 9f * d
        // No time label under the screen's chart: the bars end 2dp above the bottom
        val bottom = if (card) height.toFloat() else height - 2f * d
        val sidePad = 0f
        val plotW = width - 2 * sidePad

        // Time window: the whole workout, or (zoomed) the current interval plus a little of the
        // intervals either side for context.
        val current = plan.steps.getOrNull(state.stepIndex)
        // While a jump is being chosen or counted down, show the whole workout so the target is visible.
        val zoomed = !card && state.chartZoomed && current != null && state.landTarget == null
        val (winStart, winEnd) = if (zoomed && current != null) {
            val start = plan.startSecOf(state.stepIndex)
            val end = start + current.durationSec
            val pad = (current.durationSec * ZOOM_CONTEXT).toInt().coerceIn(ZOOM_MIN_PAD_SEC, ZOOM_MAX_PAD_SEC)
            (start - pad).coerceAtLeast(0) to (end + pad).coerceAtMost(plan.totalSec)
        } else {
            0 to plan.totalSec
        }
        val span = (winEnd - winStart).coerceAtLeast(1).toFloat()
        val ftp = state.ftp
        val hrChart = plan.hrOnly
        val lthr = plan.lthr?.takeIf { it > 0 }
        // A step's height: watts, or bpm (heart-rate workout), or a heart-rate step's watts equivalent.
        fun level(step: PlanStep, v: Int): Float = when {
            !step.hr || hrChart -> v.toFloat()
            lthr != null && ftp != null -> v.toFloat() * ftp / lthr
            else -> (ftp ?: plan.maxWatts.coerceAtLeast(200)) * 0.7f
        }
        val steps = if (zoomed) {
            var t0 = 0
            plan.steps.filter { s ->
                val visible = t0 < winEnd && t0 + s.durationSec > winStart
                t0 += s.durationSec
                visible
            }.ifEmpty { plan.steps }
        } else {
            plan.steps
        }
        val visibleMax = steps.maxOf { maxOf(level(it, it.startWatts), level(it, it.endWatts)) }
        val (floor, maxWatts, gridStep) = if (hrChart) {
            val low = steps.minOf { minOf(it.startWatts, it.endWatts) }
            Triple(minOf(80f, (low - 20) / 10 * 10f).coerceAtLeast(0f), (ceil(visibleMax * 1.05 / 10.0) * 10).toFloat().coerceAtLeast(100f), 50)
        } else {
            Triple(0f, (ceil(visibleMax * 1.1 / 100.0) * 100).toFloat().coerceAtLeast(100f), 100)
        }
        fun x(sec: Float) = sidePad + (sec - winStart) / span * plotW
        fun y(level: Float) = bottom - ((level - floor) / (maxWatts - floor)).coerceAtLeast(0f) * (bottom - top)
        fun y(watts: Int) = y(watts.toFloat())

        // No watt scale (guide lines and numbers): the workout's shape is enough (design: heavier-values)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f * d
            color = color(R.color.text)
        }
        val gap = if (card) 0.5f * d else 0.75f * d
        var t = 0f
        plan.steps.forEachIndexed { i, step ->
            val isTarget = !card && i == state.landTarget
            val current = !card && i == state.stepIndex
            // Every bar but the current one reaches the field's edges (its corners are rounded by
            // the screen); the current one keeps its space so its outline reads.
            val toEdge = !card && !current
            val x0 = if (toEdge && i == 0) 0f else x(t) + gap
            val x1 = (if (toEdge && i == plan.steps.lastIndex) width.toFloat() else x(t + step.durationSec) - gap).coerceAtLeast(x0 + 0.5f)
            val bottom = if (toEdge) height.toFloat() else bottom
            // Ramps slope; ranges and single targets are drawn flat at their middle.
            val ys = if (step.isRamp) y(level(step, step.startWatts)) to y(level(step, step.endWatts)) else y(level(step, step.avgWatts)).let { it to it }
            val path = Path().apply {
                moveTo(x0, bottom)
                lineTo(x0, ys.first)
                lineTo(x1, ys.second)
                lineTo(x1, bottom)
                close()
            }
            val bar = if (step.hr) Zones.BarStyle(Zones.hrBarColor(step.avgWatts, state.hrZones, lthr), null) else Zones.barStyle(step.avgWatts, ftp)
            // Every step in its zone colour (finished ones too); the jump target solid black/white.
            val alpha = 255
            fill.color = color(if (isTarget) landColor else bar.fill)
            // Current step: its 2dp outline goes on the bar's edge, and the fill moves in to leave
            // a 1dp gap of page colour between them, so the zone colour and outline both read
            // (design: docs/design/karoo-zone-colours/, outline gap A). Too narrow: fill as usual.
            val inset = 2f * d   // half the outline (1dp) + the gap (1dp)
            val fillPath = if (current && x1 - x0 > 2 * inset + 1f * d) {
                // The gap goes all around, the bottom too (the outline closes along the bottom).
                Path().apply {
                    moveTo(x0 + inset, bottom - inset)
                    lineTo(x0 + inset, minOf(ys.first + inset, bottom - inset))
                    lineTo(x1 - inset, minOf(ys.second + inset, bottom - inset))
                    lineTo(x1 - inset, bottom - inset)
                    close()
                }
            } else {
                path
            }
            canvas.drawPath(fillPath, fill)
            bar.edge?.let { edge ->
                // Karoo draws easy bars with a bright outline, just inside the bar.
                val inset = edgePaint.strokeWidth / 2
                val edgePath = Path().apply {
                    moveTo(x0 + inset, bottom)
                    lineTo(x0 + inset, ys.first + inset)
                    lineTo(x1 - inset, ys.second + inset)
                    lineTo(x1 - inset, bottom)
                }
                edgePaint.color = color(edge)
                edgePaint.alpha = alpha
                canvas.drawPath(edgePath, edgePaint)
            }
            if (!card && i == state.stepIndex) canvas.drawPath(path, outline)
            if (isTarget) {
                // ▼ above the target bar
                val cx = (x0 + x1) / 2
                val tipY = maxOf(minOf(ys.first, ys.second) - 2f * d, 13f * d) // stays inside the chart
                val tri = Path().apply {
                    moveTo(cx - 8f * d, tipY - 12f * d)
                    lineTo(cx + 8f * d, tipY - 12f * d)
                    lineTo(cx, tipY)
                    close()
                }
                canvas.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(landColor) })
            }
            t += step.durationSec
        }

        if (card) return bmp

        // "Now" marker
        val nowSec = plan.startSecOf(state.stepIndex) + state.elapsedInStepSec
        val nx = x(nowSec.toFloat())
        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(R.color.text); strokeWidth = 2f * d }
        canvas.drawLine(nx, top - 6f * d, nx, bottom, marker)
        val tri = Path().apply {
            moveTo(nx - 5f * d, top - 8f * d)
            lineTo(nx + 5f * d, top - 8f * d)
            lineTo(nx, top - 2f * d)
            close()
        }
        marker.style = Paint.Style.FILL
        canvas.drawPath(tri, marker)

        return bmp
    }

    companion object {
        /** Zoomed view shows this share of the interval's length before and after it… */
        private const val ZOOM_CONTEXT = 0.15
        /** …but at least / at most this many seconds. */
        private const val ZOOM_MIN_PAD_SEC = 20
        private const val ZOOM_MAX_PAD_SEC = 120
    }
}
