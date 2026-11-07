package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.view.View

/**
 * Shared chrome for existing imperative Android screens.
 * The original Activity owns its state, callbacks, privacy flags, and content tree.
 * This wrapper adds navigation only, without touching grants, audio or persistence.
 */
abstract class UiChromeActivity(private val activeTab: AppTab) : Activity() {
    /** Child pages with internal navigation may consume Back before closing their Activity. */
    protected open fun consumeChromeBack(): Boolean = false
    fun navigateBack() {
        if (!consumeChromeBack()) finish()
    }
    override fun setContentView(view: View) {
        spaceActionRows(view)
        super.setContentView(AppNavigation.shell(this, view, activeTab))
    }

    /** Presentation-only: buttons keep their existing listeners, IDs and stores. */
    private fun spaceActionRows(root: View) {
        if (root !is android.view.ViewGroup) return
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (root is android.widget.LinearLayout && child is android.widget.Button &&
                child !is android.widget.CompoundButton) {
                val params = child.layoutParams as? android.widget.LinearLayout.LayoutParams
                if (params != null) {
                    val gap = UiDesign.dp(this, 10)
                    if (root.orientation == android.widget.LinearLayout.VERTICAL) {
                        params.bottomMargin = maxOf(params.bottomMargin, gap)
                    } else if (index < root.childCount - 1) {
                        params.marginEnd = maxOf(params.marginEnd, gap)
                    }
                    child.layoutParams = params
                }
            }
            spaceActionRows(child)
        }
    }
}
