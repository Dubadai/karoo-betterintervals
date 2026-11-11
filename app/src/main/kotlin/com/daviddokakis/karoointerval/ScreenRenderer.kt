package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.roundToInt

/** Turns a [ScreenState] into the RemoteViews shown in the Karoo data field. */
class ScreenRenderer(private val context: Context, private val config: ViewConfig) {

    /**
     * Where colours come from: [context] (Auto: the Karoo's own day/night mode), or a context forced
     * to Dark or Light (Settings → Appearance). The chart and gauge are drawn from it too.
     */
    private var res: Context = context
    private var resFor: Appearance = Appearance.AUTO
    private var chart = ChartRenderer(context)
    private var gauge = GaugeRenderer(context)

    private fun useAppearance(a: Appearance) {
        if (a == resFor) return
        resFor = a
        res = a.apply(context)
        chart = ChartRenderer(res)
        gauge = GaugeRenderer(res)
    }

    /**
     * Dark / Light forced: the Karoo resolves the layout's XML colours in its own mode, so paint the
     * field's background and re-apply every XML colour (ThemeOverrides, generated from the layouts)
     * from [res]. Auto: nothing to do.
     */
    private fun applyAppearance(v: RemoteViews, layout: Int) {
        if (resFor == Appearance.AUTO || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        v.setInt(R.id.field_root, "setBackgroundColor", color(R.color.bg))
        for (op in ThemeOverrides.forLayout(layout)) {
            val c = color(op.color)
            when (op.kind) {
                ThemeOverrides.Kind.TEXT -> v.setTextColor(op.id, c)
                ThemeOverrides.Kind.TINT -> v.setInt(op.id, "setColorFilter", c)
                ThemeOverrides.Kind.BG -> v.setColorStateList(op.id, "setBackgroundTintList", opaque(c))
                ThemeOverrides.Kind.BG_COLOR -> v.setInt(op.id, "setBackgroundColor", c)
                ThemeOverrides.Kind.PROGRESS -> {
                    v.setColorStateList(op.id, "setProgressTintList", opaque(c))
                    if (op.color2 != 0) v.setColorStateList(op.id, "setProgressBackgroundTintList", opaque(color(op.color2)))
                }
            }
        }
    }

    /**
     * A background swapped in while rendering. With Dark / Light forced it's tinted with its colour
     * in that mode (the drawable's own alpha, e.g. a tonal wash or a fade, is kept).
     */
    private fun setBg(v: RemoteViews, id: Int, drawable: Int) {
        v.setInt(id, "setBackgroundResource", drawable)
        if (resFor == Appearance.AUTO || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        ThemeOverrides.drawableColor[drawable]?.let { v.setColorStateList(id, "setBackgroundTintList", opaque(color(it))) }
    }

    /**
     * Data field boundaries on (Karoo Settings): the Karoo puts every field in a white (dark mode:
     * black) card with a thin border on a grey page. The field stays see-through, so it sits on
     * that card like the Karoo's own fields; tonal tiles take the page grey to tie in with it.
     */
    private val boundaries = config.boundariesEnabled

    /**
     * The Lap avg / Target / HR tiles, per the Tiles setting: Outline (a border like Next up),
     * or Tonal ([tonal], the layout's own fill; the Karoo's page grey with boundaries on).
     */
    private fun tileDrawable(outline: Boolean, tonal: Int): Int = when {
        outline -> R.drawable.bg_outline
        boundaries -> R.drawable.bg_tile_boundary
        else -> tonal
    }

    private fun applyTiles(v: RemoteViews, tiles: List<Int>, outline: Boolean, tonal: Int) {
        tiles.forEach { setBg(v, it, tileDrawable(outline, tonal)) }
    }

    /** The heart icon swapped for the cadence icon, in the text colour (green reads as "on target"). */
    private fun showCadenceIcon(v: RemoteViews, id: Int) {
        v.setImageViewResource(id, R.drawable.ic_cadence)
        v.setInt(id, "setColorFilter", color(R.color.text))
        v.setContentDescription(id, context.getString(R.string.cadence))
    }

    /** "CADENCE" / "LAP CAD", in capitals like the Karoo's labels. */
    private fun cadenceLabel(lap: Boolean): String =
        context.getString(if (lap) R.string.lap_cadence else R.string.cadence).uppercase()

    /** A tint that replaces a drawable's colour but keeps its alpha. */
    private fun opaque(c: Int) = android.content.res.ColorStateList.valueOf(c or 0xFF000000.toInt())
    private val density = context.resources.displayMetrics.density

    /** The layout is designed for a Karoo 3 full page (~255x385 dp); scale to the real field size. */
    private val scale: Float = run {
        val (w, h) = config.viewSize
        if (w <= 0 || h <= 0) 1f else minOf(w / (REF_W_DP * density), h / (REF_H_DP * density)).coerceIn(0.5f, 1.6f)
    }
    private var blinkOn = true

    /** The done screen's recording dot: lit on the first draw, then on / off on every redraw. */
    private var recDotOn = false
    private val chartHeightPx = (CHART_DP * density * scale).toInt()

    private val nextIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_NEXT, 1) }
    private val pauseIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_PAUSE, 2) }
    private val previousIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_PREVIOUS, 3) }
    private val chartIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_CHART, 5) }
    private val jumpNowIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_JUMP_NOW, 6) }
    private val cancelSelectionIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_CANCEL_SELECTION, 8) }
    private val confirmIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_CONFIRM_SELECTION, 9) }
    private val cancelMoveIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_CANCEL_MOVE, 10) }
    private val dismissCancelledIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_DISMISS_MOVE_CANCELLED, 11) }
    private val targetToggleIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_TARGET, 12) }
    private val lapHrToggleIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_LAP_HR, 14) }
    private val lapNpToggleIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_LAP_NP, 13) }
    private val freePowerCycleIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_CYCLE_FREE_POWER, 17) }
    private val wattsColorIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_WATTS_COLOR, 7) }
    private val startIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_START, 15) }
    private val startOverIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_START_OVER, 27) }
    private val doneIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_DONE, 16) }
    private val ergToggleIntent: PendingIntent by lazy { actionIntent(ActionReceiver.ACTION_TOGGLE_ERG, 28) }
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

    fun render(s: ScreenState): RemoteViews {
        useAppearance(s.appearance)
        val plan = s.plan ?: return renderFree(s)
        if (s.notStarted) return renderReady(s, plan)
        if (s.finished) return renderDone(s, plan)
        // The Gauge watts mode has its own layout; the other four share the number one. Both in the
        // Karoo's own style (fonts, labels, icons, yellow buttons).
        // ERG drives the trainer: no gauge, the plain big number with the ERG pill above it (the
        // watts follow the target by themselves; the gauge is for riding free).
        val gaugeMode = s.wattsMode == WattsMode.GAUGE && !s.ergDrives
        val layout = if (gaugeMode) R.layout.field_interval_gauge_karoo else R.layout.field_interval_karoo
        val v = RemoteViews(context.packageName, layout)
        applyAppearance(v, layout)
        applyTiles(v, listOf(R.id.lap_tile, R.id.tile2), s.tileOutline, R.drawable.bg_tile_tonal)
        // A heart-rate step: heart rate is the big number (red gauge band), the target is in bpm
        // (or % of LTHR), the lap tile shows the lap's average heart rate, and power takes heart
        // rate's place wherever heart rate would be. Design: docs/design/hr-targets/.
        val hrStep = s.hrStep
        // Settings › Top row: one or two values at the top. The second tile (next to Lap avg)
        // shows the Target, or, when the Target is at the top, what's left of heart rate / cadence.
        val left = s.topRow ?: if (s.showsCadence) TopRow.CADENCE else TopRow.HEART_RATE
        val tops = listOfNotNull(left, s.topRowRight?.takeIf { it != left })
        val tileItem = if (TopRow.TARGET !in tops) {
            TopRow.TARGET
        } else {
            (if (s.showsCadence) listOf(TopRow.CADENCE, TopRow.HEART_RATE) else listOf(TopRow.HEART_RATE, TopRow.CADENCE)).first { it !in tops }
        }
        lapText = (if (hrStep) s.lapHrAvg else if (s.lapShowsNp) s.lapNp else s.lapAvg)?.toString() ?: "--"
        val tileItemValue = itemValue(s, tileItem, inTile = true, gaugeMode = gaugeMode)
        // The cell values' texts, so a Karoo-style cell can shrink a value that's too wide.
        targetText = tileItemValue
        applyTextSizes(v, gaugeMode)

        v.setViewVisibility(R.id.content, View.VISIBLE)
        v.setViewVisibility(R.id.btn_settings, View.VISIBLE)
        v.setViewVisibility(R.id.btn_pause, View.VISIBLE)
        v.setOnClickPendingIntent(R.id.btn_settings, settingsIntent)
        v.setOnClickPendingIntent(R.id.btn_settings_paused, settingsIntent)

        // The big number (3 s power, or heart rate on a heart-rate step). Tap to cycle how the
        // target range shows: plain, coloured number, coloured background behind the top of the
        // screen, both, or the gauge.
        val range = rangeState(s)
        val wattsColor = if (s.wattsMode.colorsText && !s.ergDrives && range != null) {
            when (range) {
                RangeState.UNDER -> R.color.watts_under
                RangeState.IN -> R.color.watts_in_range
                RangeState.OVER -> R.color.watts_over
            }
        } else {
            R.color.text
        }
        if (gaugeMode) {
            val gaugeW = (config.viewSize.first - 16 * density).toInt()
            if (gaugeW > 0) {
                // Green is power's colour: a heart-rate step's band is the heart red.
                val band = color(if (hrStep) R.color.heart_icon else R.color.karoo_green)
                v.setImageViewBitmap(
                    R.id.gauge,
                    gauge.render(gaugeW, gaugeHeightPx(s.navigating), s.mainValue, s.target, s.targetRange, color(wattsColor), band, empty = mainSensor(s).emptyText),
                )
            }
        } else {
            v.setTextViewText(R.id.watts, s.mainValue?.toString() ?: "--")
            v.setTextColor(R.id.watts, color(wattsColor))
            bigEmpty(v, R.id.watts, R.id.watts_empty, mainSensor(s), config.viewSize.first.toFloat(), 0f + config.viewSize.second)
        }
        val upperBg = if (s.wattsMode.colorsBackground && !s.ergDrives && range != null) {
            when (range) {
                RangeState.UNDER -> R.drawable.bg_range_under
                RangeState.IN -> R.drawable.bg_range_in
                RangeState.OVER -> R.drawable.bg_range_over
            }
        } else {
            0 // no background
        }
        // The range tint is the background of the upper part: the bottom layer, behind the watts,
        // tiles and everything else there, in every theme.
        if (upperBg != 0) setBg(v, R.id.upper, upperBg) else v.setInt(R.id.upper, "setBackgroundResource", 0)
        val onTint = upperBg != 0
        // Labels in the text colour, like Karoo's own fields.
        val labelColor = color(R.color.text)
        v.setTextColor(R.id.lap_avg_label, labelColor)
        v.setTextColor(R.id.tile2_label, labelColor)
        // Green icons turn black (the text colour) on the range tint, so they show.
        val iconColor = color(if (onTint) R.color.text else R.color.karoo_green)
        listOf(R.id.icon_timer, R.id.icon_lap, R.id.icon_bolt).forEach { v.setInt(it, "setColorFilter", iconColor) }
        // With ERG the watts' look is fixed (plain): a tap there changes nothing.
        if (!s.ergDrives) v.setOnClickPendingIntent(if (gaugeMode) R.id.gauge else R.id.watts, wattsColorIntent)

        // Top row: one or two values, each with its icon, drawn like BigWatts' heart-rate line
        listOf(R.id.top1_icon, R.id.top1, R.id.top2_icon, R.id.top2, R.id.target_next_top).forEach { v.setViewVisibility(it, View.GONE) }
        val topItems = listOfNotNull(ergItem(s)) + tops.map {
            TopRowArt.Item(topIcon(s, it, onTint), itemValue(s, it, inTile = false, gaugeMode = gaugeMode), heart = it == TopRow.HEART_RATE && !s.hrStep)
        }
        // Tap the top row with ERG on to switch it off (or back on) for this ride.
        if (s.erg != null) v.setOnClickPendingIntent(R.id.top_block, ergToggleIntent)
        val topLanding = s.landStep?.takeIf { tops.first() == TopRow.TARGET && s.erg == null }?.targetAt(0)?.toString()
        v.setImageViewBitmap(R.id.top_img, TopRowArt.draw(res, topItems, TOP_ROW_PX * density / 1.875f * scale, (40 * density).roundToInt(), color(R.color.text), topLanding))
        v.setContentDescription(R.id.top_img, topItems.joinToString(", ") { it.text })

        showItemIcon(v, R.id.tile2_icon, s, tileItem, top = false, onTint = onTint)
        // A heart-rate target: the heart next to the target icon
        val hrTarget = hrStep && tileItem == TopRow.TARGET
        v.setViewVisibility(R.id.tile2_icon2, if (hrTarget) View.VISIBLE else View.GONE)
        if (hrTarget) {
            v.setImageViewResource(R.id.tile2_icon2, R.drawable.ic_heart)
            v.setInt(R.id.tile2_icon2, "setColorFilter", color(R.color.heart_icon))
        }
        v.setTextViewText(R.id.tile2_label, itemLabel(s, tileItem))
        v.setTextViewText(R.id.tile2_value, tileItemValue)
        // Tap the Target tile: the target or its range (power), bpm or % of LTHR (heart rate).
        v.setOnClickPendingIntent(R.id.tile2, if (tileItem == TopRow.TARGET && (hrStep || !gaugeMode)) targetToggleIntent else null)

        // Lap tile: the lap's average power (tap: NP), or its average heart rate on a heart-rate step.
        if (hrStep) {
            v.setImageViewResource(R.id.icon_bolt, R.drawable.ic_heart)
            v.setInt(R.id.icon_bolt, "setColorFilter", color(R.color.heart_icon))
            v.setTextViewText(R.id.lap_avg_label, context.getString(R.string.lap_avg))
            v.setTextViewText(R.id.lap_avg, lapText)
        } else {
            v.setTextViewText(R.id.lap_avg_label, context.getString(if (s.lapShowsNp) R.string.np else R.string.lap_avg))
            v.setTextViewText(R.id.lap_avg, lapText)
        }
        v.setOnClickPendingIntent(R.id.lap_tile, lapNpToggleIntent)
        // Both tiles drawn 1:1 like the Karoo's own fields (75dp: a native 140 px tile)
        val ink = color(R.color.text)
        tileArt(
            v, R.id.lap_tile_img, listOf(R.id.icon_lap, R.id.icon_bolt, R.id.lap_avg_label, R.id.lap_avg), halfTileW, 75 * density,
            listOf(TileArt.Glyph(KarooType.Glyph.LAP, ink), if (hrStep) TileArt.Glyph.RED_HEART else TileArt.Glyph(KarooType.Glyph.BOLT, ink)),
            context.getString(if (!hrStep && s.lapShowsNp) R.string.np else R.string.lap_avg), lapText,
            empty = (if (hrStep) s.hrSensor else s.powerSensor).emptyText,
        )
        tileArt(
            v, R.id.tile2_img, listOf(R.id.tile2_icon, R.id.tile2_icon2, R.id.tile2_label, R.id.tile2_value), halfTileW, 75 * density,
            listOfNotNull(itemIcon(s, tileItem), if (hrTarget) TileArt.Glyph.RED_HEART else null),
            itemLabel(s, tileItem), tileItemValue, color(R.color.text),
            // A jump being chosen or counting down: "→ 250" after the target, in the tile itself
            landing = s.landStep?.takeIf { tileItem == TopRow.TARGET && tops.first() != TopRow.TARGET }?.targetAt(0)?.toString(),
            empty = itemSensor(s, tileItem)?.emptyText,
        )

        // Pause / resume (top right). Paused by you: green half-pill with a filled play symbol.
        // Ride paused by Karoo: hidden, since the workout resumes with the ride.
        val ridePaused = s.paused
        val barOnTop = s.karooBarOnTop
        val resumeShown = s.workoutPaused && !ridePaused
        // Small grey pause button normally; the big green resume half-pill while you've paused.
        v.setViewVisibility(
            R.id.btn_pause,
            when {
                s.indoor -> View.GONE   // indoors the Karoo's start / pause button pauses the workout
                resumeShown -> View.GONE
                barOnTop -> View.INVISIBLE
                else -> View.VISIBLE
            },
        )
        v.setViewVisibility(R.id.btn_resume, if (resumeShown) View.VISIBLE else View.GONE)
        // The Karoo's PAUSE strip covers the top of the screen: the gear comes down to the interval
        // row, next to the paused box. Its "Searching GPS…" strip: the gear just moves down below it.
        // Pausing just the workout while riding leaves it at the top.
        // Design: docs/design/hr-targets/ (Ride paused), docs/design/gps-strip/.
        v.setViewVisibility(R.id.btn_settings_paused, if (ridePaused) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.btn_settings, if (ridePaused) View.INVISIBLE else View.VISIBLE)
        gearBelowKarooBar(v, R.id.btn_settings, s)
        // Tonal: the text colour at 55%, so a range tint shows through (a translucent tint colour
        // alone comes out solid on the Karoo).
        v.setInt(R.id.btn_pause_icon, "setColorFilter", color(R.color.text))
        v.setInt(R.id.btn_pause_icon, "setImageAlpha", ICON_ALPHA)
        v.setOnClickPendingIntent(R.id.btn_pause, pauseIntent)
        v.setOnClickPendingIntent(R.id.btn_resume, pauseIntent)

        // Interval number, or a yellow box while paused, or "Workout complete"
        if (ridePaused || s.workoutPaused) {
            v.setViewVisibility(R.id.interval_label, View.GONE)
            v.setViewVisibility(R.id.paused_box, View.VISIBLE)
            v.setViewVisibility(R.id.icon_timer, View.GONE)
            v.setViewVisibility(R.id.paused_spacer, View.VISIBLE)
            // Ride paused: short ("Auto-paused" / "Paused"), so the gear and the clock fit beside it.
            v.setTextViewText(
                R.id.paused_text,
                context.getString(
                    when {
                        ridePaused && s.autoPaused -> R.string.ride_auto_paused_short
                        ridePaused -> R.string.ride_paused_short
                        else -> R.string.workout_paused
                    },
                ),
            )
            // Paused by you with ERG on: the box says what the trainer holds meanwhile.
            if (!ridePaused && s.erg == ErgStatus.HOLDING && s.ergWatts != null) {
                v.setTextViewText(R.id.paused_text, context.getString(R.string.erg_paused, s.ergWatts))
            }
            // Blink the pause icon: flip on every redraw (Karoo redraws a field about once a second).
            blinkOn = !blinkOn
            v.setInt(R.id.paused_icon, "setImageAlpha", if (blinkOn) 255 else 70)
            // The resume button pulses in step, between its two greens.
            if (resumeShown) {
                setBg(v, R.id.btn_resume, if (blinkOn) R.drawable.bg_resume_karoo else R.drawable.bg_resume_karoo_alt)
            }
        } else {
            v.setViewVisibility(R.id.interval_label, View.VISIBLE)
            v.setViewVisibility(R.id.paused_box, View.GONE)
            v.setViewVisibility(R.id.icon_timer, View.GONE)   // no timer icon before INTERVAL
            v.setViewVisibility(R.id.paused_spacer, View.GONE)
            if (s.finished) {
                v.setTextViewText(R.id.interval_label, context.getString(R.string.workout_complete))
                v.setTextColor(R.id.interval_label, color(R.color.watts_in_range))
            } else {
                v.setTextViewText(R.id.interval_label, relLine(context.getString(R.string.interval_of, s.stepIndex + 1, s.stepCount).uppercase()))
                v.setTextColor(R.id.interval_label, color(R.color.text))
            }
        }
        // Time left in the interval, with a minus so it reads as counting down.
        v.setTextViewText(R.id.time_left, s.intervalRemainingSec?.let { "-" + formatClock(it) } ?: "--:--")
        val total = s.intervalTotalSec ?: 0
        val progress = if (total > 0) (s.elapsedInStepSec * 1000 / total).coerceIn(0, 1000) else 0
        v.setProgressBar(R.id.interval_progress, 1000, progress, false)

        // Where a jump would land: drawn into the top row's image or the Target tile's (TopRowArt, TileArt)
        if (!gaugeMode) v.setViewVisibility(R.id.target_next, View.GONE)

        // Next up box / countdown box
        val jump = s.jumpStep
        if (s.countdown != null && (jump != null || s.finishing)) {
            v.setViewVisibility(R.id.next_box, View.GONE)
            v.setViewVisibility(R.id.countdown_box, View.VISIBLE)
            v.setViewVisibility(R.id.tiles_row, View.VISIBLE)
            v.setViewVisibility(R.id.confirm_row, View.GONE)
            v.setViewVisibility(R.id.move_cancelled, View.GONE)
            v.setTextViewText(R.id.countdown, s.countdown.toString())
            v.setViewVisibility(R.id.countdown_label, View.GONE)   // no "JUMPING TO": the number and the step say it
            v.setTextViewText(R.id.countdown_value, if (jump != null) relLine(summary(jump, s)) else plexWord(context.getString(R.string.finish)))
            // The box fills with its darker colour as the seconds run down.
            val total = WorkoutSession.COUNTDOWN_SECONDS
            v.setProgressBar(R.id.countdown_fill, 1000, ((total - s.countdown + 1) * 1000 / total).coerceIn(0, 1000), false)
        } else {
            v.setViewVisibility(R.id.next_box, View.VISIBLE)
            v.setViewVisibility(R.id.countdown_box, View.GONE)
            val choosing = s.selectTarget?.let { s.plan?.steps?.getOrNull(it) }
            val next = s.nextStep
            when {
                choosing != null -> {
                    // How far the jump goes, counted in intervals from where you are.
                    val distance = (s.selectTarget ?: s.stepIndex) - s.stepIndex
                    val label = when {
                        s.selectRestart -> context.getString(R.string.restarting_interval)
                        distance > 0 -> context.resources.getQuantityString(R.plurals.jumping_ahead, distance, distance)
                        else -> context.resources.getQuantityString(R.plurals.jumping_back, -distance, -distance)
                    }
                    v.setTextViewText(R.id.next_label, label)
                    v.setViewVisibility(R.id.next_label, View.VISIBLE)
                    v.setTextViewText(R.id.next_value, relLine(summary(choosing, s)))
                    v.setTextColor(R.id.next_value, color(R.color.text))
                }
                next != null -> {
                    v.setTextViewText(R.id.next_label, context.getString(R.string.next_up))
                    v.setViewVisibility(R.id.next_label, View.VISIBLE)
                    v.setTextViewText(R.id.next_value, relLine(summary(next, s)))
                    v.setTextColor(R.id.next_value, color(R.color.text))
                }
                else -> {
                    v.setViewVisibility(R.id.next_label, View.GONE)
                    v.setTextViewText(R.id.next_value, context.getString(R.string.next_last))
                    v.setTextColor(R.id.next_value, color(R.color.text_secondary))
                }
            }
            v.setViewVisibility(R.id.select_fill, View.GONE)
            // One 52 dp row: "Confirm move" + ✕ while choosing, "Move cancelled" for a moment after
            // its ✕, otherwise the Lap avg / Target tiles.
            val showConfirm = choosing != null
            val showCancelled = !showConfirm && s.moveCancelled
            v.setViewVisibility(R.id.confirm_row, if (showConfirm) View.VISIBLE else View.GONE)
            // Chosen with a remote button: the button fills and confirms itself.
            val left = s.autoConfirmLeft
            if (showConfirm && left != null) {
                val total = WorkoutSession.AUTO_CONFIRM_SECONDS
                v.setViewVisibility(R.id.confirm_fill, View.VISIBLE)
                v.setProgressBar(R.id.confirm_fill, 1000, (total - left + 1) * 1000 / total, false)
                v.setTextViewText(R.id.confirm_text, context.getString(R.string.confirming, left))
            } else {
                v.setViewVisibility(R.id.confirm_fill, View.GONE)
                v.setTextViewText(R.id.confirm_text, context.getString(R.string.confirm_move))
            }
            v.setViewVisibility(R.id.move_cancelled, if (showCancelled) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.tiles_row, if (showConfirm || showCancelled) View.GONE else View.VISIBLE)
            // Buttons keep their space when hidden, so the text stays centred. While choosing,
            // both stay available so you can tap back and forth.
            v.setViewVisibility(R.id.btn_skip, if (s.hasNext || choosing != null) View.VISIBLE else View.INVISIBLE)
            v.setViewVisibility(R.id.btn_previous, View.VISIBLE)
        }
        // The skip button is grey and not tappable while the ride or workout is paused, so the play
        // button (Karoo's, or our resume button) is the one to press. Inverted normally (black in
        // light mode, white in dark), so Karoo's yellow stays for its own navigation (route pill,
        // page arrows).
        val skipEnabled = !(ridePaused || s.workoutPaused)
        val skipBg = if (skipEnabled) R.drawable.bg_btn_skip_inverted else R.drawable.bg_btn_skip_disabled
        val skipFg = if (skipEnabled) R.color.bg else R.color.skip_disabled_fg
        setBg(v, R.id.skip_bg, skipBg)
        v.setInt(R.id.skip_icon, "setColorFilter", color(skipFg))
        // Taps may or may not reach a data field on Karoo; the bonus actions (remote buttons) always work.
        v.setOnClickPendingIntent(R.id.btn_skip, if (skipEnabled) nextIntent else null)
        v.setOnClickPendingIntent(R.id.btn_previous, previousIntent)
        v.setOnClickPendingIntent(R.id.next_mid, cancelSelectionIntent)
        v.setOnClickPendingIntent(R.id.countdown_box, jumpNowIntent)
        v.setOnClickPendingIntent(R.id.btn_confirm, confirmIntent)
        v.setOnClickPendingIntent(R.id.btn_confirm_x, cancelMoveIntent)
        v.setOnClickPendingIntent(R.id.move_cancelled, dismissCancelledIntent)

        val chartW = config.viewSize.first   // edge to edge, bottom corners rounded like the Karoo's card
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setViewLayoutHeight(R.id.chart, chartHeightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
        if (chartW > 0) v.setImageViewBitmap(R.id.chart, roundBottomCorners(chart.render(s, chartW, chartHeightPx, scale)))
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
        val p = s.mainValue ?: return null
        val range = s.targetRange ?: return null
        return when {
            p < range.first -> RangeState.UNDER
            p > range.last -> RangeState.OVER
            else -> RangeState.IN
        }
    }

    /**
     * Workout done: a big green check, the workout's own numbers (time, average power, NP, average
     * heart rate or cadence), DONE to take the workout off, and a note that the ride keeps
     * recording. No heart rate row. Design: docs/design/start-workout/ (done screen).
     */
    private fun renderDone(s: ScreenState, plan: WorkoutPlan): RemoteViews {
        val layout = R.layout.field_done_karoo
        val v = RemoteViews(context.packageName, layout)
        applyAppearance(v, layout)
        val sum = s.summary
        v.setTextViewText(R.id.done_name, plan.name)
        v.setTextViewText(R.id.done_value_1, sum?.timeSec?.takeIf { it > 0 }?.let(::formatClock) ?: formatClock(plan.totalSec))
        v.setTextViewText(R.id.done_value_2, sum?.avgPower?.toString() ?: "--")
        v.setTextViewText(R.id.done_value_3, sum?.np?.toString() ?: "--")
        if (s.showsCadence) {
            v.setTextViewText(R.id.done_label_4, context.getString(R.string.avg_cadence))
            v.setTextViewText(R.id.done_value_4, sum?.avgCadence?.toString() ?: "--")
            showCadenceIcon(v, R.id.done_icon_4)
        } else {
            v.setTextViewText(R.id.done_value_4, sum?.avgHr?.toString() ?: "--")
            // The heart in this mode's red (Dark / Light forced too), like the workout screen's
            v.setInt(R.id.done_icon_4, "setColorFilter", color(R.color.heart_icon))
        }
        applyTiles(v, listOf(R.id.done_tile_1, R.id.done_tile_2, R.id.done_tile_3, R.id.done_tile_4), s.tileOutline, R.drawable.bg_tile_tonal)
        // Each number fills its tile, like the Karoo's fields (tile padding 8dp each side, 5dp bottom)
        val doneW = config.viewSize.first / 2f - 24 * density
        val doneTexts = listOf(
            sum?.timeSec?.takeIf { it > 0 }?.let(::formatClock) ?: formatClock(plan.totalSec), sum?.avgPower?.toString() ?: "--",
            sum?.np?.toString() ?: "--", (if (s.showsCadence) sum?.avgCadence else sum?.avgHr)?.toString() ?: "--",
        )
        // Drawn 1:1 like the Karoo's own fields (TileArt)
        val ink = color(R.color.text)
        val doneTileW = (config.viewSize.first - 22 * density) / 2f
        val doneLabels = listOf(R.string.time, R.string.avg_power, R.string.done_np, if (s.showsCadence) R.string.avg_cadence else R.string.avg_hr)
        val doneIcons = listOf(
            TileArt.Glyph(KarooType.Glyph.TIMER, ink), TileArt.Glyph(KarooType.Glyph.BOLT, ink), TileArt.Glyph(KarooType.Glyph.BOLT, ink),
            if (s.showsCadence) TileArt.Glyph(KarooType.Glyph.CADENCE, color(R.color.text)) else TileArt.Glyph(KarooType.Glyph.HEART, color(R.color.text)),
        )
        listOf(R.id.done_tile_1_img, R.id.done_tile_2_img, R.id.done_tile_3_img, R.id.done_tile_4_img).forEachIndexed { i, img ->
            val ids = listOf(
                listOf(R.id.done_icon_1, R.id.done_label_1, R.id.done_value_1), listOf(R.id.done_icon_2, R.id.done_label_2, R.id.done_value_2),
                listOf(R.id.done_icon_3, R.id.done_label_3, R.id.done_value_3), listOf(R.id.done_icon_4, R.id.done_label_4, R.id.done_value_4),
            )[i]
            tileArt(v, img, ids, doneTileW, 54 * density, listOf(doneIcons[i]), context.getString(doneLabels[i]), doneTexts[i])
        }
        // "Ride is still recording": the red dot blinks, flipping on every redraw (about once a second)
        recDotOn = !recDotOn
        v.setInt(R.id.rec_dot, "setImageAlpha", if (recDotOn) 255 else 45)
        v.setInt(R.id.rec_dot, "setColorFilter", color(R.color.recording))
        v.setOnClickPendingIntent(R.id.btn_done, doneIntent)
        v.setOnClickPendingIntent(R.id.btn_settings, settingsIntent)
        gearBelowKarooBar(v, R.id.btn_settings, s)
        return v
    }

    /**
     * Ready (workout loaded, not started): live watts and heart rate, a big START WORKOUT in the
     * middle, the workout's name and length, and "First up" with ⏮ / ⏭ to pick the step to start
     * from; the chart outlines that step. Nothing runs until START (or the remote's pause button).
     * Design: docs/design/start-workout/ (B).
     */
    private fun renderReady(s: ScreenState, plan: WorkoutPlan): RemoteViews {
        val layout = R.layout.field_ready_karoo
        val v = RemoteViews(context.packageName, layout)
        applyAppearance(v, layout)

        v.setTextViewText(R.id.watts, s.powerShown?.toString() ?: "--")
        bigEmpty(v, R.id.watts, R.id.watts_empty, s.powerSensor, config.viewSize.first.toFloat(), 0f + config.viewSize.second)
        // Outdoor profile with a trainer, before the ride: the orange hint (docs/design/indoor-erg/trainer-hint)
        val trainerHint = !s.indoor && s.trainerOutdoor && s.rideIdle && s.countdown == null && s.resumeFromSec == null
        // Every start screen (indoor and outdoor): the watts sit in the top row until the countdown.
        val wattsUp = s.countdown == null && s.resumeFromSec == null
        if (wattsUp) {
            v.setViewVisibility(R.id.watts, View.INVISIBLE)
            v.setViewVisibility(R.id.watts_empty, View.GONE)
        }
        // The top row as on the workout screen (Settings › Top row), drawn like BigWatts' heart-rate line
        val left = s.topRow ?: if (s.showsCadence) TopRow.CADENCE else TopRow.HEART_RATE
        val tops = listOfNotNull(left, s.topRowRight?.takeIf { it != left })
        v.setViewVisibility(R.id.hr_icon, View.GONE)
        v.setViewVisibility(R.id.hr, View.GONE)
        // Indoors before the ride the watts join the top row (bolt and number, like ERG and the heart
        // rate) and the yellow box takes the big number's room (docs/design/indoor-erg/indoor-start).
        // The trainer hint does the same: the watts go up, the box and the hint get the room.
        val wattsItem = if (wattsUp) TopRowArt.Item(TileArt.Icon(R.drawable.ic_bolt_filled, color(R.color.karoo_success_green)), s.powerShown?.toString() ?: "--") else null
        // ERG first, as on the workout screen: "● ERG READY" once the trainer is connected.
        val topItems = listOfNotNull(ergItem(s), wattsItem) + tops.map {
            TopRowArt.Item(topIcon(s, it, onTint = false), itemValue(s, it, inTile = false, gaugeMode = true), heart = it == TopRow.HEART_RATE && !s.hrStep)
        }
        // With the watts in it (indoor start) the row must fit between the gear and the edge: it may
        // shrink, never below the smallest text (24 px on the Karoo), else ERG READY becomes ERG.
        val rowH = (40 * density).roundToInt()
        val k = density / 1.875f
        val avail = config.viewSize.first - 2 * 62 * k
        var rowSize = TOP_ROW_PX * k * scale
        var row = TopRowArt.draw(res, topItems, rowSize, rowH, color(R.color.text), iconGap = if (wattsItem != null) INDOOR_ICON_GAP else null)
        if (wattsItem != null) {
            while (row.width > avail && rowSize > 24 * k) { rowSize -= k; row = TopRowArt.draw(res, topItems, rowSize, rowH, color(R.color.text), iconGap = if (wattsItem != null) INDOOR_ICON_GAP else null) }
            if (row.width > avail) {
                val shortItems = topItems.map { if (it.text == "ERG READY") TopRowArt.Item(it.icon, "ERG", it.heart) else it }
                rowSize = TOP_ROW_PX * k * scale
                row = TopRowArt.draw(res, shortItems, rowSize, rowH, color(R.color.text), iconGap = INDOOR_ICON_GAP)
                while (row.width > avail && rowSize > 24 * k) { rowSize -= k; row = TopRowArt.draw(res, shortItems, rowSize, rowH, color(R.color.text), iconGap = INDOOR_ICON_GAP) }
            }
        }
        v.setImageViewBitmap(R.id.top_img, row)
        v.setContentDescription(R.id.top_img, topItems.joinToString(", ") { it.text })
        v.setViewVisibility(R.id.btn_pause, View.GONE)     // nothing runs yet
        v.setViewVisibility(R.id.btn_resume, View.GONE)
        v.setOnClickPendingIntent(R.id.btn_settings, settingsIntent)
        v.setOnClickPendingIntent(R.id.btn_start, startIntent)
        gearBelowKarooBar(v, R.id.btn_settings, s)

        // Indoors, until the workout starts: INDOOR SESSION with the trainer icon above the name
        v.setViewVisibility(R.id.indoor_label, if (s.indoor) View.VISIBLE else View.GONE)
        v.setInt(R.id.indoor_icon, "setColorFilter", color(R.color.settings_help))
        v.setViewVisibility(R.id.trainer_hint, if (trainerHint) View.VISIBLE else View.GONE)
        if (trainerHint) {
            v.setInt(R.id.trainer_hint_icon, "setColorFilter", color(R.color.settings_warn))
            v.setTextColor(R.id.trainer_hint_text, color(R.color.settings_warn))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                v.setColorStateList(R.id.trainer_hint, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(color(R.color.settings_warn_bg)))
            }
        }
        v.setTextViewText(R.id.ready_name, plan.name)
        v.setTextViewText(R.id.ready_total, formatClock(plan.totalSec))

        // Start countdown (5 s): the button counts down too (the seconds above TAP TO CANCEL, filling
        // with its darker colour) and a tap on it cancels. The First up box becomes the usual jump
        // countdown, so it looks like any other interval change; a tap on it starts right away.
        val countdown = s.countdown
        val total = WorkoutSession.COUNTDOWN_SECONDS
        // Selected again mid-ride: CONTINUE (where it was) above a START OVER button
        val resume = s.resumeFromSec?.takeIf { countdown == null }
        v.setTextViewText(
            R.id.start_label,
            context.getString(when { countdown != null -> R.string.tap_to_cancel; resume != null -> R.string.continue_workout; else -> R.string.start_workout }),
        )
        v.setViewVisibility(R.id.start_sub, if (resume != null) View.VISIBLE else View.GONE)
        if (resume != null) {
            v.setTextViewText(R.id.start_sub, context.getString(R.string.continue_from, s.stepIndex + 1, plan.steps.size, formatClock(resume)).uppercase())
        }
        v.setViewVisibility(R.id.btn_start_over, if (s.resumeFromSec != null) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.btn_start_over, startOverIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Indoors before the ride the box is taller (its line at 24 px); the big watts give way.
            val indoorBox = s.indoor && s.rideIdle && countdown == null && s.resumeFromSec == null
            v.setViewLayoutHeight(R.id.btn_start, when { s.resumeFromSec != null -> 60f; indoorBox -> indoorBoxDp(); trainerHint -> indoorBoxDp() - HINT_BOX_DP; countdown == null -> indoorBoxDp() + if (s.indoor) 0f else LABEL_DP; else -> 96f }, TypedValue.COMPLEX_UNIT_DIP)
        }
        // Indoors before the ride: the Karoo's own start button starts it all, so the box shows it
        // (a real Karoo, its start button flashing). A tap on the box still starts the ride.
        val indoorStart = s.indoor && s.rideIdle && countdown == null && resume == null
        v.setViewVisibility(R.id.start_content, if (indoorStart) View.INVISIBLE else View.VISIBLE)
        v.setViewVisibility(R.id.start_indoor, if (indoorStart) View.VISIBLE else View.GONE)
        if (indoorStart) {
            val w = (config.viewSize.first - 16 * density).roundToInt().coerceAtLeast(1)
            val h = (indoorBoxDp() * density).roundToInt()
            v.setImageViewBitmap(R.id.start_indoor, IndoorStartArt.draw(context, w, h, 12 * density, density / 1.875f, s.startFlash))
            v.setContentDescription(R.id.btn_start, context.getString(R.string.start_indoor_title).replace('\n', ' '))
        } else {
            v.setContentDescription(R.id.btn_start, context.getString(R.string.start_workout))
        }
        v.setViewVisibility(R.id.start_icon, if (countdown != null || resume != null) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.start_count, if (countdown != null) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.start_count, countdown?.toString() ?: "")
        v.setProgressBar(R.id.start_fill, 1000, countdown?.let { ((total - it + 1) * 1000 / total).coerceIn(0, 1000) } ?: 0, false)
        v.setOnClickPendingIntent(R.id.countdown_box, jumpNowIntent)
        if (countdown != null) {
            v.setViewVisibility(R.id.next_box, View.GONE)
            v.setViewVisibility(R.id.countdown_box, View.VISIBLE)
            v.setTextViewText(R.id.countdown, countdown.toString())
            v.setViewVisibility(R.id.countdown_label, View.GONE)   // like a jump: the number and the step, no label
            v.setTextViewText(R.id.countdown_value, relLine(plan.steps.getOrNull(s.stepIndex)?.let { summary(it, s) } ?: ""))
            v.setProgressBar(R.id.countdown_fill, 1000, ((total - countdown + 1) * 1000 / total).coerceIn(0, 1000), false)
        } else {
            v.setViewVisibility(R.id.next_box, View.VISIBLE)
            v.setViewVisibility(R.id.countdown_box, View.GONE)
        }

        // First up: the step it will start from. ⏮ / ⏭ move it, wrapping round at the ends (⏮ on
        // the first step goes to the last); hidden when there's only one step.
        val index = s.stepIndex
        v.setViewVisibility(R.id.select_fill, View.GONE)
        v.setTextViewText(
            R.id.next_label,
            if (index == 0) context.getString(R.string.first_up) else context.getString(R.string.start_at, index + 1, s.stepCount),
        )
        v.setViewVisibility(R.id.next_label, View.VISIBLE)
        v.setTextViewText(R.id.next_value, relLine(plan.steps.getOrNull(index)?.let { summary(it, s) } ?: ""))
        v.setTextColor(R.id.next_value, color(R.color.text))
        val canMove = if (s.stepCount > 1) View.VISIBLE else View.INVISIBLE
        v.setViewVisibility(R.id.btn_previous, canMove)
        v.setViewVisibility(R.id.btn_skip, canMove)
        setBg(v, R.id.skip_bg, R.drawable.bg_btn_skip_inverted)
        v.setInt(R.id.skip_icon, "setColorFilter", color(R.color.bg))
        v.setOnClickPendingIntent(R.id.btn_skip, nextIntent)
        v.setOnClickPendingIntent(R.id.btn_previous, previousIntent)

        val chartW = config.viewSize.first   // edge to edge, bottom corners rounded like the Karoo's card
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setViewLayoutHeight(R.id.chart, chartHeightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        }
        if (chartW > 0) v.setImageViewBitmap(R.id.chart, roundBottomCorners(chart.render(s, chartW, chartHeightPx, scale)))
        v.setOnClickPendingIntent(R.id.chart, chartIntent)
        return v
    }

    /**
     * Free ride (no workout loaded): big 3 s watts, lap average and heart rate tiles (the heart
     * rate tile takes the colour of the current HR zone), then Karoo's lap number, lap time and
     * last lap. The gear opens the Workouts page to load a workout.
     */
    private fun renderFree(s: ScreenState): RemoteViews {
        // Watts first, a LAP TIME row, Lap avg and HR as full-size Karoo data fields and Last lap at
        // the bottom (field_free_karoo.xml).
        val layout = R.layout.field_free_karoo
        val v = RemoteViews(context.packageName, layout)
        applyAppearance(v, layout)
        val freeTonal = R.drawable.bg_tile_tonal
        applyTiles(v, listOf(R.id.free_lap_tile), s.tileOutline, freeTonal)
        listOf(
            R.id.free_lap_label to 15f, R.id.free_hr_label to 15f,
            R.id.free_lap_time to 28f,
        ).forEach { (id, sp) -> v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * density * scale) }

        val watts = s.powerShown?.toString() ?: "--"
        v.setTextViewText(R.id.free_watts, watts)
        bigEmpty(v, R.id.free_watts, R.id.free_watts_empty, s.powerSensor, config.viewSize.first.toFloat(), 0f + config.viewSize.second)
        v.setTextViewText(R.id.free_watts_label, context.getString(s.powerSmoothing.powerName))
        v.setTextViewTextSize(R.id.free_watts_label, TypedValue.COMPLEX_UNIT_PX, 15f * density * scale)
        v.setViewPadding(R.id.free_watts_label, 0, (24 * density).toInt(), 0, wattsLabelLift(watts, s.navigating))
        // Power tile: tap to go on: LAP AVG → LAP NP → RIDE AVG. The ride's average has the
        // Karoo's ride icon (the bolt without the lap arrow).
        v.setTextViewText(
            R.id.free_lap_label,
            context.getString(
                when (s.freePower) { FreePower.LAP_AVG -> R.string.lap_avg; FreePower.LAP_NP -> R.string.np; FreePower.RIDE_AVG -> R.string.ride_avg },
            ),
        )
        v.setTextViewText(R.id.free_lap_avg, s.shownFreePower?.toString() ?: "--")
        val freeW = (config.viewSize.first - 6 * density) / 2f - 2 * density - 7 * density
        fillValue(v, R.id.free_lap_avg, s.shownFreePower?.toString() ?: "--", freeW, 75 * density, 15f, 6 * density, -6 * density)
        v.setViewVisibility(R.id.th_free_karoo_3, if (s.freePower == FreePower.RIDE_AVG) View.GONE else View.VISIBLE)
        v.setOnClickPendingIntent(R.id.free_lap_tile, freePowerCycleIntent)
        // HR tile: tap to go on: HR → LAP HR → RIDE HR. With cadence instead (Settings), the same
        // tile shows CADENCE → LAP CAD → RIDE CAD.
        v.setTextViewText(R.id.free_hr, s.shownSecond?.toString() ?: "--")
        fillValue(v, R.id.free_hr, s.shownSecond?.toString() ?: "--", (config.viewSize.first - 6 * density) / 2f - 2 * density - 7 * density, 75 * density, 15f, 6 * density, -6 * density)
        v.setTextViewText(
            R.id.free_hr_label,
            if (s.showsCadence) {
                if (s.freeSecond == FreeSecond.RIDE) context.getString(R.string.ride_cadence).uppercase() else cadenceLabel(lap = s.freeSecond == FreeSecond.LAP)
            } else {
                context.getString(when (s.freeSecond) { FreeSecond.NOW -> R.string.hr; FreeSecond.LAP -> R.string.lap_hr; FreeSecond.RIDE -> R.string.ride_hr })
            },
        )
        if (s.showsCadence) showCadenceIcon(v, R.id.free_hr_icon)
        // LAP HR / LAP CAD: the lap arrow before the heart, like LAP AVG (green; black on a zone colour, below)
        v.setViewVisibility(R.id.free_hr_lap_icon, if (s.freeSecond == FreeSecond.LAP) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.free_hr_tile, lapHrToggleIntent)

        // The tile takes the Karoo's own colour for the heart rate zone it shows, like the Karoo's
        // HR field with Zone Color on: black text and outline heart (cadence has no zones).
        // No zone: like the Lap avg tile (Tiles setting).
        val zoneBg = if (s.showsCadence) null else s.zoneOf(s.shownHr)?.let { hrZoneDrawable(it, s.hrZones.size) }
        setBg(v, R.id.free_hr_tile, zoneBg ?: tileDrawable(s.tileOutline, freeTonal))
        if (zoneBg != null) {
            val ink = color(R.color.karoo_hr_text)
            v.setTextColor(R.id.free_hr, ink)
            v.setTextColor(R.id.free_hr_label, ink)
            v.setImageViewResource(R.id.free_hr_icon, R.drawable.ic_heart_outline)
            v.setInt(R.id.free_hr_icon, "setColorFilter", ink)
            v.setInt(R.id.free_hr_lap_icon, "setColorFilter", ink)
        }

        v.setTextViewText(R.id.free_lap_number, relLine(s.lapNumber?.let { context.getString(R.string.lap_number, it).uppercase() } ?: ""))
        v.setTextViewText(R.id.free_lap_time, s.lapTimeSec?.let(::formatClock) ?: "--:--")
        // Last lap: time, average watts and average heart rate ("8:20 · 271 W · 142 bpm"). Karoo
        // style has its own "LAST LAP" label above, so only the values.
        val last = s.lastLapTimeSec?.takeIf { it > 0 }?.let { time ->
            val parts = listOfNotNull(
                formatClock(time),
                s.lastLapPowerAvg?.let { "$it W" },
                if (s.showsCadence) {
                    s.lastLapCadenceAvg?.takeIf { it > 0 }?.let { context.getString(R.string.rpm_value, it) }
                } else {
                    s.lastLapHrAvg?.takeIf { it > 0 }?.let { context.getString(R.string.bpm_value, it) }
                },
            ).joinToString(" · ")
            parts
        }
        v.setTextViewText(R.id.free_last_lap, relLine(last ?: ""))
        // No last lap yet (first lap): hide the "LAST LAP" label too. Its space stays, so nothing
        // moves when the first lap ends.
        v.setViewVisibility(R.id.th_free_karoo_5, if (last == null) View.INVISIBLE else View.VISIBLE)
        v.setOnClickPendingIntent(R.id.free_btn_settings, settingsIntent)
        gearBelowKarooBar(v, R.id.free_btn_settings, s)
        // Both tiles drawn 1:1 like the Karoo's own fields (75dp: a native 140 px tile)
        val textInk = color(R.color.text)
        tileArt(
            v, R.id.free_lap_img, listOf(R.id.th_free_karoo_3, R.id.th_free_karoo_4, R.id.free_lap_label, R.id.free_lap_avg), halfTileW, 75 * density,
            listOfNotNull(if (s.freePower != FreePower.RIDE_AVG) TileArt.Glyph(KarooType.Glyph.LAP, textInk) else null, TileArt.Glyph(KarooType.Glyph.BOLT, textInk)),
            context.getString(when (s.freePower) { FreePower.LAP_AVG -> R.string.lap_avg; FreePower.LAP_NP -> R.string.np; FreePower.RIDE_AVG -> R.string.ride_avg }),
            s.shownFreePower?.toString() ?: "--",
            empty = s.powerSensor.emptyText,
        )
        val hrInk = if (zoneBg != null) color(R.color.karoo_hr_text) else color(R.color.text)
        val hrIcon = when {
            s.showsCadence -> TileArt.Glyph(KarooType.Glyph.CADENCE, hrInk)
            // The Karoo's outline heart, like its HR field; the red heart is the workout screen's
            else -> TileArt.Glyph(KarooType.Glyph.HEART, hrInk)
        }
        tileArt(
            v, R.id.free_hr_img, listOf(R.id.free_hr_lap_icon, R.id.free_hr_icon, R.id.free_hr_label, R.id.free_hr), halfTileW, 75 * density,
            listOfNotNull(if (s.freeSecond == FreeSecond.LAP) TileArt.Glyph(KarooType.Glyph.LAP, hrInk) else null, hrIcon),
            if (s.showsCadence) {
                if (s.freeSecond == FreeSecond.RIDE) context.getString(R.string.ride_cadence) else cadenceLabel(lap = s.freeSecond == FreeSecond.LAP)
            } else {
                context.getString(when (s.freeSecond) { FreeSecond.NOW -> R.string.hr; FreeSecond.LAP -> R.string.lap_hr; FreeSecond.RIDE -> R.string.ride_hr })
            },
            s.shownSecond?.toString() ?: "--", hrInk,
            empty = (if (s.showsCadence) s.cadenceSensor else s.hrSensor).emptyText,
        )
        return v
    }

    /**
     * The Karoo's HR zone colour for [zone] (1-based) of [zones]: its ride app picks from seven
     * colours by the number of zones (5 zones: c1 c2 c3 c4 c6).
     */
    private fun hrZoneDrawable(zone: Int, zones: Int): Int? =
        Zones.hrZoneColor(zone, zones)?.let { HR_ZONE_BG[HR_ZONE_COLORS.indexOf(it)] }

    /** "4m @ 280W", "4m @ 273–287W"; a heart-rate step "4m @ 138 bpm", or "4m @ 84% LTHR" with % of LTHR on. */
    private fun summary(step: PlanStep, s: ScreenState): String {
        if (!step.hr) return summary(step)
        val dur = formatClock(step.durationSec)
        val lo = if (step.isRange && step.rangeLow != null) step.rangeLow else if (step.isRamp) step.startWatts else null
        val hi = if (step.isRange && step.rangeHigh != null) step.rangeHigh else if (step.isRamp) step.endWatts else null
        // % as the step is written ("85% HR", "95% LTHR"), else % of LTHR from bpm
        val unit = s.pctUnitOf(step)
        if (s.hrTargetLthr && unit != null) {
            val pct = if (step.pctUnit != null && step.pctLow != null && step.pctHigh != null) {
                step.pctLow to step.pctHigh
            } else {
                val lthr = s.plan?.lthr ?: 1
                fun p(bpm: Int) = (bpm * 100.0 / lthr).roundToInt()
                if (lo != null && hi != null) p(lo) to p(hi) else p(step.avgWatts).let { it to it }
            }
            val ofMax = unit == "%hr"
            return if (pct.first != pct.second) {
                context.getString(if (ofMax) R.string.step_summary_hrpct_range else R.string.step_summary_lthr_range, dur, pct.first, pct.second)
            } else {
                context.getString(if (ofMax) R.string.step_summary_hrpct else R.string.step_summary_lthr, dur, pct.first)
            }
        }
        return run {
            if (lo != null && hi != null) context.getString(R.string.step_summary_bpm_range, dur, lo, hi)
            else context.getString(R.string.step_summary_bpm, dur, step.avgWatts)
        }
    }

    /** The label a top-row item has in the second tile. */
    private fun itemLabel(s: ScreenState, item: TopRow): String = when (item) {
        TopRow.TARGET -> context.getString(
            when {
                !s.hrStep || !s.showsPct -> R.string.target
                s.currentStep?.let(s::pctUnitOf) == "%hr" -> R.string.hr_pct_label
                else -> R.string.lthr_label
            },
        )
        TopRow.CADENCE -> cadenceLabel(lap = false)
        TopRow.HEART_RATE -> context.getString(if (s.hrStep) R.string.power_label else R.string.hr)
    }

    /**
     * What a top-row item shows: the target (range when it has one; heart-rate steps in bpm or %
     * of LTHR), cadence, or heart rate (power instead on a heart-rate step). [inTile]: in the
     * second tile (no spaces around the dash; the number styles' tile follows the range tap).
     */
    private fun itemValue(s: ScreenState, item: TopRow, inTile: Boolean, gaugeMode: Boolean): String = when (item) {
        TopRow.CADENCE -> s.cadence?.toString() ?: "--"
        TopRow.HEART_RATE -> (if (s.hrStep) s.powerShown else s.heartRate)?.toString() ?: "--"
        TopRow.TARGET -> {
            val band = s.targetRange
            // A plain hyphen, like the Karoo's own target ("162-162"): Relative has no en dash
            val dash = if (inTile) "-" else " - "
            val step = s.currentStep
            val pct = if (s.hrStep && s.showsPct && step != null) s.pctOf(step, s.elapsedInStepSec) else null
            if (pct != null) {
                // As written: "85" (one number) or "82–88"; at the top with its "%"
                val text = if (pct.first != pct.second) "${pct.first}$dash${pct.second}" else "${pct.first}"
                if (inTile) text else "$text%"
            } else {
                val showRange = band != null && band.first != band.last && (!inTile || gaugeMode || s.hrStep || s.targetShowsRange)
                if (showRange && band != null) "${band.first}$dash${band.last}" else s.target?.toString() ?: "--"
            }
        }
    }

    /**
     * A tile's header and number as one image (TileArt: the Karoo's own geometry, 1:1). The
     * tile's old icon, label and value views are hidden ([hide]); its background and tap stay.
     */
    private fun tileArt(
        v: RemoteViews, img: Int, hide: List<Int>, w: Float, h: Float, icons: List<TileArt.Glyph>, label: String, value: String,
        ink: Int = color(R.color.text), landing: String? = null, arrowColor: Int = 0, empty: String? = null,
    ) {
        val k = density / 1.875f
        v.setImageViewBitmap(img, TileArt.draw(res, w.roundToInt(), h.roundToInt(), k, icons, label.uppercase(), value, ink, ink, landing, arrowColor, empty))
        v.setContentDescription(img, "$label ${empty ?: value}")
        hide.forEach { v.setViewVisibility(it, View.GONE) }
    }

    /**
     * A text line in Relative (monospaced) made to read like proportional type: spaces become thin
     * spaces, and the signs Relative doesn't have ("@", "·", "/") are drawn in Plex Sans Condensed,
     * a little smaller so they sit on the digits. Design: docs/design/karoo-type/.
     */
    /** Words in the box (Workout finished) in the Karoo's label font; the box's own font is for numbers. */
    private fun plexWord(text: String): CharSequence = android.text.SpannableString(text).apply {
        setSpan(android.text.style.TypefaceSpan("ibm-plex-sans-condensed"), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun relLine(text: String): CharSequence {
        val t = text.replace(' ', '\u2009')
        val out = android.text.SpannableString(t)
        t.forEachIndexed { i, ch ->
            if (ch == '@' || ch == '·' || ch == '/') {
                out.setSpan(android.text.style.TypefaceSpan("ibm-plex-sans-condensed"), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(android.text.style.RelativeSizeSpan(0.9f), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return out
    }

    /**
     * The ERG mark first in the top row (docs/design/indoor-erg/erg-top-row): a dot and "ERG", no
     * background. Green dot: the trainer follows the workout; grey: connecting, free or off for this
     * ride; amber: the trainer is lost. Null when ERG is off in Settings or the ride isn't indoors.
     */
    private fun ergItem(s: ScreenState): TopRowArt.Item? {
        val erg = s.erg ?: return null
        val (dot, word) = when (erg) {
            ErgStatus.HOLDING -> R.color.karoo_success_green to "ERG"
            ErgStatus.READY -> R.color.karoo_success_green to "ERG READY"
            ErgStatus.FREE -> R.color.erg_wait_fg to "ERG"
            ErgStatus.CONNECTING -> R.color.erg_wait_fg to "ERG …"
            ErgStatus.LOST -> R.color.erg_warn_fg to "ERG ✕"
            ErgStatus.OFF -> R.color.erg_wait_fg to "ERG off"
        }
        return TopRowArt.Item(TileArt.Icon(R.drawable.ic_erg_dot, color(dot)), word)
    }

    /** Screens without an interval row: the gear moves down out from under the Karoo's top strip. */
    private fun gearBelowKarooBar(v: RemoteViews, id: Int, s: ScreenState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        v.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, if (s.karooBarOnTop) KAROO_BAR_DP else 0f, android.util.TypedValue.COMPLEX_UNIT_DIP)
    }

    /** The chart reaches the field's bottom corners: round them like the Karoo's card (14 px on a Karoo 3). */
    private fun roundBottomCorners(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val r = 14f * density / 1.875f
        val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val path = android.graphics.Path().apply {
            addRoundRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r), android.graphics.Path.Direction.CW)
        }
        c.clipPath(path)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    /** Width of a half-width tile (workout and free ride: 6dp of edges, 1dp between them). */
    private val halfTileW: Float get() = (config.viewSize.first - 6 * density) / 2f - 1 * density

    /** A top-row item's icon (centred in its box) and colour, for [TopRowArt]. */
    private fun topIcon(s: ScreenState, item: TopRow, onTint: Boolean): TileArt.Icon {
        val green = color(if (onTint) R.color.text else R.color.karoo_green)
        return when {
            item == TopRow.TARGET -> TileArt.Icon(R.drawable.ic_target, green)
            // Cadence in the text colour: green reads as "on target", which cadence never is
            item == TopRow.CADENCE -> TileArt.Icon(R.drawable.ic_top_cadence, color(R.color.text))
            s.hrStep -> TileArt.Icon(R.drawable.ic_bolt_filled, green)
            else -> TileArt.Icon(R.drawable.ic_karoo_heart, KarooType.HEART_RED)
        }
    }

    /** The sensor behind a top-row item's value (none for the target). */
    private fun itemSensor(s: ScreenState, item: TopRow): SensorState? = when {
        item == TopRow.TARGET -> null
        item == TopRow.CADENCE -> s.cadenceSensor
        s.hrStep -> s.powerSensor
        else -> s.hrSensor
    }

    /** The sensor behind the big number: heart rate on a heart-rate step, else power. */
    private fun mainSensor(s: ScreenState) = if (s.hrStep) s.hrSensor else s.powerSensor

    /** The big number's empty state: the Karoo's words where the number goes, or the number. */
    /**
     * The indoor START box's height: it takes the big watts' room (only the top row stays above it),
     * so it follows the field's height (a route makes the field shorter); never below its own minimum.
     */
    /** The trainer hint's room (two lines at 24 px, its padding and margin), taken from the taller box. */
    private val HINT_BOX_DP = 30f
    /** INDOOR SESSION's room (outdoors there's none, so the box gets it). */
    private val LABEL_DP = 28f

    private fun indoorBoxDp(): Float {
        val fieldDp = config.viewSize.second / density
        return (IndoorStartArt.HEIGHT_DP + (fieldDp - FULL_FIELD_DP) + INDOOR_BOX_EXTRA_DP).coerceIn(IndoorStartArt.HEIGHT_DP, IndoorStartArt.HEIGHT_DP + INDOOR_BOX_EXTRA_DP)
    }

    private fun bigEmpty(v: RemoteViews, number: Int, image: Int, sensor: SensorState, w: Float, h: Float) {
        val text = sensor.emptyText
        v.setViewVisibility(number, if (text == null) View.VISIBLE else View.INVISIBLE)
        v.setViewVisibility(image, if (text == null) View.GONE else View.VISIBLE)
        if (text == null || w < 1 || h < 1) return
        val k = density / 1.875f
        val p = KarooType.emptyPaint(text, (w / k).roundToInt()).apply { color = color(R.color.text); textSize *= k }
        val b = android.graphics.Rect(); p.getTextBounds(text, 0, text.length, b)
        val bmp = android.graphics.Bitmap.createBitmap(w.roundToInt(), (b.height() + 8 * k).roundToInt(), android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawText(text, w / 2f, -b.top + 4 * k, p)
        v.setImageViewBitmap(image, bmp)
        v.setContentDescription(image, text)
    }

    /** A top-row item's icon and its colour, for a tile drawn by [tileArt]. */
    private fun itemIcon(s: ScreenState, item: TopRow): TileArt.Glyph {
        return when {
            item == TopRow.TARGET -> TileArt.Glyph(KarooType.Glyph.TARGET, color(R.color.text))
            item == TopRow.CADENCE -> TileArt.Glyph(KarooType.Glyph.CADENCE, color(R.color.text))
            s.hrStep -> TileArt.Glyph(KarooType.Glyph.BOLT, color(R.color.text))
            else -> TileArt.Glyph.RED_HEART
        }
    }

    /** A top-row item's icon (top row: centred on the digits; tile: the small header icon). */
    private fun showItemIcon(v: RemoteViews, id: Int, s: ScreenState, item: TopRow, top: Boolean, onTint: Boolean) {
        val green = color(if (onTint) R.color.text else R.color.karoo_green)
        when {
            item == TopRow.TARGET -> {
                v.setImageViewResource(id, R.drawable.ic_target)
                v.setInt(id, "setColorFilter", green)
                v.setContentDescription(id, context.getString(R.string.target))
            }
            item == TopRow.CADENCE -> {
                v.setImageViewResource(id, if (top) R.drawable.ic_top_cadence else R.drawable.ic_cadence)
                v.setInt(id, "setColorFilter", color(R.color.text))
                v.setContentDescription(id, context.getString(R.string.cadence))
            }
            s.hrStep -> {
                // Heart rate is the big number: power in its place
                v.setImageViewResource(id, R.drawable.ic_bolt_filled)
                v.setInt(id, "setColorFilter", green)
                v.setContentDescription(id, context.getString(R.string.power_label))
            }
            else -> {
                v.setImageViewResource(id, if (top) R.drawable.ic_top_heart else R.drawable.ic_heart)
                v.setInt(id, "setColorFilter", color(R.color.heart_icon))
                v.setContentDescription(id, context.getString(R.string.cd_heart_rate))
            }
        }
    }

    private fun summary(step: PlanStep): String = if (step.isRange && step.rangeLow != null && step.rangeHigh != null) {
        context.getString(R.string.step_summary_ramp, formatClock(step.durationSec), step.rangeLow, step.rangeHigh)
    } else if (step.isRamp) {
        context.getString(R.string.step_summary_ramp, formatClock(step.durationSec), step.startWatts, step.endWatts)
    } else {
        context.getString(R.string.step_summary, formatClock(step.durationSec), step.startWatts)
    }

    private fun summaryWithZone(step: PlanStep, ftp: Int?): String {
        val zone = Zones.zoneOf(step.avgWatts, ftp) ?: return summary(step)
        return "${summary(step)} · Z$zone"
    }

    /** Cell values in the Karoo's value font (Relative, KarooType). */
    private val numberPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = KarooType.value
        letterSpacing = KarooType.VALUE_LETTER_SPACING
    }

    /**
     * The gauge's space on screen (the ImageView's height). The Karoo doesn't tell the app when a
     * route makes the field shorter, so it's worked out from the layout (measured on a Karoo 3)
     * and the navigation state; the bitmap is drawn at that size so the arc can use all of it.
     */
    private fun gaugeHeightPx(navigating: Boolean): Int {
        val dp = GAUGE_KAROO_DP - (if (navigating) ROUTE_DP else 0f)
        return (dp * density * scale).toInt().coerceAtLeast(1)
    }

    /**
     * Bottom padding that puts the free-ride watts label 22 px above the digits. The label and
     * the watts share the same box, centred, so the padding lifts the label by half its size.
     * The watts' size is worked out the way their autosize picks it: up to 124sp, as big as fits
     * the space (shorter with a route, which the Karoo doesn't report) and the width.
     */
    private fun wattsLabelLift(watts: String, navigating: Boolean): Int {
        val areaPx = (FREE_WATTS_DP - (if (navigating) ROUTE_DP else 0f)) * density * scale - 24 * density
        val widthPx = config.viewSize.first - 6 * density
        wattsPaint.textSize = 100f
        val perPx = (wattsPaint.measureText(watts) / 100f).coerceAtLeast(0.01f)
        val size = minOf(124 * density, areaPx / 1.3f, widthPx / perPx)
        // Digits are 0.698 em tall and sit 0.026 em below the box's centre; the label's capitals
        // (15sp) are 20 px tall. Label centre = digits' top − 22 px − half the label.
        val labelCap = 20f * scale
        // (+3 px measured on a Karoo 3: the label's capitals sit a little low in their box)
        val lift = 2 * (0.349f * size - 0.026f * size + 25 * scale + labelCap / 2)
        return lift.toInt().coerceAtLeast(0)
    }

    private val wattsPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = KarooType.value
        letterSpacing = KarooType.VALUE_LETTER_SPACING
    }

    /** Largest size up to [CELL_VALUE_SP] at which [text] fits in a Karoo-style cell. */
    private fun cellValueSp(text: String): Float {
        val cellPx = ((config.viewSize.first - 6 * density) / 2f - 1 * density - 8 * density).coerceAtLeast(1f)   // tiles at the Karoo's geometry
        var sp = CELL_VALUE_SP
        while (sp > 16f) {
            numberPaint.textSize = sp * density * scale
            if (numberPaint.measureText(text) <= cellPx) break
            sp -= 1f
        }
        return sp
    }

    private var lapText = ""
    private var targetText = ""

    private fun applyTextSizes(v: RemoteViews, gaugeMode: Boolean) {
        // Must match the sp sizes in the layouts (the reference size).
        val sizes = mutableListOf(
            R.id.paused_text to 13f, R.id.next_label to 11f, R.id.confirm_text to 17f,
            R.id.move_cancelled_text to 16f, R.id.countdown to 31f,
        )
        // One compact block under the watts, the same on every screen: 15sp labels, 31sp values
        // (smaller only if a value doesn't fit), 28sp time; the top row's values 20sp.
        sizes += listOf(R.id.lap_avg_label to 15f, R.id.interval_label to 15f, R.id.time_left to 28f)
        sizes += listOf(R.id.tile2_label to 15f, R.id.top1 to 20f, R.id.top2 to 20f, R.id.target_next_top to 14f)
        sizes.forEach { (id, sp) -> v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * density * scale) }
        // Lap avg and the second tile: each value fills its tile, like the Karoo's own fields
        val cellW = (config.viewSize.first - 6 * density) / 2f - 1 * density - 8 * density
        listOf(R.id.lap_avg to lapText, R.id.tile2_value to targetText).forEach { (id, text) ->
            fillValue(v, id, text, cellW, 75 * density, labelSp = 15f, padTop = 6 * density, marginBottom = -1.5f * density)
        }
    }

    /**
     * The Karoo's value fill (KarooType.valueSize, from its singleNumericDataStyle): [text] auto-sized
     * so its whole line (ascent to descent) fits the space under the header, and no wider than [w]. Measured 1:1 against a native 236 × 140 px field: 64 px
     * digits, 24 px under the label. Then the bottom padding puts the digits' baseline 12 px above
     * the tile's bottom, like the native field.
     */
    private fun fillValue(v: RemoteViews, id: Int, text: String, w: Float, tileH: Float, labelSp: Float, padTop: Float, marginBottom: Float) {
        val k = density / 1.875f * scale
        // The line's space: the tile less 35 px, calibrated so a native-size 140 px field gets the
        // Karoo's 64 px digits (measured) (Relative's line is about 1.65 × its digit height)
        val area = tileH - 34f * k
        val size = KarooType.valueSize(numberPaint, text, (w - 2 * 4f * density).toInt(), area.toInt())
        numberPaint.textSize = size
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, size)
        // The value hangs [marginBottom] below the tile (negative margin); lift its baseline to 12 px
        val pad = 14f * k - marginBottom - numberPaint.fontMetrics.descent   // the digits' ink sits 2 px under the baseline
        v.setViewPadding(id, 0, 0, 0, pad.toInt().coerceAtLeast(0))
    }

    /** How much higher a cell value at [sp] has to sit to share the full-size value's baseline. */
    private fun baselineLift(sp: Float): Int {
        numberPaint.textSize = CELL_VALUE_SP * density * scale
        val full = numberPaint.fontMetrics.descent
        numberPaint.textSize = sp * density * scale
        return (full - numberPaint.fontMetrics.descent).toInt().coerceAtLeast(0)
    }

    private fun color(id: Int) = ContextCompat.getColor(res, id)

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
        /** A full-page field on the Karoo 3 (721 px) in dp. */
        private const val FULL_FIELD_DP = 721f / 1.875f
        /** On a full page the indoor START box grows by this into the big watts' room. */
        private const val INDOOR_BOX_EXTRA_DP = 46f
        /** Indoor start's top row (three items): icons closer to their values than the usual 0.3. */
        private const val INDOOR_ICON_GAP = 0.14f
        /** The Karoo's PAUSE / "Searching GPS…" strip over a field's top, about 40 px on a Karoo 3. */
        private const val KAROO_BAR_DP = 24f
        /** The Karoo's seven HR zone colours (hr_colors.xml), picked per zone count by hrZoneDrawable. */
        private val HR_ZONE_COLORS = listOf(
            R.color.karoo_hr_c1, R.color.karoo_hr_c2, R.color.karoo_hr_c3, R.color.karoo_hr_c4,
            R.color.karoo_hr_c5, R.color.karoo_hr_c6, R.color.karoo_hr_c7,
        )
        private val HR_ZONE_BG = listOf(
            R.drawable.bg_hr_c1, R.drawable.bg_hr_c2, R.drawable.bg_hr_c3, R.drawable.bg_hr_c4,
            R.drawable.bg_hr_c5, R.drawable.bg_hr_c6, R.drawable.bg_hr_c7,
        )
        /** Gear and pause icons: 55% of the text colour. */
        private const val ICON_ALPHA = 140
        private const val REF_W_DP = 255f
        private const val REF_H_DP = 385f
        private const val CHART_DP = 42f   // 56 with the time label; the bars keep their size
        private const val CELL_VALUE_SP = 31f
        /** The top row's text size in Karoo 3 px: BigWatts' heart-rate line as measured on a Karoo (24 px digits). */
        private const val TOP_ROW_PX = 34f
        /** Height of the gauge's space on a full page without a route (measured), and how much a route takes. */
        private const val GAUGE_KAROO_DP = 121.7f   // 228 px on a Karoo 3: 7dp went to the native-height tiles
        private const val ROUTE_DP = 42.1f          // a route makes the field 79 px shorter
        private const val FREE_WATTS_DP = 196.3f    // the free-ride watts' space: 368 px on a Karoo 3


    }
}
