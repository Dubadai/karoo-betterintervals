package com.daviddokakis.karoointerval

import android.content.Intent
import android.graphics.Bitmap
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

/**
 * Settings: Appearance (Dark / Light / Auto), Karoo style, Tiles (Tonal / Outline), Power
 * smoothing and Heart rate or cadence (with cadence smoothing) first, both as Karoo's own segmented
 * control (as in Karoo Settings → Display), then the intervals.icu account as one row that opens
 * the Account page.
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
        // Bar with back arrow and "Settings" stays; the app's name scrolls away; the Example (the
        // workout screen as these settings draw it) then stays just under the bar.
        example = ExampleCard(this)
        setContentView(
            SettingsLayout(
                this,
                back = iconButton(R.drawable.ic_back, getString(R.string.cd_back)) { finish() },
                barTitle = getString(R.string.settings_title),
                title = text(getString(R.string.app_title), 22f, bold = true).apply { setPadding(dp(12), dp(4), dp(12), dp(4)) },
                card = example,
                form = form,
                pageColor = getColor(R.color.bg),
                textColor = getColor(R.color.text),
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
        form.addView(settingsHeading(R.drawable.ic_appearance, getString(R.string.appearance)))
        val current = store.appearance.value
        form.addView(
            segmented(
                listOf(R.string.appearance_dark to Appearance.DARK, R.string.appearance_light to Appearance.LIGHT, R.string.appearance_auto to Appearance.AUTO),
                current,
            ) { picked ->
                if (picked != store.appearance.value) {
                    store.setAppearance(picked)
                    recreate()
                }
            },
        )
        form.addView(settingsHelp(getString(R.string.appearance_help)))
        form.addView(settingsDivider())

        // Tiles: Tonal / Outline (the Lap avg, Target and HR tiles on the workout screen)
        form.addView(settingsHeading(R.drawable.ic_tiles, getString(R.string.tiles)))
        form.addView(
            segmented(listOf(R.string.tiles_tonal to false, R.string.tiles_outline to true), store.tileOutline.value) { on ->
                store.setTileOutline(on)
                render()
            },
        )
        form.addView(settingsHelp(getString(R.string.tiles_help)))
        form.addView(settingsDivider())

        // Power: how the big watts are smoothed
        form.addView(settingsHeading(R.drawable.ic_bolt, getString(R.string.power_smoothing)))
        form.addView(
            segmented(Smoothing.entries.map { it.label to it }, store.powerSmoothing.value) { picked ->
                store.setPowerSmoothing(picked)
                render()
            },
        )
        form.addView(settingsHelp(getString(R.string.power_smoothing_help)))
        form.addView(settingsDivider())

        // Heart rate or cadence in the HR spots, and (cadence) its smoothing
        form.addView(settingsHeading(R.drawable.ic_heart_outline, getString(R.string.second_field)))
        form.addView(
            segmented(
                listOf(R.string.heart_rate to SecondField.HEART_RATE, R.string.cadence to SecondField.CADENCE),
                store.secondField.value,
            ) { picked ->
                store.setSecondField(picked)
                render()
            },
        )
        form.addView(settingsHelp(getString(R.string.second_field_help)))
        if (store.secondField.value == SecondField.CADENCE) {
            form.addView(settingsHeading(R.drawable.ic_cadence, getString(R.string.cadence_smoothing)))
            form.addView(
                segmented(Smoothing.entries.map { it.label to it }, store.cadenceSmoothing.value) { picked ->
                    store.setCadenceSmoothing(picked)
                    render()
                },
            )
            form.addView(settingsHelp(getString(R.string.cadence_smoothing_help)))
        }
        form.addView(settingsDivider())

        // Top row of the workout screen: one value or two (Left and Right), each heart rate,
        // cadence or the target; what isn't at the top goes in the tile next to Lap avg.
        // Design: docs/design/hr-targets/ (Two values at the top).
        form.addView(settingsHeading(R.drawable.ic_top_row, getString(R.string.top_row)))
        val topOptions = listOf(R.string.heart_rate to TopRow.HEART_RATE, R.string.cadence to TopRow.CADENCE, R.string.target to TopRow.TARGET)
        val left = store.topRow.value ?: if (store.secondField.value == SecondField.CADENCE) TopRow.CADENCE else TopRow.HEART_RATE
        val right = store.topRowRight.value?.takeIf { it != left }
        form.addView(
            segmented(listOf(R.string.top_row_one to false, R.string.top_row_two to true), right != null) { two ->
                // Two: the right one starts as the first that isn't on the left
                store.setTopRowRight(if (two) TopRow.entries.first { it != left } else null)
                render()
            },
        )
        form.addView(settingsSubLabel(getString(R.string.top_row_left)))
        form.addView(
            segmented(topOptions, left) { picked ->
                store.setTopRow(picked)
                // The right one can't be the same: it moves to the first other one
                if (store.topRowRight.value == picked) store.setTopRowRight(TopRow.entries.first { it != picked })
                render()
            },
        )
        if (right != null) {
            form.addView(settingsSubLabel(getString(R.string.top_row_right)))
            form.addView(segmented(topOptions, right, disabled = left) { picked -> store.setTopRowRight(picked); render() })
        }
        form.addView(settingsHelp(getString(R.string.top_row_help)))
        form.addView(settingsDivider())

        // Update notices: Off / On (see UpdateCheck)
        form.addView(settingsHeading(R.drawable.ic_update, getString(R.string.update_notices)))
        form.addView(
            segmented(listOf(R.string.off to false, R.string.on to true), UpdateCheck.isEnabled(this)) { on ->
                UpdateCheck.setEnabled(this, on)
                render()
            },
        )
        form.addView(settingsHelp(getString(R.string.update_notices_help)))
        form.addView(settingsDivider())

        // intervals.icu: one row, opens the Account page
        val status = getString(if (store.hasCredentials) R.string.connected else R.string.not_connected)
        form.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                isClickable = true
                setOnClickListener { startActivity(Intent(this@SettingsActivity, AccountActivity::class.java)) }
                addView(settingsIcon(R.drawable.ic_person))
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(settingsRowTitle(getString(R.string.intervals_account)))
                        addView(settingsHelp(status).apply { setPadding(0, 0, 0, 0) })
                    },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
                addView(settingsIcon(R.drawable.ic_chevron))
            },
        )
        form.addView(settingsDivider())

        // The first-run introduction, again (e.g. to see how to add the field to a profile)
        form.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                isClickable = true
                setOnClickListener { startActivity(Intent(this@SettingsActivity, IntroActivity::class.java).putExtra(IntroActivity.EXTRA_AGAIN, true)) }
                addView(settingsIcon(R.drawable.ic_info))
                addView(settingsRowTitle(getString(R.string.intro_again)), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(settingsIcon(R.drawable.ic_chevron))
            },
        )
    }
}
