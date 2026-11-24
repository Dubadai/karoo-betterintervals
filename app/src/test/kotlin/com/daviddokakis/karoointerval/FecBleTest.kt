package com.daviddokakis.karoointerval

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FecBleTest {

    private fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()
    private fun xor(b: ByteArray) = b.fold(0) { acc, x -> acc xor (x.toInt() and 0xFF) }

    @Test fun `target power is page 49 in quarter watts, wrapped as an ANT acknowledged message`() {
        // 250 W = 1000 quarter watts = 0x03E8.
        val m = FecBle.targetPower(250)
        assertArrayEquals(hex("A4 09 4F 05 31 FF FF FF FF FF E8 03"), m.copyOf(12))
        assertEquals(xor(m.copyOf(12)), m[12].toInt() and 0xFF)
        assertEquals(0, xor(m))
    }

    @Test fun `target power is clamped like FTMS`() {
        val q = ErgPlanner.MAX_WATTS * 4
        val m = FecBle.targetPower(99_999)
        assertEquals(q and 0xFF, m[10].toInt() and 0xFF)
        assertEquals(q shr 8, m[11].toInt() and 0xFF)
        val zero = FecBle.targetPower(-5)
        assertEquals(0, zero[10].toInt()); assertEquals(0, zero[11].toInt())
    }

    @Test fun `handing back is a flat road, page 51 at 0 percent`() {
        val m = FecBle.flatRoad()
        assertArrayEquals(hex("A4 09 4F 05 33 FF FF FF FF 20 4E 50"), m.copyOf(12))
        assertEquals(0, xor(m))
    }

    @Test fun `reads the trainer's watts from page 25`() {
        // Page 25: event 7, cadence 90, accumulated power, instantaneous power 0x123 = 291 W, status.
        val page = hex("A4 09 4E 05 19 07 5A 10 27 23 31 20 00")
        assertEquals(291, FecBle.parsePower(page))
    }

    @Test fun `ignores other pages, invalid power and broken messages`() {
        assertNull(FecBle.parsePower(hex("A4 09 4E 05 10 19 00 00 00 00 FF 24 00")))
        assertNull(FecBle.parsePower(hex("A4 09 4E 05 19 07 5A 10 27 FF 3F 20 00")))
        assertNull(FecBle.parsePower(hex("A4 09 4E 05 19")))
        assertNull(FecBle.parsePower(hex("00 09 4E 05 19 07 5A 10 27 23 31 20 00")))
    }

    @Test fun `matches the Karoo's ANT+ trainer by its Bluetooth name`() {
        assertTrue(FecBle.nameMatches("KICKR CORE 6F14", 28436))
        assertTrue(FecBle.nameMatches("Tacx Neo 2T 28436", 28436))
        assertTrue(FecBle.nameMatches("Tacx Flux 04537", 4537))
        assertFalse(FecBle.nameMatches("Tacx Neo 2T 28437", 28436))
        assertFalse(FecBle.nameMatches("Tacx Neo 2T", 28436))
        assertFalse(FecBle.nameMatches("KICKR CORE 6F15", 28436))
    }
}
