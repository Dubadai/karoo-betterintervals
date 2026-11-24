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

    @Test fun `ANT+ trainer the Karoo uses is found over Bluetooth by its number`() {
        val m = ErgController.trainerIn(SavedDevices(listOf(neoBle, neoAnt.copy(enabled = true))))
        assertEquals(ErgTrainer.Match(antNumber = 22877), m)
    }

    @Test fun `ANT+ trainer switched off on the Karoo is driven over ANT+ by the app`() {
        val m = ErgController.trainerIn(SavedDevices(listOf(neoBle, neoAnt)))
        assertEquals(ErgTrainer.Match(antNumber = 22877, overAnt = true), m)
    }

    @Test fun `a trainer the Karoo uses wins over one switched off`() {
        val kickr = dev("28436-17-5", "ANT_PLUS", "Trainer 28436", true, trainerType)
        assertEquals(ErgTrainer.Match(antNumber = 28436), ErgController.trainerIn(SavedDevices(listOf(neoAnt, kickr))))
        val ble = dev("E9:04:D3:0C:93:5D", "BLE", "KICKR CORE 6F14", true, trainerType)
        assertEquals(ErgTrainer.Match(address = "E9:04:D3:0C:93:5D"), ErgController.trainerIn(SavedDevices(listOf(neoAnt, ble))))
    }

    @Test fun `no trainer, no ERG`() {
        assertNull(ErgController.trainerIn(SavedDevices(listOf(neoBle))))
        // A Bluetooth trainer switched off: nothing to drive it with but Bluetooth, which the Karoo doesn't hold.
        assertNull(ErgController.trainerIn(SavedDevices(listOf(dev("AA:BB", "BLE", "KICKR", false, trainerType)))))
    }

    @Test fun `the trainer's Bluetooth power counts as the trainer`() {
        val devices = SavedDevices(listOf(neoBle, neoAnt))
        val m = ErgTrainer.Match(antNumber = 22877, overAnt = true)
        assertTrue(ErgController.isTrainer("CF:0B:B9:B6:97:29", m, devices))
        assertTrue(ErgController.isTrainer("22877-11-5", m, devices))
        assertTrue(!ErgController.isTrainer("49752-11-5", m, devices))
    }
}
