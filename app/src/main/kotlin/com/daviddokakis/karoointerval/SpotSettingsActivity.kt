package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.LinearLayout

/**
 * Settings › Workout screen › one spot (docs/design/more-control): the top row, the line under the
 * watts, or one of the two tiles. The Example card shows the workout screen with that spot ringed in
 * blue; under it the spot's choices, built from the settings kit.
 */
class SpotSettingsActivity : AppearanceActivity() {

    enum class Spot(val title: Int) { TOP_ROW(R.string.top_row), UNDER_WATTS(R.string.under_watts), LEFT_TILE(R.string.left_tile), RIGHT_TILE(R.string.right_tile) }

    private lateinit var store: PlanStore
    private lateinit var form: LinearLayout
    private lateinit var example: ExampleCard
    private lateinit var spot: Spot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlanStore.get(this)
        spot = intent.getStringExtra(EXTRA_SPOT)?.let { n -> Spot.entries.firstOrNull { it.name == n } } ?: Spot.TOP_ROW
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
            // the choice cards' blue ring hangs into the page's margin
            clipChildren = false; clipToPadding = false
        }
        example = ExampleCard(this)
        setContentView(
            SettingsLayout(
                this,
                back = settingsBack(getString(R.string.back)) { finish() },
                barTitle = getString(R.string.settings),
                title = settingsTitle(getString(spot.title)),
                card = example,
                form = form,
                pageColor = getColor(R.color.settings_bg),
                textColor = getColor(R.color.settings_title),
            ),
        )
        render()
    }

    private fun render() {
        showExample()
        form.removeAllViews()
        when (spot) {
            Spot.TOP_ROW -> topRow()
            Spot.UNDER_WATTS -> underWatts()
            Spot.LEFT_TILE -> tile(store.leftItems.value) { store.setLeftItems(it) }
            Spot.RIGHT_TILE -> tile(store.rightItems.value) { store.setRightItems(it) }
        }
    }

    /** One value or two; each heart rate, cadence, target, calories or speed (the right one not the left's). */
    private fun topRow() {
        form.addView(settingsIntro(getString(R.string.top_row_help)))
        val left = store.topRow.value ?: if (store.secondField.value == SecondField.CADENCE) TopRow.CADENCE else TopRow.HEART_RATE
        val right = store.topRowRight.value?.takeIf { it != left }
        form.addView(
            settingsSegmented(listOf(getString(R.string.top_row_one) to false, getString(R.string.top_row_two) to true), right != null) { two ->
                // Two: the right one starts as the first that isn't on the left
                store.setTopRowRight(if (two) TopRow.entries.first { it != left } else null)
                render()
            },
        )
        form.addView(settingsSubLabel(getString(R.string.top_row_left)))
        form.addView(
            settingsChoiceCards(TopRow.entries.map { Triple(topName(it), null, it) }, left) { picked ->
                store.setTopRow(picked)
                // The right one can't be the same: it moves to the first other one
                if (store.topRowRight.value == picked) store.setTopRowRight(TopRow.entries.first { it != picked })
                render()
            },
        )
        if (right != null) {
            form.addView(settingsSubLabel(getString(R.string.top_row_right)))
            form.addView(
                settingsChoiceCards(TopRow.entries.filter { it != left }.map { Triple(topName(it), null, it) }, right) { picked ->
                    store.setTopRowRight(picked); render()
                },
            )
        }
    }

    /** The target (default), heart rate, cadence, calories, speed or nothing. */
    private fun underWatts() {
        form.addView(settingsIntro(getString(R.string.under_watts_help)))
        val options = UNDER_ORDER.map { Triple(topName(it), null, it) } + Triple(getString(R.string.nothing), null, null)
        form.addView(settingsChoiceCards<TopRow?>(options, store.underWatts.value) { picked -> store.setUnderWatts(picked); render() })
    }

    /** A switch per metric; a tap on the tile goes through the ones on. The last one on stays on. */
    private fun tile(on: List<TileItem>, set: (List<TileItem>) -> Unit) {
        form.addView(settingsIntro(getString(R.string.tile_help)))
        TileItem.entries.forEach { item ->
            val (icon, name) = tileRow(item)
            form.addView(
                settingsSwitchRow(icon, getString(name), item in on) { now ->
                    // Switching the last one off does nothing (the switch flips back)
                    if (now || on.size > 1) set(if (now) on + item else on - item)
                    render()
                },
            )
        }
    }

    private fun topName(t: TopRow) = getString(
        when (t) {
            TopRow.HEART_RATE -> R.string.heart_rate
            TopRow.CADENCE -> R.string.cadence
            TopRow.TARGET -> R.string.target
            TopRow.CALORIES -> R.string.calories
            TopRow.SPEED -> R.string.speed
        },
    )

    /** The workout screen, this spot ringed in Apple blue (the kit's selection colour), 160 dp tall. */
    private fun showExample() {
        // The gauge has no line under the watts: the spot pages show the number look
        val state = store.exampleState().let { if (it.wattsMode == WattsMode.GAUGE) it.copy(wattsMode = WattsMode.PLAIN) else it }
        val full = ExampleScreen.draw(this, state, ExampleScreen.FULL_W, ExampleScreen.FULL_H)
        val c = Canvas(full)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = getColor(R.color.settings_select_blue) }
        val w = ExampleScreen.FULL_W.toFloat()
        val two = store.topRowRight.value != null
        val r = when (spot) {
            Spot.TOP_ROW -> if (two) RectF(w * 0.2f, 6f, w * 0.8f, 64f) else RectF(w * 0.36f, 6f, w * 0.64f, 64f)
            Spot.UNDER_WATTS -> RectF(w * 0.32f, 246f, w * 0.68f, 306f)
            Spot.LEFT_TILE -> RectF(6f, 388f, w / 2f - 2f, 530f)
            Spot.RIGHT_TILE -> RectF(w / 2f + 2f, 388f, w - 6f, 530f)
        }
        c.drawRoundRect(r, 16f, 16f, p)
        val h = dp(160)
        example.show(Bitmap.createScaledBitmap(full, h * ExampleScreen.FULL_W / ExampleScreen.FULL_H, h, true), getString(R.string.spot_description))
    }

    companion object {
        private const val EXTRA_SPOT = "spot"
        /** Under the watts: the target first (the default). */
        private val UNDER_ORDER = listOf(TopRow.TARGET, TopRow.HEART_RATE, TopRow.CADENCE, TopRow.CALORIES, TopRow.SPEED)

        fun open(context: Context, spot: Spot) = context.startActivity(Intent(context, SpotSettingsActivity::class.java).putExtra(EXTRA_SPOT, spot.name))

        /** A tile metric's Settings icon and name. */
        fun tileRow(item: TileItem): Pair<Int, Int> = when (item) {
            TileItem.LAP_AVG -> R.drawable.ic_settings_lap to R.string.lap_avg
            TileItem.LAP_NP -> R.drawable.ic_settings_lap to R.string.np
            TileItem.RIDE_AVG -> R.drawable.ic_settings_power to R.string.ride_avg
            TileItem.TARGET -> R.drawable.ic_settings_target to R.string.target
            TileItem.HEART_RATE -> R.drawable.ic_settings_heart to R.string.heart_rate
            TileItem.CADENCE -> R.drawable.ic_settings_cadence to R.string.cadence
            TileItem.CALORIES -> R.drawable.ic_settings_calories to R.string.calories
            TileItem.SPEED -> R.drawable.ic_settings_speed to R.string.speed
        }
    }
}

/** The Example card's workout screen with this rider's settings (Settings and its spot pages). */
fun PlanStore.exampleState(): ScreenState = ExampleScreen.state(
    wattsMode, appearance.value, tileOutline = tileOutline.value, secondField = secondField.value, topRow = topRow.value,
).copy(
    topRowRight = topRowRight.value, leftItems = leftItems.value, rightItems = rightItems.value, underWatts = underWatts.value,
    zonePalette = zonePalette.value, calories = 412, speedMs = 8.7, lapNp = 268, rideAvgPower = 236,
)
