package com.daviddokakis.karoointerval

import androidx.annotation.ColorRes

/**
 * Power zones as % of the workout's FTP: the Karoo's seven power zones, coloured as in BigWatts
 * (design: docs/design/karoo-zone-colours/).
 */
object Zones {
    /** Zone number 1–7 (used in labels like "Z4"). */
    fun zoneOf(watts: Int, ftp: Int?): Int? {
        if (ftp == null || ftp <= 0) return null
        val pct = watts * 100 / ftp
        return when {
            pct <= 55 -> 1
            pct <= 75 -> 2
            pct <= 90 -> 3
            pct <= 105 -> 4
            pct <= 120 -> 5
            pct <= 150 -> 6
            else -> 7
        }
    }

    /** Fill and optional outline for a chart bar. */
    data class BarStyle(@ColorRes val fill: Int, @ColorRes val edge: Int?)

    /** Chart bar colour for a step: the zone colour (Z1 grey … Z7 purple). */
    fun barStyle(watts: Int, ftp: Int?): BarStyle = BarStyle(
        when (zoneOf(watts, ftp)) {
            1 -> R.color.z1
            2 -> R.color.z2
            3 -> R.color.z3
            4 -> R.color.z4
            5 -> R.color.z5
            6 -> R.color.z6
            7 -> R.color.z7
            else -> R.color.done
        },
        null,
    )
}
