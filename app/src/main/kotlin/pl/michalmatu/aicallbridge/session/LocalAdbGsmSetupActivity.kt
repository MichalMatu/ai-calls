package pl.michalmatu.aicallbridge.session

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Explicit local pairing page; not a terminal and no automatic ADB startup. */
class LocalAdbGsmSetupActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private var inProgress = false

    override fun toolbarTitle(): String = getString(R.string.local_adb_title)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != GsmShellMediaProtocol.S8_PACKAGE) {
            finish()
            return
        }
        val content = UiDesign.content(this)
        content.addView(UiDesign.caption(this, getString(R.string.local_adb_intro)),
            UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.local_adb_open_settings)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }, UiDesign.spaceAfter(this, 12))
        fun field(label: Int, password: Boolean = false): EditText {
            content.addView(UiDesign.caption(this, getString(label)), UiDesign.spaceAfter(this, 4))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    (if (password) InputType.TYPE_NUMBER_VARIATION_PASSWORD else 0)
                setSingleLine(true)
                minimumHeight = UiDesign.dp(this@LocalAdbGsmSetupActivity, 48)
                content.addView(this, UiDesign.spaceAfter(this@LocalAdbGsmSetupActivity, 8))
            }
        }
        val pairCode = field(R.string.local_adb_pair_code, password = true)
        content.addView(UiDesign.action(this, getString(R.string.local_adb_pair), UiActionTone.PRIMARY) {
            val code = pairCode.text.toString()
            pairCode.text?.clear()
            execute {
                val controller = LocalAdbGsmController(applicationContext)
                val paired = controller.pair(code)
                if (paired != LocalAdbGsmController.Outcome.PAIRED) paired
                else {
                    // First-time pairing should not require a second tap.
                    when (val ready = controller.bootstrap()) {
                        LocalAdbGsmController.Outcome.READY,
                        LocalAdbGsmController.Outcome.ALREADY_READY -> ready
                        else -> paired // Pairing succeeded; retry is explicit.
                    }
                }
            }
        }, UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.local_adb_start), UiActionTone.PRIMARY) {
            execute { LocalAdbGsmController(applicationContext).bootstrap() }
        }, UiDesign.spaceAfter(this, 12))
        status = UiDesign.caption(this, getString(R.string.local_adb_idle)).apply {
            tag = "local-adb-status"
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        setContentView(ScrollView(this).apply { addView(content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)) })
    }

    private fun execute(task: () -> LocalAdbGsmController.Outcome) {
        if (inProgress) return
        inProgress = true
        status.setText(R.string.local_adb_working)
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
                status.setText(message)
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
