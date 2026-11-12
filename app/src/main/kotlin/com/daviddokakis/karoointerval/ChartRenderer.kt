package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

    /** Each step's bar from the last [render] (left, top, right, bottom): the details page's taps and bubble. */
    var bars: List<RectF> = emptyList()
        private set

    /**
     * [ftpLine]: a dashed line at FTP with its watts (the workout details page).
     * [sharedScale] (details page, docs/design/details-chart): every workout on one scale, the top at
     * [SHARED_TOP] × FTP, or just over the highest step when that is higher, so the FTP line is always in.
     * [selected]: that step keeps its colour, the others fade (a tap on the details page's picture).
     */
    fun render(
        state: ScreenState, width: Int, height: Int, scale: Float, look: Style = Style.SCREEN, ftpLine: Boolean = false,
        sharedScale: Boolean = false, selected: Int? = null,
    ): Bitmap {
        bars = emptyList()
        val card = look == Style.CARD
        // The screen chart has no panel: it's drawn on the page colour (follows light/dark mode).
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Transparent background: the Karoo page (screen) or the card colour (list) shows through.
        val plan = state.plan ?: return bmp
        if (plan.steps.isEmpty() || plan.totalSec <= 0) return bmp

        val d = context.resources.displayMetrics.density * scale
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = state.zonePalette
        // The graph's colours with extra contrast against the page (+15 %: the Karoo's screen is pale)
        fun contrast(c: Int): Int = ZonePalette.boosted(c, night, state.zoneContrast)
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
        } else if (sharedScale && ftp != null && ftp > 0) {
            Triple(0f, maxOf(SHARED_TOP * ftp, visibleMax * SHARED_PEAK_ROOM), 100)
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
        val boxes = ArrayList<RectF>(plan.steps.size)
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
            // Power steps in Settings › Zone colours' palette; a ramp fades from its start zone's colour
            // to its end zone's (docs/design/indoor-ride-notes). Heart-rate steps keep the Karoo's
            // heart rate zone colours.
            val zA = if (step.hr) null else Zones.zoneOf(if (step.isRamp) step.startWatts else step.avgWatts, ftp)
            val zB = if (step.hr) null else Zones.zoneOf(if (step.isRamp) step.endWatts else step.avgWatts, ftp)
            val fillA = when {
                step.hr -> contrast(color(Zones.hrBarColor(step.avgWatts, state.hrZones, lthr)))
                zA != null -> contrast(palette.fill(zA, night))
                else -> color(R.color.done)
            }
            val fillB = if (zB != null) contrast(palette.fill(zB, night)) else fillA
            val edgeA = zA?.let { palette.edge(it, night)?.let(::contrast) }
            val edgeB = zB?.let { palette.edge(it, night)?.let(::contrast) } ?: edgeA
            boxes += RectF(x0, minOf(ys.first, ys.second), x1, bottom)
            // Every step in its zone colour (finished ones too); the jump target solid black/white.
            // A tapped step on the details page: the others fade.
            val alpha = if (selected != null && i != selected) FADED else 255
            fill.shader = null
            fill.color = if (isTarget) color(landColor) else fillA
            if (!isTarget && fillA != fillB) fill.shader = android.graphics.LinearGradient(x0, 0f, x1, 0f, fillA, fillB, android.graphics.Shader.TileMode.CLAMP)
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
            fill.alpha = alpha
            canvas.drawPath(fillPath, fill)
            fill.shader = null
            fill.alpha = 255
            (if (isTarget) null else edgeA)?.let { edge ->
                // Karoo draws easy bars with a bright outline, just inside the bar.
                val inset = edgePaint.strokeWidth / 2
                val edgePath = Path().apply {
                    moveTo(x0 + inset, bottom)
                    lineTo(x0 + inset, ys.first + inset)
                    lineTo(x1 - inset, ys.second + inset)
                    lineTo(x1 - inset, bottom)
                }
                edgePaint.shader = if (edgeB != null && edgeB != edge) android.graphics.LinearGradient(x0, 0f, x1, 0f, edge, edgeB, android.graphics.Shader.TileMode.CLAMP) else null
                edgePaint.color = edge
                edgePaint.alpha = alpha
                canvas.drawPath(edgePath, edgePaint)
                edgePaint.shader = null
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
        bars = boxes

        if (ftpLine && ftp != null && !hrChart && ftp < maxWatts) {
            val fy = y(ftp)
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE; strokeWidth = 1f * d; color = color(R.color.text_secondary)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f * d, 3f * d), 0f)
            }
            canvas.drawLine(0f, fy, width.toFloat(), fy, dash)
            val text = context.getString(R.string.ftp_line, ftp)
            label.textAlign = Paint.Align.RIGHT; label.textSize = 11f * d
            // Above the line, or under it when the line is near the top
            val ty = if (fy - 4f * d - label.textSize > 0) fy - 4f * d else fy + label.textSize + 2f * d
            canvas.drawText(text, width.toFloat(), ty, label)
            label.textAlign = Paint.Align.LEFT
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
        /** The details page's shared scale: the top is 130 % of FTP… */
        const val SHARED_TOP = 1.3f
        /** …or the highest step plus 5 % when that is higher (sprints). */
        const val SHARED_PEAK_ROOM = 1.05f
        /** A step that isn't the tapped one: 30 %. */
        private const val FADED = 77

        /** The step under [x] on a chart [width] wide that shows the whole workout (the card look). */
        fun stepAt(plan: WorkoutPlan, width: Int, x: Float): Int? {
            if (plan.steps.isEmpty() || plan.totalSec <= 0 || width <= 0) return null
            val sec = (x / width * plan.totalSec).coerceIn(0f, plan.totalSec - 0.5f)
            var t = 0
            plan.steps.forEachIndexed { i, st -> t += st.durationSec; if (sec < t) return i }
            return plan.steps.lastIndex
        }
        /** Zoomed view shows this share of the interval's length before and after it… */
        private const val ZOOM_CONTEXT = 0.15
        /** …but at least / at most this many seconds. */
        private const val ZOOM_MIN_PAD_SEC = 20
        private const val ZOOM_MAX_PAD_SEC = 120
    }
}
