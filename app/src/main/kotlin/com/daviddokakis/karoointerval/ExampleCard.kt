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

/**
 * A Settings page (design round 2, karoo-starter/docs/design/settings-preview/): a fixed bar with
 * the back arrow and "Settings"; under it the app's name as a big title, the Example [card] and the
 * settings ([form]), which all scroll together (the card isn't pinned, so the settings get the room).
 */
class SettingsLayout(
    context: Context,
    back: View,
    barTitle: String,
    private val title: View,
    card: ExampleCard,
    form: View,
    pageColor: Int,
    textColor: Int,
) : LinearLayout(context) {
    private val scroll = android.widget.ScrollView(context)
    private val cardHolder = android.widget.FrameLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(pageColor)
        // The bar: back arrow left, "Settings" centred, 18 sp
        addView(
            android.widget.FrameLayout(context).apply {
                addView(back, android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(4) })
                addView(
                    TextView(context).apply {
                        text = barTitle
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        // No extra font padding, so the text's middle lines up with the arrow's
                        includeFontPadding = false
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                        setTextColor(textColor)
                    },
                    android.widget.FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(4) },
        )
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(cardHolder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(form, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        cardHolder.setPadding(dp(12), dp(4), dp(12), dp(4))
        cardHolder.addView(card, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
