package pl.michalmatu.aicallbridge

import android.content.Context
import android.graphics.Color
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.UiDesign
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
    private fun dp(value: Int) = UiDesign.dp(context, value)

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
        setTextColor(UiDesign.ink)
        setPadding(dp(16), dp(8), dp(16), dp(6))
        background = UiDesign.touchSurface(context, UiDesign.surface, 14)
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { setExpanded(!isExpanded) }
    }
    private val summary = TextView(context).apply {
        text = description
        textSize = 13f
        setTextColor(UiDesign.secondary)
        setPadding(dp(16), 0, dp(16), dp(12))
        visibility = if (description.isBlank()) GONE else VISIBLE
    }

    val controlsContainer: LinearLayout get() = content

    var isExpanded: Boolean = initiallyExpanded
        private set

    init {
        orientation = VERTICAL
        background = UiDesign.surfaceDrawable(context, UiDesign.surface, 16)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(UiDesign.GAP_ACTION)
        }
        addView(toggle)
        addView(summary)
        addView(content)
        setExpanded(initiallyExpanded)
    }

    private fun styleControl(view: View) {
        if (view is Button) UiDesign.styleAction(view)
        if (view is Spinner) {
            view.minimumHeight = dp(48)
            view.background = UiDesign.surfaceDrawable(context, UiDesign.surface, 12)
        }
    }

    fun addControl(view: View) {
        styleControl(view)
        content.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(UiDesign.GAP_ACTION)
        })
    }

    fun styleExistingControls() {
        for (index in 0 until content.childCount) styleControl(content.getChildAt(index))
    }

    fun addLabel(text: String) {
        content.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(UiDesign.secondary)
            setPadding(dp(4), dp(10), dp(4), dp(4))
        })
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.text = heading
        val arrow = context.getDrawable(
            if (expanded) R.drawable.ic_aicalls_chevron_up else R.drawable.ic_aicalls_chevron_down
        )?.mutate()
        arrow?.setTint(UiDesign.secondary)
        toggle.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null)
        toggle.compoundDrawablePadding = dp(8)
        toggle.contentDescription =
            (if (expanded) context.getString(R.string.acc_ui_collapse) else context.getString(R.string.acc_ui_expand)) + heading
    }
}
