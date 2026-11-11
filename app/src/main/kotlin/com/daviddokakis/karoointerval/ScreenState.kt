package com.daviddokakis.karoointerval

import kotlin.math.roundToInt

/** Everything the interval screen needs to draw one frame. */
data class ScreenState(
    val plan: WorkoutPlan? = null,
    /** Instant power (lap average is built from this). */
    val power: Int? = null,
    /** Power shown as the big number: instant or a 3 / 5 / 10 s average (Settings › Power). */
    val powerShown: Int? = null,
    /** Counts every sample of the shown power or heart rate, so the screen can redraw as each arrives. */
    val sampleSeq: Int = 0,
    val heartRate: Int? = null,
    /** Cadence (rpm), smoothed per Settings › Cadence. */
    val cadence: Int? = null,
    /** Karoo's lap and last-lap average cadence. */
    val lapCadenceAvg: Int? = null,
    val lastLapCadenceAvg: Int? = null,
    /** Settings › Heart rate or cadence. */
    val secondField: SecondField = SecondField.HEART_RATE,
    /** 0-based index of the current step. */
    val stepIndex: Int = 0,
    val elapsedInStepSec: Int = 0,
    val finished: Boolean = false,
    val lapAvg: Int? = null,
    /** Normalised power of the current interval lap (null in its first 30 s). */
    val lapNp: Int? = null,
    /** Lap tile shows NP instead of the lap average (tap to toggle, remembered). */
    val lapShowsNp: Boolean = false,
    /** The workout's lap tile (tap to go on): the lap's average, the lap's NP, the ride's average. */
    val lapPower: FreePower = FreePower.LAP_AVG,
    /** Settings › Right tile: the metrics its tap goes through; null: as before (what the top row leaves). */
    val rightItems: List<RightItem>? = null,
    /** Which of [rightItems] the right tile shows now. */
    val rightIndex: Int = 0,
    /** The ride's calories so far (the Karoo's). */
    val calories: Int? = null,
    /** Speed in m/s (the Karoo's). */
    val speedMs: Double? = null,
    /** The rider profile's distance unit is imperial (speed in mph). */
    val imperial: Boolean = false,
    /** The ⓘ view is open: the whole workout so far as tiles (tap anywhere to close). */
    val infoOpen: Boolean = false,
    /** The workout's own numbers so far, for the ⓘ view (kept current while it's open). */
    val live: WorkoutSummary? = null,
    /** The running workout changed on intervals.icu: the Workouts page asks keep or use new. */
    val planUpdateWaiting: Boolean = false,
    /** Settings › Zone colours. */
    val zonePalette: ZonePalette = ZonePalette.KAROO,
    /** Extra contrast for the graph's colours against the page (0.15 = +15 %; the Karoo's screen is pale). */
    val zoneContrast: Float = ZonePalette.CONTRAST,
    /** Free ride: the HR tile shows the lap's average heart rate (tap to toggle, remembered). */
    /** Free ride's tiles: which value each shows (tap to go on). */
    val freePower: FreePower = FreePower.LAP_AVG,
    val freeSecond: FreeSecond = FreeSecond.NOW,
    /** The whole ride so far (free ride's tiles): average power, heart rate, cadence. */
    val rideAvgPower: Int? = null,
    val rideAvgHr: Int? = null,
    val rideAvgCadence: Int? = null,
    /** Settings › Top row; null: as each layout has it (number styles: heart rate, gauge: target). */
    val topRow: TopRow? = null,
    /** Settings › Top row › Two: the second value at the top, or null for one value. */
    val topRowRight: TopRow? = null,
    /** A heart-rate step's target as % of LTHR instead of bpm (tap the Target tile). */
    val hrTargetLthr: Boolean = false,
    /** Karoo's average heart rate this lap, and in the last lap (free ride). */
    val lapHrAvg: Int? = null,
    val lastLapHrAvg: Int? = null,
    /** The ride is paused (Karoo's own pause). */
    val paused: Boolean = false,
    /** …by the Karoo's auto-pause (not by the rider). */
    val autoPaused: Boolean = false,
    /**
     * On the start screen: the workout was already under way in this ride (removed, then selected
     * again), [stepIndex] is where it was and this is how far into it, in seconds. The start screen
     * offers CONTINUE or START OVER.
     */
    val resumeFromSec: Int? = null,
    /** Each sensor's state, for the Karoo's own empty states ("No Sensor", "Searching…"). */
    val powerSensor: SensorState = SensorState.OK,
    val hrSensor: SensorState = SensorState.OK,
    val cadenceSensor: SensorState = SensorState.OK,
    /** The Karoo is searching for GPS: its grey "Searching GPS…" strip covers the top of the field. */
    val gpsSearching: Boolean = false,
    /** The workout is paused from this screen: interval clock and lap average stop, ride keeps recording. */
    val workoutPaused: Boolean = false,
    /** A workout is loaded but waits for START WORKOUT (the ready screen); [stepIndex] is where it will start. */
    val notStarted: Boolean = false,
    /** The finished workout's own numbers, for the done screen. */
    val summary: WorkoutSummary? = null,
    /** Seconds left before jumping, or null when no jump is pending. */
    val countdown: Int? = null,
    /** Interval being chosen with ⏮/⏭ (before the countdown), or null. */
    val selectTarget: Int? = null,
    /** The choice is "restart the current interval". */
    val selectRestart: Boolean = false,
    /** Confirmation fill, 1..4 (0 = not choosing). */
    val selectProgress: Int = 0,
    /** Seconds until a remote-made choice confirms itself, or null (on-screen choice / none). */
    val autoConfirmLeft: Int? = null,
    /** Step the pending jump will land on. */
    val jumpTarget: Int? = null,
    /** How the target range is shown (tap the watts to cycle). */
    val wattsMode: WattsMode = WattsMode.GAUGE,
    /** The Karoo is navigating (route pill shown, field shorter). */
    val navigating: Boolean = false,
    /** Dark / Light / Auto setting (Auto: follow the Karoo). */
    val appearance: Appearance = Appearance.AUTO,
    /** Settings › Power: what the big watts are (named above them on the free-ride screen). */
    val powerSmoothing: Smoothing = Smoothing.S5,
    /** Tiles setting: Outline (border only) instead of Tonal (grey fill). */
    val tileOutline: Boolean = false,
    /** Target tile shows the target range instead of the single target (tap to toggle). */
    val targetShowsRange: Boolean = false,
    /** "Move cancelled" is showing (after ✕ on the countdown). */
    val moveCancelled: Boolean = false,
    /** Chart shows the current interval zoomed in (with the edges of its neighbours) instead of the whole workout. */
    val chartZoomed: Boolean = false,
    // Karoo's own lap data, shown when no workout is loaded (free ride).
    val lapNumber: Int? = null,
    val lapTimeSec: Int? = null,
    val lapPowerAvg: Int? = null,
    /** Normalised power of Karoo's current lap (free ride). */
    val freeLapNp: Int? = null,
    val lastLapTimeSec: Int? = null,
    val lastLapPowerAvg: Int? = null,
    /** Heart rate zones from the Karoo rider profile (min..max bpm), Z1 first. */
    val hrZones: List<IntRange> = emptyList(),
    /** FTP from the Karoo rider profile, used when the plan has none. */
    val profileFtp: Int? = null,
    /** Indoor ERG: the pill next to the gear, or null when ERG is off or the ride isn't indoors. */
    val erg: ErgStatus? = null,
    /** The watts the trainer holds now, while [erg] is [ErgStatus.HOLDING]. */
    val ergWatts: Int? = null,
    /** ERG turned off from the screen (tap the pill) for this ride: ride free, with the gauge. */
    val ergOffThisRide: Boolean = false,
    /** The Karoo's ride profile is marked indoor: the Karoo's start / pause button runs the workout. */
    val indoor: Boolean = false,
    /** No ride yet (the Karoo isn't recording or paused). */
    val rideIdle: Boolean = false,
    /** Indoor start box: the start button's green flash, on or off (flips once a second). */
    val startFlash: Boolean = false,
    /** A trainer sends the Karoo's power on an outdoor profile: before the ride, a hint to pick an indoor one. */
    val trainerOutdoor: Boolean = false,
)
 {
    val workoutLoaded: Boolean get() = plan != null
    /** ERG drives the trainer (or is getting there): the plain big number with the big pill, no gauge. */
    val ergDrives: Boolean get() = erg != null && erg != ErgStatus.OFF
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val currentStep: PlanStep? get() = plan?.steps?.getOrNull(stepIndex)
    val nextStep: PlanStep? get() = plan?.steps?.getOrNull(stepIndex + 1)
    val jumpStep: PlanStep? get() = jumpTarget?.let { plan?.steps?.getOrNull(it) }
    /** Where a jump would land: the countdown's target, else the one being chosen. */
    val landTarget: Int? get() = jumpTarget ?: selectTarget
    /** The last interval's ending countdown: the box says Workout finished (no next interval to show). */
    val finishing: Boolean get() = countdown != null && jumpTarget == null && !notStarted && plan != null && !hasNext
    val landStep: PlanStep? get() = landTarget?.let { plan?.steps?.getOrNull(it) }
    val hasNext: Boolean get() = stepIndex < stepCount - 1
    val hasPrevious: Boolean get() = stepIndex > 0
    val ftp: Int? get() = plan?.ftp ?: profileFtp

    val intervalTotalSec: Int? get() = currentStep?.durationSec
    val intervalRemainingSec: Int? get() = currentStep?.let { (it.durationSec - elapsedInStepSec).coerceAtLeast(0) }
    val workoutRemainingSec: Int?
        get() = plan?.let { (it.totalSec - it.startSecOf(stepIndex) - elapsedInStepSec).coerceAtLeast(0) }

    /** 1-based heart rate zone, or null when unknown or below Z1. */
    val hrZone: Int? get() = zoneOf(heartRate)

    /** Free ride: the heart rate the HR tile shows (now, the lap's or the ride's average). */
    val shownHr: Int? get() = when (freeSecond) { FreeSecond.NOW -> heartRate; FreeSecond.LAP -> lapHrAvg; FreeSecond.RIDE -> rideAvgHr }

    /** Cadence instead of heart rate (Settings). */
    val showsCadence: Boolean get() = secondField == SecondField.CADENCE

    /** The value in the HR spots: heart rate or cadence, now. */
    val secondValue: Int? get() = if (showsCadence) cadence else heartRate

    /** Free ride's HR tile: now, the lap's or the ride's average (tap to go on), heart rate or cadence. */
    val shownSecond: Int? get() = if (showsCadence) {
        when (freeSecond) { FreeSecond.NOW -> cadence; FreeSecond.LAP -> lapCadenceAvg; FreeSecond.RIDE -> rideAvgCadence }
    } else {
        shownHr
    }

    /** Free ride's power tile: the lap's average, the lap's NP or the ride's average. */
    val shownFreePower: Int? get() = when (freePower) { FreePower.LAP_AVG -> lapPowerAvg; FreePower.LAP_NP -> freeLapNp; FreePower.RIDE_AVG -> rideAvgPower }

    /** 1-based zone of [hr], or null when unknown or below Z1. */
    fun zoneOf(hr: Int?): Int? {
        if (hr == null || hrZones.isEmpty()) return null
        val i = hrZones.indexOfFirst { hr in it }
        return when {
            i >= 0 -> i + 1
            hr > hrZones.last().last -> hrZones.size
            else -> null
        }
    }

    /** The current step runs on heart rate: heart rate is the big number, power goes where heart rate was. */
    val hrStep: Boolean get() = currentStep?.hr == true

    /** A Karoo bar covers the field's top (its PAUSE strip, or "Searching GPS…"), gear included. */
    val karooBarOnTop: Boolean get() = paused || gpsSearching

    /** The big number: heart rate on a heart-rate step, else the (smoothed) power. */
    val mainValue: Int? get() = if (hrStep) heartRate else powerShown

    /**
     * How a heart-rate step's target shows as a percentage: as the workout writes it ("%hr", of
     * max HR, or "%lthr"), else % of LTHR worked out from bpm when the workout says the LTHR;
     * null: bpm only.
     */
    fun pctUnitOf(step: PlanStep): String? = step.pctUnit ?: if ((plan?.lthr ?: 0) > 0) "%lthr" else null

    /** The Target tile was tapped to % and the current step can show it. */
    val showsPct: Boolean get() = hrTargetLthr && currentStep?.let(::pctUnitOf) != null

    /**
     * [step]'s target in %, low to high (the same twice for a single target): the written numbers,
     * else bpm / LTHR. For a ramp, [elapsedSec] picks the point on it.
     */
    fun pctOf(step: PlanStep, elapsedSec: Int = 0): Pair<Int, Int>? {
        if (step.pctUnit != null && step.pctLow != null && step.pctHigh != null) {
            if (!step.isRamp) return step.pctLow to step.pctHigh
            val f = if (step.durationSec > 0) elapsedSec.coerceIn(0, step.durationSec).toDouble() / step.durationSec else 0.0
            val p = (step.pctLow + (step.pctHigh - step.pctLow) * f).roundToInt()
            return p to p
        }
        val lthr = plan?.lthr?.takeIf { it > 0 } ?: return null
        fun pct(bpm: Int) = (bpm * 100.0 / lthr).roundToInt()
        val band = if (step.isRamp) step.targetAt(elapsedSec).let { it..it } else step.rangeAt(elapsedSec)
        return pct(band.first) to pct(band.last)
    }

    val target: Int? get() = currentStep?.targetAt(elapsedInStepSec)
    val targetRange: IntRange? get() = currentStep?.rangeAt(elapsedInStepSec)
}

