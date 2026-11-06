package pl.michalmatu.aicallbridge

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
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
        setTextColor(if (dark) Color.WHITE else Color.rgb(29, 43, 62))
        setOnClickListener { setExpanded(!isExpanded) }
    }
    private val summary = TextView(context).apply {
        text = description
        textSize = 13f
        setTextColor(if (dark) Color.rgb(189, 199, 212) else Color.rgb(78, 92, 108))
        setPadding(dp(16), 0, dp(16), dp(12))
        visibility = if (description.isBlank()) GONE else VISIBLE
    }

    val controlsContainer: LinearLayout get() = content

    var isExpanded: Boolean = initiallyExpanded
        private set

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(if (dark) Color.rgb(36, 43, 53) else Color.rgb(247, 249, 252))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1).coerceAtLeast(1),
                if (dark) Color.rgb(78, 91, 108) else Color.rgb(210, 218, 229))
        }
        addView(toggle)
        addView(summary)
        addView(content)
        setExpanded(initiallyExpanded)
    }

    fun addControl(view: View) {
        content.addView(view)
    }

    fun addLabel(text: String) {
        content.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (dark) Color.rgb(189, 199, 212) else Color.rgb(78, 92, 108))
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
