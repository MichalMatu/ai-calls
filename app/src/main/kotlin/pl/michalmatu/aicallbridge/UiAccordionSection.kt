package pl.michalmatu.aicallbridge

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Presentation-only category. The children retain their original listeners and stores.
 * STOP and authorized task entry points stay outside all collapsed categories.
 */
internal class UiAccordionSection(
    context: Context,
    val sectionId: String,
    private val heading: String,
    private val description: String = "",
    initiallyExpanded: Boolean = false,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val ink = Color.rgb(27, 42, 64)
    private val secondary = Color.rgb(76, 92, 110)
    private fun dp(value: Int) = (value * density).toInt()

    private val content = LinearLayout(context).apply {
        orientation = VERTICAL
        tag = "ui-accordion-content:$sectionId"
        setPadding(dp(12), 0, dp(12), dp(14))
    }
    private val toggle = Button(context).apply {
        tag = "ui-accordion-toggle:$sectionId"
        isAllCaps = false
        textSize = 17f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = dp(52)
        setTextColor(ink)
        setPadding(dp(16), dp(8), dp(16), dp(6))
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        setOnClickListener { setExpanded(!isExpanded) }
    }
    private val summary = TextView(context).apply {
        text = description
        textSize = 13f
        setTextColor(secondary)
        setPadding(dp(16), 0, dp(16), dp(12))
        visibility = if (description.isBlank()) GONE else VISIBLE
    }

    val controlsContainer: LinearLayout get() = content

    var isExpanded: Boolean = initiallyExpanded
        private set

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1).coerceAtLeast(1),
                Color.rgb(210, 218, 229))
        }
        addView(toggle)
        addView(summary)
        addView(content)
        setExpanded(initiallyExpanded)
    }

    private fun styleControl(view: View) {
        if (view is Button || view is Spinner) {
            view.background = GradientDrawable().apply {
                setColor(Color.rgb(245, 248, 252))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1).coerceAtLeast(1), Color.rgb(210, 218, 229))
            }
            view.minimumHeight = dp(46)
            view.setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        if (view is Button) {
            view.isAllCaps = false
            view.textSize = 14f
            view.setTextColor(ink)
        }
    }

    fun addControl(view: View) {
        styleControl(view)
        content.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(7)
        })
    }

    fun styleExistingControls() {
        for (index in 0 until content.childCount) styleControl(content.getChildAt(index))
    }

    fun addLabel(text: String) {
        content.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(secondary)
            setPadding(dp(4), dp(10), dp(4), dp(4))
        })
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.text = (if (expanded) "▾ " else "▸ ") + heading
        toggle.contentDescription =
            (if (expanded) "Zwiń: " else "Rozwiń: ") + heading
    }
}
