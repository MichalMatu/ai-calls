package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R

/** Grant overview only; Android permission requests require an explicit tap. */
class GeneralSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.mv_permissions)
    private lateinit var role: DialerRoleCard
    private lateinit var micStatus: TextView
    private lateinit var phoneStatus: TextView
    private lateinit var micAction: android.widget.Button
    private lateinit var phoneAction: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 24),
                UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 28))
        }
        body.addView(UiDesign.caption(this, getString(R.string.mv_permissions_hint)),
            UiDesign.spaceAfter(this, 18))
        role = DialerRoleCard(this)
        body.addView(role.view, UiDesign.spaceAfter(this, 16))
        val microphone = UiDesign.card(this).apply { tag = "permissions-microphone" }
        microphone.addView(UiDesign.heading(this, getString(R.string.perm_microphone_title)),
            UiDesign.spaceAfter(this, 8))
        micStatus = UiDesign.caption(this, "").apply {
            tag = "permissions-microphone-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        microphone.addView(micStatus, UiDesign.spaceAfter(this, 10))
        micAction = UiDesign.action(this, getString(R.string.ui_microphone)) {
            if (!granted(Manifest.permission.RECORD_AUDIO))
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }.apply { tag = "permissions-microphone-request" }
        microphone.addView(micAction)
        body.addView(microphone, UiDesign.spaceAfter(this, 16))
        val phone = UiDesign.card(this).apply { tag = "permissions-phone-state" }
        phone.addView(UiDesign.heading(this, getString(R.string.perm_phone_state_title)),
            UiDesign.spaceAfter(this, 8))
        phoneStatus = UiDesign.caption(this, "").apply {
            tag = "permissions-phone-state-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        phone.addView(phoneStatus, UiDesign.spaceAfter(this, 10))
        phoneAction = UiDesign.action(this, getString(R.string.perm_phone_state_action)) {
            if (!granted(Manifest.permission.READ_PHONE_STATE))
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_PHONE_STATE)
        }.apply { tag = "permissions-phone-state-request" }
        phone.addView(phoneAction)
        body.addView(phone, UiDesign.spaceAfter(this, 12))
        setContentView(ScrollView(this).apply { addView(body) })
        updateStatuses()
    }

    override fun onResume() {
        super.onResume()
        if (::role.isInitialized) role.refresh()
        if (::micStatus.isInitialized && ::phoneStatus.isInitialized) updateStatuses()
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun updateStatuses() {
        val micGranted = granted(Manifest.permission.RECORD_AUDIO)
        micStatus.setText(if (micGranted) R.string.ui_general_mic_granted
            else R.string.ui_general_mic_missing)
        micAction.visibility = if (micGranted) View.GONE else View.VISIBLE
        val phoneGranted = granted(Manifest.permission.READ_PHONE_STATE)
        phoneStatus.setText(if (phoneGranted) R.string.perm_phone_state_granted
            else R.string.perm_phone_state_missing)
        phoneAction.visibility = if (phoneGranted) View.GONE else View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC || requestCode == REQUEST_PHONE_STATE) updateStatuses()
    }

    private companion object {
        const val REQUEST_MIC = 1001
        const val REQUEST_PHONE_STATE = 1002
    }
}
