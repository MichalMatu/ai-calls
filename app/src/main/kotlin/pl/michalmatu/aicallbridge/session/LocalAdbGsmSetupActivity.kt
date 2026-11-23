package pl.michalmatu.aicallbridge.session

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/**
 * One GSM setup screen for both outgoing and incoming calls.
 * Read-only status checks cannot start the shell host or grant ADB access.
 */
class LocalAdbGsmSetupActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var gsmStatus: TextView
    private lateinit var adbStatus: TextView
    private lateinit var pairingSection: LinearLayout
    private lateinit var startButton: Button
    private var inProgress = false
    private var refreshing = false
    private var pairingExpanded = false

    override fun toolbarTitle(): String = getString(R.string.local_adb_title)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!GsmShellMediaProtocol.supportedPackage(packageName)) {
            finish()
            return
        }
        val content = UiDesign.content(this)

        val overview = UiDesign.card(this).apply { tag = "local-adb-readiness-card" }
        overview.addView(UiDesign.heading(this, getString(R.string.local_adb_status_title)),
            UiDesign.spaceAfter(this, 8))
        gsmStatus = UiDesign.caption(this, getString(R.string.local_adb_status_checking)).apply {
            tag = "local-adb-gsm-readiness"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        overview.addView(gsmStatus, UiDesign.spaceAfter(this, 6))
        adbStatus = UiDesign.caption(this, getString(R.string.local_adb_status_checking)).apply {
            tag = "local-adb-connection-readiness"
            // Reserve the final status height to avoid shifting the screen after NSD.
            minLines = 2
            maxLines = 2
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        overview.addView(adbStatus, UiDesign.spaceAfter(this, 10))
        overview.addView(UiDesign.action(this, getString(R.string.local_adb_refresh)) {
            refreshStatus()
        }, UiDesign.spaceAfter(this, 0))
        content.addView(overview, UiDesign.spaceAfter(this, 12))

        // Stable one-row control; background status updates never expand/collapse it.
        val pairingToggle = UiDesign.action(this, getString(R.string.local_adb_manage_pairing)) {
            pairingExpanded = !pairingExpanded
            pairingSection.visibility = if (pairingExpanded) View.VISIBLE else View.GONE
        }
        pairingToggle.tag = "local-adb-pairing-toggle"
        content.addView(pairingToggle, UiDesign.spaceAfter(this, 12))

        pairingSection = UiDesign.card(this).apply { tag = "local-adb-pairing-section" }
        pairingSection.addView(UiDesign.caption(this, getString(R.string.local_adb_intro)),
            UiDesign.spaceAfter(this, 12))
        pairingSection.addView(UiDesign.action(this, getString(R.string.local_adb_open_settings)) {
            launchPairingFlow()
        }, UiDesign.spaceAfter(this, 12))
        pairingSection.addView(UiDesign.caption(this, getString(R.string.local_adb_pair_code)),
            UiDesign.spaceAfter(this, 4))
        val pairCode = EditText(this).apply {
            tag = "local-adb-code-input"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
            minimumHeight = UiDesign.dp(this@LocalAdbGsmSetupActivity, 48)
        }
        pairingSection.addView(pairCode, UiDesign.spaceAfter(this, 8))
        pairingSection.addView(UiDesign.action(this, getString(R.string.local_adb_pair),
            UiActionTone.PRIMARY) {
            val code = pairCode.text.toString()
            pairCode.text?.clear()
            execute {
                val controller = LocalAdbGsmController(applicationContext)
                val paired = controller.pair(code)
                if (paired != LocalAdbGsmController.Outcome.PAIRED) paired
                else when (val ready = controller.bootstrap()) {
                    LocalAdbGsmController.Outcome.READY,
                    LocalAdbGsmController.Outcome.ALREADY_READY -> ready
                    else -> paired
                }
            }
        }, UiDesign.spaceAfter(this, 0))
        pairingSection.visibility = View.GONE
        content.addView(pairingSection, UiDesign.spaceAfter(this, 12))

        startButton = UiDesign.action(this, getString(R.string.local_adb_start),
            UiActionTone.PRIMARY) {
            execute { LocalAdbGsmController(applicationContext).bootstrap() }
        }
        startButton.tag = "local-adb-start-host"
        content.addView(startButton, UiDesign.spaceAfter(this, 12))
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        // Synchronous Binder readiness avoids a first-frame change of controls.
        val hostReady = GsmMediaTransportReadiness.isAvailable(this)
        gsmStatus.setText(if (hostReady) R.string.local_adb_gsm_ready
            else R.string.local_adb_gsm_offline)
        startButton.visibility = if (hostReady) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (::gsmStatus.isInitialized) refreshStatus()
    }

    private fun refreshStatus() {
        if (refreshing || isFinishing || isDestroyed) return
        refreshing = true
        adbStatus.setText(R.string.local_adb_status_checking)
        worker.execute {
            val result = runCatching {
                LocalAdbGsmController(applicationContext).inspect()
            }.getOrElse {
                LocalAdbGsmStatus.Snapshot(
                    runCatching { GsmMediaTransportReadiness.isAvailable(applicationContext) }
                        .getOrDefault(false),
                    LocalAdbGsmStatus.AdbState.UNAVAILABLE
                )
            }
            runOnUiThread {
                refreshing = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                gsmStatus.setText(if (result.gsmReady) R.string.local_adb_gsm_ready
                    else R.string.local_adb_gsm_offline)
                adbStatus.setText(when (result.adbState) {
                    LocalAdbGsmStatus.AdbState.CONNECTED -> R.string.local_adb_connected
                    LocalAdbGsmStatus.AdbState.KEY_STORED_OFFLINE -> R.string.local_adb_key_saved
                    LocalAdbGsmStatus.AdbState.DEBUGGING_DISABLED -> R.string.local_adb_debug_off
                    LocalAdbGsmStatus.AdbState.NOT_CONFIGURED -> R.string.local_adb_unpaired
                    LocalAdbGsmStatus.AdbState.UNAVAILABLE -> R.string.local_adb_status_unavailable
                })
                // Keep the pairing section controlled exclusively by user action.
                // Do not reflow the screen when ADB authentication finishes.
                startButton.visibility = if (result.gsmReady) View.GONE else View.VISIBLE
            }
        }
    }

    private fun launchPairingFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
            return
        }
        if (!LocalAdbPairingNotification.begin(this)) {
            showMessage(R.string.local_adb_notification_permission_missing)
            return
        }
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFY) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) launchPairingFlow()
            else showMessage(R.string.local_adb_notification_permission_missing)
        }
    }

    private fun showMessage(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun execute(task: () -> LocalAdbGsmController.Outcome) {
        if (inProgress) return
        inProgress = true
        worker.execute {
            val result = task()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                inProgress = false
                val message = when (result) {
                    LocalAdbGsmController.Outcome.PAIRED -> R.string.local_adb_paired
                    LocalAdbGsmController.Outcome.READY -> R.string.local_adb_ready
                    LocalAdbGsmController.Outcome.ALREADY_READY -> R.string.local_adb_ready_existing
                    LocalAdbGsmController.Outcome.INVALID_INPUT -> R.string.local_adb_invalid
                    LocalAdbGsmController.Outcome.WRONG_DEVICE -> R.string.local_adb_wrong_device
                    LocalAdbGsmController.Outcome.CALL_BUSY -> R.string.local_adb_busy
                    LocalAdbGsmController.Outcome.BOOTSTRAP_FAILED -> R.string.local_adb_boot_failed
                    LocalAdbGsmController.Outcome.ADB_UNAVAILABLE -> R.string.local_adb_failed
                    LocalAdbGsmController.Outcome.NOT_DISCOVERED -> R.string.local_adb_not_discovered
                }
                showMessage(message)
                if (result == LocalAdbGsmController.Outcome.PAIRED ||
                    result == LocalAdbGsmController.Outcome.READY ||
                    result == LocalAdbGsmController.Outcome.ALREADY_READY) {
                    pairingExpanded = false
                    pairingSection.visibility = View.GONE
                }
                refreshStatus()
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_NOTIFY = 7109
    }
}
