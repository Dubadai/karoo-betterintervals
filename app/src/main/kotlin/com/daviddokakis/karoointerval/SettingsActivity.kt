package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.LinearLayout

/**
 * Settings, built from the shared settings kit (SettingsKit.kt) in its order: the look (Appearance,
 * Tiles), what the screen shows (Heart rate or cadence, Top row), smoothing, the intervals.icu
 * account (opens the Account page), then the rows every app ends with (update notices, Show
 * introduction, version).
 */
class SettingsActivity : AppearanceActivity() {

    private lateinit var store: PlanStore
    private lateinit var form: LinearLayout
    private lateinit var example: ExampleCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlanStore.get(this)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        example = ExampleCard(this)
        setContentView(
            SettingsLayout(
                this,
                back = settingsBack(getString(R.string.back)) { finish() },
                barTitle = getString(R.string.settings),
                title = settingsTitle(getString(R.string.app_title)),
                card = example,
                form = form,
                pageColor = getColor(R.color.settings_bg),
                textColor = getColor(R.color.settings_title),
            ),
        )
        render()
    }

    override fun onResume() {
        super.onResume()
        render() // the account row shows whether a key is set
    }

    /** The workout screen with a made-up workout, drawn full size and shown small (127 dp tall). */
    private fun showExample() {
        val s = ExampleScreen.state(
            store.wattsMode, store.appearance.value,
            tileOutline = store.tileOutline.value, secondField = store.secondField.value, topRow = store.topRow.value,
        ).copy(topRowRight = store.topRowRight.value)
        val full = ExampleScreen.draw(this, s, ExampleScreen.FULL_W, ExampleScreen.FULL_H)
        val h = dp(127)
        val small = Bitmap.createScaledBitmap(full, h * ExampleScreen.FULL_W / ExampleScreen.FULL_H, h, true)
        example.show(small, getString(R.string.example_description))
    }

    private fun render() {
        showExample()
        form.removeAllViews()

        // Appearance: Dark / Light / Auto. The page itself switches right away.
        form.addView(settingsHeading(R.drawable.ic_settings_appearance, getString(R.string.appearance)))
        form.addView(
            settingsSegmented(appearanceOptions(), store.appearance.value) { picked ->
                store.setAppearance(picked)
                recreate()
            },
        )
        section(R.string.appearance_help)

        // Tiles: Tonal / Outline (the Lap avg, Target and HR tiles on the workout screen)
        form.addView(settingsHeading(R.drawable.ic_settings_tiles, getString(R.string.tiles)))
        form.addView(
            settingsSegmented(named(R.string.tiles_tonal to false, R.string.tiles_outline to true), store.tileOutline.value) { on ->
                store.setTileOutline(on)
                render()
            },
        )
        section(R.string.tiles_help)

        // Heart rate or cadence in the HR spots
        form.addView(settingsHeading(R.drawable.ic_settings_heart, getString(R.string.second_field)))
        form.addView(
            settingsSegmented(
                named(R.string.heart_rate to SecondField.HEART_RATE, R.string.cadence to SecondField.CADENCE),
                store.secondField.value,
            ) { picked ->
                store.setSecondField(picked)
                render()
            },
        )
        section(R.string.second_field_help)

        // Top row of the workout screen: one value or two (Left and Right), each heart rate,
        // cadence or the target; what isn't at the top goes in the tile next to Lap avg.
        // Design: docs/design/hr-targets/ (Two values at the top).
        form.addView(settingsHeading(R.drawable.ic_settings_top_row, getString(R.string.top_row)))
        val topOptions = named(R.string.heart_rate to TopRow.HEART_RATE, R.string.cadence to TopRow.CADENCE, R.string.target to TopRow.TARGET)
        val left = store.topRow.value ?: if (store.secondField.value == SecondField.CADENCE) TopRow.CADENCE else TopRow.HEART_RATE
        val right = store.topRowRight.value?.takeIf { it != left }
        form.addView(
            settingsSegmented(named(R.string.top_row_one to false, R.string.top_row_two to true), right != null) { two ->
                // Two: the right one starts as the first that isn't on the left
                store.setTopRowRight(if (two) TopRow.entries.first { it != left } else null)
                render()
            },
        )
        form.addView(settingsSubLabel(getString(R.string.top_row_left)))
        form.addView(
            settingsSegmented(topOptions, left) { picked ->
                store.setTopRow(picked)
                // The right one can't be the same: it moves to the first other one
                if (store.topRowRight.value == picked) store.setTopRowRight(TopRow.entries.first { it != picked })
                render()
            },
        )
        if (right != null) {
            form.addView(settingsSubLabel(getString(R.string.top_row_right)))
            form.addView(settingsSegmented(topOptions, right, unavailable = setOf(left)) { picked -> store.setTopRowRight(picked); render() })
        }
        section(R.string.top_row_help)

        // Power: how the big watts are smoothed; cadence's own smoothing when cadence is shown
        form.addView(settingsHeading(R.drawable.ic_settings_power, getString(R.string.power_smoothing)))
        form.addView(
            settingsSegmented(Smoothing.entries.map { getString(it.label) to it }, store.powerSmoothing.value) { picked ->
                store.setPowerSmoothing(picked)
                render()
            },
        )
        section(R.string.power_smoothing_help)
        if (store.secondField.value == SecondField.CADENCE) {
            form.addView(settingsHeading(R.drawable.ic_settings_cadence, getString(R.string.cadence_smoothing)))
            form.addView(
                settingsSegmented(Smoothing.entries.map { getString(it.label) to it }, store.cadenceSmoothing.value) { picked ->
                    store.setCadenceSmoothing(picked)
                    render()
                },
            )
            section(R.string.cadence_smoothing_help)
        }

        // intervals.icu: one row, opens the Account page
        form.addView(
            settingsNavRow(
                R.drawable.ic_settings_person,
                getString(R.string.intervals_account),
                status = getString(if (store.hasCredentials) R.string.connected else R.string.not_connected),
                statusColor = if (store.hasCredentials) R.color.settings_ok else R.color.settings_help,
            ) { startActivity(Intent(this, AccountActivity::class.java)) },
        )

        // The first-run introduction, again (e.g. to see how to add the field to a profile)
        val intro = settingsNavRow(R.drawable.ic_settings_intro, getString(R.string.intro_again)) {
            startActivity(Intent(this, IntroActivity::class.java).putExtra(IntroActivity.EXTRA_AGAIN, true))
        }
        settingsAppRows(
            form,
            repo = "karoo-betterintervals",
            extra = listOf(intro),
            updateHelp = getString(R.string.update_notices_help),
        ) { render() }
    }

    private fun section(help: Int) {
        form.addView(settingsHelp(getString(help)))
        form.addView(settingsDivider())
    }

    private fun <T> named(vararg options: Pair<Int, T>) = options.map { (name, value) -> getString(name) to value }
}

/** Dark / Light / Auto, for Settings and the introduction. */
fun Context.appearanceOptions() = listOf(
    getString(R.string.appearance_dark) to Appearance.DARK,
    getString(R.string.appearance_light) to Appearance.LIGHT,
    getString(R.string.appearance_auto) to Appearance.AUTO,
)