/** Tap the watts to cycle: plain → coloured number → coloured background → both → plain. */
enum class WattsMode {
    /** White (dark mode) or black (light mode). */
    PLAIN,
    /** The watts number turns blue / green / red below / in / above the target range. */
    TEXT,
    /** The top of the screen gets a blue / green / red tint; the number stays plain. */
    BACKGROUND,
    /** Both: coloured number on the tinted background. */
    BOTH,
    /**
     * Power gauge: an arc with the target range in green and a needle at the watts. Uses its own
     * layout (field_interval_gauge.xml): target on top, Lap avg / HR tiles under the gauge.
     */
    GAUGE,
    ;

    val colorsText: Boolean get() = this == TEXT || this == BOTH
    val colorsBackground: Boolean get() = this == BACKGROUND || this == BOTH

    fun next(): WattsMode = entries[(ordinal + 1) % entries.size]
}

/** The workout's own time and averages (only while it ran), shown when it's done. */
data class WorkoutSummary(
    val timeSec: Int,
    val avgPower: Int?,
    val np: Int?,
    val avgHr: Int?,
    val avgCadence: Int?,
    /** The highest power while it ran (the ⓘ view). */
    val maxPower: Int? = null,
    /** Calories while it ran (the ⓘ view). */
    val calories: Int? = null,
)

