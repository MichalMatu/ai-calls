package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness
import pl.michalmatu.aicallbridge.session.LocalAdbGsmSetupActivity
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantPolicyStore
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity

/** One place to inspect and explicitly request the app's runtime/role permissions. */
class GeneralSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.mv_permissions)

    private data class GrantRow(
        val permission: String,
        val status: TextView,
        val action: Button,
    )
    private lateinit var role: DialerRoleCard
    private lateinit var incomingOverview: Pair<View, TextView>
    private val grants = linkedMapOf<String, GrantRow>()
    private var gsmAudioOverview: Pair<View, TextView>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = UiDesign.content(this)
        role = DialerRoleCard(this, titleRes = R.string.perm_setup_phone)
        body.addView(role.view, UiDesign.spaceAfter(this, 8))

        body.addView(UiDesign.navigationRow(this, getString(R.string.perm_app_settings_action)) {
            openAppSettings()
        }.apply { tag = "permissions-app-details" }, UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.navigationRow(this, getString(R.string.perm_default_apps_action)) {
            openDefaultAppsSettings()
        }.apply { tag = "permissions-default-apps" }, UiDesign.spaceAfter(this, 12))

        val card = UiDesign.card(this).apply { tag = "permissions-grants-card" }
        card.addView(UiDesign.heading(this, getString(R.string.perm_setup_grants)),
            UiDesign.spaceAfter(this, 6))
        card.addView(UiDesign.caption(this, getString(R.string.perm_setup_grants_detail)),
            UiDesign.spaceAfter(this, 12))
        fun group(tag: String, label: String): Pair<LinearLayout, LinearLayout> {
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                this.tag = "permissions-" + tag
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(UiDesign.heading(this, label).apply { textSize = 16f },
                UiDesign.spaceAfter(this, 4))
            block.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(block, UiDesign.spaceAfter(this, 16))
            return labels to block
        }
        fun actionLayout() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = UiDesign.dp(this@GeneralSettingsActivity, UiDesign.GAP_ACTION) }
        fun compact(button: Button) = button.apply {
            maxWidth = UiDesign.dp(this@GeneralSettingsActivity, 120)
        }
        fun runtime(tag: String, title: Int, permission: String, actionLabel: Int, code: Int) {
            val (labels, block) = group(tag, getString(title))
            val status = UiDesign.caption(this, "").apply {
                this.tag = "permissions-" + tag + "-status"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            labels.addView(status)
            val button = UiDesign.action(this, getString(actionLabel)) {
                requestOrOpenSettings(permission, code)
            }.apply { this.tag = "permissions-" + tag + "-request" }
            block.addView(compact(button), actionLayout())
            grants[permission] = GrantRow(permission, status, button)
        }

        runtime("phone-state", R.string.perm_phone_state_title,
            Manifest.permission.READ_PHONE_STATE, R.string.perm_action_allow, REQUEST_PHONE_STATE)
        runtime("microphone", R.string.perm_microphone_title,
            Manifest.permission.RECORD_AUDIO, R.string.perm_action_allow, REQUEST_MIC)
        runtime("answer-phone", R.string.perm_answer_phone_title,
            Manifest.permission.ANSWER_PHONE_CALLS, R.string.perm_action_allow, REQUEST_ANSWER_PHONE)
        runtime("call-phone", R.string.perm_call_phone_title,
            Manifest.permission.CALL_PHONE, R.string.perm_action_allow, REQUEST_CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) {
            runtime("notifications", R.string.perm_notifications_title,
                Manifest.permission.POST_NOTIFICATIONS, R.string.perm_action_allow, REQUEST_NOTIFICATIONS)
        }

        body.addView(card, UiDesign.spaceAfter(this, 12))

        if (CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB) {
            val status = UiDesign.caption(this, "").apply {
                tag = "permissions-gsm-shell-status"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            val row = UiDesign.navigationRow(this, getString(R.string.perm_setup_audio), status) {
                startActivity(Intent(this, LocalAdbGsmSetupActivity::class.java))
            }.apply { tag = "permissions-gsm-shell" }
            gsmAudioOverview = row to status
            body.addView(row, UiDesign.spaceAfter(this, 12))
        }
        val incomingStatus = UiDesign.caption(this, "")
        val incomingRow = UiDesign.navigationRow(this,
            getString(R.string.perm_incoming_ai_action), incomingStatus) {
            startActivity(Intent(this, IncomingAssistantSettingsActivity::class.java))
        }.apply { tag = "permissions-incoming-ai-settings" }
        incomingOverview = incomingRow to incomingStatus
        body.addView(incomingRow)
        setContentView(ScrollView(this).apply { addView(body) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::role.isInitialized) refresh()
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requestOrOpenSettings(permission: String, code: Int) {
        if (granted(permission)) { refresh(); return }
        val prefs = getSharedPreferences("permission_overview_attempts_v1", MODE_PRIVATE)
        if (prefs.getBoolean(permission, false) && !shouldShowRequestPermissionRationale(permission)) {
            openAppSettings()
            return
        }
        prefs.edit().putBoolean(permission, true).apply()
        requestPermissions(arrayOf(permission), code)
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)))
        } catch (_: ActivityNotFoundException) {
            openAndroidSettingsFallback()
        } catch (_: SecurityException) {
            openAndroidSettingsFallback()
        }
    }

    private fun openDefaultAppsSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            openAndroidSettingsFallback()
        } catch (_: SecurityException) {
            openAndroidSettingsFallback()
        }
    }

    private fun openAndroidSettingsFallback() {
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun refresh() {
        role.refresh()
        val mode = runCatching {
            when (IncomingAssistantPolicyStore(this).load().mode) {
                AnsweringMode.AI -> R.string.perm_incoming_mode_ai
                AnsweringMode.MANUAL -> R.string.perm_incoming_mode_manual
            }
        }.getOrDefault(R.string.perm_incoming_mode_unavailable)
        val (incomingRow, incomingStatus) = incomingOverview
        incomingStatus.text = getString(R.string.perm_setup_incoming_detail, getString(mode))
        incomingRow.contentDescription =
            "${getString(R.string.perm_incoming_ai_action)}. ${incomingStatus.text}"
        grants.values.forEach { row ->
            val isGranted = granted(row.permission)
            row.status.setText(if (isGranted) R.string.perm_status_granted
                else R.string.perm_status_missing)
            row.action.visibility = if (isGranted) View.GONE else View.VISIBLE
            val asked = getSharedPreferences("permission_overview_attempts_v1", MODE_PRIVATE)
                .getBoolean(row.permission, false)
            row.action.setText(if (asked && !shouldShowRequestPermissionRationale(row.permission))
                R.string.perm_action_settings else R.string.perm_action_allow)
        }
        gsmAudioOverview?.let { (row, status) ->
            status.setText(if (GsmMediaTransportReadiness.isAvailable(this))
                R.string.perm_gsm_shell_ready else R.string.perm_gsm_shell_offline)
            row.contentDescription = "${getString(R.string.perm_setup_audio)}. ${status.text}"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode in setOf(REQUEST_MIC, REQUEST_PHONE_STATE, REQUEST_ANSWER_PHONE,
                REQUEST_CALL_PHONE, REQUEST_NOTIFICATIONS)) refresh()
    }

    private companion object {
        const val REQUEST_MIC = 1001
        const val REQUEST_PHONE_STATE = 1002
        const val REQUEST_CALL_PHONE = 1003
        const val REQUEST_NOTIFICATIONS = 1004
        const val REQUEST_ANSWER_PHONE = 1006
    }
}
