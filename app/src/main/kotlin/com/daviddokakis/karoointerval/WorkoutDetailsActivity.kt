package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A workout's details (docs/design/workout-details), opened from its card on the Workouts page: its
 * picture with FTP, its numbers (time, TSS, IF, average power, NP, estimated calories), the notes
 * from intervals.icu, time in each power zone and every interval, repeats grouped. Select workout,
 * top right, is the page's one yellow button (not shown for the workout already selected).
 */
class WorkoutDetailsActivity : AppearanceActivity() {

    private lateinit var store: PlanStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlanStore.get(this)
        val json = intent.getStringExtra(EXTRA_JSON) ?: exampleJson()
        val event = json?.let { runCatching { JSONObject(it) }.getOrNull() }
        val planned = event?.let { IntervalsIcu.plannedFrom(it) }
        if (event == null || planned == null) { finish(); return }
        val selected = intent.getBooleanExtra(EXTRA_SELECTED, false)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(12), dp(8))
            addView(settingsBack(getString(R.string.back)) { finish() })
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            if (!selected) {
                addView(
                    settingsButton(SettingsButton.MAIN, getString(R.string.select_workout)) {
                        store.select(planned)
                        setResult(RESULT_OK)
                        finish()
                    }.apply {
                        setPadding(dp(18), 0, dp(18), 0)
                        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(44))
                    },
                )
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(24))
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.settings_bg))
                addView(bar, MATCH_PARENT, WRAP_CONTENT)
                addView(ScrollView(context).apply { addView(content, MATCH_PARENT, WRAP_CONTENT) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            },
        )
        build(content, planned, event)
    }

    private fun build(content: LinearLayout, planned: PlannedWorkout, event: JSONObject) {
        val plan = planned.plan
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = store.zonePalette.value
        val stats = WorkoutStats.of(plan, event)

        content.addView(text(plan.name, 22f, R.color.settings_title, bold = true))
        content.addView(
            text(listOf(planned.date.format(DAY_FORMAT), formatClock(plan.totalSec)).joinToString(" · "), 13f, R.color.settings_help).apply {
                setPadding(0, dp(4), 0, dp(12))
            },
        )
        // The workout's picture: the rider's zone colours, ramps fading, FTP as a dashed line
        val w = resources.displayMetrics.widthPixels - dp(24)
        content.addView(
            ImageView(this).apply {
                setImageBitmap(
                    ChartRenderer(this@WorkoutDetailsActivity).render(
                        ScreenState(plan = plan, hrZones = store.hrZones, zonePalette = palette), w, dp(120), 1f, ChartRenderer.Style.CARD, ftpLine = true,
                    ),
                )
                contentDescription = getString(R.string.cd_chart)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(120)),
        )
        content.addView(StatGrid(this, stats.tiles(this)), LinearLayout.LayoutParams(MATCH_PARENT, dp(2 * 60 + 8)).apply { topMargin = dp(16); bottomMargin = dp(16) })

        // Notes: the description's own words (the step lines are the intervals, listed below)
        notesOf(event)?.let { notes ->
            content.addView(settingsDivider())
            content.addView(settingsHeading(R.drawable.ic_settings_info, getString(R.string.notes)))
            content.addView(text(notes, 15f, R.color.settings_title).apply { setPadding(0, 0, 0, dp(12)); setLineSpacing(0f, 1.15f) })
        }

        // Time in zones (power workouts)
        val ftp = plan.ftp
        if (!plan.hrOnly && ftp != null && ftp > 0) {
            content.addView(settingsDivider())
            content.addView(settingsHeading(R.drawable.ic_settings_colours, getString(R.string.time_in_zones)))
            val secs = IntArray(7)
            plan.steps.forEach { st ->
                if (st.hr) return@forEach
                for (i in 0 until st.durationSec) {
                    val watts = (st.startWatts + (st.endWatts - st.startWatts) * (i + 0.5) / st.durationSec).roundToInt()
                    Zones.zoneOf(if (st.isRamp) watts else st.avgWatts, ftp)?.let { secs[it - 1]++ }
                }
            }
            val most = secs.maxOrNull()?.coerceAtLeast(1) ?: 1
            for (z in 1..7) content.addView(zoneRow(z, secs[z - 1], plan.totalSec, most, ftp, palette, night))
            content.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
        }

        // Every interval, repeats grouped
        val doc = event.optJSONObject("workout_doc")?.optJSONArray("steps")
        if (doc != null) {
            content.addView(settingsDivider())
            content.addView(settingsHeading(R.drawable.ic_settings_timer, getString(R.string.intervals_heading)))
            addSteps(content, doc, plan, palette, night, indent = 0)
        }
    }

    /** One zone: "Z3 Tempo" and its time and share; a bar in the zone's colour, its watts beside it. */
    private fun zoneRow(z: Int, sec: Int, total: Int, most: Int, ftp: Int, palette: ZonePalette, night: Boolean): View {
        val on = sec > 0
        val line1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text("Z$z", 15f, R.color.settings_title, bold = true).apply { minWidth = dp(30) })
            addView(text(getString(ZONE_NAMES[z - 1]), 15f, R.color.settings_title), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(text(if (on) "${formatClock(sec)} · ${(100.0 * sec / total).roundToInt()} %" else "–", 15f, R.color.settings_title, medium = true))
        }
        val fill = ZonePalette.boosted(palette.fill(z, night), night)
        val edge = palette.edge(z, night)?.let { ZonePalette.boosted(it, night) }
        val bar = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val h = height.toFloat(); val r = h / 2
                p.style = Paint.Style.FILL; p.color = getColor(R.color.segment_track)
                c.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), r, r, p)
                if (!on) return
                val rect = RectF(0f, 0f, (width * sec.toFloat() / most).coerceAtLeast(h), h)
                p.color = fill; c.drawRoundRect(rect, r, r, p)
                if (edge != null) { p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * resources.displayMetrics.density; p.color = edge; c.drawRoundRect(rect, r, r, p) }
            }
        }
        val line2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bar, LinearLayout.LayoutParams(0, dp(8), 1f))
            addView(text(zoneRange(z, ftp), 12f, R.color.settings_help).apply { gravity = Gravity.END; minWidth = dp(76) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            alpha = if (on) 1f else 0.5f
            setPadding(0, dp(6), 0, dp(6))
            addView(line1)
            addView(line2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
        }
    }

    /** A zone's watts, as [Zones.zoneOf] draws the lines (% of FTP, whole numbers). */
    private fun zoneRange(z: Int, ftp: Int): String {
        val tops = listOf(55, 75, 90, 105, 120, 150)
        val hi = { pct: Int -> ((pct + 1) * ftp - 1) / 100 }   // the highest watts still in that %
        return when (z) {
            1 -> "≤ ${hi(55)} W"
            7 -> "${hi(150) + 1}+ W"
            else -> "${hi(tops[z - 2]) + 1}–${hi(tops[z - 1])} W"
        }
    }

    private fun addSteps(into: LinearLayout, steps: JSONArray, plan: WorkoutPlan, palette: ZonePalette, night: Boolean, indent: Int) {
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            val children = step.optJSONArray("steps")
            if (children != null) {
                val reps = getString(R.string.repeat_times, step.optInt("reps", 1))
                into.addView(text(step.optString("text").trim().takeIf { it.isNotEmpty() }?.let { "$it · $reps" } ?: reps, 16f, R.color.settings_title, bold = true).apply { setPadding(dp(indent), dp(8), 0, dp(2)) })
                val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                // A thin line down the left: these repeat
                val line = View(this).apply { setBackgroundColor(getColor(R.color.segment_track)) }
                into.addView(
                    LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(indent + 3), 0, 0, 0)
                        addView(line, LinearLayout.LayoutParams(dp(2), MATCH_PARENT).apply { marginEnd = dp(12) })
                        addView(block, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    },
                )
                addSteps(block, children, plan, palette, night, indent = 0)
                continue
            }
            val duration = step.optInt("duration", 0)
            if (duration <= 0) continue
            into.addView(stepRow(step, IntervalsIcu.stepOf(step, duration, plan.ftp, plan.lthr), plan, palette, night, indent))
        }
    }

    /** One interval: a zone-coloured strip, its name and time; its watts, % of FTP and zone under them. */
    private fun stepRow(json: JSONObject, st: PlanStep, plan: WorkoutPlan, palette: ZonePalette, night: Boolean, indent: Int): View {
        val ftp = plan.ftp
        val za = if (st.hr) null else Zones.zoneOf(st.startWatts, ftp)
        val zb = if (st.hr) null else Zones.zoneOf(st.endWatts, ftp)
        val name = when {
            json.optString("text").isNotBlank() -> json.optString("text").trim()
            json.optBoolean("warmup") -> getString(R.string.warm_up)
            json.optBoolean("cooldown") -> getString(R.string.cool_down)
            za != null -> getString(ZONE_NAMES[maxOf(za, zb ?: za) - 1])
            else -> getString(R.string.heart_rate)
        }
        val unit = if (st.hr) "bpm" else "W"
        val watts = when {
            st.isRamp -> "${st.startWatts} → ${st.endWatts} $unit"
            st.isRange && st.rangeLow != null && st.rangeHigh != null -> "${st.rangeLow}–${st.rangeHigh} $unit"
            else -> "${st.avgWatts} $unit"
        }
        val pct = { w: Int -> if (ftp != null && ftp > 0) (w * 100.0 / ftp).roundToInt() else null }
        val detail = when {
            st.hr && st.pctUnit != null -> if (st.pctLow != st.pctHigh) "${st.pctLow}–${st.pctHigh} % ${pctName(st.pctUnit)}" else "${st.pctLow} % ${pctName(st.pctUnit)}"
            st.hr -> ""
            else -> listOfNotNull(
                pct(st.startWatts)?.let { a -> val b = pct(st.endWatts)!!; if (st.isRamp) "$a–$b % FTP" else "${pct(st.avgWatts)} % FTP" },
                za?.let { a -> val b = zb ?: a; if (a == b) "Z$a" else "Z${minOf(a, b)}–${maxOf(a, b)}" },
            ).joinToString(" · ")
        }
        val colors = (if (st.hr) listOf(getColor(Zones.hrBarColor(st.avgWatts, store.hrZones, plan.lthr))) else listOf(za ?: 1, zb ?: za ?: 1).map { ZonePalette.boosted(palette.fill(it, night), night) })
        val strip = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, (if (colors.size == 1) colors + colors else colors).toIntArray()).apply { cornerRadius = dp(3).toFloat() }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(text(name, 16f, R.color.settings_title, medium = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(text(formatClock(st.durationSec), 16f, R.color.settings_title, bold = true))
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(text(watts, 14f, R.color.settings_title, bold = true))
                    if (detail.isNotEmpty()) addView(text(detail, 13f, R.color.settings_help).apply { setPadding(dp(8), 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                },
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(indent), dp(6), 0, dp(6))
            addView(strip, LinearLayout.LayoutParams(dp(5), MATCH_PARENT).apply { marginEnd = dp(12) })
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    }

    private fun pctName(unit: String) = if (unit == "%lthr") "LTHR" else "HR"

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false, medium: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(color))
        typeface = when {
            bold -> Typeface.create("sans-serif", Typeface.BOLD)
            medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    /** Debug builds: the example workout from the debug assets (the emulator has no intervals.icu). */
    private fun exampleJson(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 || !intent.getBooleanExtra(EXTRA_EXAMPLE, false)) return null
        return runCatching { assets.open("example_event.json").bufferedReader().readText() }.getOrNull()
    }

    companion object {
        const val EXTRA_JSON = "json"
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_EXAMPLE = "example"
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val ZONE_NAMES = listOf(R.string.zone1, R.string.zone2, R.string.zone3, R.string.zone4, R.string.zone5, R.string.zone6, R.string.zone7)

        fun intent(context: Context, json: String, selected: Boolean) =
            Intent(context, WorkoutDetailsActivity::class.java).putExtra(EXTRA_JSON, json).putExtra(EXTRA_SELECTED, selected)

        /**
         * The notes: the description's lines that aren't the workout itself (intervals.icu writes the
         * steps into the description too: "- 10m 55%", "3x"). Null when nothing is left.
         */
        fun notesOf(event: JSONObject): String? {
            val d = event.optString("description").takeIf { event.has("description") && !event.isNull("description") } ?: return null
            // A step line, or a repeat ("3x", "Main set 3x")
            val step = Regex("""^\s*(-|\*)|(^|\s)\d+\s*[xX]\s*$|^\s*\d+\s*[xX]\s""")
            val lines = d.lines()
            // A short heading right before the steps ("Warmup", "Cooldown") goes with them
            val heading = { i: Int -> lines[i].trim().let { it.isNotEmpty() && it.length <= 30 && !it.endsWith(".") } &&
                lines.drop(i + 1).firstOrNull { it.isNotBlank() }?.let { step.containsMatchIn(it) } == true }
            val kept = lines.filterIndexed { i, l -> !step.containsMatchIn(l) && !heading(i) }
            return kept.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim().ifEmpty { null }
        }
    }
}

