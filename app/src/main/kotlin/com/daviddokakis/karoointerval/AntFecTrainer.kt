package com.daviddokakis.karoointerval

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.dsi.ant.plugins.antplus.pcc.AntPlusFitnessEquipmentPcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pcc.defines.RequestStatus
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.math.BigDecimal

/**
 * The smart trainer over ANT+ FE-C, through the ANT+ Plugin Service: target power (page 49),
 * handing back as a flat road (page 51 at 0 %), the trainer's watts from its trainer data
 * (page 25). For trainers the app can't reach over Bluetooth, like the original Tacx NEO (T2800):
 * its one Bluetooth connection is usually the Karoo's, and it has no FTMS.
 *
 * Only works while the Karoo itself isn't connected to the trainer over ANT+: the Karoo's ANT chip
 * hands every message from a trainer to the one channel tracking it, so a second channel to the
 * same trainer never finds it (tested on a Karoo 3, docs/design/indoor-erg). The rider pairs the
 * trainer on the Karoo over ANT+ and switches it off on the indoor profile; [ErgController.trainerIn]
 * then picks this link. The Karoo records power from another sensor (the trainer's Bluetooth
 * power, or a power meter).
 *
 * Plugin calls block for a moment, so everything runs on its own thread. Reconnects by itself
 * until [stop].
 */
class AntFecTrainer(context: Context) : TrainerLink {
    private val app = context.applicationContext
    private val h = Handler(HandlerThread("ant-fec").apply { start() }.looper)

    private val _link = MutableStateFlow(ErgLink.OFF)
    override val link: StateFlow<ErgLink> = _link
    @Volatile override var watts: Int? = null
        private set
    @Volatile override var wattsAtMs = 0L
        private set
    @Volatile override var problem: String? = null
        private set

    /** The trainer's ANT+ device number while we want it. */
    private var number: Int? = null
    private var handle: PccReleaseHandle<AntPlusFitnessEquipmentPcc>? = null
    private var pcc: AntPlusFitnessEquipmentPcc? = null
    /** Bumps with every open and close: callbacks from an older handle are ignored. */
    private var generation = 0
    private var pending: Int? = null
    private var sent: Int? = null
    private var controlled = false
    /** A plugin request is out (the plugin takes one at a time); since when, in case it never answers. */
    private var busySinceMs = 0L
    private var retryMs = RETRY_MIN_MS
    private val reopen = Runnable { if (number != null && pcc == null) open() }

    override fun start(match: ErgTrainer.Match) {
        h.post {
            val n = match.antNumber ?: return@post
            if (n == number && _link.value != ErgLink.OFF) return@post
            stopNow()
            number = n
            open()
        }
    }

    override fun setTarget(watts: Int) {
        h.post {
            pending = watts
            controlled = true
            flush()
        }
    }

    override fun release() {
        h.post {
            pending = null
            if (!controlled) return@post
            controlled = false
            Timber.i("ERG: handed the trainer back (ANT+)")
            flatRoad(FLAT_TRIES)
        }
    }

    override fun stop() { h.post { stopNow() } }

    private fun stopNow() {
        h.removeCallbacks(reopen)
        val p = pcc
        if (controlled && p != null) runCatching { p.trainerMethods.requestSetTrackResistance(BigDecimal.ZERO, ROLLING) { } }
        controlled = false; pending = null; number = null
        // The flat road goes out before the channel closes.
        closeHandle(delayMs = if (p != null) 300L else 0L)
        _link.value = ErgLink.OFF
    }

    // ---- connect -------------------------------------------------------------------------------

    private fun open() {
        val n = number ?: return
        val gen = ++generation
        _link.value = ErgLink.CONNECTING
        Timber.i("ERG: looking for ANT+ trainer %d", n)
        handle = try {
            AntPlusFitnessEquipmentPcc.requestNewOpenAccess(
                app, n, 0,
                { result, code, state -> h.post { if (gen == generation) onAccess(result, code, state) else result?.releaseAccess() } },
                { state -> h.post { if (gen == generation) onState(state) } },
                { _, _, _, _ -> },
            )
        } catch (e: Exception) {
            Timber.w(e, "ERG: ANT+ plugin")
            null
        }
        if (handle == null) lost("ANT+ Plugin Service not available")
    }

