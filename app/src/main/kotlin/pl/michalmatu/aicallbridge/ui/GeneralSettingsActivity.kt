package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R

/** Config-only screen. UI selections delegate to the existing preference contract. */
class GeneralSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.mv_permissions)
    private lateinit var role: DialerRoleCard
    private lateinit var feedback: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 24),
                UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 28))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.mv_permissions_hint)
            textSize = 14f
            setTextColor(UiDesign.secondary)
            setPadding(0, 0, 0, UiDesign.dp(this@GeneralSettingsActivity, 18))
        })
        role = DialerRoleCard(this)
        content.addView(role.view, UiDesign.spaceAfter(this, 16))
        feedback = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextColor(UiDesign.secondary)
            textSize = 13f
            setPadding(0, UiDesign.dp(this@GeneralSettingsActivity, 12),
                0, UiDesign.dp(this@GeneralSettingsActivity, 10))
        }
        content.addView(feedback)
        val permission = UiDesign.button(this, getString(R.string.ui_microphone)) { requestMicrophone() }
        content.addView(permission, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = UiDesign.dp(this@GeneralSettingsActivity, 18) })
        updateMicrophoneStatus()
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onResume() {
        super.onResume()
        if (::feedback.isInitialized) updateMicrophoneStatus()
        if (::role.isInitialized) role.refresh()
    }
    private fun updateMicrophoneStatus() {
        feedback.text = getString(if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) R.string.ui_general_mic_granted
            else R.string.ui_general_mic_missing)
    }
    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            updateMicrophoneStatus()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) feedback.text = getString(
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                R.string.ui_general_mic_granted else R.string.ui_general_mic_denied)
    }
    private companion object { const val REQUEST_MIC = 1001 }
}
