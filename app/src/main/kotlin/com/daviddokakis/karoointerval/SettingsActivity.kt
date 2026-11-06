package com.daviddokakis.karoointerval

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Settings: Appearance (Dark / Light / Auto) and Karoo style first, both as Karoo's own segmented
 * control (as in Karoo Settings → Display), then the intervals.icu account as one row that opens
 * the Account page.
 */
class SettingsActivity : AppearanceActivity() {

    private lateinit var store: PlanStore
    private lateinit var form: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlanStore.get(this)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        root.addView(
            iconButton(R.drawable.ic_back, getString(R.string.cd_back)) { finish() }.apply {
                (layoutParams as LinearLayout.LayoutParams).setMargins(dp(4), dp(8), 0, 0)
            },
        )
        root.addView(text(getString(R.string.settings_title), 22f, bold = true).apply { setPadding(dp(12), 0, dp(12), dp(4)) })
        root.addView(ScrollView(this).apply { addView(form, MATCH_PARENT, WRAP_CONTENT) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render() // the account row shows whether a key is set
    }

    private fun render() {
        form.removeAllViews()

        // Appearance: Dark / Light / Auto. The page itself switches right away.
        form.addView(heading(R.drawable.ic_appearance, getString(R.string.appearance)))
        val current = store.appearance.value
        form.addView(
            segmented(
                listOf(R.string.appearance_dark to Appearance.DARK, R.string.appearance_light to Appearance.LIGHT, R.string.appearance_auto to Appearance.AUTO),
                current,
            ) { picked ->
                if (picked != store.appearance.value) {
                    store.setAppearance(picked)
                    recreate()
                }
            },
        )
        form.addView(help(getString(R.string.appearance_help)))
        form.addView(divider())

        // Karoo style: Off / On
        form.addView(heading(R.drawable.ic_style, getString(R.string.karoo_style)))
        form.addView(
            segmented(listOf(R.string.off to false, R.string.on to true), store.karooStyle.value) { on ->
                store.setKarooStyle(on)
                render()
            },
        )
        form.addView(help(getString(R.string.karoo_style_help)))
        form.addView(divider())

        // intervals.icu: one row, opens the Account page
        val status = getString(if (store.hasCredentials) R.string.connected else R.string.not_connected)
        form.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                isClickable = true
                setOnClickListener { startActivity(Intent(this@SettingsActivity, AccountActivity::class.java)) }
                addView(icon(R.drawable.ic_person))
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(rowTitle(getString(R.string.intervals_account)))
                        addView(help(status).apply { setPadding(0, 0, 0, 0) })
                    },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
                addView(icon(R.drawable.ic_chevron))
            },
        )
    }

    /** Icon + title, like Karoo's "In-Ride Appearance" heading. */
    private fun heading(iconRes: Int, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(10))
        addView(icon(iconRes))
        addView(rowTitle(title))
    }

    private fun icon(res: Int) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(getColor(R.color.settings_title))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
    }

    private fun rowTitle(value: String) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(getColor(R.color.settings_title))
    }

    private fun help(value: String) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(getColor(R.color.settings_help))
        setPadding(0, dp(10), 0, dp(14))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.settings_divider))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
    }

    /** Karoo's segmented control: a pale pill track, the selected option as a blue-grey pill. */
    private fun <T> segmented(options: List<Pair<Int, T>>, selected: T, onPick: (T) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(R.drawable.bg_segment_track)
        setPadding(dp(2), dp(2), dp(2), dp(2))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(42))
        options.forEach { (label, value) ->
            addView(
                TextView(this@SettingsActivity).apply {
                    text = getString(label)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(getColor(R.color.segment_text))
                    if (value == selected) setBackgroundResource(R.drawable.bg_segment_selected)
                    contentDescription = getString(label)
                    isClickable = true
                    setOnClickListener { onPick(value) }
                },
                LinearLayout.LayoutParams(0, MATCH_PARENT, 1f),
            )
        }
    }
}