    private fun onAccess(result: AntPlusFitnessEquipmentPcc?, code: RequestAccessResult, state: DeviceState?) {
        if (code != RequestAccessResult.SUCCESS || result == null) {
            return lost(
                when (code) {
                    RequestAccessResult.SEARCH_TIMEOUT -> "trainer ${number} not found over ANT+"
                    RequestAccessResult.DEPENDENCY_NOT_INSTALLED -> "ANT+ Plugin Service missing"
                    else -> "ANT+: $code"
                },
            )
        }
        pcc = result
        Timber.i("ERG: connected to ANT+ trainer %d (%s, %s)", result.antDeviceNumber, result.deviceName, state)
        result.trainerMethods.subscribeRawTrainerDataEvent { _, _, _, _, power, _ ->
            if (power >= 0) { watts = power; wattsAtMs = System.currentTimeMillis() }
        }
        retryMs = RETRY_MIN_MS
        problem = null
        _link.value = ErgLink.READY
        // Control only once there's a target: before START the trainer stays free.
        if (pending != null) flush()
    }

    private fun onState(state: DeviceState) {
        when (state) {
            DeviceState.DEAD, DeviceState.CLOSED -> lost("trainer dropped (ANT+ $state)")
            DeviceState.SEARCHING -> if (pcc != null) _link.value = ErgLink.CONNECTING
            DeviceState.TRACKING, DeviceState.PROCESSING_REQUEST -> if (pcc != null) {
                if (_link.value != ErgLink.READY) { _link.value = ErgLink.READY; sent = null; flush() }
            }
            else -> {}
        }
    }

    // ---- control -------------------------------------------------------------------------------

    private fun flush() {
        val p = pcc ?: return
        val w = (pending ?: return).coerceIn(0, ErgPlanner.MAX_WATTS)
        val now = System.currentTimeMillis()
        if (busySinceMs != 0L && now - busySinceMs < BUSY_MS) return // its answer sends the newest
        busySinceMs = now
        val ok = runCatching {
            p.trainerMethods.requestSetTargetPower(BigDecimal(w)) { status -> h.post { onSent(w, status) } }
        }.onFailure { Timber.w(it, "ERG: target power") }.getOrDefault(false)
        if (!ok) { busySinceMs = 0L; Timber.w("ERG: %d W not sent (ANT+ plugin busy)", w) }
    }

    private fun onSent(w: Int, status: RequestStatus) {
        busySinceMs = 0L
        if (status == RequestStatus.SUCCESS) {
            if (sent != w) Timber.i("ERG: trainer at %d W (ANT+)", w)
            sent = w
        } else {
            Timber.w("ERG: %d W answered %s", w, status)
            sent = null
        }
        // A newer target came in meanwhile.
        val p = pending?.coerceIn(0, ErgPlanner.MAX_WATTS)
        if (controlled && p != null && p != sent) flush()
    }

    /** Page 51 at 0 %: FE-C has no "hand back", so the trainer rides like a flat road. */
    private fun flatRoad(tries: Int) {
        val p = pcc ?: return
        val ok = runCatching {
            p.trainerMethods.requestSetTrackResistance(BigDecimal.ZERO, ROLLING) { status ->
                if (status != RequestStatus.SUCCESS) Timber.w("ERG: flat road answered %s", status)
            }
        }.getOrDefault(false)
        sent = null
        if (!ok && tries > 1) h.postDelayed({ if (!controlled) flatRoad(tries - 1) }, 500)
    }

    private fun lost(why: String) {
        Timber.w("ERG: %s", why)
        problem = why
        closeHandle(0L)
        if (number == null) { _link.value = ErgLink.OFF; return }
        _link.value = ErgLink.LOST
        val delay = retryMs
        retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
        h.removeCallbacks(reopen)
        h.postDelayed(reopen, delay)
    }

    private fun closeHandle(delayMs: Long) {
        generation++
        val old = handle
        handle = null; pcc = null; sent = null; busySinceMs = 0L
        if (old != null) h.postDelayed({ runCatching { old.close() } }, delayMs)
    }

    companion object {
        /** Rolling resistance for the flat road: 0.004, a road bike on asphalt. */
        private val ROLLING = BigDecimal("0.004")
        private const val FLAT_TRIES = 3
        private const val BUSY_MS = 5_000L
        private const val RETRY_MIN_MS = 3_000L
        private const val RETRY_MAX_MS = 30_000L
    }
}
