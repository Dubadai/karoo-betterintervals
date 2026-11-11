package com.daviddokakis.karoointerval

import com.dubadai.karoo.common.*
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.net.wifi.WifiManager
import android.provider.Settings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.withContext

/**
 * Workouts page (Karoo app list, or the gear button on the interval screen):
 * the current workout with a way to stop it, and this week's planned rides from intervals.icu
 * to pick from. The intervals.icu API key is entered once and kept; "Account" changes it.
 */
class SetupActivity : AppearanceActivity() {

    private lateinit var store: PlanStore
    private lateinit var content: LinearLayout
    /** API key at the last sync, to re-sync when it changes on the Account page. */
    private var syncedKey: String? = null
    private var trainerName: String? = null
    private var loading = false
    private var error: String? = null
    /** Shown immediately from the last sync, then replaced when a sync succeeds. */
    private var week: List<PlannedWorkout> = emptyList()
    /** Last sync failed because there was no connection (no Wi-Fi and no phone). */
    private var offline = false
    private lateinit var karoo: KarooSystemService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlanStore.get(this)
        karoo = KarooSystemService(this)
        // The trainer paired on the Karoo, named in the ERG card.
        karoo.connect { connected ->
            if (!connected) return@connect
            karoo.addConsumer<SavedDevices> { e ->
                val name = e.devices.firstOrNull { it.enabled && "TYPE_TRAINER_ID" in it.supportedDataTypes }?.name
                if (name != trainerName) { trainerName = name; runOnUiThread { render() } }
            }
        }
        // The ERG card follows the trainer live (connecting → ready, off for this ride).
        IntervalExtension.session?.let { session ->
            scope.launch {
                session.state.map { it.erg to it.ergOffThisRide }.distinctUntilChanged().collect { render() }
            }
        }
        week = store.cachedWeek()

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
            clipToPadding = false // the loaded workout's ring sits just outside the cards' edges
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.settings_bg))
                addView(content, MATCH_PARENT, WRAP_CONTENT)
            },
        )
        render()
        when {
            store.hasCredentials -> load()
            // First run: the introduction (connect, look, add to a profile, tap the watts).
            // Riders who already have a key (updating from an earlier version) never see it.
            !store.introDone -> startActivity(Intent(this, IntroActivity::class.java))
            else -> openAccount()
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Back from the Account page with a new key: sync with it.
        if (store.hasCredentials && store.apiKey != syncedKey) load() else render()
    }

    /**
     * The ERG card (docs/design/indoor-erg/trainer-card, A): under Back to ride while ERG is on for
     * an indoor ride. ERG on or off for this ride, and how the trainer is. The rest of the trainer
     * settings are in Settings › Trainer (indoors).
     */
    private fun ergCard(): View? {
        val session = IntervalExtension.session ?: return null
        val st = session.state.value
        val erg = st.erg ?: return null
        val name = trainerName ?: getString(R.string.erg_card_trainer)
        val text = when (erg) {
            ErgStatus.READY -> getString(R.string.erg_card_ready, name)
            ErgStatus.HOLDING -> getString(R.string.erg_card_on, name)
            ErgStatus.FREE -> getString(R.string.erg_card_free, name)
            ErgStatus.CONNECTING -> getString(R.string.erg_card_connecting)
            ErgStatus.LOST -> getString(R.string.erg_card_lost)
            ErgStatus.OFF -> getString(R.string.erg_card_off)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Even room inside: the switch row brings its own 48 dp height, so less on top.
            setPadding(dp(14), dp(4), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                // ERG on: a clear green; off for this ride: grey.
                setColor(getColor(if (st.ergOffThisRide) R.color.erg_wait_bg else R.color.erg_card_on))
            }
            addView(
                settingsSwitchRow(R.drawable.ic_settings_target, getString(R.string.erg_card_title), !st.ergOffThisRide) { _ ->
                    session.toggleErg()
                },
            )
            addView(settingsHelp(text).apply { setPadding(0, 0, 0, 0) })
        }
        // "Indoor ride" just above the card, on the left.
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
            addView(settingsSubLabel(getString(R.string.erg_card_label)).apply { setPadding(0, 0, 0, dp(6)) })
            addView(card)
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openAccount() {
        startActivity(Intent(this, AccountActivity::class.java))
    }

    /**
     * While the page is open, sync as soon as a Wi-Fi network with internet comes up after a sync
     * failed for lack of connection (e.g. the rider just switched Wi-Fi on). Registering also reports
     * a network that's already up, which covers coming back from the Wi-Fi panel.
     */
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch {
                if (offline && !loading && store.hasCredentials) {
                    delay(WIFI_SETTLE_MS) // give the Karoo a moment to start using the new network
                    if (offline && !loading) load()
                }
            }
        }
    }

    /** Re-draws the "Enable Wi-Fi" hint when Wi-Fi is switched on or off. */
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (offline) render()
        }
    }

    override fun onStart() {
        super.onStart()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, wifiCallback) }
        ContextCompat.registerReceiver(
            this,
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiCallback) }
        runCatching { unregisterReceiver(wifiStateReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        karoo.disconnect()
        super.onDestroy()
    }

    /**
     * Syncs this week from intervals.icu through the Karoo (Wi-Fi, or the phone over Bluetooth).
     * [openWifiIfOffline]: when the user tapped Sync and there's no connection with Wi-Fi off,
     * open the Wi-Fi panel straight away (apps can't switch Wi-Fi on themselves).
     */
    private fun load(openWifiIfOffline: Boolean = false) {
        syncJob?.cancel()
        loading = true
        error = null
        render()
        syncedKey = store.apiKey
        syncJob = scope.launch {
            val result = withContext(Dispatchers.IO) { store.syncWeek(karoo) }
            loading = false
            offline = false
            result.onSuccess { week = it }
            result.onFailure {
                offline = it == SyncError.Offline
                // Offline is worded at render time, since it depends on whether Wi-Fi is on.
                error = when (it) {
                    SyncError.WrongKey -> getString(R.string.sync_wrong_key)
                    is SyncError.Server -> getString(R.string.sync_server_error, it.code)
                    SyncError.Offline -> null
                    else -> getString(R.string.setup_error)
                }
            }
            render()
            if (offline && !wifiEnabled() && openWifiIfOffline) openWifi()
        }
    }

    private fun wifiEnabled(): Boolean =
        runCatching { (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled }
            .getOrDefault(true)

    /** Opens the Wi-Fi quick panel (or full Wi-Fi settings) so the rider can switch it on. */
    private fun openWifi() {
        runCatching { startActivity(Intent(Settings.Panel.ACTION_WIFI)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
    }

    private fun render() {
        content.removeAllViews()

        // Header: title + Settings, then Back to ride (the page's main action) + Sync
        content.addView(
            row(
                text(getString(R.string.workouts_title), 22f, bold = true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                },
                iconButton(R.drawable.ic_settings, getString(R.string.settings_title)) { openSettings() },
            ),
        )
        content.addView(
            row(
                settingsButton(SettingsButton.MAIN, getString(R.string.back_to_ride)) { goToRide() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }
                },
                settingsButton(SettingsButton.ADD, getString(if (loading) R.string.syncing else R.string.sync)) {
                    if (!loading && store.hasCredentials) load(openWifiIfOffline = true)
                }.apply { layoutParams = LinearLayout.LayoutParams(dp(96), dp(48)) },
            ).apply { setPadding(0, dp(8), 0, 0) },
        )
        ergCard()?.let { content.addView(it) }
        syncLine()?.let { content.addView(it) }

        // Scale workout (only with a workout loaded): its power targets in % of the plan, 1% a tap,
        // like the Karoo's own SCALE − 100% +. Not for a workout with only heart-rate targets.
        val current = store.plan.value
        val pct = store.scalePct.value
        if (current != null && !current.hrOnly) {
            content.addView(settingsHeading(R.drawable.ic_settings_power, getString(R.string.scale_workout)))
            content.addView(settingsIntro(getString(R.string.scale_help)))
            content.addView(scaleControl(pct))
            content.addView(settingsDivider())
        }

        // Current workout (drawn at its scale), Remove under it; only when one is loaded
        if (current != null) {
            val currentWorkout = week.firstOrNull { it.plan.id == current.id }
            content.addView(settingsHeading(R.drawable.ic_settings_intro, getString(R.string.current_workout)))
            content.addView(card(current.scaled(pct), currentWorkout?.date, loaded = true, onClick = null))
            content.addView(
                settingsButton(SettingsButton.REMOVE, getString(R.string.stop_workout)) {
                    store.clearSelection()
                    render()
                }.apply {
                    background = rounded(R.color.remove_tonal, 28)
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) }
                },
            )
            content.addView(
                settingsDivider().apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(8) }
                },
            )
        }

        // The week, without a heading: the day headings say it
        when {
            !store.hasCredentials -> {
                content.addView(text(getString(R.string.need_api_key), 14f, secondary = true).apply { setPadding(0, dp(16), 0, 0) })
                // Back to ride is the page's one yellow button
                content.addView(settingsButton(SettingsButton.ADD, getString(R.string.connect_intervals)) { openAccount() })
            }
            loading && week.isEmpty() -> content.addView(text(getString(R.string.setup_loading), 14f, secondary = true))
            error != null -> content.addView(text(error!!, 14f, secondary = true))
        }
        if (store.hasCredentials && !loading && error == null && !offline && week.isEmpty()) {
            content.addView(text(getString(R.string.none_this_week), 14f, secondary = true))
        }
        val today = LocalDate.now()
        // Today first, then the coming days, then the days already past this week. The loaded
        // workout is already shown above, so it isn't listed again (nor a day left empty by it).
        val byDate = week.filter { it.plan.id != current?.id }.groupBy { it.date }
        val days = byDate.keys.sortedWith(compareBy({ if (it == today) 0 else if (it > today) 1 else 2 }, { it }))
        days.forEach { date ->
            val label = if (date == today) getString(R.string.today_label, date.format(DAY_FORMAT)) else date.format(DAY_FORMAT)
            content.addView(
                TextView(this).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(getColor(if (date == today) R.color.settings_title else R.color.settings_help))
                    if (date == today) setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(2), dp(8), 0, dp(6))
                },
            )
            byDate.getValue(date).forEach { w ->
                content.addView(
                    card(w.plan, w.date, loaded = false) {
                        store.select(w)
                        goToRide()
                    },
                )
            }
        }
    }

    /**
     * One line under the buttons: when it last synced, and when offline why, with Turn on for
     * Wi-Fi when it's off (apps can't switch it on themselves). Null when there's nothing to say.
     */
    private fun syncLine(): View? {
        val synced = store.lastSynced?.let {
            val time = if (it.toLocalDate() == LocalDate.now()) it.format(TIME_FORMAT) else it.format(DATE_TIME_FORMAT)
            getString(R.string.synced_at, time)
        }
        val wifiOff = offline && !wifiEnabled()
        val problem = when {
            wifiOff -> getString(R.string.wifi_off)
            offline -> getString(R.string.offline)
            else -> null
        }
        if (synced == null && problem == null) return null
        fun small(value: String, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(getColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(16), 0, dp(8))
            synced?.let { addView(small(it, R.color.settings_help)) }
            if (problem != null) {
                if (synced != null) addView(small("  ·  ", R.color.settings_help))
                addView(small(problem, R.color.settings_warn, bold = true))
            }
            if (wifiOff) {
                addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(
                    small(getString(R.string.turn_on), R.color.segment_text_selected, bold = true).apply {
                        background = rounded(R.color.segment_selected, 16)
                        setPadding(dp(12), dp(5), dp(12), dp(5))
                        isClickable = true
                        setOnClickListener { openWifi() }
                    },
                )
            }
        }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(getColor(color))
        cornerRadius = dp(radiusDp).toFloat()
    }

    /**
     * Back to the ride screen. Opened from the interval screen, closing this page is enough;
     * opened from the app list, open the Karoo ride app.
     */
    private fun goToRide() {
        if (!intent.getBooleanExtra(EXTRA_FROM_RIDE, false)) {
            packageManager.getLaunchIntentForPackage(RIDE_APP)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    /**
     * A workout card: name, a small profile of the workout, then its time (and, for the loaded one,
     * its day and scale). [loaded]: faint blue inside the selection ring (the Apple-blue outline a
     * little outside the card, as in SmallerFields' settings).
     */
    private fun card(plan: WorkoutPlan, date: LocalDate?, loaded: Boolean, onClick: (() -> Unit)?): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(if (loaded) R.color.select_tint else R.color.segment_track, 20)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(text(plan.name, 16f, bold = true).apply { setPadding(0, 0, 0, dp(6)) })
            addView(thumbnail(plan))
            val day = date?.takeIf { loaded }?.let { if (it == LocalDate.now()) getString(R.string.today) else it.format(DAY_FORMAT) }
            val scale = plan.scalePct.takeIf { it != 100 && !plan.hrOnly }?.let { getString(R.string.scale_pct, it) }
            addView(
                TextView(this@SetupActivity).apply {
                    text = listOfNotNull(formatClock(plan.totalSec), day, scale).joinToString(" · ")
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(getColor(R.color.settings_help))
                    setPadding(0, dp(6), 0, 0)
                },
            )
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        if (!loaded) {
            card.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
            return card
        }
        // 3 dp ring, 2 dp gap; the ring sits outside the card's edges, so the card lines up with the others
        val ring = RING_STROKE_DP + RING_GAP_DP
        return FrameLayout(this).apply {
            setPadding(dp(ring), dp(ring), dp(ring), dp(ring))
            background = GradientDrawable().apply {
                cornerRadius = dp(20 + ring).toFloat()
                setStroke(dp(RING_STROKE_DP), getColor(R.color.select_blue))
            }
            isSelected = true
            addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(-dp(ring), 0, -dp(ring), 0)
            }
        }
    }

    /** − 100% +: a pale pill track, the buttons filled like a picked option. */
    private fun scaleControl(pct: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(R.color.segment_track, 28)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        fun step(icon: Int, description: Int, delta: Int) = FrameLayout(this@SetupActivity).apply {
            background = rounded(R.color.segment_selected, 20)
            contentDescription = getString(description)
            isClickable = true
            setOnClickListener {
                store.setScalePct(store.scalePct.value + delta)
                render()
            }
            addView(
                ImageView(this@SetupActivity).apply {
                    setImageResource(icon)
                    setColorFilter(getColor(R.color.segment_text_selected))
                },
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER),
            )
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(40))
        }
        addView(step(R.drawable.ic_minus, R.string.cd_scale_down, -1))
        addView(
            TextView(this@SetupActivity).apply {
                text = getString(R.string.scale_pct, pct)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(getColor(R.color.settings_title))
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        addView(step(R.drawable.ic_plus, R.string.cd_scale_up, +1))
    }

    private fun thumbnail(plan: WorkoutPlan): ImageView {
        val w = resources.displayMetrics.widthPixels - dp(48)
        val h = dp(48)
        return ImageView(this).apply {
            // The rider's own heart rate zones, so heart-rate steps have the ride screen's colours
            setImageBitmap(ChartRenderer(this@SetupActivity).render(ScreenState(plan = plan, hrZones = store.hrZones), w, h, 1f, ChartRenderer.Style.CARD))
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = getString(R.string.cd_chart)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, h)
        }
    }

    companion object {
        /** Set when opened from the gear button on the interval screen. */
        const val EXTRA_FROM_RIDE = "from_ride"
        private const val RIDE_APP = "io.hammerhead.rideapp"
        private const val WIFI_SETTLE_MS = 2_000L
        private const val RING_STROKE_DP = 3
        private const val RING_GAP_DP = 2
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
    }
}
