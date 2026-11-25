package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.ReleaseAnt
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.RequestAnt
import io.hammerhead.karooext.models.RequestBluetooth
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
    private val context: Context,
    private val karoo: KarooSystemService,
    private val workout: WorkoutSession,
    private val store: PlanStore,
    /** The app's ANT+ connection, shared with the trainer sensor ([TrainerSensor]). */
    private val antFec: AntFecTrainer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bluetooth = ErgTrainer(context)
    /** The link in use: Settings › Trainer › Connection ([ErgConnection]). */
    private var trainer: TrainerLink = bluetooth
    /**
     * The radio we asked the Karoo to keep on for [trainer]. The Karoo turns Bluetooth off when no
     * Bluetooth sensor is on the profile (seen on a Karoo 3 with the trainer on ANT+ only).
     */
    private var radioHeld: TrainerLink? = null
    private val planner = ErgPlanner()
    private val pacer = ErgSendPacer()

    private val indoor = MutableStateFlow(false)
    private val recording = MutableStateFlow(false)
    private val match = MutableStateFlow<ErgTrainer.Match?>(null)
    /** The Karoo's saved sensors, latest. */
    @Volatile var devices: SavedDevices? = null
        private set

    // Match PM (docs/design/indoor-erg/powermatch): the Karoo's power, where it comes from, and the
    // matcher for this ride (made when recording starts, saved when it ends).
    @Volatile private var pmWatts: Int? = null
    @Volatile private var pmAtMs = 0L
    @Volatile private var pmSource: String? = null
    private var matcher: PmMatcher? = null
    private var pmName: String? = null
    private var pmIsTrainer = false
    private var loggedSource: String? = null
    private var afterFinish = false

    // The ERG lines in the ride log (files/ridelog.txt, debug/pull.sh): every LOG_EVERY_MS and on a change.
    private var logAtMs = 0L
    private var logKey: String? = null

    fun start() {
        scope.launch { karoo.consumerFlow<ActiveRideProfile>().collect { indoor.value = it.profile.indoor } }
        scope.launch { karoo.consumerFlow<RideState>().collect { onRecording(it !is RideState.Idle) } }
        scope.launch { karoo.consumerFlow<SavedDevices>().collect { devices = it; pickTrainer() } }
        scope.launch { store.ergConnection.collect { pickTrainer() } }
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

    private fun pickTrainer() {
        val d = devices ?: return
        val pick = trainerPick(d, store.ergConnection.value)
        if (pick?.match != match.value) Timber.i("ERG: trainer %s", pick)
        match.value = pick?.match
    }

    private fun tick() {
        val st = workout.state.value
        val m = match.value
        // A finished workout keeps the trainer free until the next one starts.
        // Outdoor profile, trainer sending the Karoo's power: the start screen says to pick an indoor
        // profile (docs/design/indoor-erg/trainer-hint).
        // Pedalling before the workout starts, with the Karoo's power coming from the trainer: shown
        // until the ride starts or the trainer stops sending.
        val src = pmSource
        workout.setTrainerOutdoor(
            !indoor.value && !recording.value && st.plan != null && st.notStarted &&
                m != null && src != null && isTrainer(src, m, devices) && System.currentTimeMillis() - pmAtMs < FRESH_MS,
        )
        if (st.finished) afterFinish = true else if (st.plan != null && !st.notStarted) afterFinish = false
        // Also without a workout while a ride records: ERG holds the When paused watts between workouts.
        val wanted = indoor.value && store.ergOn.value && (st.plan != null || recording.value)
        // Indoors with ERG on and a workout: say on the start screen what's wrong in the Karoo's sensors.
        workout.setErgSetupHint(
            devices?.takeIf { indoor.value && store.ergOn.value && st.plan != null }
                ?.let { ErgSetup.firstProblem(ErgSetup.check(it, store.ergConnection.value, hasBluetoothPermission())) }
                ?.let { context.getString(screenHint(it.kind)) },
        )
        if (!wanted || m == null) {
            if (trainer.link.value != ErgLink.OFF || radioHeld != null) {
                Timber.i("ERG: off (indoor=%s ergOn=%s plan=%s trainer=%s)", indoor.value, store.ergOn.value, st.plan != null, m != null)
                trainer.stop()
                holdRadio(null)
            }
            workout.setErg(null, null)
            return
        }
        val link = if (m.overAnt) antFec else bluetooth
        if (link !== trainer) { trainer.stop(); trainer = link }
        holdRadio(trainer)
        trainer.start(m)
        // Turned off on the screen: hand the trainer back, stay connected for the way back on.
        val cmd = planner.next(
            ErgInputs(indoor.value, !st.ergOffThisRide, recording.value, st, store.ergRecoveryPct.value, afterFinish),
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
            ErgLink.READY -> when {
                cmd is ErgCommand.Target -> ErgStatus.HOLDING
                st.notStarted -> ErgStatus.READY
                else -> ErgStatus.FREE
            }
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
        pmIsTrainer = source != null && isTrainer(source, m, devices)
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
            afterFinish = false
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

    private fun hasBluetoothPermission() = android.os.Build.VERSION.SDK_INT < 31 ||
        (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED)

    private fun screenHint(k: ErgSetup.Kind) = when (k) {
        ErgSetup.Kind.TRAINER_ON -> R.string.erg_hint_no_trainer
        ErgSetup.Kind.PERMISSION -> R.string.erg_hint_permission
        ErgSetup.Kind.BLUETOOTH_FREE -> R.string.erg_hint_bluetooth_taken
        ErgSetup.Kind.OUR_SENSOR_OFF -> R.string.erg_hint_our_sensor
        ErgSetup.Kind.TRAINER_KNOWN -> R.string.erg_hint_add_trainer
        ErgSetup.Kind.KAROO_ANT_OFF -> R.string.erg_hint_karoo_ant
        ErgSetup.Kind.POWER_RECORDED, ErgSetup.Kind.READ_ONLY -> R.string.erg_hint_add_sensor
    }

    private fun holdRadio(link: TrainerLink?) {
        if (link === radioHeld) return
        when (radioHeld) {
            bluetooth -> karoo.dispatch(ReleaseBluetooth(RADIO_ID))
            antFec -> karoo.dispatch(ReleaseAnt(RADIO_ID))
        }
        when (link) {
            bluetooth -> karoo.dispatch(RequestBluetooth(RADIO_ID))
            antFec -> karoo.dispatch(RequestAnt(RADIO_ID))
        }
        radioHeld = link
        Timber.i("ERG: radio %s", if (link === bluetooth) "Bluetooth" else if (link === antFec) "ANT" else "released")
    }

    fun stop() {
        saveMatch()
        trainer.stop()
        holdRadio(null)
        scope.cancel()
    }

    companion object {
        private const val FRESH_MS = 3_000L
        private const val RADIO_ID = "erg"
        private const val LOG_EVERY_MS = 5_000L

        /**
         * The Karoo's power comes from the trainer itself: an ANT+ id with its device number, its
         * address, the app's own trainer sensor ([TrainerSensor]), or the trainer's Bluetooth side,
         * which trainers name after the ANT+ number ("Tacx Neo 22877").
         */
        fun isTrainer(source: String, m: ErgTrainer.Match, devices: SavedDevices? = null): Boolean {
            if (m.antNumber != null && source.startsWith("${m.antNumber}-")) return true
            if (m.address != null && source.equals(m.address, ignoreCase = true)) return true
            val n = m.antNumber ?: return false
            if (TrainerSensor.numberOf(source) == n) return true
            val d = devices?.devices?.firstOrNull { it.id.equals(source, ignoreCase = true) } ?: return false
            return d.connectionType == "BLE" && FecBle.nameMatches(d.name, n)
        }

        /** The trainer ERG uses, its name for Settings, and what stands in the way. */
        data class TrainerPick(
            val name: String,
            val match: ErgTrainer.Match,
            /** ANT+: the Karoo is connected to this trainer over ANT+ itself, which leaves no room for ours. */
            val karooHoldsAnt: Boolean = false,
            /** ANT+: the app's trainer sensor is in the Karoo's list, so the Karoo records this connection. */
            val inKarooSensors: Boolean = false,
        )

        fun trainerIn(devices: SavedDevices, connection: ErgConnection = ErgConnection.BLUETOOTH): ErgTrainer.Match? =
            trainerPick(devices, connection)?.match

        /**
         * - **Bluetooth** (default): the trainer the Karoo uses, a sensor with trainer data that's
         *   switched on. Paired over Bluetooth: its address. Over ANT+: the device number (the id's
         *   first part, "28436-17-5"), found on the trainer's Bluetooth side by name.
         * - **ANT+**: the app's trainer sensor in the Karoo's list, else an ANT+ trainer paired on
         *   the Karoo (switched on or off). Its number, over the app's own ANT+ connection
         *   ([AntFecTrainer]). If the Karoo is connected to it over ANT+ itself, ours can't be.
         */
        fun trainerPick(devices: SavedDevices, connection: ErgConnection): TrainerPick? {
            val antTrainers = devices.devices.filter { "TYPE_TRAINER_ID" in it.supportedDataTypes && it.connectionType == "ANT_PLUS" && antNumberOf(it) != null }
            if (connection == ErgConnection.BLUETOOTH) {
                val t = devices.devices.firstOrNull { it.enabled && "TYPE_TRAINER_ID" in it.supportedDataTypes } ?: return null
                val m = when (t.connectionType) {
                    "BLE" -> ErgTrainer.Match(address = t.id)
                    "ANT_PLUS" -> antNumberOf(t)?.let { ErgTrainer.Match(antNumber = it) }
                    else -> null
                }
                return m?.let { TrainerPick(t.name, it) }
            }
            val ours = devices.devices.filter { TrainerSensor.numberOf(it.id) != null }.sortedByDescending { it.enabled }.firstOrNull()
            val n = ours?.let { TrainerSensor.numberOf(it.id) }
                ?: (antTrainers.firstOrNull { !it.enabled } ?: antTrainers.firstOrNull())?.let { antNumberOf(it) }
                ?: return null
            val native = antTrainers.filter { antNumberOf(it) == n }
            return TrainerPick(
                name = ours?.name ?: native.first().name,
                match = ErgTrainer.Match(antNumber = n, overAnt = true),
                karooHoldsAnt = native.any { it.enabled },
                inKarooSensors = ours != null,
            )
        }

        /** The ANT+ trainers paired on the Karoo: number and name (for the sensor scan). */
        fun antTrainers(devices: SavedDevices?): List<Pair<Int, String>> =
            devices?.devices.orEmpty()
                .filter { "TYPE_TRAINER_ID" in it.supportedDataTypes && it.connectionType == "ANT_PLUS" }
                .mapNotNull { d -> antNumberOf(d)?.let { it to d.name } }

        private fun antNumberOf(d: SavedDevices.SavedDevice): Int? = d.id.substringBefore('-').toIntOrNull()
    }
}
