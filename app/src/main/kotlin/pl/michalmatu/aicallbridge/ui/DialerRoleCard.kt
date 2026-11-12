package pl.michalmatu.aicallbridge.ui

import android.app.role.RoleManager
import android.view.View
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import pl.michalmatu.aicallbridge.R

/** Role setup only. A request is launched exclusively by the owner's button tap. */
internal class DialerRoleCard(
    private val activity: ComponentActivity,
    private val showOnlyWhenMissing: Boolean = false,
) {
    val view: LinearLayout = UiDesign.card(activity).apply { tag = "dialer-role-card" }
    private val status = UiDesign.caption(activity, "").apply {
        tag = "dialer-role-status"
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    // A result-bearing launch identifies the requesting package to Android's role controller.
    private val roleRequest = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ -> refresh() }
    private val request: android.widget.Button = UiDesign.action(activity, activity.getString(R.string.role_ui_action)) {
        val manager = activity.getSystemService(RoleManager::class.java)
        if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) != true) {
            status.setText(R.string.role_ui_unavailable)
        } else if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            refresh()
        } else {
            try {
                roleRequest.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } catch (_: android.content.ActivityNotFoundException) {
                status.setText(R.string.role_ui_request_failed)
            } catch (_: SecurityException) {
                status.setText(R.string.role_ui_request_failed)
            }
        }
    }.apply { tag = "dialer-role-request" }

    init {
        view.addView(UiDesign.heading(activity, activity.getString(R.string.role_ui_title)).apply {
            textSize = 16f
        },
            UiDesign.spaceAfter(activity, 6))
        view.addView(status, UiDesign.spaceAfter(activity, 8))
        view.addView(UiDesign.caption(activity, activity.getString(R.string.role_ui_detail)),
            UiDesign.spaceAfter(activity, 10))
        view.addView(request)
        refresh()
    }

    fun refresh() {
        val manager = activity.getSystemService(RoleManager::class.java)
        val available = runCatching { manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true }
            .getOrDefault(false)
        val held = runCatching { manager?.isRoleHeld(RoleManager.ROLE_DIALER) == true }
            .getOrDefault(false)
        status.setText(when {
            held -> R.string.role_ui_ready
            available -> R.string.role_ui_missing
            else -> R.string.role_ui_unavailable
        })
        request.visibility = if (available && !held) View.VISIBLE else View.GONE
        view.visibility = if (showOnlyWhenMissing && held) View.GONE else View.VISIBLE
    }
}