/**
 * A planned workout's numbers: time, TSS, IF, average power, NP and calories. TSS and IF come from
 * intervals.icu when it sends them, else from the steps (NP from 30 s rolling power). Calories:
 * the work in kJ, about the same in kcal (a rider turns about a quarter of the energy into work).
 */
class WorkoutStats(val totalSec: Int, val tss: Int?, val intensity: Double?, val avg: Int?, val np: Int?, val kcal: Int?) {

    fun tiles(c: Context): List<Pair<String, String>> = listOf(
        c.getString(R.string.time) to shortTime(totalSec),
        c.getString(R.string.tss) to (tss?.toString() ?: "--"),
        c.getString(R.string.intensity_factor) to (intensity?.let { String.format(Locale.US, "%.2f", it) } ?: "--"),
        c.getString(R.string.avg_power) to (avg?.toString() ?: "--"),
        c.getString(R.string.np_short) to (np?.toString() ?: "--"),
        c.getString(R.string.calories) to (kcal?.toString() ?: "--"),
    )

    companion object {
        /** "2h20", "1h05", "45m": short, so the six numbers share one size. */
        fun shortTime(sec: Int): String {
            val h = sec / 3600; val m = (sec % 3600) / 60
            return if (h > 0) "${h}h%02d".format(m) else "${m}m"
        }

        fun of(plan: WorkoutPlan, event: JSONObject): WorkoutStats {
            val ftp = plan.ftp?.takeIf { it > 0 }
            val power = if (plan.hrOnly) null else IntArray(plan.totalSec).also { arr ->
                var t = 0
                plan.steps.forEach { st ->
                    for (i in 0 until st.durationSec) {
                        arr[t + i] = if (st.hr) 0 else (st.startWatts + (st.endWatts - st.startWatts) * (i + 0.5) / st.durationSec).roundToInt()
                    }
                    t += st.durationSec
                }
            }
            val avg = power?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val np = power?.takeIf { it.size >= 30 }?.let { p ->
                var sum = 0.0; var acc = 0.0; var n = 0
                for (i in p.indices) {
                    sum += p[i]; if (i >= 30) sum -= p[i - 30]
                    if (i >= 29) { acc += (sum / 30).pow(4); n++ }
                }
                (acc / n).pow(0.25).roundToInt()
            }
            val icuIf = event.optDouble("icu_intensity").takeIf { !it.isNaN() && it > 0 }?.let { if (it > 3) it / 100 else it }
            val intensity = icuIf ?: if (np != null && ftp != null) np.toDouble() / ftp else null
            val icuLoad = event.optDouble("icu_training_load").takeIf { !it.isNaN() && it > 0 }?.roundToInt()
            val tss = icuLoad ?: if (np != null && ftp != null && intensity != null) (plan.totalSec * np * intensity / (ftp * 36.0)).roundToInt() else null
            val kcal = power?.sum()?.let { (it / 1000.0).roundToInt() }?.takeIf { it > 0 }
            return WorkoutStats(plan.totalSec, tss, intensity, avg, np, kcal)
        }
    }
}

