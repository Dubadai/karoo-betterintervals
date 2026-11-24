package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Entry point the Karoo system binds to. */
class IntervalExtension : KarooExtension(EXTENSION_ID, "1") {

    private lateinit var karoo: KarooSystemService
    private lateinit var workout: WorkoutSession
    private lateinit var erg: ErgController
    private lateinit var antFec: AntFecTrainer
    private lateinit var trainerSensor: TrainerSensor
    private var syncJob: Job? = null
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val types: List<DataTypeImpl> by lazy {
        listOf(IntervalDataType(extension, workout))
    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree(), RideLogTree(this))
        val store = PlanStore.get(this)
        karoo = KarooSystemService(this)
        workout = WorkoutSession(karoo, store)
        session = workout
        karoo.connect { connected -> Timber.i("Karoo system connected: $connected") }
        workout.start()
        antFec = AntFecTrainer(this)
        erg = ErgController(this, karoo, workout, store, antFec).also { it.start() }
        trainerSensor = TrainerSensor(this, extension, antFec, store) { ErgController.antTrainers(erg.devices) }

        // Not riding: tell the rider when a newer release is on GitHub (see UpdateCheck).
        UpdateCheck(this, karoo, appName = "BetterIntervals").start(updateScope)

        // Refresh this week's list in the background so it's ready when the rider picks a workout.
        if (store.hasCredentials) {
            syncJob = CoroutineScope(Dispatchers.IO).launch {
                store.syncWeek(karoo)
                    .onFailure { Timber.w(it, "Couldn't sync this week's workouts") }
            }
        }
    }

    /** Karoo Sensors › Add sensor: the trainer over the app's ANT+ connection (Connection › ANT+ only). */
    override fun startScan(emitter: Emitter<Device>) = trainerSensor.scan(emitter)

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) = trainerSensor.connect(uid, emitter)

    override fun onBonusAction(actionId: String) {
        when (actionId) {
            ACTION_NEXT -> workout.requestJump(+1, fromRemote = true)
            ACTION_PREVIOUS -> workout.requestJump(-1, fromRemote = true)
            ACTION_PAUSE -> workout.togglePause()
            else -> Timber.w("Unknown action $actionId")
        }
    }

    override fun onDestroy() {
        syncJob?.cancel()
        updateScope.cancel()
        erg.stop()
        workout.stop()
        session = null
        karoo.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "intervalscreen"
        const val ACTION_NEXT = "skip"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_PAUSE = "pause"

        /** Shared with [ActionReceiver], which runs in the same process. */
        @Volatile
        var session: WorkoutSession? = null
            private set
    }
}
