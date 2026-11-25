package com.daviddokakis.karoointerval

import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.SavedDevices.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which trainer ERG uses, and over what, from the Karoo's saved sensors. */
class TrainerPickTest {
    private fun dev(id: String, conn: String, name: String, enabled: Boolean, vararg types: String) = SavedDevice(
        id = id, connectionType = conn, name = name, enabled = enabled,
        details = SavedDevice.DeviceDetail(null, null, null, null), components = null,
        supportedDataTypes = types.toList(), gearInfo = null,
    )

    private val trainerType = "TYPE_TRAINER_ID"
    private val power = "TYPE_POWER_ID"

    /** The rider's setup from issue #23: NEO T2800 over ANT+ (switched off) and its Bluetooth power. */
    private val neoBle = dev("CF:0B:B9:B6:97:29", "BLE", "Tacx Neo 22877", true, power)
    private val neoAnt = dev("22877-17-5", "ANT_PLUS", "Tacx NEO trainer", false, trainerType, power)

    private val bt = ErgConnection.BLUETOOTH
    private val ant = ErgConnection.ANT
    /** The app's trainer sensor as the Karoo lists it once added (Sensors › Add sensor). */
    private val ours = dev("intervalscreen::fec-22877", "EXTENSION", "Tacx NEO trainer (BetterIntervals)", true, power, "TYPE_CAD_CADENCE_ID", "TYPE_SPD_SPEED_ID")

    @Test fun `Bluetooth - the ANT+ trainer the Karoo uses is found over Bluetooth by its number`() {
        val m = ErgController.trainerIn(SavedDevices(listOf(neoBle, neoAnt.copy(enabled = true))), bt)
        assertEquals(ErgTrainer.Match(antNumber = 22877), m)
    }

    @Test fun `Bluetooth - a trainer switched off isn't used (the developer's default stays as it was)`() {
        assertNull(ErgController.trainerIn(SavedDevices(listOf(neoBle, neoAnt)), bt))
        assertNull(ErgController.trainerIn(SavedDevices(listOf(dev("AA:BB", "BLE", "KICKR", false, trainerType))), bt))
        val ble = dev("E9:04:D3:0C:93:5D", "BLE", "KICKR CORE 6F14", true, trainerType)
        assertEquals(ErgTrainer.Match(address = "E9:04:D3:0C:93:5D"), ErgController.trainerIn(SavedDevices(listOf(neoAnt, ble)), bt))
    }

    @Test fun `ANT+ - the Karoo's ANT+ trainer switched off, driven by the app`() {
        val p = ErgController.trainerPick(SavedDevices(listOf(neoBle, neoAnt)), ant)!!
        assertEquals(ErgTrainer.Match(antNumber = 22877, overAnt = true), p.match)
        assertTrue(!p.karooHoldsAnt)
        assertTrue(!p.inKarooSensors)
    }

    @Test fun `ANT+ - the Karoo still on the trainer over ANT+ is flagged`() {
        val p = ErgController.trainerPick(SavedDevices(listOf(neoAnt.copy(enabled = true))), ant)!!
        assertEquals(22877, p.match.antNumber)
        assertTrue(p.karooHoldsAnt)
    }

    @Test fun `ANT+ - the app's sensor in the Karoo's list gives the trainer, even without the Karoo's own pairing`() {
        val p = ErgController.trainerPick(SavedDevices(listOf(ours)), ant)!!
        assertEquals(ErgTrainer.Match(antNumber = 22877, overAnt = true), p.match)
        assertTrue(p.inKarooSensors)
        assertEquals("Tacx NEO trainer (BetterIntervals)", p.name)
        assertTrue(!ErgController.trainerPick(SavedDevices(listOf(ours, neoAnt)), ant)!!.karooHoldsAnt)
        assertTrue(ErgController.trainerPick(SavedDevices(listOf(ours, neoAnt.copy(enabled = true))), ant)!!.karooHoldsAnt)
    }

    @Test fun `no trainer, no ERG`() {
        assertNull(ErgController.trainerIn(SavedDevices(listOf(neoBle)), bt))
        assertNull(ErgController.trainerIn(SavedDevices(listOf(neoBle)), ant))
    }

    @Test fun `the trainer's Bluetooth power and the app's sensor count as the trainer`() {
        val devices = SavedDevices(listOf(neoBle, neoAnt, ours))
        val m = ErgTrainer.Match(antNumber = 22877, overAnt = true)
        assertTrue(ErgController.isTrainer("CF:0B:B9:B6:97:29", m, devices))
        assertTrue(ErgController.isTrainer("22877-11-5", m, devices))
        assertTrue(ErgController.isTrainer("intervalscreen::fec-22877", m, devices))
        assertTrue(ErgController.isTrainer("fec-22877", m, devices))
        assertTrue(!ErgController.isTrainer("49752-11-5", m, devices))
    }

    @Test fun `sensor ids`() {
        assertEquals("fec-22877", TrainerSensor.uid(22877))
        assertEquals(22877, TrainerSensor.numberOf("intervalscreen::fec-22877"))
        assertNull(TrainerSensor.numberOf("22877-17-5"))
        assertEquals("Tacx", TrainerSensor.makerName(89))
    }

    @Test fun `connection setting decodes, Bluetooth by default`() {
        assertEquals(ErgConnection.BLUETOOTH, ErgConnection.decode(null))
        assertEquals(ErgConnection.BLUETOOTH, ErgConnection.decode("junk"))
        assertEquals(ErgConnection.ANT, ErgConnection.decode("ANT"))
    }
}
