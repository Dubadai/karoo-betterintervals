package com.daviddokakis.karoointerval

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.models.ViewConfig

/** Turns a [ScreenState] into the RemoteViews shown in the Karoo data field. */
class ScreenRenderer(private val context: Context, private val config: ViewConfig) {

    private val chart = ChartRenderer(context)
    private val density = context.resources.displayMetrics.density

    /** The layout is designed for a Karoo 3 full page (~255x385 dp); scale to the real field size. */
    private val scale: Float = run {
        val (w, h) = config.viewSize
        if (w <= 0 || h <= 0) 1f else minOf(w / (REF_W_DP * density), h / (REF_H_DP * density)).coerceIn(0.5f, 1.6f)
    }
    private val chartHeightPx = (CHART_DP * density * scale).toInt()

    private val nextIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_NEXT, 1) }
    private val pauseIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_PAUSE, 2) }
    private val previousIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_PREVIOUS, 3) }
    private val chartIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_CHART, 5) }
    private val cancelJumpIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_CANCEL_JUMP, 6) }
    private val wattsColorIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_WATTS_COLOR, 7) }
    private val settingsIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            4,
            Intent(context, SetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SetupActivity.EXTRA_FROM_RIDE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun render(state: ScreenState): RemoteViews {
        // Debug builds show demo data; in release builds this returns the real state unchanged.
        val s = DemoData.apply(state)
        val plan = s.plan ?: return renderFree(s)
        val v = RemoteViews(context.packageName, R.layout.field_interval)
        applyTextSizes(v)

        v.setViewVisibility(R.id.content, View.VISIBLE)
        v.setViewVisibility(R.id.btn_settings, View.VISIBLE)
        v.setViewVisibility(R.id.btn_pause, View.VISIBLE)
        v.setOnClickPendingIntent(R.id.btn_settings, settingsIntent)

        // 3 s average power. Tap to cycle how the target range shows: plain, coloured number,
        // coloured background behind the top of the screen, or both.
        val range = rangeState(s)
        v.setTextViewText(R.id.watts, s.power3s?.toString() ?: "--")
        val wattsColor = if (s.wattsMode.colorsText && range != null) {
            when (range) {
                RangeState.UNDER -> R.color.watts_under
                RangeState.IN -> R.color.watts_in_range
                RangeState.OVER -> R.color.watts_over
            }
        } else {
            R.color.text
        }
        v.setTextColor(R.id.watts, color(wattsColor))
        val upperBg = if (s.wattsMode.colorsBackground && range != null) {
            when (range) {
                RangeState.UNDER -> R.drawable.bg_range_under
                RangeState.IN -> R.drawable.bg_range_in
                RangeState.OVER -> R.drawable.bg_range_over
            }
        } else {
            0 // no background
        }
        v.setInt(R.id.upper, "setBackgroundResource", upperBg)
        // On a tint, the tile labels use the full text colour so they stay readable (>= 7:1).
        val labelColor = color(if (upperBg != 0) R.color.text else R.color.text_secondary)
        v.setTextColor(R.id.lap_avg_label, labelColor)
        v.setTextColor(R.id.target_label, labelColor)
        v.setOnClickPendingIntent(R.id.watts, wattsColorIntent)
        v.setTextViewText(R.id.hr, s.heartRate?.toString() ?: "--")

        // Pause / resume the workout (top right); the ride keeps recording
        v.setImageViewResource(R.id.btn_pause_icon, if (s.workoutPaused) R.drawable.ic_play else R.drawable.ic_pause)
        v.setContentDescription(
            R.id.btn_pause_icon,
            context.getString(if (s.workoutPaused) R.string.cd_resume else R.string.cd_pause),
        )
        v.setOnClickPendingIntent(R.id.btn_pause, pauseIntent)

        // Interval number (or "Paused" / "Workout complete"), time left and progress
        when {
            s.paused || s.workoutPaused -> {
                v.setTextViewText(R.id.interval_label, context.getString(R.string.paused))
                v.setTextColor(R.id.interval_label, color(R.color.paused))
            }
            s.finished -> {
                v.setTextViewText(R.id.interval_label, context.getString(R.string.workout_complete))
                v.setTextColor(R.id.interval_label, color(R.color.watts_in_range))
            }
            else -> {
                v.setTextViewText(R.id.interval_label, context.getString(R.string.interval_of, s.stepIndex + 1, s.stepCount))
                v.setTextColor(R.id.interval_label, color(R.color.text_secondary))
            }
        }
        v.setTextViewText(R.id.time_left, s.intervalRemainingSec?.let(::formatClock) ?: "--:--")
        val total = s.intervalTotalSec ?: 0
        val progress = if (total > 0) (s.elapsedInStepSec * 1000 / total).coerceIn(0, 1000) else 0
        v.setProgressBar(R.id.interval_progress, 1000, progress, false)

        // Tiles
        v.setTextViewText(R.id.lap_avg, s.lapAvg?.toString() ?: "--")
        v.setTextViewText(R.id.target, s.target?.toString() ?: "--")
        val jump = s.jumpStep
        if (s.countdown != null && jump != null) {
            v.setTextViewText(R.id.target_next, "→ ${jump.targetAt(0)}")
            v.setViewVisibility(R.id.target_next, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.target_next, View.GONE)
        }

        // Next box / countdown box
        if (s.countdown != null && jump != null) {
            v.setViewVisibility(R.id.next_box, View.GONE)
            v.setViewVisibility(R.id.countdown_box, View.VISIBLE)
            v.setTextViewText(R.id.countdown, s.countdown.toString())
            v.setTextViewText(R.id.countdown_value, summary(jump))
        } else {
            v.setViewVisibility(R.id.next_box, View.VISIBLE)
            v.setViewVisibility(R.id.countdown_box, View.GONE)
            val next = s.nextStep
            if (next != null) {
                v.setTextViewText(R.id.next_label, context.getString(R.string.next_label, s.stepIndex + 2, s.stepCount))
                v.setTextViewText(R.id.next_value, summaryWithZone(next, s.ftp))
            } else {
                v.setTextViewText(R.id.next_label, context.getString(R.string.next_last))
                v.setTextViewText(R.id.next_value, "")
            }
            v.setViewVisibility(R.id.btn_skip, if (s.hasNext) View.VISIBLE else View.INVISIBLE)
            v.setViewVisibility(R.id.btn_previous, if (s.hasPrevious) View.VISIBLE else View.INVISIBLE)
        }
        // Taps may or may not reach a data field on Karoo; the bonus actions (remote buttons) always work.
        v.setOnClickPendingIntent(R.id.btn_skip, nextIntent)
        v.setOnClickPendingIntent(R.id.btn_previous, previousIntent)
        v.setOnClickPendingIntent(R.id.countdown_box, cancelJumpIntent)

        // Chart
        val chartW = (config.viewSize.first - 16 * density).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setViewLayoutHeight(R.id.chart, chartHeightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
        if (chartW > 0) v.setImageViewBitmap(R.id.chart, chart.render(s, chartW, chartHeightPx, scale))
        v.setOnClickPendingIntent(R.id.chart, chartIntent)

        return v
    }

    private enum class RangeState { UNDER, IN, OVER }

    /**
     * Where 3 s power sits against the target range, or null when the colours shouldn't show:
     * workout or ride paused, workout finished, or no power or target.
     */
    private fun rangeState(s: ScreenState): RangeState? {
        if (s.finished || s.workoutPaused || s.paused) return null
        val p = s.power3s ?: return null
        val range = s.targetRange ?: return null
        return when {
            p < range.first -> RangeState.UNDER
            p > range.last -> RangeState.OVER
            else -> RangeState.IN
        }
    }

    /**
     * Free ride (no workout loaded): big 3 s watts, lap average and heart rate tiles (the heart
     * rate tile takes the colour of the current HR zone), then Karoo's lap number, lap time and
     * last lap. The gear opens the Workouts page to load a workout.
     */
    private fun renderFree(s: ScreenState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.field_free)
        listOf(
            R.id.free_lap_avg to 36f, R.id.free_hr to 36f,
            R.id.free_lap_label to 13f, R.id.free_hr_label to 13f,
            R.id.free_lap_number to 15f, R.id.free_lap_time to 40f, R.id.free_last_lap to 14f,
        ).forEach { (id, sp) -> v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * density * scale) }

        v.setTextViewText(R.id.free_watts, s.power3s?.toString() ?: "--")
        v.setTextViewText(R.id.free_lap_avg, s.lapPowerAvg?.toString() ?: "--")
        v.setTextViewText(R.id.free_hr, s.heartRate?.toString() ?: "--")

        val zoneBg = when (s.hrZone) {
            1 -> R.drawable.bg_hr_z1
            2 -> R.drawable.bg_hr_z2
            3 -> R.drawable.bg_hr_z3
            4 -> R.drawable.bg_hr_z4
            5 -> R.drawable.bg_hr_z5
            else -> null
        }
        // Soft tint of the HR zone colour; text keeps the normal colours and the heart stays red.
        v.setInt(R.id.free_hr_tile, "setBackgroundResource", zoneBg ?: R.drawable.bg_tile)

        v.setTextViewText(R.id.free_lap_number, s.lapNumber?.let { context.getString(R.string.lap_number, it) } ?: "")
        v.setTextViewText(R.id.free_lap_time, s.lapTimeSec?.let(::formatClock) ?: "--:--")
        val last = s.lastLapTimeSec?.takeIf { it > 0 }?.let { time ->
            s.lastLapPowerAvg?.let { context.getString(R.string.last_lap, formatClock(time), it) }
                ?: context.getString(R.string.last_lap_no_power, formatClock(time))
        }
        v.setTextViewText(R.id.free_last_lap, last ?: "")
        v.setOnClickPendingIntent(R.id.free_btn_settings, settingsIntent)
        return v
    }

    private fun summary(step: PlanStep): String = if (step.isRange && step.rangeLow != null && step.rangeHigh != null) {
        context.getString(R.string.step_summary_ramp, formatDuration(step.durationSec), step.rangeLow, step.rangeHigh)
    } else if (step.isRamp) {
        context.getString(R.string.step_summary_ramp, formatDuration(step.durationSec), step.startWatts, step.endWatts)
    } else {
        context.getString(R.string.step_summary, formatDuration(step.durationSec), step.startWatts)
    }

    private fun summaryWithZone(step: PlanStep, ftp: Int?): String {
        val zone = Zones.zoneOf(step.avgWatts, ftp) ?: return summary(step)
        return "${summary(step)} · Z$zone"
    }

    private fun applyTextSizes(v: RemoteViews) {
        // Must match the sp sizes in field_interval.xml (the reference size).
        listOf(
            R.id.hr to 18f, R.id.interval_label to 13f, R.id.time_left to 30f,
            R.id.lap_avg to 22f, R.id.target to 22f, R.id.target_next to 16f,
            R.id.next_label to 11f, R.id.next_value to 17f, R.id.countdown to 31f, R.id.countdown_value to 20f,
        ).forEach { (id, sp) ->
            v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * density * scale)
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REF_W_DP = 255f
        private const val REF_H_DP = 385f
        private const val CHART_DP = 56f


    }
}
