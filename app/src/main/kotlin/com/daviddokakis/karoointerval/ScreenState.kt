package com.daviddokakis.karoointerval

/** Everything the interval screen needs to draw one frame. */
data class ScreenState(
    val plan: WorkoutPlan? = null,
    /** Instant power (lap average is built from this). */
    val power: Int? = null,
    /** 3-second average power, shown as the big number. */
    val power3s: Int? = null,
    val heartRate: Int? = null,
    /** 0-based index of the current step. */
    val stepIndex: Int = 0,
    val elapsedInStepSec: Int = 0,
    val finished: Boolean = false,
    val lapAvg: Int? = null,
    /** The ride is paused (Karoo's own pause). */
    val paused: Boolean = false,
    /** The workout is paused from this screen: interval clock and lap average stop, ride keeps recording. */
    val workoutPaused: Boolean = false,
    /** Seconds left before jumping, or null when no jump is pending. */
    val countdown: Int? = null,
    /** Step the pending jump will land on. */
    val jumpTarget: Int? = null,
    /** Chart shows the current interval zoomed in (with the edges of its neighbours) instead of the whole workout. */
    val chartZoomed: Boolean = false,
    // Karoo's own lap data, shown when no workout is loaded (free ride).
    val lapNumber: Int? = null,
    val lapTimeSec: Int? = null,
    val lapPowerAvg: Int? = null,
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
    val hasNext: Boolean get() = stepIndex < stepCount - 1
    val hasPrevious: Boolean get() = stepIndex > 0
    val ftp: Int? get() = plan?.ftp ?: profileFtp

    val intervalTotalSec: Int? get() = currentStep?.durationSec
    val intervalRemainingSec: Int? get() = currentStep?.let { (it.durationSec - elapsedInStepSec).coerceAtLeast(0) }
    val workoutRemainingSec: Int?
        get() = plan?.let { (it.totalSec - it.startSecOf(stepIndex) - elapsedInStepSec).coerceAtLeast(0) }

    /** 1-based heart rate zone, or null when unknown or below Z1. */
    val hrZone: Int?
        get() {
            val hr = heartRate ?: return null
            if (hrZones.isEmpty()) return null
            val i = hrZones.indexOfFirst { hr in it }
            return when {
                i >= 0 -> i + 1
                hr > hrZones.last().last -> hrZones.size
                else -> null
            }
        }

    val target: Int? get() = currentStep?.targetAt(elapsedInStepSec)
    val targetRange: IntRange? get() = currentStep?.rangeAt(elapsedInStepSec)
}
