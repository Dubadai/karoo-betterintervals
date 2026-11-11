package com.daviddokakis.karoointerval

import android.content.Context
import android.os.SystemClock
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** The full-page "Interval screen" data field. */
class IntervalDataType(
    extension: String,
    private val session: WorkoutSession,
) : DataTypeImpl(extension, TYPE_ID) {

    /** Numeric fallback (current watts) for places that show the field as a plain number. */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            session.state.collect { s ->
                val p = s.powerShown
                emitter.onNext(
                    if (p == null) {
                        StreamState.NotAvailable
                    } else {
                        StreamState.Streaming(DataPoint(dataTypeId, values = mapOf(DataType.Field.SINGLE to p.toDouble())))
                    },
                )
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Interval screen view started: $config")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val renderer = ScreenRenderer(context, config)
        session.viewStarted()
        val job = CoroutineScope(Dispatchers.Default).launch {
            // Karoo drops view updates that come less than ~0.9 s apart. Redraw every second, but
            // redraw as soon as allowed when something the rider is waiting for changes (countdown
            // number, interval, pause, chart zoom). During a countdown only redraw on a new number,
            // so each number appears (and beeps) exactly one second after the previous one.
            var lastRender = 0L
            var shown: Key? = null
            // Redraw as each sample arrives. A fixed one-second redraw could land just before a
            // sample and block it for the Karoo's ~0.9 s gap, so the numbers lagged the Karoo's own
            // fields by up to a second. While samples come in, they set the pace; the timer only
            // redraws when they stop (no sensor), so the interval clock still ticks.
            var shownSeq = -1
            var seenSeq = -1
            var lastSampleAt = 0L
            while (isActive) {
                val s = session.state.value
                val key = Key(
                    s.countdown, s.stepIndex, s.workoutPaused, s.chartZoomed, s.plan?.id, s.selectTarget,
                    s.selectProgress, s.moveCancelled, s.targetShowsRange, s.wattsMode, s.powerSmoothing, s.tileOutline, s.secondField, s.notStarted, s.appearance, s.navigating, s.freeSecond, s.freePower, s.topRow, s.topRowNone, s.leftItems, s.leftIndex, s.underWatts,
                    s.autoConfirmLeft, s.topRowRight, s.plan?.scalePct, s.hrTargetLthr, s.paused, s.elapsedInStepSec, s.infoOpen, s.rightIndex, s.rightItems, s.zonePalette,
                )
                val now = SystemClock.elapsedRealtime()
                val since = now - lastRender
                if (s.sampleSeq != seenSeq) { seenSeq = s.sampleSeq; lastSampleAt = now }
                val streaming = now - lastSampleAt < STREAMING_MS
                val changed = key != shown
                val counting = s.countdown != null || shown?.countdown != null
                val newSample = s.sampleSeq != shownSeq
                val fallback = if (streaming) FALLBACK_STREAMING_MS else 1000L
                val due = if (counting) changed && since >= MIN_GAP_MS else since >= fallback || ((changed || newSample) && since >= MIN_GAP_MS)
                if (due) {
                    try {
                        emitter.updateView(renderer.render(s))
                        lastRender = SystemClock.elapsedRealtime()
                        if (s.countdown != null && s.countdown != shown?.countdown) session.onCountdownShown()
                        shown = key
                        shownSeq = s.sampleSeq
                    } catch (e: Exception) {
                        Timber.e(e, "Interval screen render failed")
                        lastRender = SystemClock.elapsedRealtime()
                    }
                }
                // Wake on the next state change, but never closer than the Karoo accepts redraws
                val sinceNow = SystemClock.elapsedRealtime() - lastRender
                val pending = key != shown || (!counting && s.sampleSeq != shownSeq)
                val wait = if (pending) MIN_GAP_MS - sinceNow else (if (counting) 1000L else fallback) - sinceNow
                withTimeoutOrNull(wait.coerceIn(1L, 2000L)) { session.state.first { it !== s } }
            }
        }
        emitter.setCancellable {
            job.cancel()
            session.viewStopped()
        }
    }

    /** What triggers an immediate redraw when it changes. */
    private data class Key(
        val countdown: Int?,
        val step: Int,
        val paused: Boolean,
        val zoomed: Boolean,
        val plan: String?,
        val selectTarget: Int?,
        val selectProgress: Int,
        val moveCancelled: Boolean,
        val targetRange: Boolean,
        val wattsMode: WattsMode,
        val powerSmoothing: Smoothing,
        val tileOutline: Boolean,
        val secondField: SecondField,
        val notStarted: Boolean,
        val appearance: Appearance,
        val navigating: Boolean,
        val freeSecond: FreeSecond,
        val freePower: FreePower,
        val topRow: TopRow?,
        val topRowNone: Boolean,
        val leftItems: List<TileItem>,
        val leftIndex: Int,
        val underWatts: TopRow?,
        val autoConfirmLeft: Int?,
        val topRowRight: TopRow?,
        val scalePct: Int?,
        val hrTargetLthr: Boolean,
        val ridePaused: Boolean,
        /**
         * The interval clock's second: each new one redraws as soon as the Karoo allows, so the
         * clock never skips one (redraws paced only by power samples could land either side of
         * a second and skip it: 2026-11-11).
         */
        val elapsedSec: Int,
        val infoOpen: Boolean,
        val rightIndex: Int,
        val rightItems: List<TileItem>,
        val zonePalette: ZonePalette,
    )

    companion object {
        const val TYPE_ID = "interval-screen"
        private const val MIN_GAP_MS = 920L
        /** Samples count as coming in if the last one is this recent… */
        private const val STREAMING_MS = 2500L
        /** …and then the timer only redraws if none has come for this long. */
        private const val FALLBACK_STREAMING_MS = 1600L
    }
}
