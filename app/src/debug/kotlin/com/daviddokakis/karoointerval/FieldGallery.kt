package com.daviddokakis.karoointerval

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * DEBUG BUILDS ONLY (src/debug): the field gallery. Renders this app's field(s) with the field's own
 * code at the Karoo 3's field sizes, light and dark, as PNGs, so mockups need no Karoo. Run it with
 * `Karoo/design-assets/tools/field-gallery/gallery.sh <app folder>` (Karoo 3 emulator, no device).
 * What to draw is in GalleryFields.kt next to this file. Shared copy: design-assets/tools/field-gallery/android/.
 */
class FieldGalleryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            val out = File(context.filesDir, "gallery")
            try {
                FieldGallery.run(context.applicationContext, out)
            } catch (e: Throwable) {
                Log.e("FieldGallery", "Gallery failed", e)
                File(out, "FAILED").writeText(Log.getStackTraceString(e))
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object FieldGallery {
    /** Field sizes the Karoo 3 reports to fields (ViewConfig.viewSize), in pixels. */
    val SIZES = listOf(
        "full" to (478 to 721),     // whole page (60 × 60 grid)
        "half" to (478 to 336),     // half page, full width (60 × 30)
        "row" to (478 to 141),      // one row, full width (60 × 12)
        "quarter" to (238 to 168),  // half width, quarter height (30 × 15)
        "small" to (236 to 141),    // half width, one row (30 × 12)
    )

    fun run(context: Context, out: File) {
        out.mkdirs()
        out.listFiles()?.forEach { it.delete() }
        useKarooFonts(context)
        // GalleryFields.sizes: the sizes this app's fields are made for (null: all of them)
        val sizes = GalleryFields.sizes?.let { wanted -> SIZES.filter { it.first in wanted } } ?: SIZES
        for (variant in GalleryFields.variants) for ((size, wh) in sizes) for (night in listOf(false, true)) {
            val (w, h) = wh
            val c = withNight(context, night)
            val field = GalleryFields.render(c, variant, w, h, night)
            val tile = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(tile).apply {
                drawColor(if (night) Color.BLACK else Color.WHITE) // the Karoo's tile behind the field
                drawBitmap(field, 0f, 0f, null)
            }
            val mode = if (night) "dark" else "light"
            FileOutputStream(File(out, "$variant-$size-$mode.png")).use { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(out, "DONE").writeText("${GalleryFields.variants.size * sizes.size * 2} fields")
    }

    /** [base] in light or dark mode, so values-night/ colours resolve like on the Karoo. */
    fun withNight(base: Context, night: Boolean): Context {
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(config)
    }

    /**
     * The Karoo's system fonts (IBM Plex) instead of the emulator's Roboto. gallery.sh pushes them to
     * files/fonts/; they replace the system font families the fields ask for. Needs the emulator's
     * hidden-API check off (gallery.sh does that).
     */
    @Suppress("UNCHECKED_CAST")
    private fun useKarooFonts(context: Context) {
        val dir = File(context.filesDir, "fonts")
        fun tf(name: String) = File(dir, name).takeIf { it.exists() }?.let { Typeface.createFromFile(it) }
        val regular = tf("IBMPlexSans-Regular.ttf")
        if (regular == null) {
            Log.w("FieldGallery", "No Karoo fonts pushed; using the emulator's")
            return
        }
        val families = mapOf(
            "sans-serif" to regular,
            "sans-serif-medium" to (tf("IBMPlexSans-Medium.ttf") ?: regular),
            "sans-serif-light" to (tf("IBMPlexSans-Light.ttf") ?: regular),
            "ibm-plex-sans-condensed" to (tf("IBMPlexSansCondensed-Medium.otf") ?: regular),
        )
        runCatching {
            val f = Typeface::class.java.getDeclaredField("sSystemFontMap").apply { isAccessible = true }
            val map = HashMap(f.get(null) as Map<String, Typeface>)
            map.putAll(families)
            f.set(null, map)
        }.onFailure { Log.w("FieldGallery", "Couldn't swap system fonts", it) }
        for ((field, value) in listOf("DEFAULT" to regular, "SANS_SERIF" to regular, "DEFAULT_BOLD" to Typeface.create(regular, Typeface.BOLD))) {
            runCatching {
                Typeface::class.java.getDeclaredField(field).apply { isAccessible = true }.set(null, value)
            }
        }
    }
}
