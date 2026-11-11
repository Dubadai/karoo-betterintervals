package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.SavedDevices
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
    private lateinit var karoo: KarooSystemService
    /** The trainer paired on the Karoo, as Settings names it; null: none (or not heard yet). */
    private var trainerName: String? = null

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
        // The trainer paired on the Karoo, for the Trainer section.
        karoo = KarooSystemService(this)
        karoo.connect { connected ->
            if (!connected) return@connect
            karoo.addConsumer<SavedDevices> { e ->
                val t = e.devices.firstOrNull { it.enabled && "TYPE_TRAINER_ID" in it.supportedDataTypes }
                val name = t?.let { getString(R.string.erg_trainer_found, it.name, if (it.connectionType == "BLE") "Bluetooth" else "ANT+") }
                if (name != trainerName) { trainerName = name; runOnUiThread { render() } }
            }
        }
    }

    override fun onDestroy() {
        karoo.disconnect()
        super.onDestroy()
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        BLUETOOTH_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

        // Trainer (indoors): ERG on an indoor ride profile (docs/design/indoor-erg)
        form.addView(settingsHeading(R.drawable.ic_settings_target, getString(R.string.erg_heading)))
        form.addView(
            settingsSwitchRow(R.drawable.ic_settings_power, getString(R.string.erg_on), store.ergOn.value) { on ->
                store.setErgOn(on)
                if (on && !hasBluetoothPermission() && Build.VERSION.SDK_INT >= 31) requestPermissions(BLUETOOTH_PERMISSIONS, 1)
                render()
            },
        )
        if (store.ergOn.value) {
            when {
                !hasBluetoothPermission() -> form.addView(settingsInfoRow(R.drawable.ic_settings_info, getString(R.string.erg_no_permission), SettingsTone.WARN))
                trainerName == null -> form.addView(settingsInfoRow(R.drawable.ic_settings_info, getString(R.string.erg_no_trainer), SettingsTone.WARN))
                else -> form.addView(settingsInfoRow(R.drawable.ic_settings_info, trainerName!!, SettingsTone.OK))
            }
            form.addView(settingsSubLabel(getString(R.string.erg_when_paused)))
            form.addView(
                settingsSegmented(RECOVERY_CHOICES.map { getString(R.string.erg_pct_ftp, it) to it }, store.ergRecoveryPct.value) { pct ->
                    store.setErgRecoveryPct(pct)
                    render()
                },
            )
            // Match PM (docs/design/indoor-erg/powermatch)
            val match = store.matchPm.value
            form.addView(
                settingsSwitchRow(R.drawable.ic_settings_power, getString(R.string.match_pm), match) { on ->
                    store.setMatchPm(on)
                    render()
                }.apply {
                    // Air between the When paused buttons and this row, as in the mock.
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() }
                },
            )
            if (match) {
                val last = store.pmLastRide.value
                val sum = last?.summary
                when {
                    last == null || (!last.fromTrainer && sum == null) ->
                        form.addView(settingsInfoRow(R.drawable.ic_settings_info, getString(R.string.match_pm_new)))
                    last.fromTrainer ->
                        form.addView(settingsInfoRow(R.drawable.ic_settings_info, getString(R.string.match_pm_from_trainer), SettingsTone.WARN))
                    else -> form.addView(settingsInfoRow(R.drawable.ic_settings_info, matchSummary(last.pmName, sum!!), SettingsTone.OK))
                }
            }
            form.addView(settingsHelp(getString(if (match) R.string.match_pm_help_on else R.string.match_pm_help_off)))
        }
        section(R.string.erg_help)

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

    /** "Last ride with Power 12027, trainer vs PM: 150 W +5 %, 250 W +3 %. Cold start +7 %, warm after 12 min." */
    private fun matchSummary(pm: String?, s: PmSummary): String {
        val bands = s.bands.joinToString(", ") { (w, p) -> "$w W ${if (p >= 0) "+" else "\u2212"}${kotlin.math.abs(p)} %" }
        val head = if (pm != null) getString(R.string.match_pm_last_named, pm, bands) else getString(R.string.match_pm_last, bands)
        val cold = s.coldPct; val warm = s.warmMin
        return if (cold != null && warm != null) head + " " + getString(R.string.match_pm_cold, if (cold >= 0) "+$cold" else "\u2212${-cold}", warm) else head
    }

    private fun section(help: Int) {
        form.addView(settingsHelp(getString(help)))
        form.addView(settingsDivider())
    }

    companion object {
        internal val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        internal val RECOVERY_CHOICES = listOf(40, 50, 60)
    }

    private fun <T> named(vararg options: Pair<Int, T>) = options.map { (name, value) -> getString(name) to value }
}

/** Dark / Light / Auto, for Settings and the introduction. */
fun Context.appearanceOptions() = listOf(
    getString(R.string.appearance_dark) to Appearance.DARK,
    getString(R.string.appearance_light) to Appearance.LIGHT,
    getString(R.string.appearance_auto) to Appearance.AUTO,
)