/** Settings › Right tile: what the tile next to the lap tile can show (a tap goes to the next one on). */
enum class RightItem { TARGET, CALORIES, SPEED, LAP_NP, HEART_RATE, CADENCE }

/**
 * Settings › Zone colours: the workout chart's power zone colours (Z1…Z7), light and dark, and an
 * outline for each bar where the palette draws one (intervals.icu: a pale fill, a stronger edge).
 */
enum class ZonePalette(private val light: IntArray, private val dark: IntArray, private val edgeLight: IntArray? = null, private val edgeDark: IntArray? = null) {
    /** The Karoo's own zone colours, the same in light and dark (its ride app's climb1..7). */
    KAROO(KAROO_ZONES, KAROO_ZONES),
    /** Zwift's: six zones, so Z7 shares Z6's red (its usual grey, blue, green, yellow, orange, red). */
    ZWIFT(ZWIFT_ZONES, ZWIFT_ZONES),
    /**
     * intervals.icu's, read off its workout charts (light and dark, 2026-11-11): its six hues on
     * Z1…Z6 as it draws them, and magenta for Z7 (it shows only six; picked over grey, plum, indigo
     * and ink: docs/design/intervals-z7), so all seven differ.
     */
    INTERVALS(
        rgb(0x8FC4B6, 0x8FC380, 0xFAE191, 0xF7BA86, 0xE38395, 0xB6A3E6, 0xE58FD0),
        rgb(0x458376, 0x458234, 0xBFA34B, 0xBC773C, 0xA6374E, 0x5D41A5, 0xA3368A),
        rgb(0x5CAD98, 0x5CAC43, 0xF9D660, 0xF49D4E, 0xD84B64, 0x7A58D1, 0xCC4FAE),
        rgb(0x52A590, 0x52A438, 0xF2CE57, 0xE28F48, 0xD0415A, 0x714FC9, 0xC64FA9),
    );

