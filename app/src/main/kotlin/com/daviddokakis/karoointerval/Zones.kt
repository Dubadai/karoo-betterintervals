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

    /**
     * The Karoo's colour for heart rate zone [zone] (1-based) of [zones]: its ride app picks from
     * seven colours by the number of zones (5 zones: c1 c2 c3 c4 c6).
     */
    @ColorRes
    fun hrZoneColor(zone: Int, zones: Int): Int? {
        val picks = when (zones) {
            1 -> listOf(3)
            2 -> listOf(2, 6)
            3 -> listOf(2, 3, 6)
            4 -> listOf(2, 3, 4, 6)
            5 -> listOf(1, 2, 3, 4, 6)
            6 -> listOf(1, 2, 3, 4, 6, 7)
            else -> listOf(1, 2, 3, 4, 5, 6, 7)
        }
        return picks.getOrNull(zone - 1)?.let { HR_COLORS[it - 1] }
    }

    /** The chart's version of a Karoo HR colour (deeper in light mode, the original in dark). */
    @ColorRes
    private fun chartColor(@ColorRes hr: Int): Int = CHART_HR_COLORS[HR_COLORS.indexOf(hr).coerceAtLeast(0)]

    private val CHART_HR_COLORS = listOf(
        R.color.chart_hr_c1, R.color.chart_hr_c2, R.color.chart_hr_c3, R.color.chart_hr_c4,
        R.color.chart_hr_c5, R.color.chart_hr_c6, R.color.chart_hr_c7,
    )

    private val HR_COLORS = listOf(
        R.color.karoo_hr_c1, R.color.karoo_hr_c2, R.color.karoo_hr_c3, R.color.karoo_hr_c4,
        R.color.karoo_hr_c5, R.color.karoo_hr_c6, R.color.karoo_hr_c7,
    )

    /**
     * Chart colour for a heart-rate target: its zone in the Karoo rider profile's zones, else by
     * % of LTHR in five zones (below 81 %, 81–89, 90–93, 94–99, 100+), else the middle colour.
     */
    @ColorRes
    fun hrBarColor(bpm: Int, profileZones: List<IntRange>, lthr: Int?): Int {
        if (profileZones.isNotEmpty()) {
            val i = profileZones.indexOfFirst { bpm in it }
            val zone = when {
                i >= 0 -> i + 1
                bpm > profileZones.last().last -> profileZones.size
                else -> 1
            }
            hrZoneColor(zone, profileZones.size)?.let { return chartColor(it) }
        }
        if (lthr != null && lthr > 0) {
            val pct = bpm * 100 / lthr
            val zone = when {
                pct < 81 -> 1
                pct < 90 -> 2
                pct < 94 -> 3
                pct < 100 -> 4
                else -> 5
            }
            return chartColor(hrZoneColor(zone, 5) ?: R.color.karoo_hr_c3)
        }
        return R.color.chart_hr_c3
    }
}
