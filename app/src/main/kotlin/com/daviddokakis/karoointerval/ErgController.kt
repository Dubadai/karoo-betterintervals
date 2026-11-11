package com.daviddokakis.karoointerval

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Indoor ERG: once a second turns the workout into a trainer target ([ErgPlanner]), writes it to
 * the trainer when due ([ErgSendPacer]) and shows how it's going on the screen (the ERG pill).
 *
 * On only with an indoor ride profile, Settings › Trainer on, a trainer paired on the Karoo and a
 * workout loaded. Every way out hands the trainer back.
 */
class ErgController(
    context: Context,
    private val karoo: KarooSystemService,
    private val workout: WorkoutSession,
    private val store: PlanStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val trainer = ErgTrainer(context)
    private val planner = ErgPlanner()
    private val pacer = ErgSendPacer()

    private val indoor = MutableStateFlow(false)
    private val recording = MutableStateFlow(false)
    private val match = MutableStateFlow<ErgTrainer.Match?>(null)
    private var devices: SavedDevices? = null

    // Match PM (docs/design/indoor-erg/powermatch): the Karoo's power, where it comes from, and the
    // matcher for this ride (made when recording starts, saved when it ends).
    @Volatile private var pmWatts: Int? = null
    @Volatile private var pmAtMs = 0L
    @Volatile private var pmSource: String? = null
    private var matcher: PmMatcher? = null
    private var pmName: String? = null
    private var pmIsTrainer = false
    private var loggedSource: String? = null

    // The ERG lines in the ride log (files/ridelog.txt, debug/pull.sh): every LOG_EVERY_MS and on a change.
    private var logAtMs = 0L
    private var logKey: String? = null

    fun start() {
        scope.launch { karoo.consumerFlow<ActiveRideProfile>().collect { indoor.value = it.profile.indoor } }
        scope.launch { karoo.consumerFlow<RideState>().collect { onRecording(it !is RideState.Idle) } }
        scope.launch { karoo.consumerFlow<SavedDevices>().collect { devices = it; match.value = trainerIn(it) } }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.POWER).collect { s ->
                val p = (s as? StreamState.Streaming)?.dataPoint ?: return@collect
                pmWatts = p.singleValue?.toInt(); pmAtMs = System.currentTimeMillis(); pmSource = p.sourceId
            }
        }
        scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    private fun tick() {
        val st = workout.state.value
        val m = match.value
        val wanted = indoor.value && store.ergOn.value && st.plan != null
        if (!wanted || m == null) {
            if (trainer.link.value != ErgLink.OFF) {
                Timber.i("ERG: off (indoor=%s ergOn=%s plan=%s trainer=%s)", indoor.value, store.ergOn.value, st.plan != null, m != null)
                trainer.stop()
            }
            workout.setErg(null, null)
            return
        }
        trainer.start(m)
        // Turned off on the screen: hand the trainer back, stay connected for the way back on.
        val cmd = planner.next(
            ErgInputs(indoor.value, !st.ergOffThisRide, recording.value, st, store.ergRecoveryPct.value),
            System.currentTimeMillis() / 1000,
        )
        val send = if (cmd is ErgCommand.Target) ErgCommand.Target(matched(cmd.watts, st, m)) else cmd
        if (pacer.due(send, System.currentTimeMillis())) {
            when (send) {
                is ErgCommand.Target -> trainer.setTarget(send.watts)
                ErgCommand.Release -> trainer.release()
            }
        }
        val status = if (st.ergOffThisRide) ErgStatus.OFF else when (trainer.link.value) {
            ErgLink.READY -> if (cmd is ErgCommand.Target) ErgStatus.HOLDING else ErgStatus.FREE
            ErgLink.LOST -> ErgStatus.LOST
            ErgLink.CONNECTING, ErgLink.OFF -> ErgStatus.CONNECTING
        }
        workout.setErg(status, (cmd as? ErgCommand.Target)?.watts)
        logTick(st, cmd, send, status)
    }

    /**
     * "ERG HOLDING plan=198 sent=204 trainer=203 karoo=199(28436-11-5) link=READY step=2 rec":
     * what the workout asks, what went to the trainer, what the trainer and the Karoo read.
     */
    private fun logTick(st: ScreenState, cmd: ErgCommand, send: ErgCommand, status: ErgStatus) {
        val now = System.currentTimeMillis()
        val plan = (cmd as? ErgCommand.Target)?.watts?.toString() ?: "release"
        val key = "$status $plan ${st.stepIndex} ${st.paused || st.workoutPaused} ${trainer.link.value}"
        if (key == logKey && now - logAtMs < LOG_EVERY_MS) return
        logKey = key; logAtMs = now
        val trainerW = trainer.watts?.takeIf { now - trainer.wattsAtMs < FRESH_MS }
        val karooW = pmWatts?.takeIf { now - pmAtMs < FRESH_MS }
        val flags = buildString {
            if (recording.value) append(" rec")
            if (st.paused) append(" ridePaused")
            if (st.workoutPaused) append(" workoutPaused")
            if (st.finished) append(" finished")
            if (st.ergOffThisRide) append(" offThisRide")
            if (store.matchPm.value) append(if (pmIsTrainer) " matchPm=trainerIsPm" else " matchPm=" + "%.3f".format(java.util.Locale.US, matcher?.ratioFor(PmMatcher.bandOf((cmd as? ErgCommand.Target)?.watts ?: 0)) ?: 1.0))
        }
        Timber.i(
            "ERG %s plan=%s sent=%s trainer=%s karoo=%s(%s) link=%s step=%d%s",
            status, plan, (send as? ErgCommand.Target)?.watts ?: "-", trainerW ?: "-", karooW ?: "-", pmSource ?: "?",
            trainer.link.value, st.stepIndex, flags,
        )
    }

    /** Match PM: the watts to send for the planner's [target]; the target as is when it's off. */
    private fun matched(target: Int, st: ScreenState, m: ErgTrainer.Match): Int {
        val mm = matcher ?: return target
        if (!store.matchPm.value) return target
        val now = System.currentTimeMillis()
        val source = pmSource
        if (source != loggedSource) { loggedSource = source; Timber.i("Match PM: Karoo power from %s", source) }
        pmIsTrainer = source != null && isTrainer(source, m)
        if (pmIsTrainer) return target
        if (source != null) devices?.devices?.firstOrNull { it.id == source }?.name?.let { pmName = it }
        val steady = !st.paused && !st.workoutPaused && !st.finished && st.currentStep?.isRamp != true
        val trainerW = trainer.watts?.takeIf { now - trainer.wattsAtMs < FRESH_MS }
        val pm = pmWatts?.takeIf { now - pmAtMs < FRESH_MS }
        return mm.next(target, steady, st.stepIndex, trainerW, pm, now / 1000)
    }

    private fun onRecording(on: Boolean) {
        if (on == recording.value) return
        recording.value = on
        if (on) {
            matcher = PmMatcher(PmMatcher.decode(store.pmBands))
            pmIsTrainer = false; pmName = null
        } else saveMatch()
    }

    /** End of a ride: the band table for the next one, and what it saw for Settings. */
    private fun saveMatch() {
        val mm = matcher ?: return
        matcher = null
        if (!store.matchPm.value) return
        if (pmIsTrainer) { store.setPmLastRide(PmLastRide(fromTrainer = true)); return }
        val summary = mm.summary() ?: return
        store.pmBands = PmMatcher.encode(mm.bands())
        store.setPmLastRide(PmLastRide(pmName = pmName, summary = summary))
        Timber.i("Match PM: saved %s", summary)
    }

    fun stop() {
        saveMatch()
        trainer.stop()
        scope.cancel()
    }

    companion object {
        private const val FRESH_MS = 3_000L
        private const val LOG_EVERY_MS = 5_000L

        /** The Karoo's power comes from the trainer itself: an ANT+ id with its device number, or its address. */
        fun isTrainer(source: String, m: ErgTrainer.Match): Boolean =
            (m.antNumber != null && source.startsWith("${m.antNumber}-")) ||
                (m.address != null && source.equals(m.address, ignoreCase = true))

        /**
         * The trainer paired on the Karoo: an enabled sensor with trainer data. Bluetooth: its
         * address. ANT+: the device number (the id's first part, "28436-17-5").
         */
        fun trainerIn(devices: SavedDevices): ErgTrainer.Match? {
            val t = devices.devices.firstOrNull { it.enabled && "TYPE_TRAINER_ID" in it.supportedDataTypes } ?: return null
            return when (t.connectionType) {
                "BLE" -> ErgTrainer.Match(address = t.id)
                "ANT_PLUS" -> t.id.substringBefore('-').toIntOrNull()?.let { ErgTrainer.Match(antNumber = it) }
                else -> null
            }
        }
    }
}
