package com.daviddokakis.karoointerval

import io.hammerhead.karooext.models.DataType

/** Settings › Power / Cadence: instant, or the Karoo's own 3, 5 or 10 s average. */
/** [powerName]: what the Karoo calls that power field ("3s Power"), shown above the free-ride watts. */
enum class Smoothing(val label: Int, val power: String, val cadence: String, val powerName: Int) {
    INSTANT(R.string.smoothing_instant, DataType.Type.POWER, DataType.Type.CADENCE, R.string.power_name_instant),
    S3(R.string.smoothing_3s, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE, R.string.power_name_3s),
    S5(R.string.smoothing_5s, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, DataType.Type.SMOOTHED_5S_AVERAGE_CADENCE, R.string.power_name_5s),
    S10(R.string.smoothing_10s, DataType.Type.SMOOTHED_10S_AVERAGE_POWER, DataType.Type.SMOOTHED_10S_AVERAGE_CADENCE, R.string.power_name_10s),
}

/** Settings › Heart rate or cadence: what the HR spots on the screens show. */
enum class SecondField { HEART_RATE, CADENCE }
