package com.daviddokakis.karoointerval

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
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

    fun start() {
        scope.launch { karoo.consumerFlow<ActiveRideProfile>().collect { indoor.value = it.profile.indoor } }
        scope.launch { karoo.consumerFlow<RideState>().collect { recording.value = it !is RideState.Idle } }
        scope.launch { karoo.consumerFlow<SavedDevices>().collect { match.value = trainerIn(it) } }
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
            if (trainer.link.value != ErgLink.OFF) { Timber.i("ERG: off"); trainer.stop() }
            workout.setErg(null, null)
            return
        }
        trainer.start(m)
        // Turned off on the screen: hand the trainer back, stay connected for the way back on.
        val cmd = planner.next(
            ErgInputs(indoor.value, !st.ergOffThisRide, recording.value, st, store.ergRecoveryPct.value),
            System.currentTimeMillis() / 1000,
        )
        if (pacer.due(cmd, System.currentTimeMillis())) {
            when (cmd) {
                is ErgCommand.Target -> trainer.setTarget(cmd.watts)
                ErgCommand.Release -> trainer.release()
            }
        }
        val status = if (st.ergOffThisRide) ErgStatus.OFF else when (trainer.link.value) {
            ErgLink.READY -> if (cmd is ErgCommand.Target) ErgStatus.HOLDING else ErgStatus.FREE
            ErgLink.LOST -> ErgStatus.LOST
            ErgLink.CONNECTING, ErgLink.OFF -> ErgStatus.CONNECTING
        }
        workout.setErg(status, (cmd as? ErgCommand.Target)?.watts)
    }

    fun stop() {
        trainer.stop()
        scope.cancel()
    }

    companion object {
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
