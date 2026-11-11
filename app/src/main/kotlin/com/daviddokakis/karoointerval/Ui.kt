package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Small view builders shared by the Workouts and Account pages (settings pieces: SettingsKit.kt).
// Colours come from resources, so they follow light/dark mode (values/ and values-night/).

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.text(value: String, sp: Float, bold: Boolean = false, secondary: Boolean = false) =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(if (secondary) R.color.text_secondary else R.color.text))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

/** [yellow]: the page's main action in Karoo yellow (black text in both modes), e.g. Back to ride. */
fun Context.button(label: String, primary: Boolean, yellow: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
    text = label
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTypeface(typeface, Typeface.BOLD)
    gravity = Gravity.CENTER
    setTextColor(getColor(if (yellow) R.color.karoo_yellow_fg else if (primary) R.color.skip_fg else R.color.text))
    setBackgroundResource(if (yellow) R.drawable.bg_btn_skip_karoo else if (primary) R.drawable.bg_btn_skip else R.drawable.bg_btn_pause)
    setPadding(dp(14), dp(10), dp(14), dp(10))
    minHeight = dp(44)
    isClickable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
}

/** Icon-only button: no background, 48dp touch area. */
fun Context.iconButton(icon: Int, description: String, onClick: () -> Unit) = ImageView(this).apply {
    setImageResource(icon)
    contentDescription = description
    scaleType = ImageView.ScaleType.CENTER
    isClickable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
}

fun Context.row(vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    views.forEach { addView(it) }
}

fun Context.edit(value: String, type: Int) = EditText(this).apply {
    // Single line first: setting it after inputType resets the input type and would show a
    // password field's text in plain view.
    isSingleLine = true
    inputType = type
    if (type and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
        transformationMethod = PasswordTransformationMethod.getInstance()
    }
    setText(value)
    setTextColor(getColor(R.color.text))
    setHintTextColor(getColor(R.color.text_secondary))
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
}
