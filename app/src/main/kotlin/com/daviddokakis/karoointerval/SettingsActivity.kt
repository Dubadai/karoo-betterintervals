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
        ).copy(topRowRight = store.topRowRight.value, rightItems = store.rightItems.value, rightIndex = 0, zonePalette = store.zonePalette.value, calories = 412)
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
        form.addView(settingsIntro(getString(R.string.appearance_help)))
        form.addView(
            settingsSegmented(appearanceOptions(), store.appearance.value) { picked ->
                store.setAppearance(picked)
                recreate()
            },
        )
        form.addView(settingsDivider())

        // Tiles: Tonal / Outline (the Lap avg, Target and HR tiles on the workout screen)
        form.addView(settingsHeading(R.drawable.ic_settings_tiles, getString(R.string.tiles)))
        form.addView(settingsIntro(getString(R.string.tiles_help)))
        form.addView(
            settingsSegmented(named(R.string.tiles_tonal to false, R.string.tiles_outline to true), store.tileOutline.value) { on ->
                store.setTileOutline(on)
                render()
            },
        )
        form.addView(settingsDivider())

        // Heart rate or cadence in the HR spots
        form.addView(settingsHeading(R.drawable.ic_settings_heart, getString(R.string.second_field)))
        form.addView(settingsIntro(getString(R.string.second_field_help)))
        form.addView(
            settingsSegmented(
                named(R.string.heart_rate to SecondField.HEART_RATE, R.string.cadence to SecondField.CADENCE),
                store.secondField.value,
            ) { picked ->
                store.setSecondField(picked)
                render()
            },
        )
        form.addView(settingsDivider())

        // Top row of the workout screen: one value or two (Left and Right), each heart rate,
        // cadence or the target; what isn't at the top goes in the tile next to Lap avg.
        // Design: docs/design/hr-targets/ (Two values at the top).
        form.addView(settingsHeading(R.drawable.ic_settings_top_row, getString(R.string.top_row)))
        form.addView(settingsIntro(getString(R.string.top_row_help)))
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
        form.addView(settingsDivider())

        // Right tile: the metrics its tap goes through, any of them (docs/design/indoor-ride-notes).
        // Not set yet: on is what the tile shows today (the top row's leftover).
        form.addView(settingsHeading(R.drawable.ic_settings_one_tile, getString(R.string.right_tile)))
        form.addView(settingsIntro(getString(R.string.right_tile_help)))
        val onNow = store.rightItems.value ?: listOf(
            when {
                TopRow.TARGET !in listOfNotNull(left, right) -> RightItem.TARGET
                TopRow.HEART_RATE !in listOfNotNull(left, right) -> RightItem.HEART_RATE
                else -> RightItem.CADENCE
            },
        )
        listOf(
            Triple(RightItem.TARGET, R.drawable.ic_settings_target, R.string.target),
            Triple(RightItem.CALORIES, R.drawable.ic_settings_calories, R.string.calories),
            Triple(RightItem.SPEED, R.drawable.ic_settings_speed, R.string.speed),
            Triple(RightItem.LAP_NP, R.drawable.ic_settings_lap, R.string.np),
            Triple(RightItem.HEART_RATE, R.drawable.ic_settings_heart, R.string.heart_rate),
            Triple(RightItem.CADENCE, R.drawable.ic_settings_cadence, R.string.cadence),
        ).forEach { (item, icon, name) ->
            // The last one on stays on: the tile always shows something
            val on = item in onNow
            form.addView(
                settingsSwitchRow(icon, getString(name), on) { now ->
                    // Switching the last one off does nothing (the switch flips back)
                    if (now || onNow.size > 1) store.setRightItems(if (now) onNow + item else onNow - item)
                    render()
                },
            )
        }
        form.addView(settingsDivider())

        // Zone colours: the chart's power zones as the Karoo, Zwift or intervals.icu colour them
        form.addView(settingsHeading(R.drawable.ic_settings_colours, getString(R.string.zone_colours)))
        form.addView(settingsIntro(getString(R.string.zone_colours_help)))
        val palettes = listOf(ZonePalette.KAROO to R.string.zone_karoo, ZonePalette.ZWIFT to R.string.zone_zwift, ZonePalette.INTERVALS to R.string.zone_intervals)
        val cards = settingsChoiceCards(palettes.map { (p, n) -> Triple(getString(n), null, p) }, store.zonePalette.value) { picked ->
            store.setZonePalette(picked)
            render()
        }
        // Each card shows its seven zones under its name
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        palettes.forEachIndexed { i, (p, _) ->
            val texts = ((cards.getChildAt(i) as? android.view.ViewGroup)?.getChildAt(0) as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.LinearLayout
            texts?.addView(zoneStrip(p, night))
        }
        form.addView(cards)
        form.addView(settingsDivider())

        // Power: how the big watts are smoothed; cadence's own smoothing when cadence is shown
        form.addView(settingsHeading(R.drawable.ic_settings_power, getString(R.string.power_smoothing)))
        form.addView(settingsIntro(getString(R.string.power_smoothing_help)))
        form.addView(
            settingsSegmented(Smoothing.entries.map { getString(it.label) to it }, store.powerSmoothing.value) { picked ->
                store.setPowerSmoothing(picked)
                render()
            },
        )
        form.addView(settingsDivider())
        if (store.secondField.value == SecondField.CADENCE) {
            form.addView(settingsHeading(R.drawable.ic_settings_cadence, getString(R.string.cadence_smoothing)))
            form.addView(settingsIntro(getString(R.string.cadence_smoothing_help)))
            form.addView(
                settingsSegmented(Smoothing.entries.map { getString(it.label) to it }, store.cadenceSmoothing.value) { picked ->
                    store.setCadenceSmoothing(picked)
                    render()
                },
            )
            form.addView(settingsDivider())
        }

        // Trainer (indoors): ERG on an indoor ride profile (docs/design/indoor-erg)
        form.addView(settingsHeading(R.drawable.ic_settings_target, getString(R.string.erg_heading)))
        form.addView(settingsIntro(getString(R.string.erg_help)))
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
            form.addView(settingsIntro(getString(R.string.match_pm_help)))
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
        }
        form.addView(settingsDivider())

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

    /** A palette's seven zones as small rounded blocks, for its choice card. */
    private fun zoneStrip(p: ZonePalette, night: Boolean) = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (2 * resources.displayMetrics.density).toInt())
        val d = resources.displayMetrics.density
        for (z in 1..7) {
            addView(
                android.view.View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 3 * d
                        setColor(p.fill(z, night))
                        p.edge(z, night)?.let { setStroke((1.5f * d).toInt().coerceAtLeast(1), it) }
                    }
                },
                android.widget.LinearLayout.LayoutParams((21 * d).toInt(), (15 * d).toInt()).apply { marginEnd = (4 * d).toInt() },
            )
        }
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** "Last ride with Power 12027, trainer vs PM: 150 W +5 %, 250 W +3 %. Cold start +7 %, warm after 12 min." */
    private fun matchSummary(pm: String?, s: PmSummary): String {
        val bands = s.bands.joinToString(", ") { (w, p) -> "$w W ${if (p >= 0) "+" else "\u2212"}${kotlin.math.abs(p)} %" }
        val head = if (pm != null) getString(R.string.match_pm_last_named, pm, bands) else getString(R.string.match_pm_last, bands)
        val cold = s.coldPct; val warm = s.warmMin
        return if (cold != null && warm != null) head + " " + getString(R.string.match_pm_cold, if (cold >= 0) "+$cold" else "\u2212${-cold}", warm) else head
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
