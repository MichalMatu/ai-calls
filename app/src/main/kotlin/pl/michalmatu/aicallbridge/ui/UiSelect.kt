package pl.michalmatu.aicallbridge.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R

/** Unified native dropdown look; settings persistence remains owned by each controller. */
internal object UiSelect {
    fun setOptions(spinner: Spinner, labels: List<String>) {
        val context = spinner.context
        val pad = UiDesign.dp(context, 14)
        val adapter = object : ArrayAdapter<String>(
            context, android.R.layout.simple_spinner_item, labels
        ) {
            private fun option(position: Int, recycled: View?, selected: Boolean): TextView {
                val text = (recycled as? TextView) ?: TextView(context)
                text.text = labels[position]
                text.textSize = 14f
                text.setTextColor(UiDesign.ink)
                text.gravity = Gravity.CENTER_VERTICAL
                text.minHeight = UiDesign.dp(context, 52)
                text.setPadding(pad, UiDesign.dp(context, 12), pad, UiDesign.dp(context, 12))
                text.background = if (selected) null else ColorDrawable(Color.WHITE)
                if (selected) {
                    val arrow = context.getDrawable(R.drawable.ic_aicalls_chevron_down)?.mutate()
                    arrow?.setTint(UiDesign.secondary)
                    text.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null)
                    text.compoundDrawablePadding = UiDesign.dp(context, 8)
                } else {
                    text.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                }
                return text
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                option(position, convertView, true)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                option(position, convertView, false)
        }
        spinner.adapter = adapter
        spinner.background = UiDesign.surfaceDrawable(context, UiDesign.surface, 12)
        spinner.minimumHeight = UiDesign.dp(context, 52)
        spinner.dropDownVerticalOffset = UiDesign.dp(context, 4)
    }
}
