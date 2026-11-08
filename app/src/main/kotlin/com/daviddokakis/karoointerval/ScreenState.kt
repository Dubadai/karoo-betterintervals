package com.daviddokakis.karoointerval

import kotlin.math.roundToInt

/** Everything the interval screen needs to draw one frame. */
data class ScreenState(
    val plan: WorkoutPlan? = null,
    /** Instant power (lap average is built from this). */
    val power: Int? = null,
    /** Power shown as the big number: instant or a 3 / 5 / 10 s average (Settings › Power). */
    val powerShown: Int? = null,
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
    val powerSmoothing: Smoothing = Smoothing.S3,
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
) {
    val workoutLoaded: Boolean get() = plan != null
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val currentStep: PlanStep? get() = plan?.steps?.getOrNull(stepIndex)
    val nextStep: PlanStep? get() = plan?.steps?.getOrNull(stepIndex + 1)
    val jumpStep: PlanStep? get() = jumpTarget?.let { plan?.steps?.getOrNull(it) }
    /** Where a jump would land: the countdown's target, else the one being chosen. */
    val landTarget: Int? get() = jumpTarget ?: selectTarget
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
)
