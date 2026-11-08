package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver

/**
 * Shared chrome for existing imperative Android screens.
 * The original Activity owns its state, callbacks, privacy flags, and content tree.
 * This wrapper adds navigation only, without touching grants, audio or persistence.
 */
abstract class UiChromeActivity(private val activeTab: AppTab) : AppBaseActivity() {
    open fun toolbarTitle(): String? = null
    open fun isPrimaryTabScreen(): Boolean = false
    private var spacingTarget: View? = null
    private var spacingListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** Child pages with internal navigation may consume Back before closing their Activity. */
    protected open fun consumeChromeBack(): Boolean = false
    fun navigateBack() {
        if (!consumeChromeBack()) finish()
    }
    override fun setContentView(view: View?) {
        // AndroidX ComponentActivity exposes a nullable Java View signature.
        if (view == null) {
            super.setContentView(view)
            return
        }
        spaceActionRows(view)
        spacingTarget?.viewTreeObserver?.takeIf { it.isAlive }?.let { observer ->
            spacingListener?.let(observer::removeOnGlobalLayoutListener)
        }
        super.setContentView(AppNavigation.shell(this, view, activeTab))
        // Some screens populate their action rows asynchronously after setContentView.
        // Apply gaps to newly attached controls, but only mutate params when needed.
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isFinishing && !isDestroyed) spaceActionRows(view)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        spacingTarget = view
        spacingListener = listener
    }

    override fun onDestroy() {
        spacingTarget?.viewTreeObserver?.takeIf { it.isAlive }?.let { observer ->
            spacingListener?.let(observer::removeOnGlobalLayoutListener)
        }
        spacingListener = null
        spacingTarget = null
        super.onDestroy()
    }

    /** Presentation-only: buttons keep their existing listeners, IDs and stores. */
    private fun spaceActionRows(root: View) {
        if (root !is android.view.ViewGroup) return
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (root is android.widget.LinearLayout && root.orientation == android.widget.LinearLayout.VERTICAL &&
                child.getTag(pl.michalmatu.aicallbridge.R.id.ai_calls_ui_card_marker) == true) {
                val params = child.layoutParams as? android.widget.LinearLayout.LayoutParams
                val gap = UiDesign.dp(this, 16)
                if (params != null && params.bottomMargin < gap) {
                    params.bottomMargin = gap
                    child.layoutParams = params
                }
            }
            if (root is android.widget.LinearLayout && child is android.widget.Button &&
                child !is android.widget.CompoundButton) {
                val params = child.layoutParams as? android.widget.LinearLayout.LayoutParams
                if (params != null) {
                    val gap = UiDesign.dp(this, 10)
                    if (root.orientation == android.widget.LinearLayout.VERTICAL &&
                        params.bottomMargin < gap) {
                        params.bottomMargin = maxOf(params.bottomMargin, gap)
                        child.layoutParams = params
                    } else if (root.orientation == android.widget.LinearLayout.HORIZONTAL &&
                        index < root.childCount - 1 && params.marginEnd < gap) {
                        params.marginEnd = maxOf(params.marginEnd, gap)
                        child.layoutParams = params
                    }
                }
            }
            spaceActionRows(child)
        }
    }
}
