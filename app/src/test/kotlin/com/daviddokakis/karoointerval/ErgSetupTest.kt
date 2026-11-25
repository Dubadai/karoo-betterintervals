package com.daviddokakis.karoointerval

import com.daviddokakis.karoointerval.ErgSetup.Kind
import com.daviddokakis.karoointerval.ErgSetup.State
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.SavedDevices.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The ERG setup checklist against the setups (and mistakes) seen on a Karoo 3 with a Tacx NEO T2800. */
class ErgSetupTest {
    private fun dev(id: String, conn: String, name: String, enabled: Boolean, vararg types: String) = SavedDevice(
        id = id, connectionType = conn, name = name, enabled = enabled,
        details = SavedDevice.DeviceDetail(null, null, null, null), components = null,
        supportedDataTypes = types.toList(), gearInfo = null,
    )

    private val neoAnt = dev("22877-17-5", "ANT_PLUS", "Tacx NEO trainer", true, "TYPE_TRAINER_ID", "TYPE_POWER_ID")
    private val neoBle = dev("CF:0B:B9:B6:97:29", "BLE", "Tacx Neo 22877", true, "TYPE_POWER_ID", "TYPE_CAD_CADENCE_ID")
    private val ours = dev("intervalscreen::fec-22877", "EXTENSION", "Tacx NEO trainer (BetterIntervals)", true, "TYPE_POWER_ID")
    private val quarq = dev("49752-11-5", "ANT_PLUS", "Quarq MTB", true, "TYPE_POWER_ID")

    private fun check(conn: ErgConnection, perm: Boolean, vararg d: SavedDevice) = ErgSetup.check(SavedDevices(d.toList()), conn, perm)
    private fun first(steps: List<ErgSetup.Step>) = ErgSetup.firstProblem(steps)?.kind

    @Test fun `Bluetooth, the developer's setup - all fine`() {
        val s = check(ErgConnection.BLUETOOTH, true, neoAnt, neoBle.copy(enabled = false))
        assertNull(first(s))
        assertEquals(listOf(Kind.TRAINER_ON, Kind.PERMISSION, Kind.BLUETOOTH_FREE, Kind.READ_ONLY), s.map { it.kind })
    }

    @Test fun `Bluetooth - the Karoo's Bluetooth sensor on the NEO is caught (issue #23's first test)`() {
        val s = check(ErgConnection.BLUETOOTH, true, neoAnt, neoBle)
        assertEquals(Kind.BLUETOOTH_FREE, first(s))
        assertEquals("Tacx Neo 22877", ErgSetup.firstProblem(s)!!.name)
    }

    @Test fun `Bluetooth - no permission, no trainer, our sensor left on`() {
        assertEquals(Kind.PERMISSION, first(check(ErgConnection.BLUETOOTH, false, neoAnt)))
        assertEquals(Kind.TRAINER_ON, first(check(ErgConnection.BLUETOOTH, true, neoAnt.copy(enabled = false))))
        val s = check(ErgConnection.BLUETOOTH, true, neoAnt, ours)
        assertEquals(Kind.OUR_SENSOR_OFF, first(s))
    }

    @Test fun `ANT+, the finished setup - all fine`() {
        assertNull(first(check(ErgConnection.ANT, false, neoAnt.copy(enabled = false), ours, neoBle.copy(enabled = false))))
    }

    @Test fun `ANT+ - the Karoo's own ANT+ trainer still on is caught first`() {
        val s = check(ErgConnection.ANT, false, neoAnt, ours)
        assertEquals(Kind.KAROO_ANT_OFF, first(s))
        assertEquals("Tacx NEO trainer", ErgSetup.firstProblem(s)!!.name)
    }

    @Test fun `ANT+ - no power sensor means no power in the ride file`() {
        val s = check(ErgConnection.ANT, false, neoAnt.copy(enabled = false))
        assertEquals(Kind.POWER_RECORDED, first(s))
        // Another power source is fine, just said
        val p = check(ErgConnection.ANT, false, neoAnt.copy(enabled = false), quarq)
        assertNull(first(p))
        assertEquals(State.INFO, p.first { it.kind == Kind.POWER_RECORDED }.state)
        assertEquals(State.INFO, check(ErgConnection.ANT, false, neoAnt.copy(enabled = false), neoBle).first { it.kind == Kind.POWER_RECORDED }.state)
    }

    @Test fun `ANT+ - nothing paired yet`() {
        assertEquals(Kind.TRAINER_KNOWN, first(check(ErgConnection.ANT, false, quarq)))
    }
}
