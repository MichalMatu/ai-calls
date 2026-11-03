package pl.michalmatu.aicallbridge

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Visual grouping only: controls retain their original listeners, permissions and authority.
 * STOP and essential task actions must live outside collapsed sections.
 */
internal class UiAccordionSection(
    context: Context,
    val sectionId: String,
    private val heading: String,
    initiallyExpanded: Boolean = false,
) : LinearLayout(context) {
    private val content = LinearLayout(context).apply {
        orientation = VERTICAL
        tag = "ui-accordion-content:$sectionId"
    }
    private val toggle = Button(context).apply {
        tag = "ui-accordion-toggle:$sectionId"
        isAllCaps = false
        setOnClickListener { setExpanded(!isExpanded) }
    }

    val controlsContainer: LinearLayout get() = content

    var isExpanded: Boolean = initiallyExpanded
        private set

    init {
        orientation = VERTICAL
        addView(toggle)
        addView(content)
        setExpanded(initiallyExpanded)
    }

    fun addControl(view: View) {
        content.addView(view)
    }

    fun addLabel(text: String) {
        content.addView(TextView(context).apply { this.text = text })
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.text = (if (expanded) "▾ " else "▸ ") + heading
        toggle.contentDescription =
            (if (expanded) "Zwiń: " else "Rozwiń: ") + heading
    }
}
