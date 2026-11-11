package com.daviddokakis.karoointerval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PmMatcherTest {

    /** A trainer that reads [ratio] × what the PM reads: it holds [sent] by its own watts. */
    private fun ride(m: PmMatcher, target: Int, secs: Int, ratio: (Long) -> Double, from: Long = 0, step: Int = 0): Int {
        var sent = target
        for (t in from until from + secs) {
            val trainer = sent
            val pm = (trainer / ratio(t)).roundToInt()
            sent = m.next(target, steady = true, stepIndex = step, trainerW = trainer, pmW = pm, nowSec = t)
        }
        return sent
    }

    @Test fun `sends the plain target before it knows anything`() {
        assertEquals(200, PmMatcher().next(200, true, 0, null, null, 0))
    }

    @Test fun `a trainer reading 4 % high gets asked for 4 % more until the PM shows the target`() {
        val m = PmMatcher()
        val sent = ride(m, 200, 300, { 1.04 })
        assertEquals(208, sent)
        assertEquals(200, (sent / 1.04).roundToInt())   // what the PM shows
    }

    @Test fun `each power band keeps its own correction`() {
        val m = PmMatcher()
        ride(m, 150, 400, { 1.06 }, step = 0)
        ride(m, 350, 400, { 1.02 }, from = 400, step = 1)
        assertEquals(1.06, m.ratioFor(PmMatcher.bandOf(150)), 0.005)
        assertEquals(1.02, m.ratioFor(PmMatcher.bandOf(350)), 0.005)
    }

    @Test fun `a jump to a band seen before lands on its correction at once`() {
        val m = PmMatcher(mapOf(PmMatcher.bandOf(300) to 1.05))
        assertEquals(315, m.next(300, true, 2, 300, 286, 0))
    }

    @Test fun `follows a trainer that drifts as it warms up`() {
        val m = PmMatcher()
        // Reads 7 % high cold, 3 % high once warm (after 10 minutes).
        val sent = ride(m, 250, 1500, { t -> if (t < 600) 1.07 - 0.04 * t / 600 else 1.03 })
        assertEquals(258, sent)
        val s = assertNotNull(m.summary()).let { m.summary()!! }
        assertEquals(listOf(275 to 3), s.bands)
        assertTrue("cold ${s.coldPct}", (s.coldPct ?: 0) >= 5)
        // Within 1 % of the warm 3 % from about minute 7.5 (when it reads 4 % high).
        assertTrue("warm ${s.warmMin}", (s.warmMin ?: 0) in 7..10)
    }

    @Test fun `never more than 10 % either way`() {
        val m = PmMatcher()
        assertEquals(220, ride(m, 200, 600, { 1.30 }))
        assertEquals(180, ride(PmMatcher(), 200, 600, { 0.70 }))
    }

    @Test fun `holds still while not steady, without readings or when pedalling stops`() {
        val m = PmMatcher(mapOf(4 to 1.05))
        for (t in 0L until 120) m.next(220, steady = false, stepIndex = 0, trainerW = 220, pmW = 180, nowSec = t)
        for (t in 120L until 240) m.next(220, steady = true, stepIndex = 0, trainerW = null, pmW = 180, nowSec = t)
        for (t in 240L until 360) m.next(220, steady = true, stepIndex = 0, trainerW = 20, pmW = 15, nowSec = t)
        assertEquals(1.05, m.ratioFor(4), 1e-9)
    }

    @Test fun `waits for the watts to settle after a new interval`() {
        val m = PmMatcher(mapOf(4 to 1.0))
        for (t in 0L until 19) m.next(220, true, 1, 240, 200, t)   // 10 s settle + 10 s average: nothing yet
        assertEquals(1.0, m.ratioFor(4), 1e-9)
        m.next(220, true, 1, 240, 200, 20)
        assertTrue(m.ratioFor(4) > 1.0)
    }

    @Test fun `bands and summary survive saving`() {
        val bands = mapOf(3 to 1.05, 5 to 0.98)
        assertEquals(bands, PmMatcher.decode(PmMatcher.encode(bands)))
        val s = PmSummary(listOf(175 to 5, 275 to 3), coldPct = 7, warmMin = 12)
        assertEquals(s, PmSummary.decode(s.encode()))
        assertEquals(PmSummary(listOf(175 to 5)), PmSummary.decode(PmSummary(listOf(175 to 5)).encode()))
        assertNull(PmSummary.decode(""))
    }

    @Test fun `no summary without enough steady riding`() {
        val m = PmMatcher()
        ride(m, 200, 40, { 1.04 })
        assertNull(m.summary())
    }

    @Test fun `reads the trainer's watts from FTMS Indoor Bike Data`() {
        // KICKR-style: speed, cadence and power (flags 0x0044): 30.00 km/h, 90 rpm, 251 W.
        val v = byteArrayOf(0x44, 0x00, 0xB8.toByte(), 0x0B, 0xB4.toByte(), 0x00, 0xFB.toByte(), 0x00)
        assertEquals(251, ErgTrainer.parsePower(v))
        assertNull(ErgTrainer.parsePower(byteArrayOf(0x04, 0x00, 0x10, 0x00, 0x20, 0x00)))   // no power field
    }

    @Test fun `knows when the Karoo's power is the trainer`() {
        val ant = ErgTrainer.Match(antNumber = 28436)
        assertTrue(ErgController.isTrainer("28436-11-5", ant))
        assertTrue(!ErgController.isTrainer("12027-11-5", ant))
        assertTrue(ErgController.isTrainer("aa:bb:cc:dd:ee:ff", ErgTrainer.Match(address = "AA:BB:CC:DD:EE:FF")))
    }

    @Test fun `last ride survives saving`() {
        val r = PmLastRide(pmName = "Power 12027", summary = PmSummary(listOf(175 to 5), 7, 12))
        assertEquals(r, PmLastRide.decode(r.encode()))
        assertEquals(PmLastRide(fromTrainer = true), PmLastRide.decode(PmLastRide(fromTrainer = true).encode()))
    }
}
