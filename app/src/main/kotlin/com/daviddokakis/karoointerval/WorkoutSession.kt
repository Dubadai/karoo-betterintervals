package com.daviddokakis.karoointerval

import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.MarkLap
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Runs the loaded workout: keeps time while the ride is recording, moves between steps,
 * handles the 5 s countdown when jumping back or forward, and collects power/HR for the screen.
 *
 * Karoo's own workout player isn't used, so steps can be changed in both directions.
 * All work runs on a single-threaded dispatcher, so the mutable fields need no locking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSession(
    private val karoo: KarooSystemService,
    private val store: PlanStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state

    private var recording = false
    private var elapsedInStepMs = 0L
    private var lastTick = 0L
    private var lapSum = 0.0
    private var lapSamples = 0
    private var countdownJob: Job? = null
    private var started = false

    /** Interval screens currently showing. While one is, it plays the countdown beeps (see [onCountdownShown]). */
    private val viewsActive = java.util.concurrent.atomic.AtomicInteger(0)

    fun viewStarted() { viewsActive.incrementAndGet() }
    fun viewStopped() { viewsActive.decrementAndGet() }

    /**
     * Called by the interval screen right after a new countdown number is on screen, so the beep
     * matches what the rider sees (Karoo only redraws the field about once a second).
     */
    fun onCountdownShown() {
        scope.launch { beep(TICK_TONE) }
    }

    /** Tapping the chart toggles between the whole workout and a zoom on the current interval. */
    fun toggleChartZoom() {
        scope.launch { _state.update { it.copy(chartZoomed = !it.chartZoomed) } }
    }

    fun start() {
        if (started) return
        started = true

        scope.launch {
            // Raw power feeds the lap average…
            karoo.streamDataFlow(DataType.Type.POWER).collect { onPower(it.single()) }
        }
        scope.launch {
            // …and the big number shows Karoo's 3-second average power.
            karoo.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_POWER).collect { s ->
                _state.update { it.copy(power3s = s.single()?.roundToInt()) }
            }
        }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.HEART_RATE).collect { s ->
                _state.update { it.copy(heartRate = s.single()?.roundToInt()) }
            }
        }
        scope.launch {
            karoo.consumerFlow<RideState>().collect { ride ->
                recording = ride is RideState.Recording
                _state.update { it.copy(paused = ride is RideState.Paused) }
                // No ride (not started, finished or discarded): start the workout from the top
                // next time. Progress is only kept while a ride is recording or paused.
                if (ride is RideState.Idle) resetToStart()
            }
        }
        scope.launch {
            karoo.consumerFlow<UserProfile>().collect { profile ->
                _state.update {
                    it.copy(
                        profileFtp = profile.ftp.takeIf { ftp -> ftp > 0 },
                        hrZones = profile.heartRateZones.map { z -> z.min..z.max },
                    )
                }
            }
        }
        // Karoo's lap data for the free-ride screen (times come in milliseconds).
        collectInt(DataType.Type.LAP_NUMBER) { v -> _state.update { it.copy(lapNumber = v) } }
        collectInt(DataType.Type.ELAPSED_TIME_LAP, divideBy = 1000) { v -> _state.update { it.copy(lapTimeSec = v) } }
        collectInt(DataType.Type.POWER_LAP) { v -> _state.update { it.copy(lapPowerAvg = v) } }
        collectInt(DataType.Type.ELAPSED_TIME_LAST_LAP, divideBy = 1000) { v -> _state.update { it.copy(lastLapTimeSec = v) } }
        collectInt(DataType.Type.AVERAGE_POWER_LAST_LAP) { v -> _state.update { it.copy(lastLapPowerAvg = v) } }
        scope.launch {
            store.plan.collect { plan -> loadPlan(plan) }
        }
        scope.launch { tickLoop() }
    }

    fun stop() {
        saveProgress()
        scope.cancel()
    }

    /** Ask to go [delta] steps (+1 next, -1 previous). Pressing the same direction again cancels. */
    fun requestJump(delta: Int) {
        scope.launch {
            val st = _state.value
            val pending = st.jumpTarget
            if (countdownJob?.isActive == true && pending != null) {
                val pendingDelta = pending - st.stepIndex
                cancelCountdown()
                if (pendingDelta == delta) {
                    beep(CANCEL_TONE)
                    return@launch
                }
            }
            val target = st.stepIndex + delta
            if (st.plan == null || target !in 0 until st.stepCount) return@launch
            startCountdown(target)
        }
    }

    /**
     * Pauses or resumes the interval timer (e.g. for traffic) so the time left in the interval
     * is kept. The ride keeps recording and the lap average keeps counting. Pausing the ride
     * itself (Karoo's own pause) also stops the interval timer.
     */
    /** Cancels a pending jump (tap on the countdown box). */
    fun cancelJump() {
        scope.launch {
            if (countdownJob?.isActive == true) {
                cancelCountdown()
                beep(CANCEL_TONE)
            }
        }
    }

    fun togglePause() {
        scope.launch {
            val nowPaused = !_state.value.workoutPaused
            if (nowPaused) cancelCountdown()
            _state.update { it.copy(workoutPaused = nowPaused) }
            beep(if (nowPaused) CANCEL_TONE else GO_TONE)
            Timber.i("Workout ${if (nowPaused) "paused" else "resumed"}")
        }
    }

    private fun loadPlan(plan: WorkoutPlan?) {
        cancelCountdown()
        resetLap()
        val (index, elapsedSec) = plan?.let { store.restoreProgress(it) } ?: (0 to 0)
        elapsedInStepMs = elapsedSec * 1000L
        _state.update {
            it.copy(plan = plan, stepIndex = index, elapsedInStepSec = elapsedSec, finished = false, lapAvg = null, workoutPaused = false)
        }
        Timber.i("Loaded workout ${plan?.name} at step $index + ${elapsedSec}s")
    }

    private fun resetToStart() {
        val st = _state.value
        if (st.plan == null || (st.stepIndex == 0 && elapsedInStepMs == 0L && !st.finished && !st.workoutPaused)) return
        cancelCountdown()
        resetLap()
        elapsedInStepMs = 0
        store.clearProgress()
        _state.update { it.copy(stepIndex = 0, elapsedInStepSec = 0, finished = false, lapAvg = null, workoutPaused = false) }
        Timber.i("No ride active: workout reset to the first interval")
    }

    private suspend fun tickLoop() {
        lastTick = SystemClock.elapsedRealtime()
        var sinceSave = 0L
        while (scope.isActive) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val dt = now - lastTick
            lastTick = now
            val st = _state.value
            val step = st.currentStep ?: continue
            if (!recording || st.finished || st.workoutPaused) continue

            val before = (step.durationSec * 1000L - elapsedInStepMs) / 1000
            elapsedInStepMs += dt
            val remainingMs = step.durationSec * 1000L - elapsedInStepMs
            val after = remainingMs / 1000

            // Warn with short beeps in the last 3 seconds, unless a jump countdown is beeping already.
            if (countdownJob?.isActive != true && after != before && after in 1..3) beep(WARN_TONE)

            if (remainingMs <= 0) {
                if (st.hasNext) {
                    goToStep(st.stepIndex + 1, natural = true)
                } else {
                    _state.update { it.copy(finished = true, elapsedInStepSec = step.durationSec) }
                    karoo.dispatch(MarkLap)
                    beep(GO_TONE)
                    store.clearProgress()
                }
            } else {
                _state.update { it.copy(elapsedInStepSec = (elapsedInStepMs / 1000).toInt()) }
            }

            sinceSave += dt
            if (sinceSave >= SAVE_EVERY_MS) {
                sinceSave = 0
                saveProgress()
            }
        }
    }

    private fun startCountdown(target: Int) {
        countdownJob = scope.launch {
            for (n in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = n, jumpTarget = target) }
                // With the screen showing, it beeps when the number appears; otherwise beep here.
                if (viewsActive.get() == 0) beep(TICK_TONE)
                delay(1000)
            }
            countdownJob = null
            goToStep(target, natural = false)
        }
    }

    private fun goToStep(index: Int, natural: Boolean) {
        // A step change (natural or not) clears any pending jump, so a forward jump that was
        // counting down when the interval ended doesn't skip the interval we just entered.
        if (natural) cancelCountdown()
        Timber.i("Step ${_state.value.stepIndex + 1} -> ${index + 1} (natural=$natural)")
        elapsedInStepMs = 0
        resetLap()
        _state.update {
            it.copy(stepIndex = index, elapsedInStepSec = 0, finished = false, lapAvg = null, countdown = null, jumpTarget = null)
        }
        karoo.dispatch(MarkLap) // one lap per interval in the ride file
        beep(GO_TONE)
        saveProgress()
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(countdown = null, jumpTarget = null) }
    }

    private fun resetLap() {
        lapSum = 0.0
        lapSamples = 0
    }

    private fun onPower(watts: Double?) {
        val st = _state.value
        // The lap average keeps counting while the workout is paused; only the interval timer stops.
        if (watts != null && recording && st.workoutLoaded && !st.finished) {
            lapSum += watts
            lapSamples++
        }
        _state.update {
            it.copy(
                power = watts?.roundToInt(),
                lapAvg = if (lapSamples > 0) (lapSum / lapSamples).roundToInt() else it.lapAvg,
            )
        }
    }

    private fun saveProgress() {
        val st = _state.value
        val plan = st.plan ?: return
        if (!st.finished) store.saveProgress(plan, st.stepIndex, st.elapsedInStepSec)
    }

    private fun collectInt(dataTypeId: String, divideBy: Int = 1, onValue: (Int?) -> Unit) {
        scope.launch {
            karoo.streamDataFlow(dataTypeId).collect { s -> onValue(s.single()?.let { (it / divideBy).roundToInt() }) }
        }
    }

    private fun beep(tone: PlayBeepPattern.Tone) {
        karoo.dispatch(PlayBeepPattern(listOf(tone)))
    }

    companion object {
        const val COUNTDOWN_SECONDS = 5
        private const val TICK_MS = 250L
        private const val SAVE_EVERY_MS = 15_000L

        private val TICK_TONE = PlayBeepPattern.Tone(frequency = 2000, durationMs = 100)
        private val WARN_TONE = PlayBeepPattern.Tone(frequency = 1500, durationMs = 80)
        private val GO_TONE = PlayBeepPattern.Tone(frequency = 3000, durationMs = 400)
        private val CANCEL_TONE = PlayBeepPattern.Tone(frequency = 1000, durationMs = 200)
    }
}
