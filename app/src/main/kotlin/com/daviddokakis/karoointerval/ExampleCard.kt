package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "Example" card at the top of a Settings page (design: karoo-starter/docs/design/settings-preview/).
 * Shows the app's field as the current settings make it, drawn by the field's own renderer with
 * made-up values, so the rider sees a change before riding. Call [show] again after every change.
 * Reusable: copy this file (change the package) with the `example_*` colours and the `example` string.
 */
class ExampleCard(context: Context) : LinearLayout(context) {
    private val image = ImageView(context)

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(context.getColor(R.color.example_card))
        }
        setPadding(dp(10), dp(6), dp(10), dp(10))
        addView(
            TextView(context).apply {
                text = context.getString(R.string.example)
                isAllCaps = true
                letterSpacing = 0.06f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(R.color.example_label))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        image.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // The Karoo's field tile behind the field: white or black, thin border, rounded corners.
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(context.getColor(R.color.example_tile))
                setStroke(dp(1).coerceAtLeast(1), context.getColor(R.color.example_tile_border))
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(6).toFloat())
            }
            clipToOutline = true
        }
        addView(image, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
        })
    }

    /** [field]: the field as the renderer draws it, at the size it should appear (in pixels). */
    fun show(field: Bitmap, description: String) {
        image.setImageBitmap(field)
        image.contentDescription = description
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
