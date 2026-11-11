package com.daviddokakis.karoointerval

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Match PM (docs/design/indoor-erg/powermatch): makes the bike's power meter, not the trainer, show
 * the ERG target.
 *
 * No fixed percentage. Every second it compares the trainer's own watts with the power meter's
 * (both averaged over [AVG_SEC]) and keeps the ratio trainer / PM per [BAND_W] power band, moving
 * slowly towards what it measures (time constant [TAU_SEC]). The target sent to the trainer is the
 * workout target times that band's ratio, so drift that differs across the power band and a
 * trainer that reads differently cold and warm are both followed live. The ratio is measured
 * between two sensors, not from our own command, so the loop can't wind itself up.
 *
 * The band table comes from the last ride ([bands]) and is saved back at the end of a ride.
 */
class PmMatcher(saved: Map<Int, Double> = emptyMap()) {
    private val bands = saved.filterValues { it in MIN_RATIO..MAX_RATIO }.toMutableMap()
    private val trainerAvg = ArrayDeque<Int>()
    private val pmAvg = ArrayDeque<Int>()
    private var lastTarget: Int? = null
    private var holdUntilSec = 0L
    private var lastStep = -1
    private var startSec: Long? = null

    /** Per ride minute: the mean measured ratio (steady seconds only), for the summary. */
    private val minutes = sortedMapOf<Int, MutableList<Double>>()
    /** Per band: the steady seconds seen this ride, for the summary. */
    private val seen = mutableMapOf<Int, Int>()

    /** The band table to save (band index → ratio trainer / PM). */
    fun bands(): Map<Int, Double> = bands.toMap()

    /**
     * The watts to send for [target] W. [steady]: the workout holds a target now (not a pause,
     * a release, a soft start or a ramp). [trainerW] and [pmW]: the latest readings, null when
     * missing. Call once a second.
     */
    fun next(target: Int, steady: Boolean, stepIndex: Int, trainerW: Int?, pmW: Int?, nowSec: Long): Int {
        if (startSec == null) startSec = nowSec
        // A new interval or a target jump: let the watts settle before measuring.
        val prev = lastTarget
        if (stepIndex != lastStep || prev == null || abs(target - prev) > maxOf(3, prev / 50)) holdUntilSec = nowSec + SETTLE_SEC
        lastStep = stepIndex
        lastTarget = target

        push(trainerAvg, trainerW)
        push(pmAvg, pmW)
        if (trainerW == null || pmW == null || !steady || nowSec < holdUntilSec) { trainerAvg.clear(); pmAvg.clear() }
        val band = bandOf(target)
        if (steady && nowSec >= holdUntilSec && trainerAvg.size == AVG_SEC && pmAvg.size == AVG_SEC) {
            val t = trainerAvg.average()
            val p = pmAvg.average()
            // Below this the rider has stopped pedalling or the readings are noise.
            if (t >= MIN_W && p >= MIN_W) {
                val r = (t / p).coerceIn(MIN_RATIO, MAX_RATIO)
                val f = ratioFor(band)
                bands[band] = f + (r - f) / TAU_SEC
                seen[band] = (seen[band] ?: 0) + 1
                minutes.getOrPut(((nowSec - startSec!!) / 60).toInt()) { mutableListOf() }.add(r)
            }
        }
        return (target * ratioFor(band)).roundToInt().coerceIn(0, ErgPlanner.MAX_WATTS)
    }

    /** The band's ratio; a band never seen takes its nearest neighbour's, else 1. */
    fun ratioFor(band: Int): Double =
        bands[band] ?: bands.minByOrNull { abs(it.key - band) }?.value ?: 1.0

    /** What this ride showed, for Settings; null when it never measured long enough. */
    fun summary(): PmSummary? {
        val bandPct = seen.filterValues { it >= MIN_BAND_SEC }.keys.sorted()
            .map { (it * BAND_W + BAND_W / 2) to pct(bands[it] ?: 1.0) }
        if (bandPct.isEmpty()) return null
        val perMin = minutes.filterValues { it.size >= 20 }.mapValues { it.value.average() }
        var coldPct: Int? = null
        var warmMin: Int? = null
        if (perMin.size >= 10) {
            val keys = perMin.keys.toList()
            val end = keys.takeLast(5).map { perMin.getValue(it) }.average()
            val first = perMin.getValue(keys.first())
            // Warm: from the first minute after which every minute stays within 1 % of the end.
            val settled = keys.indices.firstOrNull { i -> keys.drop(i).all { abs(perMin.getValue(it) - end) <= 0.01 } }
            if (settled != null && settled > 0 && abs(first - end) > 0.01) { coldPct = pct(first); warmMin = keys[settled] }
        }
        return PmSummary(bandPct, coldPct, warmMin)
    }

    private fun push(q: ArrayDeque<Int>, v: Int?) {
        if (v == null) return
        q.addLast(v); while (q.size > AVG_SEC) q.removeFirst()
    }

    companion object {
        const val BAND_W = 50
        const val AVG_SEC = 10
        const val TAU_SEC = 30.0
        const val SETTLE_SEC = 10L
        const val MIN_W = 40
        /** At most ±10 % from the plan's watts. */
        const val MIN_RATIO = 0.90
        const val MAX_RATIO = 1.10
        /** A band shows in the summary after this many steady seconds. */
        const val MIN_BAND_SEC = 60

        fun bandOf(watts: Int) = watts.coerceAtLeast(0) / BAND_W
        private fun pct(r: Double) = ((r - 1) * 100).roundToInt()

        fun encode(bands: Map<Int, Double>) = bands.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${"%.4f".format(java.util.Locale.US, it.value)}" }
        fun decode(s: String?): Map<Int, Double> = s.orEmpty().split(';').mapNotNull { e ->
            val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (k.toIntOrNull() ?: return@mapNotNull null) to (v.toDoubleOrNull() ?: return@mapNotNull null)
        }.toMap()
    }
}

/**
 * Match PM after a ride: trainer vs PM in % per band (band middle in W → %), and when the trainer
 * read differently cold: [coldPct] at the start, steady from [warmMin].
 */
data class PmSummary(val bands: List<Pair<Int, Int>>, val coldPct: Int? = null, val warmMin: Int? = null) {
    fun encode() = bands.joinToString(",") { "${it.first}:${it.second}" } + "|" + (coldPct ?: "") + "|" + (warmMin ?: "")

    companion object {
        fun decode(s: String?): PmSummary? {
            val parts = s?.split('|')?.takeIf { it.size == 3 } ?: return null
            val bands = parts[0].split(',').mapNotNull { e ->
                val (w, p) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                (w.toIntOrNull() ?: return@mapNotNull null) to (p.toIntOrNull() ?: return@mapNotNull null)
            }
            return if (bands.isEmpty()) null else PmSummary(bands, parts[1].toIntOrNull(), parts[2].toIntOrNull())
        }
    }
}

/** Match PM's last ride for Settings: the Karoo's power came from the trainer, or what it measured. */
data class PmLastRide(val fromTrainer: Boolean = false, val pmName: String? = null, val summary: PmSummary? = null) {
    fun encode() = if (fromTrainer) "trainer" else "pm\n${pmName.orEmpty()}\n${summary?.encode().orEmpty()}"

    companion object {
        fun decode(s: String?): PmLastRide? {
            if (s == "trainer") return PmLastRide(fromTrainer = true)
            val parts = s?.split('\n')?.takeIf { it.size == 3 && it[0] == "pm" } ?: return null
            return PmLastRide(pmName = parts[1].ifEmpty { null }, summary = PmSummary.decode(parts[2]) ?: return null)
        }
    }
}