/**
 * The details page's six numbers: grey rounded tiles in a 3 × 2 grid, the label small in the help
 * colour, the number in Relative (the Karoo's values), every number at one size: the largest that
 * fits the widest of them.
 */
class StatGrid(context: Context, private val tiles: List<Pair<String, String>>) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.segment_track) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.settings_help); textSize = 11 * d; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.settings_title); typeface = KarooType.value }

    init { contentDescription = tiles.joinToString(", ") { "${it.first} ${it.second}" } }

    override fun onDraw(c: Canvas) {
        val g = 8 * d; val cols = 3; val rows = (tiles.size + cols - 1) / cols
        val w = (width - g * (cols - 1)) / cols; val h = (height - g * (rows - 1)) / rows
        val pad = 10 * d
        value.textSize = 28 * d
        while (tiles.maxOf { value.measureText(it.second) } > w - 2 * pad && value.textSize > 12 * d) value.textSize -= d
        tiles.forEachIndexed { i, (l, v) ->
            val x = (i % cols) * (w + g); val y = (i / cols) * (h + g)
            c.drawRoundRect(RectF(x, y, x + w, y + h), 10 * d, 10 * d, bg)
            c.drawText(l.uppercase(), x + pad, y + pad + 11 * d, label)
            c.drawText(v, x + pad, y + h - pad, value)
        }
    }
}
