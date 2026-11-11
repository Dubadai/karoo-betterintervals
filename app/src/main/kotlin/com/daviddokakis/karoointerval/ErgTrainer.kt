package com.daviddokakis.karoointerval

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.UUID

/** How the link to the trainer is doing, for the ERG pill. */
enum class ErgLink { OFF, CONNECTING, READY, LOST }

/**
 * The smart trainer over Bluetooth FTMS (Fitness Machine Service): find it, connect, take control,
 * set target power, hand it back. The Karoo itself only controls trainers over ANT+, so the
 * trainer's Bluetooth side is free (docs/design/indoor-erg).
 *
 * The trainer is the one paired on the Karoo: a Bluetooth trainer by its address, an ANT+ one by
 * its device number, which Wahoo trainers also put in their Bluetooth name ("KICKR CORE 6F14" is
 * ANT+ 28436). Never a trainer we can't match: in a gym that could be someone else's.
 *
 * GATT operations run one at a time on the main thread. Reconnects by itself while [want] is set.
 */
@SuppressLint("MissingPermission")
class ErgTrainer(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _link = MutableStateFlow(ErgLink.OFF)
    val link: StateFlow<ErgLink> = _link
    /** Why the link is [ErgLink.LOST], for the log and Settings. */
    @Volatile var problem: String? = null
        private set

    private var want: Match? = null
    private var gatt: BluetoothGatt? = null
    private var scanCb: ScanCallback? = null
    private val queue = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var busy = false
    private var controlled = false
    /** Connected and subscribed: control can be asked for. */
    private var ready = false
    private var retryMs = RETRY_MIN_MS
    private var pending: Int? = null

    /** The trainer to look for: a Bluetooth address, or an ANT+ device number. */
    data class Match(val address: String? = null, val antNumber: Int? = null)

    fun start(match: Match) = main.post {
        if (want == match && _link.value != ErgLink.OFF) return@post
        stopNow()
        want = match
        connectOrScan()
    }

    /** Sends [watts] as soon as the trainer is ready (only the newest counts). */
    fun setTarget(watts: Int) = main.post {
        pending = watts
        if (controlled) flush() else if (ready) writeCp(byteArrayOf(OP_REQUEST_CONTROL))
    }

    /** Hands the trainer back (FTMS reset) but stays connected. */
    fun release() = main.post {
        pending = null
        if (controlled) { writeCp(byteArrayOf(OP_RESET)); controlled = false }
    }

    /** Hands back and disconnects. */
    fun stop() = main.post { stopNow() }

    private fun stopNow() {
        main.removeCallbacksAndMessages(null)
        want = null
        pending = null
        stopScan()
        gatt?.let { g ->
            if (controlled) {
                // Best effort: the reset goes out before the disconnect when the link is up.
                gatt?.getService(FTMS)?.getCharacteristic(CONTROL_POINT)?.let { c ->
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    c.value = byteArrayOf(OP_RESET)
                    g.writeCharacteristic(c)
                }
            }
            main.postDelayed({ g.disconnect(); g.close() }, 300)
        }
        gatt = null
        controlled = false; ready = false
        queue.clear(); busy = false
        _link.value = ErgLink.OFF
    }

    // ---- find ----------------------------------------------------------------------------------

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)

    private fun adapter() = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun connectOrScan() {
        val m = want ?: return
        if (!hasPermission()) return lost("no Bluetooth permission")
        val adapter = adapter() ?: return lost("no Bluetooth")
        if (!adapter.isEnabled) return lost("Bluetooth off")
        _link.value = ErgLink.CONNECTING
        val address = m.address ?: knownAddress
        if (address != null) connect(address) else scan()
    }

    private fun scan() {
        val scanner = adapter()?.bluetoothLeScanner ?: return lost("no Bluetooth scanner")
        val number = want?.antNumber ?: return lost("trainer has no number")
        val suffix = "%04X".format(number and 0xFFFF)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name ?: return
                if (!name.uppercase().endsWith(suffix)) return
                Timber.i("ERG: found %s (%s)", name, r.device.address)
                main.post { stopScan(); knownAddress = r.device.address; connect(r.device.address) }
            }
            override fun onScanFailed(code: Int) { main.post { lost("scan failed $code") } }
        }
        scanCb = cb
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(FTMS)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        main.postDelayed({ if (scanCb === cb) { stopScan(); lost("trainer not found") } }, SCAN_MS)
    }

    private fun stopScan() {
        scanCb?.let { cb -> runCatching { adapter()?.bluetoothLeScanner?.stopScan(cb) } }
        scanCb = null
    }

    // ---- connect -------------------------------------------------------------------------------

    private fun connect(address: String) {
        val device = runCatching { adapter()?.getRemoteDevice(address) }.getOrNull() ?: return lost("bad address")
        gatt = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) { g.discoverServices(); return@post }
                g.close(); gatt = null; controlled = false; ready = false
                queue.clear(); busy = false
                lost("disconnected ($status)")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                val svc = g.getService(FTMS) ?: return@post lost("no FTMS on this trainer")
                for (u in listOf(CONTROL_POINT, STATUS)) {
                    val c = svc.getCharacteristic(u) ?: continue
                    enqueue { gg ->
                        gg.setCharacteristicNotification(c, true)
                        val d = c.getDescriptor(CCCD) ?: return@enqueue false
                        d.value = if (u == CONTROL_POINT) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gg.writeDescriptor(d)
                    }
                }
                ready = true
                retryMs = RETRY_MIN_MS
                problem = null
                _link.value = ErgLink.READY
                // Control only once there's a target: before START the trainer stays free.
                if (pending != null) writeCp(byteArrayOf(OP_REQUEST_CONTROL))
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { main.post { next() } }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { main.post { next() } }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            main.post {
                when (c.uuid) {
                    CONTROL_POINT -> onResponse(v.getOrNull(1)?.toInt() ?: -1, v.getOrNull(2)?.toInt() ?: -1)
                    STATUS -> if ((v.getOrNull(0)?.toInt() ?: 0) and 0xFF == 0xFF) {
                        Timber.w("ERG: trainer says control was lost")
                        controlled = false
                        if (pending != null) requestControlLater()
                    }
                }
            }
        }
    }

    private fun onResponse(op: Int, result: Int) {
        if (result != RESULT_SUCCESS) Timber.w("ERG: op 0x%02x answered %d", op, result)
        when (op) {
            OP_REQUEST_CONTROL.toInt() -> if (result == RESULT_SUCCESS) {
                controlled = true
                writeCp(byteArrayOf(OP_START))
                flush()
            } else {
                lost("trainer refused control")
            }
        }
    }

    private fun requestControlLater() {
        main.postDelayed({ if (ready && !controlled && want != null) writeCp(byteArrayOf(OP_REQUEST_CONTROL)) }, 2_000)
    }

    private fun flush() {
        val w = pending ?: return
        val clamped = w.coerceIn(0, ErgPlanner.MAX_WATTS)
        writeCp(byteArrayOf(OP_TARGET_POWER, (clamped and 0xFF).toByte(), (clamped shr 8 and 0xFF).toByte()))
    }

    private fun lost(why: String) {
        Timber.w("ERG: %s", why)
        problem = why
        stopScan()
        gatt?.close(); gatt = null; controlled = false; ready = false
        queue.clear(); busy = false
        if (want == null) { _link.value = ErgLink.OFF; return }
        _link.value = ErgLink.LOST
        // An address found by scanning that no longer answers: scan again next time.
        if (want?.address == null) knownAddress = null
        val delay = retryMs
        retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
        main.postDelayed({ if (want != null) connectOrScan() }, delay)
    }

    // ---- queue ---------------------------------------------------------------------------------

    private fun writeCp(bytes: ByteArray) {
        val c = gatt?.getService(FTMS)?.getCharacteristic(CONTROL_POINT) ?: return
        enqueue { g ->
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = bytes
            g.writeCharacteristic(c)
        }
    }

    private fun enqueue(op: (BluetoothGatt) -> Boolean) { queue.addLast(op); if (!busy) next() }

    private fun next() {
        busy = false
        val g = gatt ?: return
        while (queue.isNotEmpty()) {
            if (queue.removeFirst()(g)) { busy = true; return }
        }
    }

    private companion object {
        fun uuid16(v: Int): UUID = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))
        val FTMS = uuid16(0x1826)
        val CONTROL_POINT = uuid16(0x2AD9)
        val STATUS = uuid16(0x2ADA)
        val CCCD = uuid16(0x2902)
        const val OP_REQUEST_CONTROL: Byte = 0x00
        const val OP_RESET: Byte = 0x01
        const val OP_TARGET_POWER: Byte = 0x05
        const val OP_START: Byte = 0x07
        const val RESULT_SUCCESS = 1
        const val SCAN_MS = 20_000L
        const val RETRY_MIN_MS = 3_000L
        const val RETRY_MAX_MS = 30_000L

        /** The address found for an ANT+ trainer, so a reconnect skips the scan. */
        @Volatile var knownAddress: String? = null
    }
}
