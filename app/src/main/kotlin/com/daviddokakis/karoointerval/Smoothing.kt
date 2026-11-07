package com.daviddokakis.karoointerval

import io.hammerhead.karooext.models.DataType

/** Settings › Power / Cadence: instant, or the Karoo's own 3, 5 or 10 s average. */
enum class Smoothing(val label: Int, val power: String, val cadence: String) {
    INSTANT(R.string.smoothing_instant, DataType.Type.POWER, DataType.Type.CADENCE),
    S3(R.string.smoothing_3s, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE),
    S5(R.string.smoothing_5s, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, DataType.Type.SMOOTHED_5S_AVERAGE_CADENCE),
    S10(R.string.smoothing_10s, DataType.Type.SMOOTHED_10S_AVERAGE_POWER, DataType.Type.SMOOTHED_10S_AVERAGE_CADENCE),
}

/** Settings › Heart rate or cadence: what the HR spots on the screens show. */
enum class SecondField { HEART_RATE, CADENCE }
