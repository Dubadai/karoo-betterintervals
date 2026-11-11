package com.daviddokakis.karoointerval

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Holds the selected workout and the intervals.icu credentials, shared by the extension service
 * and the setup screen (same process). Everything is kept in app-private storage.
 */
class PlanStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("interval_screen", Context.MODE_PRIVATE)

    private val _plan = MutableStateFlow(loadSelected())
    val plan: StateFlow<WorkoutPlan?> = _plan

    var athleteId: String
        get() = prefs.getString(KEY_ATHLETE, "0") ?: "0"
        set(value) = prefs.edit().putString(KEY_ATHLETE, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    val hasCredentials: Boolean get() = apiKey.isNotBlank()

    /** The first-run introduction was finished or skipped (Settings can show it again). */
    var introDone: Boolean
        get() = prefs.getBoolean(KEY_INTRO_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO_DONE, value).apply()

    /** Target tile shows the range instead of the single target (remembered between rides). */
    var targetShowsRange: Boolean
        get() = prefs.getBoolean(KEY_TARGET_RANGE, false)
        set(value) = prefs.edit().putBoolean(KEY_TARGET_RANGE, value).apply()

    /** Free ride: what the HR tile shows (remembered). Earlier versions stored on/off for the lap's average. */
    var freeSecond: FreeSecond
        get() = prefs.getString(KEY_FREE_SECOND, null)?.let { n -> FreeSecond.entries.firstOrNull { it.name == n } }
            ?: if (prefs.getBoolean(KEY_HR_LAP, false)) FreeSecond.LAP else FreeSecond.NOW
        set(value) = prefs.edit().putString(KEY_FREE_SECOND, value.name).remove(KEY_HR_LAP).apply()

    /** Free ride: what the power tile shows (remembered). */
    var freePower: FreePower
        get() = enumPref(KEY_FREE_POWER, FreePower.entries, FreePower.LAP_AVG)
        set(value) = prefs.edit().putString(KEY_FREE_POWER, value.name).apply()

    private val _topRow = MutableStateFlow(prefs.getString(KEY_TOP_ROW, null)?.let { n -> TopRow.entries.firstOrNull { it.name == n } })

    /** Settings › Top row (null until picked: each layout as it was). */
    val topRow: StateFlow<TopRow?> = _topRow

    fun setTopRow(value: TopRow) {
        prefs.edit().putString(KEY_TOP_ROW, value.name).apply()
        _topRow.value = value
    }

    private val _topRowRight = MutableStateFlow(prefs.getString(KEY_TOP_ROW_2, null)?.let { n -> TopRow.entries.firstOrNull { it.name == n } })

    /** Settings › Top row › Two: the second value at the top (null: one value). */
    val topRowRight: StateFlow<TopRow?> = _topRowRight

    fun setTopRowRight(value: TopRow?) {
        prefs.edit().apply { if (value == null) remove(KEY_TOP_ROW_2) else putString(KEY_TOP_ROW_2, value.name) }.apply()
        _topRowRight.value = value
    }

    /** A heart-rate step's target shows as % of LTHR instead of bpm (tap the Target tile; remembered). */
    var hrTargetLthr: Boolean
        get() = prefs.getBoolean(KEY_HR_LTHR, false)
        set(value) = prefs.edit().putBoolean(KEY_HR_LTHR, value).apply()

    private val _scalePct = MutableStateFlow(loadScale())

    /** Workouts › Scale workout: the loaded workout's power targets in % of the plan (100 = as planned). */
    val scalePct: StateFlow<Int> = _scalePct

    fun setScalePct(pct: Int) {
        val v = pct.coerceIn(SCALE_MIN, SCALE_MAX)
        prefs.edit().putInt(KEY_SCALE, v).apply()
        _scalePct.value = v
    }

    /** The scale belongs to the selected workout: a newly picked (or no) workout starts at 100 %. */
    private fun loadScale(): Int =
        if (prefs.getString(KEY_SELECTED_DATE, null) != LocalDate.now().toString()) 100 else prefs.getInt(KEY_SCALE, 100)

    /**
     * The Karoo rider profile's heart rate zones (min..max bpm, Z1 first), saved by the workout
     * session, so the Workouts page colours heart-rate workouts the same as the ride screen.
     */
    var hrZones: List<IntRange>
        get() = prefs.getString(KEY_HR_ZONES, null)?.split(",")?.mapNotNull { z ->
            z.split("-").takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { lo -> b.toIntOrNull()?.let { hi -> lo..hi } } }
        } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_HR_ZONES, value.joinToString(",") { "${it.first}-${it.last}" }).apply()

    /** Lap tile shows NP instead of the lap average (remembered between rides). */
    var lapShowsNp: Boolean
        get() = prefs.getBoolean(KEY_LAP_NP, false)
        set(value) = prefs.edit().putBoolean(KEY_LAP_NP, value).apply()

    private fun <E : Enum<E>> enumPref(key: String, entries: List<E>, default: E): E =
        entries.firstOrNull { it.name == prefs.getString(key, null) } ?: default

    private val _powerSmoothing = MutableStateFlow(enumPref(KEY_POWER_SMOOTHING, Smoothing.entries, Smoothing.S5))

    /** Settings › Power: the big watts instant or as the Karoo's 3 / 5 / 10 s average (default 3 s). */
    val powerSmoothing: StateFlow<Smoothing> = _powerSmoothing

    fun setPowerSmoothing(value: Smoothing) {
        prefs.edit().putString(KEY_POWER_SMOOTHING, value.name).apply()
        _powerSmoothing.value = value
    }

    private val _secondField = MutableStateFlow(enumPref(KEY_SECOND_FIELD, SecondField.entries, SecondField.HEART_RATE))

    /** Settings › Heart rate or cadence: what the HR spots show. */
    val secondField: StateFlow<SecondField> = _secondField

    fun setSecondField(value: SecondField) {
        prefs.edit().putString(KEY_SECOND_FIELD, value.name).apply()
        _secondField.value = value
    }

    private val _cadenceSmoothing = MutableStateFlow(enumPref(KEY_CADENCE_SMOOTHING, Smoothing.entries, Smoothing.INSTANT))

    /** Settings › Cadence: instant (default, like the Karoo's Cadence field) or a 3 / 5 / 10 s average. */
    val cadenceSmoothing: StateFlow<Smoothing> = _cadenceSmoothing

    fun setCadenceSmoothing(value: Smoothing) {
        prefs.edit().putString(KEY_CADENCE_SMOOTHING, value.name).apply()
        _cadenceSmoothing.value = value
    }

    private val _tileOutline = MutableStateFlow(prefs.getBoolean(KEY_TILE_OUTLINE, false))

    /** Tiles (Settings page): Tonal (a grey fill) or Outline (a border like Next up, no fill). */
    val tileOutline: StateFlow<Boolean> = _tileOutline

    fun setTileOutline(on: Boolean) {
        prefs.edit().putBoolean(KEY_TILE_OUTLINE, on).apply()
        _tileOutline.value = on
    }

    private val _ergOn = MutableStateFlow(prefs.getBoolean(KEY_ERG_ON, false))

    /** Settings › Trainer (indoors): the app sets ERG on the trainer during an indoor ride. */
    val ergOn: StateFlow<Boolean> = _ergOn

    fun setErgOn(on: Boolean) {
        prefs.edit().putBoolean(KEY_ERG_ON, on).apply()
        _ergOn.value = on
    }

    private val _ergRecoveryPct = MutableStateFlow(prefs.getInt(KEY_ERG_RECOVERY, ErgPlanner.RECOVERY_PCT))

    /** Settings › Trainer › When paused: the trainer's target while paused or done, in % of FTP. */
    val ergRecoveryPct: StateFlow<Int> = _ergRecoveryPct

    fun setErgRecoveryPct(pct: Int) {
        prefs.edit().putInt(KEY_ERG_RECOVERY, pct).apply()
        _ergRecoveryPct.value = pct
    }

    private val _appearance = MutableStateFlow(
        Appearance.entries.firstOrNull { it.name == prefs.getString(KEY_APPEARANCE, null) } ?: Appearance.AUTO,
    )

    /** Dark / Light / Auto (Settings page). Auto follows the Karoo's own In-Ride Appearance. */
    val appearance: StateFlow<Appearance> = _appearance

    fun setAppearance(value: Appearance) {
        prefs.edit().putString(KEY_APPEARANCE, value.name).apply()
        _appearance.value = value
    }

    /** How the watts show the target range (remembered between rides; the gauge until the rider picks one). */
    var wattsMode: WattsMode
        get() {
            prefs.getString(KEY_WATTS_MODE, null)?.let { name ->
                return WattsMode.entries.firstOrNull { it.name == name } ?: WattsMode.GAUGE
            }
            // Earlier versions stored an on/off setting for coloured numbers. Nothing picked yet:
            // the power gauge (target range above it, Lap avg and HR tiles under it).
            return if (prefs.getBoolean(KEY_WATTS_COLORED, false)) WattsMode.TEXT else WattsMode.GAUGE
        }
        set(value) = prefs.edit().putString(KEY_WATTS_MODE, value.name).remove(KEY_WATTS_COLORED).apply()

    fun select(workout: PlannedWorkout) {
        prefs.edit()
            .putString(KEY_SELECTED_JSON, workout.json)
            .putString(KEY_SELECTED_DATE, LocalDate.now().toString())
            .apply()
        // Another workout starts at 100 %; the same one (updated from intervals.icu) keeps its scale.
        if (workout.plan.id != _plan.value?.id) setScalePct(100)
        _plan.value = workout.plan
    }

    fun clearSelection() {
        prefs.edit().remove(KEY_SELECTED_JSON).remove(KEY_SELECTED_DATE).apply()
        setScalePct(100)
        _plan.value = null
    }

    /**
     * Fetches this week's planned rides (Monday to Sunday). Never picks a workout by itself;
     * the rider always chooses. A selected workout that was edited on intervals.icu is updated.
     */
    /**
     * [refreshWeek] with its failures as a [SyncError]: the one place sync errors are sorted, so
     * screens only word them. Cancellation still propagates (a timeout counts as offline).
     */
    suspend fun syncWeek(karoo: KarooSystemService): Result<List<PlannedWorkout>> = try {
        Result.success(refreshWeek(karoo))
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Result.failure(SyncError.Offline)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: IntervalsIcu.ApiException) {
        Result.failure(if (e.code == 401 || e.code == 403) SyncError.WrongKey else SyncError.Server(e.code))
    } catch (e: java.io.IOException) {
        Result.failure(SyncError.Offline)
    } catch (e: Exception) {
        Timber.w(e, "Sync failed")
        Result.failure(SyncError.Unexpected)
    }

    suspend fun refreshWeek(karoo: KarooSystemService): List<PlannedWorkout> {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val workouts = IntervalsIcu(athleteId, apiKey)
            .plannedWorkouts(monday, monday.plusDays(6)) { url, headers -> karoo.karooHttpGet(url, headers) }
        prefs.edit()
            .putString(KEY_WEEK_JSON, JSONArray(workouts.map { JSONObject(it.json) }).toString())
            .putString(KEY_WEEK_SYNCED, LocalDateTime.now().toString())
            .apply()
        val current = _plan.value
        if (current != null) {
            // The selected workout was edited on intervals.icu: pick up the new version.
            workouts.firstOrNull { it.plan.id == current.id && it.plan != current }?.let(::select)
        }
        return workouts
    }

    /** The week from the last successful sync, so the list also shows without internet. */
    fun cachedWeek(): List<PlannedWorkout> {
        val json = prefs.getString(KEY_WEEK_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { IntervalsIcu.plannedFrom(array.getJSONObject(it)) }
        } catch (e: Exception) {
            Timber.w(e, "Couldn't read cached week")
            emptyList()
        }
    }

    /** When the week was last synced, or null if never. */
    val lastSynced: LocalDateTime?
        get() = prefs.getString(KEY_WEEK_SYNCED, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    fun saveProgress(plan: WorkoutPlan, stepIndex: Int, elapsedSec: Int) {
        prefs.edit()
            .putString(KEY_PROGRESS, "${LocalDate.now()}|${plan.id}|$stepIndex|$elapsedSec")
            .apply()
    }

    /** Where to resume [plan] if the app restarted mid-ride today, else (0, 0). */
    fun restoreProgress(plan: WorkoutPlan): Pair<Int, Int> {
        val parts = prefs.getString(KEY_PROGRESS, null)?.split("|") ?: return 0 to 0
        if (parts.size != 4 || parts[0] != LocalDate.now().toString() || parts[1] != plan.id) return 0 to 0
        val index = parts[2].toIntOrNull()?.takeIf { it in plan.steps.indices } ?: return 0 to 0
        return index to (parts[3].toIntOrNull() ?: 0)
    }

    fun clearProgress() {
        prefs.edit().remove(KEY_PROGRESS).remove(KEY_FINISHED).apply()
    }

    /**
     * The workout was finished today with [summary]: kept so the Workout done screen comes back
     * if the app restarts (the Karoo can restart it, more likely with a route and the map busy)
     * until DONE is pressed or the ride ends.
     */
    fun saveFinished(plan: WorkoutPlan, summary: WorkoutSummary?) {
        val s = summary
        prefs.edit()
            .remove(KEY_PROGRESS)
            .putString(KEY_FINISHED, "${LocalDate.now()}|${plan.id}|${s?.timeSec ?: 0}|${s?.avgPower ?: ""}|${s?.np ?: ""}|${s?.avgHr ?: ""}|${s?.avgCadence ?: ""}")
            .apply()
    }

    /** The summary of [plan] if it was finished today and not closed yet (see [saveFinished]), else null. */
    fun restoreFinished(plan: WorkoutPlan): WorkoutSummary? {
        val p = prefs.getString(KEY_FINISHED, null)?.split("|") ?: return null
        if (p.size != 7 || p[0] != LocalDate.now().toString() || p[1] != plan.id) return null
        return WorkoutSummary(p[2].toIntOrNull() ?: 0, p[3].toIntOrNull(), p[4].toIntOrNull(), p[5].toIntOrNull(), p[6].toIntOrNull())
    }

    /** Only today's selection is restored, so yesterday's workout doesn't show up on today's ride. */
    private fun loadSelected(): WorkoutPlan? {
        if (prefs.getString(KEY_SELECTED_DATE, null) != LocalDate.now().toString()) return null
        val json = prefs.getString(KEY_SELECTED_JSON, null) ?: return null
        return try {
            IntervalsIcu.parseEvent(JSONObject(json))
        } catch (e: Exception) {
            Timber.w(e, "Couldn't restore selected workout")
            null
        }
    }

    companion object {
        private const val KEY_ATHLETE = "athlete_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SELECTED_JSON = "selected_json"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_FINISHED = "finished"
        private const val KEY_WATTS_COLORED = "watts_colored"
        private const val KEY_WATTS_MODE = "watts_mode"
        private const val KEY_TARGET_RANGE = "target_shows_range"
        private const val KEY_LAP_NP = "lap_shows_np"
        private const val KEY_HR_LAP = "hr_shows_lap"
        private const val KEY_FREE_SECOND = "free_second"
        private const val KEY_FREE_POWER = "free_power"
        private const val KEY_TOP_ROW = "top_row"
        private const val KEY_TOP_ROW_2 = "top_row_2"
        private const val KEY_HR_LTHR = "hr_target_lthr"
        private const val KEY_SCALE = "scale_pct"
        private const val KEY_HR_ZONES = "hr_zones"
        const val SCALE_MIN = 50
        const val SCALE_MAX = 150
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_TILE_OUTLINE = "tile_outline"
        private const val KEY_ERG_ON = "erg_on"
        private const val KEY_ERG_RECOVERY = "erg_recovery_pct"
        private const val KEY_POWER_SMOOTHING = "power_smoothing"
        private const val KEY_SECOND_FIELD = "second_field"
        private const val KEY_CADENCE_SMOOTHING = "cadence_smoothing"
        private const val KEY_WEEK_JSON = "week_json"
        private const val KEY_WEEK_SYNCED = "week_synced"
        private const val KEY_INTRO_DONE = "intro_done"

        @Volatile
        private var instance: PlanStore? = null

        fun get(context: Context): PlanStore =
            instance ?: synchronized(this) {
                instance ?: PlanStore(context.applicationContext).also { instance = it }
            }
    }
}

/** Why a sync failed, as the Workouts page words it. */
sealed class SyncError : Exception() {
    /** intervals.icu refused the API key or athlete ID (HTTP 401 / 403). */
    data object WrongKey : SyncError()
    data class Server(val code: Int) : SyncError()
    /** No connection, or no answer in time. */
    data object Offline : SyncError()
    data object Unexpected : SyncError()
}
