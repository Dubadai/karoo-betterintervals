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
 * The app's own ANT+ FE-C connection to the trainer, through the ANT+ Plugin Service: target
 * power (page 49), handing back as a flat road (page 51 at 0 %), and the trainer's power,
 * cadence (page 25) and speed (page 16). Settings › Trainer › Connection › ANT+ ([ErgConnection]).
 * For trainers the app can't reach over Bluetooth, like the original Tacx NEO (T2800): its one
 * Bluetooth connection is usually the Karoo's, and it has no FTMS.
 *
 * Two users share the one connection: ERG ([start] … [stop]) and the trainer sensor the app adds to
 * the Karoo ([attachSensor] … [detachSensor], see [TrainerSensor]), so the Karoo records the same
 * trainer the app controls. It stays open while either wants it.
 *
 * Only works while the Karoo itself isn't connected to the trainer over ANT+: the Karoo's ANT chip
 * hands every message from a trainer to the one channel tracking it, so a second channel to the
 * same trainer never finds it (tested on a Karoo 3, docs/design/indoor-erg).
 *
 * Plugin calls block for a moment, so everything runs on its own thread. Reconnects by itself.
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

    /** What the trainer sends, for the Karoo sensor. */
    data class Reading(
        val watts: Int?,
        val cadence: Int?,
        val speedMps: Double?,
        val atMs: Long,
        /** The trainer's own distance count in metres since the connection opened, if it keeps one. */
        val distanceM: Long? = null,
    )

    /** The sensor side: connection changes and readings. Called on the link's thread. */
    interface Listener {
        fun onLink(link: ErgLink)
        fun onReading(r: Reading)
        fun onMaker(manufacturerId: Int, model: Int)
    }

    /** The trainer ERG wants, and the one the Karoo's sensor wants (normally the same). */
    private var ergNumber: Int? = null
    private var sensorNumber: Int? = null
    private var listener: Listener? = null

    /** The trainer the connection is for. */
    private var number: Int? = null
    private var handle: PccReleaseHandle<AntPlusFitnessEquipmentPcc>? = null
    private var pcc: AntPlusFitnessEquipmentPcc? = null
    /** Bumps with every open and close: callbacks from an older handle are ignored. */
    private var generation = 0
    private var pending: Int? = null
    private var sent: Int? = null
    private var sentAtMs = 0L
    private var controlled = false
    /** A plugin request is out (the plugin takes one at a time); since when, in case it never answers. */
    private var busySinceMs = 0L
    /** The plugin needs a moment after each answer before it takes the next request. */
    private var nextOkAtMs = 0L
    private var retryMs = RETRY_MIN_MS
    private var cadence: Int? = null
    private var speedMps: Double? = null
    private var distanceM: Long? = null
    private val reopen = Runnable { if (number != null && pcc == null) open() }
    private val flushLater = Runnable { flush() }

    // ---- ERG -----------------------------------------------------------------------------------

    override fun start(match: ErgTrainer.Match) {
        h.post {
            val n = match.antNumber ?: return@post
            if (ergNumber == n) return@post
            ergNumber = n
            reconcile()
        }
    }

    override fun setTarget(watts: Int) {
        h.post {
            if (ergNumber == null) return@post
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

    /** ERG lets go: hands back; the connection stays while the Karoo's sensor wants it. */
    override fun stop() {
        h.post {
            if (ergNumber == null) return@post
            // The connection stays for the sensor: hand back now. Otherwise closing does it.
            if (controlled && sensorNumber != null && sensorNumber == number) { controlled = false; pending = null; flatRoad(FLAT_TRIES) }
            ergNumber = null
            reconcile()
        }
    }

    // ---- the Karoo's sensor --------------------------------------------------------------------

    fun attachSensor(antNumber: Int, l: Listener) {
        h.post {
            sensorNumber = antNumber
            listener = l
            l.onLink(_link.value)
            reconcile()
        }
    }

    fun detachSensor(l: Listener) {
        h.post {
            if (listener !== l) return@post
            listener = null
            sensorNumber = null
            reconcile()
        }
    }

    // ---- connect -------------------------------------------------------------------------------

    /** Opens, keeps or closes the connection for whoever wants it. ERG's trainer wins. */
    private fun reconcile() {
        val want = ergNumber ?: sensorNumber
        if (want == number) return
        if (number != null) close()
        number = want
        if (want != null) { retryMs = RETRY_MIN_MS; open() }
    }

    private fun setLink(l: ErgLink) {
        if (_link.value == l) return
        _link.value = l
        listener?.onLink(l)
    }

    private fun open() {
        val n = number ?: return
        val gen = ++generation
        setLink(ErgLink.CONNECTING)
        Timber.i("ERG: looking for ANT+ trainer %d", n)
        handle = try {
            AntPlusFitnessEquipmentPcc.requestNewOpenAccess(
                app, n, 0,
                { result, code, _ -> h.post { if (gen == generation) onAccess(result, code) else result?.releaseAccess() } },
                { state -> h.post { if (gen == generation) onState(state) } },
                { _, _, _, _ -> },
            )
        } catch (e: Exception) {
            Timber.w(e, "ERG: ANT+ plugin")
            null
        }
        if (handle == null) lost("ANT+ Plugin Service not available")
    }

    private fun onAccess(result: AntPlusFitnessEquipmentPcc?, code: RequestAccessResult) {
        if (code != RequestAccessResult.SUCCESS || result == null) {
            return lost(
                when (code) {
                    RequestAccessResult.SEARCH_TIMEOUT -> "trainer $number not found over ANT+"
                    RequestAccessResult.DEPENDENCY_NOT_INSTALLED -> "ANT+ Plugin Service missing"
                    else -> "ANT+: $code"
                },
            )
        }
        pcc = result
        Timber.i("ERG: connected to ANT+ trainer %d (%s)", result.antDeviceNumber, result.deviceName)
        val gen = generation
        result.trainerMethods.subscribeRawTrainerDataEvent { _, _, _, cad, power, _ ->
            h.post { if (gen == generation) onTrainerData(power, cad) }
        }
        result.subscribeGeneralFitnessEquipmentDataEvent { _, _, _, distance, speed, _, _, _ ->
            h.post {
                if (gen != generation) return@post
                speedMps = speed?.toDouble()?.takeIf { it >= 0 }
                distanceM = distance.takeIf { it >= 0 }
            }
        }
        result.subscribeManufacturerIdentificationEvent { _, _, _, maker, model ->
            h.post { if (gen == generation) listener?.onMaker(maker, model) }
        }
        retryMs = RETRY_MIN_MS
        problem = null
        setLink(ErgLink.READY)
        // Control only once there's a target: before START the trainer stays free.
        if (pending != null) flush()
    }

    private fun onTrainerData(power: Int, cad: Int) {
        val now = System.currentTimeMillis()
        if (power >= 0) { watts = power; wattsAtMs = now }
        cadence = cad.takeIf { it in 0..254 }
        listener?.onReading(Reading(watts, cadence, speedMps, now, distanceM))
    }

    private fun onState(state: DeviceState) {
        when (state) {
            DeviceState.DEAD, DeviceState.CLOSED -> lost("trainer dropped (ANT+ $state)")
            DeviceState.SEARCHING -> if (pcc != null) setLink(ErgLink.CONNECTING)
            DeviceState.TRACKING, DeviceState.PROCESSING_REQUEST -> if (pcc != null && _link.value != ErgLink.READY) {
                setLink(ErgLink.READY); sent = null; flush()
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
        // FE-C targets stay until changed and nobody else drives the trainer: no keep-alive needed.
        if (w == sent && now - sentAtMs < RESEND_MS) return
        if (now < nextOkAtMs) { h.removeCallbacks(flushLater); h.postDelayed(flushLater, nextOkAtMs - now); return }
        busySinceMs = now
        val ok = runCatching {
            p.trainerMethods.requestSetTargetPower(BigDecimal(w)) { status -> h.post { onSent(w, status) } }
        }.onFailure { Timber.w(it, "ERG: target power") }.getOrDefault(false)
        if (!ok) { busySinceMs = 0L; Timber.w("ERG: %d W not sent (ANT+ plugin busy)", w) }
    }

    private fun onSent(w: Int, status: RequestStatus) {
        val now = System.currentTimeMillis()
        busySinceMs = 0L
        nextOkAtMs = now + GAP_MS
        if (status == RequestStatus.SUCCESS) {
            if (sent != w) Timber.i("ERG: trainer at %d W (ANT+)", w)
            sent = w; sentAtMs = now
        } else {
            // Busy: the plugin was still confirming the one before; the newest goes after the gap.
            if (status != RequestStatus.FAIL_ALREADY_BUSY_EXTERNAL) Timber.w("ERG: %d W answered %s", w, status)
            sent = null
        }
        // A newer target came in meanwhile.
        val p = pending?.coerceIn(0, ErgPlanner.MAX_WATTS)
        if (controlled && p != null && p != sent) flush()
    }

    /** Page 51 at 0 %: FE-C has no "hand back", so the trainer rides like a flat road. */
    private fun flatRoad(tries: Int) {
        val p = pcc ?: return
        if (controlled) return // a new target came in meanwhile
        val now = System.currentTimeMillis()
        val wait = maxOf(nextOkAtMs - now, if (busySinceMs != 0L && now - busySinceMs < BUSY_MS) 500L else 0L)
        if (wait > 0) { h.postDelayed({ flatRoad(tries) }, wait); return }
        busySinceMs = now
        val ok = runCatching {
            p.trainerMethods.requestSetTrackResistance(BigDecimal.ZERO, ROLLING) { status ->
                h.post {
                    busySinceMs = 0L
                    nextOkAtMs = System.currentTimeMillis() + GAP_MS
                    if (status != RequestStatus.SUCCESS && tries > 1) flatRoad(tries - 1)
                    else if (status != RequestStatus.SUCCESS) Timber.w("ERG: flat road answered %s", status)
                }
            }
        }.getOrDefault(false)
        sent = null
        if (!ok) { busySinceMs = 0L; if (tries > 1) h.postDelayed({ flatRoad(tries - 1) }, 500) }
    }

    private fun lost(why: String) {
        Timber.w("ERG: %s", why)
        problem = why
        dropHandle(0L)
        if (number == null) { setLink(ErgLink.OFF); return }
        setLink(ErgLink.LOST)
        val delay = retryMs
        retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
        h.removeCallbacks(reopen)
        h.postDelayed(reopen, delay)
    }

    /** Nobody wants this trainer any more. A flat road (if ERG held it) goes out before it closes. */
    private fun close() {
        h.removeCallbacks(reopen)
        val p = pcc
        // After the plugin's gap, or it answers busy.
        val waitMs = (nextOkAtMs - System.currentTimeMillis()).coerceIn(0L, GAP_MS)
        if (controlled && p != null) {
            h.postDelayed({ runCatching { p.trainerMethods.requestSetTrackResistance(BigDecimal.ZERO, ROLLING) { } } }, waitMs)
        }
        controlled = false; pending = null
        dropHandle(if (p != null) waitMs + 300L else 0L)
        number = null
        setLink(ErgLink.OFF)
    }

    private fun dropHandle(delayMs: Long) {
        generation++
        val old = handle
        handle = null; pcc = null; sent = null; busySinceMs = 0L; nextOkAtMs = 0L
        watts = null; cadence = null; speedMps = null; distanceM = null
        h.removeCallbacks(flushLater)
        if (old != null) h.postDelayed({ runCatching { old.close() } }, delayMs)
    }

    companion object {
        /** Rolling resistance for the flat road: 0.004, a road bike on asphalt. */
        private val ROLLING = BigDecimal("0.004")
        private const val FLAT_TRIES = 3
        private const val BUSY_MS = 5_000L
        /** Between plugin requests: at 4 Hz the trainer acknowledges and confirms in about a second. */
        private const val GAP_MS = 1_000L
        /** The same target again only after this long (in case the trainer missed it). */
        private const val RESEND_MS = 10_000L
        private const val RETRY_MIN_MS = 3_000L
        private const val RETRY_MAX_MS = 30_000L
    }
}