    /** Zone [zone] (1–7)'s fill. */
    fun fill(zone: Int, night: Boolean): Int = (if (night) dark else light)[(zone - 1).coerceIn(0, 6)]

    /** Zone [zone]'s outline, or null when the palette draws none. */
    fun edge(zone: Int, night: Boolean): Int? = (if (night) edgeDark else edgeLight)?.get((zone - 1).coerceIn(0, 6))

    companion object {
        /**
         * The graph's extra contrast: +15 % (our pick from a preview, 2026-11-11, docs/design/chart-contrast),
         * since the Karoo's screen shows colours lighter than they're drawn.
         */
        const val CONTRAST = 0.15f

        /** [c] with more contrast against the page: more saturated, darker on white, brighter on black. */
        fun boosted(c: Int, night: Boolean, amount: Float = CONTRAST): Int {
            if (amount == 0f) return c
            val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c, hsv)
            hsv[1] = (hsv[1] * (1 + amount * 1.5f)).coerceAtMost(1f)
            hsv[2] = if (night) (hsv[2] * (1 + amount * 0.6f)).coerceAtMost(1f) else hsv[2] * (1 - amount * 0.8f)
            return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(c), hsv)
        }
    }
}

private fun rgb(vararg c: Int) = IntArray(c.size) { 0xFF000000.toInt() or c[it] }
private val KAROO_ZONES = rgb(0x60EEB2, 0x00B988, 0xFFF500, 0xFB8C65, 0xFE581F, 0xD60404, 0xB700A2)
private val ZWIFT_ZONES = rgb(0x7F7F7F, 0x338CFF, 0x59BF59, 0xFFCC3F, 0xFF6639, 0xFF330C, 0xFF330C)

