package com.daviddokakis.karoointerval

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dsi.ant.plugins.antplus.pcc.AntPlusFitnessEquipmentPcc
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.ManufacturerInfo
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.OnManufacturerInfo
import timber.log.Timber

/**
 * The trainer as a sensor in the Karoo's list (Sensors › Add sensor › the trainer BetterIntervals
 * lists): power, cadence and speed from the app's own ANT+ connection ([AntFecTrainer]). The
 * Karoo can't share a trainer's ANT+ connection with the app, so with Settings › Trainer ›
 * Connection › ANT+ the Karoo records the trainer through this sensor instead of its own ANT+
 * pairing, and sees the same connection the app controls.
 *
 * Offered only with Connection › ANT+; Bluetooth riders don't see it.
 */
class TrainerSensor(
    private val context: Context,
    private val extension: String,
    private val link: AntFecTrainer,
    private val store: PlanStore,
    /** The ANT+ trainers paired on the Karoo: device number and name. */
    private val knownTrainers: () -> List<Pair<Int, String>>,
) {
    private val main = Handler(Looper.getMainLooper())

    fun device(number: Int, name: String) = Device(
        extension, uid(number),
        listOf(DataType.Source.POWER, DataType.Source.CADENCE, DataType.Source.SPEED),
        context.getString(R.string.sensor_name, name),
    )

    /**
     * Karoo Sensors › Add sensor: the ANT+ trainers the Karoo knows, and the first one heard
     * nearby (a trainer the Karoo itself is connected to can't be heard, see [AntFecTrainer]).
     */
    fun scan(emitter: Emitter<Device>) {
        if (store.ergConnection.value != ErgConnection.ANT) return
        val offered = java.util.Collections.synchronizedSet(HashSet<Int>())
        fun offer(n: Int, name: String) { if (offered.add(n)) emitter.onNext(device(n, name)) }
        knownTrainers().forEach { (n, name) -> offer(n, name) }
        var handle: PccReleaseHandle<AntPlusFitnessEquipmentPcc>? = null
        main.post {
            handle = runCatching {
                AntPlusFitnessEquipmentPcc.requestNewOpenAccess(
                    context.applicationContext, 0, 0,
                    { result, code, _ ->
                        if (code == RequestAccessResult.SUCCESS && result != null) {
                            val n = result.antDeviceNumber
                            Timber.i("Sensor scan: heard ANT+ trainer %d", n)
                            result.releaseAccess()
                            offer(n, context.getString(R.string.sensor_trainer_number, n))
                        }
                    },
                    { },
                    { _, _, _, _ -> },
                )
            }.onFailure { Timber.w(it, "Sensor scan") }.getOrNull()
        }
        emitter.setCancellable { main.post { runCatching { handle?.close() } } }
    }

    /** The Karoo connects to the sensor (whenever the profile has it on, riding or not). */
    fun connect(uid: String, emitter: Emitter<DeviceEvent>) {
        val n = numberOf(uid)
        if (n == null) { emitter.onNext(OnConnectionStatus(ConnectionStatus.DISCONNECTED)); return }
        val l = object : AntFecTrainer.Listener {
            @Volatile var last: AntFecTrainer.Reading? = null
            var makerSent = false
            override fun onLink(link: ErgLink) {
                emitter.onNext(
                    OnConnectionStatus(
                        when (link) {
                            ErgLink.READY -> ConnectionStatus.CONNECTED
                            ErgLink.CONNECTING, ErgLink.LOST -> ConnectionStatus.SEARCHING
                            ErgLink.OFF -> ConnectionStatus.DISCONNECTED
                        },
                    ),
                )
            }
            override fun onReading(r: AntFecTrainer.Reading) { last = r }
            override fun onMaker(manufacturerId: Int, model: Int) {
                if (makerSent) return
                makerSent = true
                emitter.onNext(OnManufacturerInfo(ManufacturerInfo(makerName(manufacturerId), n.toString(), model.toString())))
            }
        }
        // The Karoo wants a reading every second, even if unchanged; nothing when the trainer is quiet.
        val tick = object : Runnable {
            override fun run() {
                val r = l.last
                if (r != null && System.currentTimeMillis() - r.atMs < STALE_MS) {
                    r.watts?.let { emitter.onNext(point(DataType.Source.POWER, DataType.Field.POWER, it.toDouble(), uid)) }
                    r.cadence?.let { emitter.onNext(point(DataType.Source.CADENCE, DataType.Field.CADENCE, it.toDouble(), uid)) }
                    r.speedMps?.let { emitter.onNext(point(DataType.Source.SPEED, DataType.Field.SPEED, it, uid)) }
                }
                main.postDelayed(this, 1_000)
            }
        }
        Timber.i("Sensor: Karoo connects to trainer %d", n)
        link.attachSensor(n, l)
        main.post(tick)
        emitter.setCancellable {
            Timber.i("Sensor: Karoo lets go of trainer %d", n)
            main.removeCallbacks(tick)
            link.detachSensor(l)
        }
    }

    private fun point(type: String, field: String, v: Double, uid: String) =
        OnDataPoint(DataPoint(type, values = mapOf(field to v), sourceId = uid))

    companion object {
        private const val PREFIX = "fec-"
        private const val STALE_MS = 3_000L

        fun uid(number: Int) = "$PREFIX$number"

        /** The ANT+ number from the sensor's uid, or from its id in the Karoo's list ("intervalscreen::fec-22877"). */
        fun numberOf(id: String): Int? {
            val uid = id.substringAfterLast("::")
            return if (uid.startsWith(PREFIX)) uid.removePrefix(PREFIX).toIntOrNull() else null
        }

        /** ANT+ manufacturer ids of the usual trainer makers. */
        fun makerName(id: Int): String? = when (id) {
            1 -> "Garmin"
            32 -> "Wahoo"
            86 -> "Elite"
            89 -> "Tacx"
            else -> null
        }
    }
}
