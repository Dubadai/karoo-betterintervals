package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*

import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.MarkLap
import io.hammerhead.karooext.models.PauseRide
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.PerformHardwareAction
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    // One thread, so the mutable fields need no locking; passed in so tests can use a test dispatcher
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    // Milliseconds since boot; passed in so tests can run the interval timer on a fake clock
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state

    private var recording = false
    /** Known once the Karoo has said: no ride is active (not started, finished or discarded). */
    private var rideIdle = false
    private var elapsedInStepMs = 0L
    private var lastTick = 0L
    private var lapSum = 0.0
    private var lapSamples = 0
    private val lapNp = LapNp()
    private val freeLapNp = LapNp()

    // The workout's own numbers for the done screen: only while it runs (started, not paused,
    // ride recording), so riding before START or a traffic pause doesn't count.
    private var workoutMs = 0L
    private var workoutPowerSum = 0.0
    private var workoutPowerSamples = 0
    private val workoutNp = LapNp()
    private var workoutHrSum = 0L
    private var workoutHrSamples = 0
    private var workoutCadSum = 0L
    private var workoutCadSamples = 0
    private var workoutMaxPower = 0
    /** The ride's calories when the workout started (its own calories are what's added since). */
    private var caloriesAtStart: Int? = null

    private fun workoutRunning(st: ScreenState = _state.value) =
        recording && st.plan != null && !st.finished && !st.workoutPaused && !st.notStarted

    private fun resetWorkoutStats() {
        workoutMs = 0
        workoutPowerSum = 0.0
        workoutPowerSamples = 0
        workoutNp.reset()
        workoutHrSum = 0
        workoutHrSamples = 0
        workoutCadSum = 0
        workoutCadSamples = 0
        workoutMaxPower = 0
        caloriesAtStart = _state.value.calories
        _state.update { it.copy(summary = null, live = null) }
    }

    private fun summary() = WorkoutSummary(
        timeSec = (workoutMs / 1000).toInt(),
        avgPower = if (workoutPowerSamples > 0) (workoutPowerSum / workoutPowerSamples).roundToInt() else null,
        np = workoutNp.value,
        avgHr = if (workoutHrSamples > 0) (workoutHrSum / workoutHrSamples).toInt() else null,
        avgCadence = if (workoutCadSamples > 0) (workoutCadSum / workoutCadSamples).toInt() else null,
        maxPower = workoutMaxPower.takeIf { it > 0 },
        calories = _state.value.calories?.let { now -> (now - (caloriesAtStart ?: now)).coerceAtLeast(0) },
    )
    private var countdownJob: Job? = null
    /** The ending countdown's last number shown (or beeped), so each second beeps once. */
    private var lastEnding: Int? = null
    /** The workout the running countdown belongs to: it lands only there. */
    private var countdownPlanId: String? = null
    /** Plan changes seen since start: the first is the app (re)starting, later ones the rider choosing. */
    private var plansSeen = 0
    /** Jump being chosen (3 s confirmation), see [jumpStops]. */
    private var selectionPos: Int? = null
    private var selectJob: Job? = null
    private var autoConfirmJob: Job? = null
    private var moveCancelledJob: Job? = null
    private var started = false
    /** The workout as loaded (100 %); the state's plan is it at Workouts › Scale workout. */
    private var basePlan: WorkoutPlan? = null
    /** A changed version of the running workout from a sync mid-ride: loads when the ride ends. */
    private var heldPlan: WorkoutPlan? = null
    /** Keep current workout was picked for [heldPlan]: don't ask again this ride. */
    private var heldDismissed = false
    /** The Karoo ride started with the start countdown (START with no ride, or the Karoo's own start indoors). */
    private var rideStartsWithWorkout = false

    /** Interval screens currently showing. While one is, it plays the countdown beeps (see [onCountdownShown]). */
    private val viewsActive = java.util.concurrent.atomic.AtomicInteger(0)

    /** Tap on the ERG pill: ERG off (ride free) or back on, for this ride. */
    fun toggleErg() {
        scope.launch { _state.update { it.copy(ergOffThisRide = !it.ergOffThisRide) } }
    }

    /** The ERG pill (from [ErgController]); only redraws when it changes. */
    fun setErg(status: ErgStatus?, watts: Int?) {
        val st = _state.value
        if (st.erg != status || st.ergWatts != watts) _state.update { it.copy(erg = status, ergWatts = watts) }
    }

    /** A trainer is sending power on an outdoor profile (from [ErgController]). */
    fun setTrainerOutdoor(on: Boolean) {
        if (_state.value.trainerOutdoor != on) _state.update { it.copy(trainerOutdoor = on) }
    }

    fun viewStarted() { viewsActive.incrementAndGet() }
    fun viewStopped() { viewsActive.decrementAndGet() }

    /**
     * Called by the interval screen right after a new countdown number is on screen, so the beep
     * matches what the rider sees (Karoo only redraws the field about once a second).
     */
    fun onCountdownShown() {
        scope.launch { beep(TICK_TONE) }
    }

    /** Tapping the watts cycles plain → coloured number → coloured background → both. */
    fun toggleWattsColor() {
        scope.launch {
            val mode = _state.value.wattsMode.next()
            store.wattsMode = mode
            _state.update { it.copy(wattsMode = mode) }
            Timber.i("Watts mode -> $mode")
        }
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
            // …and the big number shows instant power or Karoo's 3 / 5 / 10 s average (Settings › Power).
            store.powerSmoothing.flatMapLatest { karoo.streamDataFlow(it.power) }.collect { s ->
                _state.update { it.copy(powerShown = s.single()?.roundToInt(), powerSensor = SensorState.of(s), sampleSeq = it.sampleSeq + 1) }
            }
        }
        scope.launch {
            // Cadence, for when it replaces heart rate (Settings › Cadence picks the smoothing).
            store.cadenceSmoothing.flatMapLatest { karoo.streamDataFlow(it.cadence) }.collect { s ->
                val cad = s.single()?.roundToInt()
                if (cad != null && cad > 0 && workoutRunning()) { workoutCadSum += cad; workoutCadSamples++ }
                _state.update { it.copy(cadence = cad, cadenceSensor = SensorState.of(s)) }
            }
        }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.HEART_RATE).collect { s ->
                val hr = s.single()?.roundToInt()
                if (hr != null && hr > 0 && workoutRunning()) { workoutHrSum += hr; workoutHrSamples++ }
                _state.update { it.copy(heartRate = hr, hrSensor = SensorState.of(s), sampleSeq = if (it.hrStep) it.sampleSeq + 1 else it.sampleSeq) }
            }
        }
        scope.launch {
            karoo.consumerFlow<RideState>().collect { ride ->
                val wasIdle = rideIdle
                val wasPaused = _state.value.paused
                recording = ride is RideState.Recording
                rideIdle = ride is RideState.Idle
                _state.update { it.copy(paused = ride is RideState.Paused, autoPaused = (ride as? RideState.Paused)?.auto == true, rideIdle = rideIdle) }
                // Indoors the Karoo's start button starts the workout too: a ride that starts with a
                // workout waiting on its start screen begins it (the usual 5 s countdown). Not when
                // START already did (its countdown runs), nor for a workout loaded mid-ride.
                val st = _state.value
                // The Karoo's play button on the workout summary (the ride paused at the workout's
                // end): the summary closes and the ride goes on in free ride, no workout selected.
                if (wasPaused && recording && st.finished && st.plan != null) {
                    Timber.i("Ride resumed on the summary: closing it")
                    closeWorkout()
                }
                if (wasIdle && recording && st.indoor && st.plan != null && st.notStarted && countdownJob?.isActive != true) {
                    Timber.i("Karoo ride started indoors: the workout starts with it")
                    rideStartsWithWorkout = true
                    startCountdown(st.stepIndex)
                }
                // No ride (not started, finished or discarded): start the workout from the top
                // next time. Progress is only kept while a ride is recording or paused.
                if (ride is RideState.Idle) {
                    resetToStart()
                    heldDismissed = false
                    _state.update { it.copy(planUpdateWaiting = false, infoOpen = false) }
                    heldPlan?.let { newer ->
                        heldPlan = null
                        Timber.i("Ride over: loading the changed version of ${newer.name}")
                        basePlan = newer
                        loadPlan(newer.scaled(store.scalePct.value))
                    }
                }
            }
        }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.LOCATION).collect { gps ->
                val searching = gps is StreamState.Searching
                if (searching != _state.value.gpsSearching) _state.update { it.copy(gpsSearching = searching) }
            }
        }
        // Navigating (a route or a destination): the Karoo shows its route pill and makes the field
        // shorter without telling the app, so the gauge is sized for the shorter field.
        scope.launch {
            karoo.consumerFlow<OnNavigationState>().collect { nav ->
                _state.update { it.copy(navigating = nav.state !is OnNavigationState.NavigationState.Idle) }
            }
        }
        scope.launch {
            karoo.consumerFlow<ActiveRideProfile>().collect { p ->
                if (p.profile.indoor != _state.value.indoor) _state.update { it.copy(indoor = p.profile.indoor) }
            }
        }
        scope.launch {
            karoo.consumerFlow<UserProfile>().collect { profile ->
                store.hrZones = profile.heartRateZones.map { z -> z.min..z.max }
                _state.update {
                    it.copy(
                        profileFtp = profile.ftp.takeIf { ftp -> ftp > 0 },
                        imperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                        hrZones = profile.heartRateZones.map { z -> z.min..z.max },
                    )
                }
            }
        }
        // Karoo's lap data for the free-ride screen (times come in milliseconds).
        collectInt(DataType.Type.LAP_NUMBER) { v ->
            if (v != _state.value.lapNumber) freeLapNp.reset()
            _state.update { it.copy(lapNumber = v, freeLapNp = freeLapNp.value) }
        }
        collectInt(DataType.Type.ELAPSED_TIME_LAP, divideBy = 1000) { v -> _state.update { it.copy(lapTimeSec = v) } }
        collectInt(DataType.Type.POWER_LAP) { v -> _state.update { it.copy(lapPowerAvg = v) } }
        collectInt(DataType.Type.ELAPSED_TIME_LAST_LAP, divideBy = 1000) { v -> _state.update { it.copy(lastLapTimeSec = v) } }
        collectInt(DataType.Type.AVERAGE_POWER_LAST_LAP) { v -> _state.update { it.copy(lastLapPowerAvg = v) } }
        collectInt(DataType.Type.AVERAGE_LAP_HR) { v -> _state.update { it.copy(lapHrAvg = v) } }
        collectInt(DataType.Type.AVERAGE_HR_LAST_LAP) { v -> _state.update { it.copy(lastLapHrAvg = v) } }
        // The whole ride so far, for free ride's tiles
        collectInt(DataType.Type.AVERAGE_POWER) { v -> _state.update { it.copy(rideAvgPower = v) } }
        collectInt(DataType.Type.AVERAGE_HR) { v -> _state.update { it.copy(rideAvgHr = v) } }
        // The right tile's other metrics (Settings › Right tile)
        collectInt(DataType.Type.CALORIES) { v -> _state.update { it.copy(calories = v) } }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.SPEED).collect { s -> _state.update { it.copy(speedMs = s.single()) } }
        }
        scope.launch {
            store.plan.collect { plan ->
                // The running workout changed on intervals.icu (a sync on the Workouts page mid-ride):
                // keep riding the version that's running; the new one loads when the ride ends.
                val st = _state.value
                if (plan != null && st.plan?.id == plan.id && !rideIdle && !st.notStarted) {
                    Timber.i("Workout ${plan.name} changed on intervals.icu: the running version carries on until the ride ends")
                    heldPlan = plan
                    if (!heldDismissed) _state.update { it.copy(planUpdateWaiting = true) }
                    return@collect
                }
                heldPlan = null
                heldDismissed = false
                _state.update { it.copy(planUpdateWaiting = false) }
                basePlan = plan
                loadPlan(plan?.scaled(store.scalePct.value), reselected = plansSeen++ > 0)
            }
        }
        // Workouts › Scale workout: the same workout with its power targets scaled, without
        // restarting it (where you are in it, the lap and the countdown all carry on).
        scope.launch {
            store.scalePct.collect { pct ->
                _state.update { st ->
                    val base = basePlan
                    if (base != null && st.plan?.id == base.id) st.copy(plan = base.scaled(pct)) else st
                }
            }
        }
        scope.launch { tickLoop() }
        scope.launch { store.appearance.collect { a -> _state.update { it.copy(appearance = a) } } }
        scope.launch { store.powerSmoothing.collect { p -> _state.update { it.copy(powerSmoothing = p) } } }
        scope.launch { store.tileOutline.collect { on -> _state.update { it.copy(tileOutline = on) } } }
        scope.launch { store.topRow.collect { r -> _state.update { it.copy(topRow = r) } } }
        scope.launch { store.topRowRight.collect { r -> _state.update { it.copy(topRowRight = r) } } }
        scope.launch { store.zonePalette.collect { z -> _state.update { it.copy(zonePalette = z) } } }
        scope.launch { store.leftItems.collect { l -> _state.update { it.copy(leftItems = l, leftIndex = store.leftIndex.coerceIn(0, l.lastIndex.coerceAtLeast(0))) } } }
        scope.launch { store.rightItems.collect { r -> _state.update { it.copy(rightItems = r, rightIndex = store.rightIndex.coerceIn(0, r.lastIndex.coerceAtLeast(0))) } } }
        scope.launch { store.topRowNone.collect { n -> _state.update { it.copy(topRowNone = n) } } }
        scope.launch { store.underWatts.collect { u -> _state.update { it.copy(underWatts = u) } } }
        _state.update { it.copy(wattsMode = store.wattsMode, targetShowsRange = store.targetShowsRange, freePower = store.freePower, freeSecond = store.freeSecond, hrTargetLthr = store.hrTargetLthr) }
    }

    fun stop() {
        saveProgress()
        scope.cancel()
    }

    /**
     * ⏭ ([delta] +1) / ⏮ (-1): move the jump target one stop. Stops, in order:
     * …, previous intervals, "restart the current interval", "stay" (current, nothing happens),
     * next intervals, …  Nothing moves until "Confirm move" (the choice cancels itself after
     * [SELECT_TIMEOUT_MS]); then the 5 s countdown runs. A tap on the countdown starts the chosen
     * interval right away, its ✕ cancels.
     *
     * [fromRemote]: a remote button press confirms by itself after [AUTO_CONFIRM_SECONDS] (each
     * press starts the wait over). On-screen taps still need "Confirm move".
     */
    fun requestJump(delta: Int, fromRemote: Boolean = false) {
        scope.launch {
            val st = _state.value
            if (st.plan == null) return@launch
            if (st.notStarted) {
                // Ready screen: ⏮ / ⏭ pick the step to start from, no confirm needed (and stop a
                // start countdown that's running).
                cancelCountdown()
                val index = startStepAfter(st.stepIndex, delta, st.stepCount)
                if (index != st.stepIndex) _state.update { it.copy(stepIndex = index, elapsedInStepSec = 0, resumeFromSec = null) }
                Timber.i("Start step -> ${index + 1}")
                return@launch
            }
            if (countdownJob?.isActive == true) {
                jumpNow()
                return@launch
            }
            // The interval's own ending countdown gives way to choosing a jump
            if (st.countdown != null) _state.update { it.copy(countdown = null, jumpTarget = null) }
            val stops = jumpStops(st.stepIndex, st.stepCount)
            val stay = stops.indexOf(st.stepIndex * 2)
            val from = selectionPos?.let { stops.indexOf(it) }?.takeIf { it >= 0 } ?: stay
            val to = (from + delta).coerceIn(0, stops.lastIndex)
            Timber.i("Jump tap delta=$delta current=${st.stepIndex} selection=$selectionPos from=${stops[from]} to=${stops[to]}")
            if (to == stay) {
                cancelSelection() // back where we are: nothing happens
            } else if (to != from || selectionPos == null) {
                select(stops[to])
                if (fromRemote) startAutoConfirm() else stopAutoConfirm()
            } else if (fromRemote) {
                startAutoConfirm() // pressed again at the end of the list: wait starts over
            }
        }
    }

    /** Tap on the middle of the box while choosing: never mind, stay where we are. */
    fun cancelSelection() {
        scope.launch {
            Timber.i("Selection cancelled")
            stopAutoConfirm()
            selectJob?.cancel()
            selectJob = null
            selectionPos = null
            _state.update { it.copy(selectTarget = null, selectRestart = false, selectProgress = 0) }
        }
    }

    /** Tap on the countdown: skip the rest of it and start the chosen interval now. */
    fun jumpNow() {
        scope.launch {
            Timber.i("Jump now tapped (countdown running: ${countdownJob?.isActive})")
            val target = _state.value.jumpTarget ?: return@launch
            if (countdownJob?.isActive != true) return@launch
            countdownJob?.cancel()
            countdownJob = null
            val landing = _state.value.countdownLanding(countdownPlanId, target)
            if (landing == null) {
                Timber.w("Countdown to step ${target + 1} dropped: the workout changed")
                _state.update { it.copy(countdown = null, jumpTarget = null) }
            } else if (_state.value.notStarted) beginWorkout(landing) else goToStep(landing, natural = false)
        }
    }

    /** Positions: interval i is 2·i; "restart current" is 2·current − 1. */
    private fun jumpStops(current: Int, count: Int): List<Int> =
        (0 until count).flatMap { i -> if (i == current) listOf(2 * i - 1, 2 * i) else listOf(2 * i) }

    private fun select(pos: Int) {
        Timber.i("Select pos=$pos")
        moveCancelledJob?.cancel()
        moveCancelledJob = null
        _state.update { it.copy(moveCancelled = false) }
        selectionPos = pos
        val target = (pos + 1) / 2
        val restart = pos % 2 != 0
        _state.update { it.copy(selectTarget = target, selectRestart = restart, selectProgress = 1) }
        // Nothing moves until "Confirm move". Untouched, the choice quietly cancels.
        selectJob?.cancel()
        selectJob = scope.launch {
            delay(SELECT_TIMEOUT_MS)
            Timber.i("Selection timed out")
            stopAutoConfirm()
            selectJob = null
            selectionPos = null
            _state.update { it.copy(selectTarget = null, selectRestart = false, selectProgress = 0) }
        }
    }

    /** Remote choice: fill "Confirm move" over [AUTO_CONFIRM_SECONDS], then confirm it. */
    private fun startAutoConfirm() {
        autoConfirmJob?.cancel()
        autoConfirmJob = scope.launch {
            for (n in AUTO_CONFIRM_SECONDS downTo 1) {
                _state.update { it.copy(autoConfirmLeft = n) }
                delay(1000)
            }
            autoConfirmJob = null
            val target = _state.value.selectTarget ?: return@launch
            Timber.i("Selection auto-confirmed -> countdown to ${target + 1}")
            clearSelection()
            startCountdown(target)
        }
    }

    private fun stopAutoConfirm() {
        autoConfirmJob?.cancel()
        autoConfirmJob = null
        _state.update { it.copy(autoConfirmLeft = null) }
    }

    /** "Confirm move": lock in the chosen interval and start the 5 s countdown. */
    fun confirmSelection() {
        scope.launch {
            val target = _state.value.selectTarget ?: return@launch
            Timber.i("Selection confirmed -> countdown to ${target + 1}")
            clearSelection()
            startCountdown(target)
        }
    }

    /** ✕ next to "Confirm move": drop the choice, nothing moves, show "Move cancelled" for a moment. */
    fun cancelMove() {
        scope.launch {
            if (_state.value.selectTarget == null) return@launch
            Timber.i("Move cancelled")
            clearSelection()
            beep(CANCEL_TONE)
            showMoveCancelled()
        }
    }

    /** Tap on "Move cancelled": hide it right away. */
    fun dismissMoveCancelled() {
        scope.launch {
            moveCancelledJob?.cancel()
            moveCancelledJob = null
            _state.update { it.copy(moveCancelled = false) }
        }
    }

    /** Tap on the left tile: the next metric switched on in Settings › Workout screen › Left tile. */
    fun cycleLeftTile() {
        scope.launch {
            val items = _state.value.leftItems
            if (items.size < 2) return@launch
            val next = (_state.value.leftIndex + 1) % items.size
            store.leftIndex = next
            _state.update { it.copy(leftIndex = next) }
        }
    }

    /** Tap on the right tile: the next metric switched on in Settings › Workout screen › Right tile. */
    fun cycleRightTile() {
        scope.launch {
            val items = _state.value.rightItems
            if (items.size < 2) return@launch
            val next = (_state.value.rightIndex + 1) % items.size
            store.rightIndex = next
            _state.update { it.copy(rightIndex = next) }
        }
    }

    /** ⓘ on the workout screen: the whole workout so far as tiles; a tap anywhere on it closes it. */
    fun toggleInfo() {
        scope.launch {
            val open = !_state.value.infoOpen
            _state.update { it.copy(infoOpen = open, live = if (open) summary() else it.live) }
        }
    }

    /** The running workout changed on intervals.icu: keep riding the version that's running. */
    fun keepCurrentPlan() {
        scope.launch {
            Timber.i("Workout changed on intervals.icu: keeping the running version")
            heldDismissed = true
            _state.update { it.copy(planUpdateWaiting = false) }
        }
    }

    /**
     * The running workout changed on intervals.icu: ride the new version. From where you are (the
     * interval, the lap and the workout's numbers carry on), or with [restart] from the first
     * interval after the start countdown.
     */
    fun useNewPlan(restart: Boolean) {
        scope.launch {
            val newer = heldPlan ?: return@launch
            heldPlan = null
            heldDismissed = false
            basePlan = newer
            val scaled = newer.scaled(store.scalePct.value)
            if (restart) {
                Timber.i("Workout changed on intervals.icu: restarting with the new version")
                cancelCountdown()
                clearSelection()
                resetLap()
                resetWorkoutStats()
                elapsedInStepMs = 0
                store.clearProgress()
                _state.update { it.copy(plan = scaled, stepIndex = 0, elapsedInStepSec = 0, notStarted = true, resumeFromSec = null, finished = false, planUpdateWaiting = false, infoOpen = false) }
                startCountdown(0)
            } else {
                Timber.i("Workout changed on intervals.icu: new targets from here on")
                _state.update { st ->
                    val index = st.stepIndex.coerceAtMost(scaled.steps.lastIndex)
                    st.copy(plan = scaled, stepIndex = index, planUpdateWaiting = false)
                }
                saveProgress()
            }
        }
    }

    /** Free ride: tap on the HR tile switches between the current heart rate and the lap's average. */
    /** Free ride's HR tile: now → the lap's average → the ride's average → now. */
    fun cycleFreeSecond() {
        scope.launch {
            val next = _state.value.freeSecond.next()
            store.freeSecond = next
            _state.update { it.copy(freeSecond = next) }
        }
    }

    /** Free ride's power tile: the lap's average → the lap's NP → the ride's average → back. */
    fun cycleFreePower() {
        scope.launch {
            val next = _state.value.freePower.next()
            store.freePower = next
            _state.update { it.copy(freePower = next) }
        }
    }

    /**
     * Tap on the Target tile: switch between the target and the target range, or on a heart-rate
     * step between bpm and % of LTHR.
     */
    fun toggleTargetView() {
        scope.launch {
            if (_state.value.hrStep) {
                val lthr = !_state.value.hrTargetLthr
                store.hrTargetLthr = lthr
                _state.update { it.copy(hrTargetLthr = lthr) }
                return@launch
            }
            val range = !_state.value.targetShowsRange
            store.targetShowsRange = range
            _state.update { it.copy(targetShowsRange = range) }
        }
    }

    private fun showMoveCancelled() {
        moveCancelledJob?.cancel()
        _state.update { it.copy(moveCancelled = true) }
        moveCancelledJob = scope.launch {
            delay(MOVE_CANCELLED_MS)
            moveCancelledJob = null
            _state.update { it.copy(moveCancelled = false) }
        }
    }

    /**
     * Pauses or resumes the interval timer (e.g. for traffic) so the time left in the interval
     * is kept. The ride keeps recording and the lap average keeps counting. Pausing the ride
     * itself (Karoo's own pause) also stops the interval timer.
     */
    fun togglePause() {
        scope.launch {
            if (_state.value.notStarted) {
                // The remote's pause button works like START: starts the countdown, or cancels it.
                startWorkout()
                return@launch
            }
            if (_state.value.finished) {
                closeWorkout() // …and like DONE on the workout done screen
                return@launch
            }
            val nowPaused = !_state.value.workoutPaused
            if (nowPaused) {
                cancelCountdown()
                clearSelection()
            }
            _state.update { it.copy(workoutPaused = nowPaused) }
            beep(if (nowPaused) CANCEL_TONE else GO_TONE)
            Timber.i("Workout ${if (nowPaused) "paused" else "resumed"}")
        }
    }

    /**
     * START WORKOUT (or the remote's pause button on the ready screen): the usual 5 s countdown,
     * then the workout runs from the chosen step. Tapped again while counting: cancel (a tap on
     * the countdown box starts right away instead).
     */
    fun startWorkout() {
        scope.launch {
            val st = _state.value
            if (st.plan == null || !st.notStarted) return@launch
            if (countdownJob?.isActive == true) {
                Timber.i("Start countdown cancelled")
                cancelCountdown()
                beep(CANCEL_TONE)
                return@launch
            }
            Timber.i("Start countdown, step ${st.stepIndex + 1}${st.resumeFromSec?.let { " + ${it}s (continue)" } ?: ""}")
            // One press starts both: no ride yet, so START also starts the Karoo's ride, as its
            // bottom-right button (the ▶ on the ride screen) would. Only with no ride at all: on a
            // recording or paused ride that button would pause or resume it instead.
            rideStartsWithWorkout = rideIdle
            if (rideIdle) {
                Timber.i("Starting the Karoo ride with the workout")
                karoo.dispatch(PerformHardwareAction.BottomRightPress)
            }
            startCountdown(st.stepIndex)
        }
    }

    /** START OVER on the start screen (a workout selected again mid-ride): from the first interval. */
    fun startOver() {
        scope.launch {
            val st = _state.value
            if (st.plan == null || !st.notStarted || st.resumeFromSec == null) return@launch
            cancelCountdown()
            store.clearProgress()
            _state.update { it.copy(stepIndex = 0, resumeFromSec = null) }
            Timber.i("Start over: countdown from step 1")
            startCountdown(0)
        }
    }

    /** End of the start countdown: the workout runs, with a happy little tune. */
    private fun beginWorkout(index: Int) {
        // CONTINUE: back where it was in this interval, if it's still the one it left off in
        val resume = _state.value.resumeFromSec?.takeIf { _state.value.stepIndex == index } ?: 0
        Timber.i("Workout started at step ${index + 1}" + if (resume > 0) " + ${resume}s" else "")
        elapsedInStepMs = resume * 1000L
        resetLap()
        resetWorkoutStats()
        _state.update {
            it.copy(notStarted = false, stepIndex = index, elapsedInStepSec = resume, lapAvg = null, countdown = null, jumpTarget = null, resumeFromSec = null)
        }
        // The first interval gets its own lap, except when the ride started with this countdown:
        // then the countdown's 5 s would be a lap of their own (they count in the first interval's).
        if (recording && !rideStartsWithWorkout) karoo.dispatch(MarkLap)
        rideStartsWithWorkout = false
        karoo.dispatch(PlayBeepPattern(START_TUNE))
        saveProgress()
    }

    /** DONE on the workout done screen: take the workout off; the ride carries on (free-ride screen). */
    fun closeWorkout() {
        scope.launch {
            if (!_state.value.finished) return@launch
            Timber.i("Workout done, closed")
            store.clearProgress()
            store.clearSelection()
        }
    }

    /**
     * [reselected]: the rider removed the workout and selected it again in this ride, so a workout
     * already under way asks CONTINUE or START OVER. After an app restart it carries on by itself.
     */
    private fun loadPlan(plan: WorkoutPlan?, reselected: Boolean = false) {
        // A switch mid-ride: the old workout's jump (being chosen, confirming or counting down)
        // must never land in the new one.
        val before = _state.value.plan
        if (before != null && before.id != plan?.id) Timber.i("Workout switched: ${before.name} -> ${plan?.name ?: "none"}")
        cancelCountdown()
        clearSelection()
        lastEnding = null
        moveCancelledJob?.cancel()
        moveCancelledJob = null
        resetLap()
        resetWorkoutStats()
        // Progress only carries over a restart mid-ride; with no ride active, start from the top.
        if (rideIdle) store.clearProgress()
        // Finished earlier in this ride and not closed with DONE: back to the Workout done screen
        val done = if (rideIdle) null else plan?.let { store.restoreFinished(it) }
        if (plan != null && done != null) {
            elapsedInStepMs = plan.steps.last().durationSec * 1000L
            _state.update {
                it.copy(
                    plan = plan, stepIndex = plan.steps.lastIndex, elapsedInStepSec = plan.steps.last().durationSec,
                    finished = true, summary = done, lapAvg = null, workoutPaused = false, notStarted = false,
                )
            }
            Timber.i("Loaded workout ${plan.name}: finished earlier, showing Workout done")
            return
        }
        val restored = if (rideIdle) null else plan?.let { store.restoreProgress(it) }
        val (index, elapsedSec) = restored ?: (0 to 0)
        val underWay = plan != null && (index > 0 || elapsedSec > 0)
        if (reselected && underWay) {
            // Selected again mid-ride: the start screen asks CONTINUE (from here) or START OVER
            elapsedInStepMs = 0
            _state.update { it.loaded(plan, index, 0, notStarted = true, resumeFromSec = elapsedSec) }
            Timber.i("Loaded workout ${plan?.name}: was at step ${index + 1} + ${elapsedSec}s, asking continue or start over")
            return
        }
        // A workout waits for START WORKOUT, unless it was already running (app restarted mid-ride).
        elapsedInStepMs = elapsedSec * 1000L
        _state.update { it.loaded(plan, index, elapsedSec, notStarted = !underWay) }
        Timber.i("Loaded workout ${plan?.name} at step $index + ${elapsedSec}s")
    }

    private fun resetToStart() {
        // A new ride starts with ERG as Settings has it.
        _state.update { it.copy(ergOffThisRide = false) }
        val st = _state.value
        // The ride ended while CONTINUE was offered: nothing to continue any more.
        if (st.notStarted && st.resumeFromSec != null) {
            _state.update { it.copy(stepIndex = 0, resumeFromSec = null) }
            return
        }
        // Waiting for START: keep the step the rider picked to start from.
        if (st.plan == null || st.notStarted) return
        cancelCountdown()
        resetLap()
        resetWorkoutStats()
        elapsedInStepMs = 0
        store.clearProgress()
        _state.update { it.copy(stepIndex = 0, elapsedInStepSec = 0, finished = false, lapAvg = null, workoutPaused = false, notStarted = true, resumeFromSec = null) }
        Timber.i("No ride active: workout reset to the first interval, waiting for START")
    }

    private suspend fun tickLoop() {
        lastTick = now()
        var sinceSave = 0L
        while (scope.isActive) {
            delay(TICK_MS)
            val now = now()
            val dt = now - lastTick
            lastTick = now
            val st = _state.value
            // Indoor start box: the start button's green flash flips once a second.
            if (st.indoor && st.rideIdle && st.notStarted && st.plan != null) {
                val flash = (now / 1000) % 2 == 0L
                if (flash != st.startFlash) _state.update { it.copy(startFlash = flash) }
            }
            val step = st.currentStep ?: continue
            if (!recording || st.finished || st.workoutPaused || st.notStarted) continue
            workoutMs += dt
            // The ⓘ view's numbers, once a second while it's open
            if (st.infoOpen && (workoutMs / 1000) != ((workoutMs - dt) / 1000)) _state.update { it.copy(live = summary()) }

            elapsedInStepMs += dt
            val remainingMs = step.durationSec * 1000L - elapsedInStepMs

            // Every interval ends with the one countdown (5 s, a beep a second), like a jump or START:
            // to the next interval, or "Workout finished" after the last. A jump counting down has its
            // own; while one is being chosen the box is busy, so only the beeps count it out.
            val ending = if (countdownJob?.isActive != true) endingCountdown(remainingMs, COUNTDOWN_SECONDS) else null
            if (ending != null && ending != lastEnding) {
                lastEnding = ending
                if (ending == COUNTDOWN_SECONDS) Timber.i("Countdown to ${if (st.hasNext) "step ${st.stepIndex + 2}" else "the end"} (interval ending)")
                if (st.selectTarget == null) {
                    _state.update { it.copy(countdown = ending, jumpTarget = if (it.hasNext) it.stepIndex + 1 else null, infoOpen = false) }   // a countdown closes ⓘ
                    // With the screen showing, it beeps when the number appears; otherwise beep here.
                    if (viewsActive.get() == 0) beep(TICK_TONE)
                } else {
                    beep(TICK_TONE)
                }
            }

            if (remainingMs <= 0) {
                if (st.hasNext) {
                    goToStep(st.stepIndex + 1, natural = true)
                } else {
                    val sum = summary()
                    _state.update { it.copy(finished = true, elapsedInStepSec = step.durationSec, summary = sum, countdown = null, jumpTarget = null) }
                    karoo.dispatch(MarkLap)
                    beep(GO_TONE)
                    // Indoors the ride pauses with the workout's end (ERG hands the trainer back);
                    // to cool down the rider resumes and sets the watts on the Karoo's trainer card.
                    if (st.indoor) { Timber.i("Workout finished indoors: pausing the ride"); karoo.dispatch(PauseRide) }
                    // Remember it, so a restart of the app shows Workout done again (not START WORKOUT)
                    st.plan?.let { store.saveFinished(it, sum) }
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
        countdownPlanId = _state.value.plan?.id
        Timber.i("Countdown to step ${target + 1} started")
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (n in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = n, jumpTarget = target, infoOpen = false) }
                // With the screen showing, it beeps when the number appears; otherwise beep here.
                if (viewsActive.get() == 0) beep(TICK_TONE)
                delay(1000)
            }
            countdownJob = null
            val landing = _state.value.countdownLanding(countdownPlanId, target)
            if (landing == null) {
                Timber.w("Countdown to step ${target + 1} dropped: the workout changed")
                _state.update { it.copy(countdown = null, jumpTarget = null) }
            } else if (_state.value.notStarted) beginWorkout(landing) else goToStep(landing, natural = false)
        }
    }

    private fun goToStep(index: Int, natural: Boolean) {
        lastEnding = null
        // A step change (natural or not) clears any pending jump, so a forward jump that was
        // counting down when the interval ended doesn't skip the interval we just entered.
        if (natural) {
            cancelCountdown()
            clearSelection()
        }
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

    private fun clearSelection() {
        stopAutoConfirm()
        selectJob?.cancel()
        selectJob = null
        selectionPos = null
        _state.update { it.copy(selectTarget = null, selectRestart = false, selectProgress = 0) }
    }

    private fun cancelCountdown() {
        if (countdownJob?.isActive == true) Timber.i("Countdown cancelled")
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(countdown = null, jumpTarget = null) }
    }

    private fun resetLap() {
        lapSum = 0.0
        lapSamples = 0
        lapNp.reset()
        _state.update { it.copy(lapNp = null) }
    }

    private fun onPower(watts: Double?) {
        val st = _state.value
        // The lap average keeps counting while the workout is paused; only the interval timer stops.
        if (watts != null && recording && st.workoutLoaded && !st.finished && !st.notStarted) {
            lapSum += watts
            lapSamples++
            lapNp.add(now(), watts)
        }
        if (watts != null && recording) freeLapNp.add(now(), watts)
        if (watts != null && workoutRunning(st)) {
            workoutPowerSum += watts
            workoutPowerSamples++
            workoutNp.add(now(), watts)
            workoutMaxPower = maxOf(workoutMaxPower, watts.roundToInt())
        }
        _state.update {
            it.copy(
                power = watts?.roundToInt(),
                lapAvg = if (lapSamples > 0) (lapSum / lapSamples).roundToInt() else it.lapAvg,
                lapNp = lapNp.value,
                freeLapNp = freeLapNp.value,
            )
        }
    }

    private fun saveProgress() {
        val st = _state.value
        val plan = st.plan ?: return
        if (!st.finished && !st.notStarted) store.saveProgress(plan, st.stepIndex, st.elapsedInStepSec)
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
        /** A choice made with a remote button confirms itself after this many seconds. */
        const val AUTO_CONFIRM_SECONDS = 5
        /** An unconfirmed jump choice cancels itself after this long. */
        private const val SELECT_TIMEOUT_MS = 10_000L
        /** How long "Move cancelled" shows. */
        private const val MOVE_CANCELLED_MS = 3_000L
        private const val TICK_MS = 100L
        private const val SAVE_EVERY_MS = 15_000L

        private val TICK_TONE = PlayBeepPattern.Tone(frequency = 2000, durationMs = 100)
        private val GO_TONE = PlayBeepPattern.Tone(frequency = 3000, durationMs = 400)
        private val CANCEL_TONE = PlayBeepPattern.Tone(frequency = 1000, durationMs = 200)
        /** Workout starts: a rising major arpeggio (E6, G#6, B6, E7), happier than the plain GO beep. */
        private val START_TUNE = listOf(
            PlayBeepPattern.Tone(frequency = 1319, durationMs = 110),
            PlayBeepPattern.Tone(frequency = null, durationMs = 20),
            PlayBeepPattern.Tone(frequency = 1661, durationMs = 110),
            PlayBeepPattern.Tone(frequency = null, durationMs = 20),
            PlayBeepPattern.Tone(frequency = 1976, durationMs = 110),
            PlayBeepPattern.Tone(frequency = null, durationMs = 20),
            PlayBeepPattern.Tone(frequency = 2637, durationMs = 360),
        )
    }
}