/** A sensor as the Karoo reports it: sending (or idle), searching, or not paired at all. */
enum class SensorState {
    OK, SEARCHING, NONE;

    /** The Karoo's own words for a tile without a value, or null when a value (or "--") shows. */
    val emptyText: String? get() = when (this) { NONE -> "No Sensor"; SEARCHING -> "Searching…"; OK -> null }

    companion object {
        fun of(s: io.hammerhead.karooext.models.StreamState) = when (s) {
            is io.hammerhead.karooext.models.StreamState.NotAvailable -> NONE
            is io.hammerhead.karooext.models.StreamState.Searching -> SEARCHING
            else -> OK
        }
    }
}

/**
 * The screen right after a workout is loaded: nothing of the workout before it carries over (its
 * jump being chosen, its countdown, "Move cancelled", its Workout done numbers).
 */
internal fun ScreenState.loaded(plan: WorkoutPlan?, index: Int, elapsedSec: Int, notStarted: Boolean, resumeFromSec: Int? = null) = copy(
    plan = plan, stepIndex = index, elapsedInStepSec = elapsedSec, finished = false, summary = null, lapAvg = null,
    workoutPaused = false, notStarted = notStarted, resumeFromSec = resumeFromSec,
    countdown = null, jumpTarget = null, selectTarget = null, selectRestart = false, selectProgress = 0,
    autoConfirmLeft = null, moveCancelled = false,
)

/**
 * Where a countdown started for workout [planId] may still go when it ends: [target], unless the
 * workout was switched meanwhile or the step isn't in it (then nowhere).
 */
internal fun ScreenState.countdownLanding(planId: String?, target: Int): Int? =
    target.takeIf { plan != null && plan.id == planId && it in plan.steps.indices }

/** Seconds of the ending countdown with [remainingMs] left in an interval: 5…1, else null. */
/**
 * The start screen's ⏮ / ⏭: one step, wrapping round at either end, so ⏮ on the first step goes
 * to the last (ride 2026-11-10: 14 taps of ⏭ to start at 15 / 16).
 */
internal fun startStepAfter(index: Int, delta: Int, count: Int): Int =
    if (count <= 0) 0 else Math.floorMod(index + delta, count)

internal fun endingCountdown(remainingMs: Long, seconds: Int = 5): Int? =
    ((remainingMs + 999) / 1000).toInt().takeIf { remainingMs > 0 && it in 1..seconds }

/** The ERG pill (docs/design/indoor-erg): what the trainer is doing. */
enum class ErgStatus {
    /** ● ERG, green: the trainer follows the workout. */
    HOLDING,
    /** ● ERG READY, green: before START, the trainer is connected and ERG takes over at START. */
    READY,
    /** ERG, grey: connected, not holding a target (a heart-rate step). */
    FREE,
    /** ERG …, grey: looking for or connecting to the trainer. */
    CONNECTING,
    /** ERG ✕, amber: the trainer is lost or refused control. */
    LOST,
    /** "ERG off", small and grey by the gear: turned off for this ride, tap to turn back on. */
    OFF,
}
