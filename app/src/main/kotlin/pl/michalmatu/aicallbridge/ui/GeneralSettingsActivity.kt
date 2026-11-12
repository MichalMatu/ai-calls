package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import rikka.shizuku.Shizuku

/** One place to inspect and explicitly request the app's runtime/role permissions. */
class GeneralSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.mv_permissions)

    private data class GrantRow(
        val permission: String,
        val status: TextView,
        val action: Button,
    )
    private lateinit var role: DialerRoleCard
    private val grants = linkedMapOf<String, GrantRow>()
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuAction: Button

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == REQUEST_SHIZUKU) runOnUiThread { if (!isDestroyed) refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 24),
                UiDesign.dp(this@GeneralSettingsActivity, 20),
                UiDesign.dp(this@GeneralSettingsActivity, 28))
        }
        role = DialerRoleCard(this)
        body.addView(role.view, UiDesign.spaceAfter(this, 16))

        val card = UiDesign.card(this).apply { tag = "permissions-grants-card" }
        fun group(tag: String, label: String): LinearLayout {
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                this.tag = "permissions-" + tag
            }
            block.addView(UiDesign.heading(this, label).apply { textSize = 16f },
                UiDesign.spaceAfter(this, 6))
            card.addView(block, UiDesign.spaceAfter(this, 18))
            return block
        }
        fun runtime(tag: String, title: Int, permission: String, actionLabel: Int, code: Int) {
            val block = group(tag, getString(title))
            val status = UiDesign.caption(this, "").apply {
                this.tag = "permissions-" + tag + "-status"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            block.addView(status, UiDesign.spaceAfter(this, 7))
            val button = UiDesign.action(this, getString(actionLabel)) {
                requestOrOpenSettings(permission, code)
            }.apply { this.tag = "permissions-" + tag + "-request" }
            block.addView(button)
            grants[permission] = GrantRow(permission, status, button)
        }

        runtime("microphone", R.string.perm_microphone_title,
            Manifest.permission.RECORD_AUDIO, R.string.perm_action_allow, REQUEST_MIC)
        runtime("phone-state", R.string.perm_phone_state_title,
            Manifest.permission.READ_PHONE_STATE, R.string.perm_action_allow, REQUEST_PHONE_STATE)
        runtime("call-phone", R.string.perm_call_phone_title,
            Manifest.permission.CALL_PHONE, R.string.perm_action_allow, REQUEST_CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) {
            runtime("notifications", R.string.perm_notifications_title,
                Manifest.permission.POST_NOTIFICATIONS, R.string.perm_action_allow, REQUEST_NOTIFICATIONS)
        }

        val shizuku = group("shizuku", getString(R.string.perm_shizuku_title))
        shizukuStatus = UiDesign.caption(this, "").apply {
            tag = "permissions-shizuku-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        shizuku.addView(shizukuStatus, UiDesign.spaceAfter(this, 7))
        shizukuAction = UiDesign.action(this, getString(R.string.perm_action_allow)) {
            requestShizuku()
        }.apply { tag = "permissions-shizuku-request" }
        shizuku.addView(shizukuAction)

        body.addView(card)
        setContentView(ScrollView(this).apply { addView(body) })
        refresh()
    }

    override fun onStart() {
        super.onStart()
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
    }

    override fun onStop() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::role.isInitialized && ::shizukuStatus.isInitialized) refresh()
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
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)))
    }

    private fun shizukuAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    private fun shizukuGranted(): Boolean = shizukuAvailable() &&
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    private fun openShizukuApp() {
        val target = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (target != null) startActivity(target)
    }

    private fun requestShizuku() {
        if (!shizukuAvailable() || runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)) {
            openShizukuApp()
        } else if (!shizukuGranted()) {
            runCatching { Shizuku.requestPermission(REQUEST_SHIZUKU) }
        } else refresh()
    }

    private fun refresh() {
        role.refresh()
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
        val installed = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") != null
        val available = shizukuAvailable()
        val granted = shizukuGranted()
        shizukuStatus.setText(when {
            granted -> R.string.perm_status_granted
            available -> R.string.perm_status_missing
            else -> R.string.perm_shizuku_offline
        })
        val needsApp = !available ||
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
        shizukuAction.setText(if (needsApp) R.string.perm_action_open_shizuku
            else R.string.perm_action_allow)
        shizukuAction.visibility = if (granted || (needsApp && !installed))
            View.GONE else View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode in setOf(REQUEST_MIC, REQUEST_PHONE_STATE,
                REQUEST_CALL_PHONE, REQUEST_NOTIFICATIONS)) refresh()
    }

    private companion object {
        const val REQUEST_MIC = 1001
        const val REQUEST_PHONE_STATE = 1002
        const val REQUEST_CALL_PHONE = 1003
        const val REQUEST_NOTIFICATIONS = 1004
        const val REQUEST_SHIZUKU = 1005
    }
}
