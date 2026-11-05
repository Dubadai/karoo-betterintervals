package com.daviddokakis.karoointerval

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles taps on the buttons drawn in the data field. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = IntervalExtension.session ?: return
        when (intent.action) {
            ACTION_NEXT -> session.requestJump(+1)
            ACTION_PREVIOUS -> session.requestJump(-1)
            ACTION_PAUSE -> session.togglePause()
            ACTION_TOGGLE_CHART -> session.toggleChartZoom()
            ACTION_CANCEL_JUMP -> session.cancelJump()
            ACTION_TOGGLE_WATTS_COLOR -> session.toggleWattsColor()
        }
    }

    companion object {
        const val ACTION_NEXT = "com.daviddokakis.karoointerval.NEXT"
        const val ACTION_PREVIOUS = "com.daviddokakis.karoointerval.PREVIOUS"
        const val ACTION_PAUSE = "com.daviddokakis.karoointerval.PAUSE"
        const val ACTION_TOGGLE_CHART = "com.daviddokakis.karoointerval.TOGGLE_CHART"
        const val ACTION_CANCEL_JUMP = "com.daviddokakis.karoointerval.CANCEL_JUMP"
        const val ACTION_TOGGLE_WATTS_COLOR = "com.daviddokakis.karoointerval.TOGGLE_WATTS_COLOR"
    }
}
