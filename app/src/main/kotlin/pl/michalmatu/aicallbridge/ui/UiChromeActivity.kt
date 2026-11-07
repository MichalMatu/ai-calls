package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.view.View

/**
 * Shared chrome for existing imperative Android screens.
 * The original Activity owns its state, callbacks, privacy flags, and content tree.
 * This wrapper adds navigation only, without touching grants, audio or persistence.
 */
abstract class UiChromeActivity(private val activeTab: AppTab) : Activity() {
    override fun setContentView(view: View) {
        super.setContentView(AppNavigation.shell(this, view, activeTab))
    }
}
