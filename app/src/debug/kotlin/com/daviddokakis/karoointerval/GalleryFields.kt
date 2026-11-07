package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

/**
 * DEBUG ONLY: what the field gallery (FieldGallery.kt) draws: the workout screen in its main looks
 * (ExampleScreen's made-up workout). Full page, and half a page (e.g. above a route or map).
 */
object GalleryFields {
    val variants = listOf("classic", "karoo", "gauge", "colours")
    val sizes: List<String>? = listOf("full", "half")

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val s = when (variant) {
            "karoo" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.PLAIN)
            "gauge" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.GAUGE)
            "colours" -> ExampleScreen.state(karooStyle = false, wattsMode = WattsMode.BOTH)
            else -> ExampleScreen.state(karooStyle = false, wattsMode = WattsMode.PLAIN)
        }
        // Layouts are inflated on the main thread, as on the Karoo.
        var out: Bitmap? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { out = ExampleScreen.draw(context, s, w, h); done.countDown() }
        done.await()
        return out!!
    }
}
