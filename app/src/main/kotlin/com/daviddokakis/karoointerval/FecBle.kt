package com.daviddokakis.karoointerval

import java.util.UUID

/**
 * ANT+ FE-C over Bluetooth: the Tacx way of controlling a trainer over Bluetooth (older Tacx
 * firmware has no FTMS). The trainer tunnels ANT messages through one Bluetooth service: we write
 * FE-C data pages, wrapped as ANT acknowledged messages, to [TX] and get its broadcast pages back
 * as notifications on [RX].
 *
 * A message is `A4 09 4F 05 <8-byte page> <checksum>`: ANT sync, length, acknowledged data,
 * channel 5, the page, and the XOR of everything before it.
 */
object FecBle {
    val SERVICE: UUID = UUID.fromString("6e40fec1-b5a3-f393-e0a9-e50e24dcca9e")
    /** Trainer → us (notify). */
    val RX: UUID = UUID.fromString("6e40fec2-b5a3-f393-e0a9-e50e24dcca9e")
    /** Us → trainer (write). */
    val TX: UUID = UUID.fromString("6e40fec3-b5a3-f393-e0a9-e50e24dcca9e")

    private const val SYNC = 0xA4
    private const val ACK_DATA = 0x4F
    private const val CHANNEL = 0x05
    const val PAGE_TRAINER_DATA = 0x19
    const val PAGE_TARGET_POWER = 0x31
    const val PAGE_TRACK_RESISTANCE = 0x33

    /** Wraps an 8-byte FE-C page as an ANT acknowledged message. */
    fun frame(page: IntArray): ByteArray {
        require(page.size == 8)
        val bytes = intArrayOf(SYNC, page.size + 1, ACK_DATA, CHANNEL) + page
        val sum = bytes.fold(0) { acc, b -> acc xor b }
        return (bytes + sum).map { it.toByte() }.toByteArray()
    }

    /** Page 49: ERG at [watts], in the page's 0.25 W units. */
    fun targetPower(watts: Int): ByteArray {
        val q = (watts.coerceIn(0, ErgPlanner.MAX_WATTS) * 4)
        return frame(intArrayOf(PAGE_TARGET_POWER, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, q and 0xFF, q shr 8 and 0xFF))
    }

    /**
     * Page 51 at 0 % grade: the trainer rides like a flat road. FE-C has no "hand back" (FTMS has
     * Reset), so this is how we let go: the trainer leaves ERG and the rider just pedals.
     * Grade in 0.01 % from −200 % (20000 = 0 %); rolling resistance 0.004 in 5·10⁻⁵ units.
     */
    fun flatRoad(): ByteArray {
        val grade = 20_000
        return frame(intArrayOf(PAGE_TRACK_RESISTANCE, 0xFF, 0xFF, 0xFF, 0xFF, grade and 0xFF, grade shr 8 and 0xFF, 80))
    }

    /** The trainer's watts from a page 25 (specific trainer data) notification, else null. */
    fun parsePower(v: ByteArray): Int? {
        if (v.size < 13 || v[0].toInt() and 0xFF != SYNC) return null
        if (v[4].toInt() and 0xFF != PAGE_TRAINER_DATA) return null
        val w = (v[9].toInt() and 0xFF) or ((v[10].toInt() and 0x0F) shl 8)
        return if (w == 0xFFF) null else w
    }

    /**
     * Whether a Bluetooth [name] is the ANT+ trainer [antNumber]: Wahoo ends its name with the
     * number in 4 hex digits ("KICKR CORE 6F14" = 28436), Tacx with the number in decimal
     * ("Tacx Neo 2T 28436").
     */
    fun nameMatches(name: String, antNumber: Int): Boolean {
        val n = name.trim().uppercase()
        if (n.endsWith("%04X".format(antNumber and 0xFFFF))) return true
        return n.substringAfterLast(' ').toIntOrNull() == antNumber
    }
}
