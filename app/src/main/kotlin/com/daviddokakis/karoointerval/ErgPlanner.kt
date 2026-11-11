package com.daviddokakis.karoointerval

import kotlin.math.roundToInt

/** What the trainer should do now (indoor ERG, docs/design/indoor-erg). */
sealed interface ErgCommand {
    data class Target(val watts: Int) : ErgCommand
    /** Hand the trainer back (FTMS reset): the rider pedals freely, the Karoo can take over. */
    data object Release : ErgCommand
}

/** Everything that decides the trainer target besides the workout itself. */
data class ErgInputs(
    /** The Karoo ride profile is marked indoor. */
    val indoor: Boolean,
    /** Settings › Trainer: ERG on. */
    val enabled: Boolean,
    /** A ride is recording (running or paused). */
    val recording: Boolean,
    val state: ScreenState,
    /** Settings › When paused, % of FTP. */
    val recoveryPct: Int = ErgPlanner.RECOVERY_PCT,
)

/**
 * Turns the workout's state into one trainer command a second. Holds only the soft-start memory:
 * after a pause the target climbs from the recovery watts to the interval's over [SOFT_START_SEC].
 * Big steps inside the workout go out at once (the trainer ramps its brake itself).
 */
class ErgPlanner {
    private var resting = false
    private var soft: SoftStart? = null

    private data class SoftStart(val from: Int, val startSec: Long, val stepIndex: Int)

    fun next(input: ErgInputs, nowSec: Long): ErgCommand {
        val s = input.state
        val plan = s.plan
        if (!input.indoor || !input.enabled || !input.recording || plan == null || s.notStarted) {
            resting = false; soft = null
            return ErgCommand.Release
        }
        val cap = capFor(plan, s.ftp)
        val recovery = recoveryWatts(s.ftp, input.recoveryPct).coerceAtMost(cap)
        if (s.finished || s.paused || s.workoutPaused) {
            resting = true; soft = null
            return ErgCommand.Target(recovery)
        }
        // A heart-rate step has no watts to hold: the rider rides it by feel.
        if (s.hrStep) { resting = false; soft = null; return ErgCommand.Release }
        val target = (s.target ?: return ErgCommand.Release).coerceIn(0, cap)

        if (resting) { resting = false; soft = SoftStart(recovery, nowSec, s.stepIndex) }
        val ss = soft
        if (ss != null) {
            val t = nowSec - ss.startSec
            // A jump during the soft start lands at full target, like any jump.
            if (t >= SOFT_START_SEC || ss.stepIndex != s.stepIndex) soft = null
            else return ErgCommand.Target((ss.from + (target - ss.from) * t.toDouble() / SOFT_START_SEC).roundToInt())
        }
        return ErgCommand.Target(target)
    }

    companion object {
        const val SOFT_START_SEC = 10
        const val MAX_WATTS = 1500
        const val RECOVERY_PCT = 50
        const val RECOVERY_MIN = 80
        /** Without an FTP: an easy spin. */
        const val RECOVERY_NO_FTP = 100

        /** Never above the plan's own top (+5 %), twice FTP, or [MAX_WATTS]. */
        fun capFor(plan: WorkoutPlan, ftp: Int?): Int {
            var cap = MAX_WATTS
            if (plan.maxWatts > 0) cap = minOf(cap, (plan.maxWatts * 1.05).roundToInt())
            if (ftp != null && ftp > 0) cap = minOf(cap, ftp * 2)
            return cap
        }

        fun recoveryWatts(ftp: Int?, pct: Int = RECOVERY_PCT): Int =
            if (ftp != null && ftp > 0) maxOf(RECOVERY_MIN, ftp * pct / 100) else RECOVERY_NO_FTP
    }
}

/**
 * When to write to the trainer. The Karoo's own trainer card sends one command per tap and never
 * repeats it (desk test 2026-11-11), so a target resent every [KEEP_ALIVE_MS] wins back within
 * that time. A change goes out at once; a release is sent once.
 */
class ErgSendPacer {
    private var last: ErgCommand? = null
    private var lastAtMs = 0L

    fun due(cmd: ErgCommand, nowMs: Long): Boolean {
        val prev = last
        val send = when {
            prev == null -> true
            cmd is ErgCommand.Release -> prev !is ErgCommand.Release
            prev != cmd -> nowMs - lastAtMs >= MIN_GAP_MS
            else -> nowMs - lastAtMs >= KEEP_ALIVE_MS
        }
        if (send) { last = cmd; lastAtMs = nowMs }
        return send
    }

    companion object {
        const val KEEP_ALIVE_MS = 2_000L
        /** Trainers take about one command a second. */
        const val MIN_GAP_MS = 900L
    }
}
