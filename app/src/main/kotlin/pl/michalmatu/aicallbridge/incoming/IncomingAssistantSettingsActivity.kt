package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect
import pl.michalmatu.aicallbridge.ui.UiActionTone

/** Displays incoming-call preferences; all store/Telecom effects live in the controller. */
class IncomingAssistantSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var controller: IncomingAssistantSettingsController
    private lateinit var feedback: TextView
    private lateinit var simPicker: Spinner
    private lateinit var simPermissionAction: View
    private var simAccounts: List<PhoneAccountHandle> = emptyList()
    private var persistedAccountKey: String? = null

    override fun toolbarTitle(): String = getString(R.string.incoming_ui_title)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = IncomingAssistantSettingsController(this)
        feedback = TextView(this)
        val loadedPolicy = controller.current()
        val policy = loadedPolicy.getOrElse {
            feedback.text = getString(R.string.incoming_ui_read_error)
            IncomingAssistantPolicy("unavailable", AnsweringMode.MANUAL, null, true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@IncomingAssistantSettingsActivity, 20),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 16),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 20),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 28))
        }
        fun description(value: Int) = UiDesign.caption(this, getString(value)).apply {
            textSize = 14f
            setPadding(0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 7),
                0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 10))
        }
        content.addView(description(R.string.incoming_ui_intro))
        val mode = Spinner(this).apply {
            tag = "incoming-mode-spinner"
            contentDescription = getString(R.string.incoming_ui_mode_label)
            UiSelect.setOptions(this, listOf(
                getString(R.string.incoming_ui_mode_manual),
                getString(R.string.incoming_ui_mode_ai)))
            setSelection(savedInstanceState?.getInt(DRAFT_MODE)
                ?: if (policy.mode == AnsweringMode.AI) 1 else 0)
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        content.addView(UiDesign.caption(this, getString(R.string.incoming_ui_mode_label)),
            UiDesign.spaceAfter(this, 6))
        content.addView(mode, UiDesign.spaceAfter(this, 10))
        persistedAccountKey = savedInstanceState?.getString(DRAFT_ACCOUNT) ?: policy.accountKey
        simPicker = Spinner(this).apply {
            tag = "incoming-sim-spinner"
            contentDescription = getString(R.string.incoming_ui_sim_label)
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        reloadAccounts()
        content.addView(UiDesign.caption(this, getString(R.string.incoming_ui_sim_label)),
            UiDesign.spaceAfter(this, 6))
        content.addView(simPicker, UiDesign.spaceAfter(this, 10))
        simPermissionAction = UiDesign.action(this, getString(R.string.incoming_ui_grant_phone)) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_PHONE_STATE)
        }.apply {
            tag = "incoming-grant-sim"
            visibility = if (hasPhonePermission()) View.GONE else View.VISIBLE
        }
        content.addView(simPermissionAction, UiDesign.spaceAfter(this, 8))
        val recording = CheckBox(this).apply {
            tag = "incoming-recording"
            text = getString(R.string.incoming_ui_record)
            isChecked = savedInstanceState?.getBoolean(DRAFT_RECORD) ?: policy.recordEvidence
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        content.addView(recording, UiDesign.spaceAfter(this, 8))
        content.addView(description(R.string.incoming_ui_record_hint))
        content.addView(description(R.string.incoming_ui_engines_hint))
        content.addView(UiDesign.action(this, getString(R.string.incoming_ui_save), UiActionTone.PRIMARY) {
            val selected = if (mode.selectedItemPosition == 1) AnsweringMode.AI else AnsweringMode.MANUAL
            val chosen = simAccounts.getOrNull(simPicker.selectedItemPosition - 1)
            feedback.text = getString(when (controller.save(selected, chosen, recording.isChecked)) {
                IncomingAssistantSettingsController.SaveResult.NEED_SIM -> R.string.incoming_ui_choose_sim
                IncomingAssistantSettingsController.SaveResult.SAVED_AI -> R.string.incoming_ui_saved_ai
                IncomingAssistantSettingsController.SaveResult.SAVED_MANUAL -> R.string.incoming_ui_saved_manual
                IncomingAssistantSettingsController.SaveResult.FAILED -> R.string.incoming_ui_save_failed
            })
        }.apply {
            tag = "incoming-save-mode"
            isEnabled = loadedPolicy.isSuccess
        }, UiDesign.spaceAfter(this, 8))
        feedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        feedback.setTextColor(UiDesign.secondary)
        feedback.setPadding(0, UiDesign.dp(this, 4), 0, UiDesign.dp(this, 12))
        content.addView(feedback)
        content.addView(UiDesign.caption(this, getString(R.string.ux08_incoming_model_agent_hint)),
            UiDesign.spaceAfter(this, 8))
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun hasPhonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun reloadAccounts() {
        simAccounts = controller.availableAccounts()
        val labels = simAccounts.mapIndexed { index, handle ->
            controller.displayAccount(handle) ?: getString(R.string.incoming_ui_sim_fallback, index + 1)
        }
        UiSelect.setOptions(simPicker, if (labels.isEmpty())
            listOf(getString(R.string.incoming_ui_no_sim))
            else listOf(getString(R.string.incoming_ui_select_sim)) + labels)
        val knownIndex = controller.selectedAccountIndex(simAccounts, persistedAccountKey)
        simPicker.setSelection(if (knownIndex < 0) 0 else knownIndex + 1)
        if (persistedAccountKey != null && knownIndex < 0 && simAccounts.isNotEmpty()) {
            feedback.text = getString(R.string.incoming_ui_sim_removed)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHONE_STATE) {
            if (hasPhonePermission()) {
                reloadAccounts()
                simPermissionAction.visibility = View.GONE
                feedback.text = getString(if (simAccounts.isEmpty())
                    R.string.incoming_ui_no_sim else R.string.incoming_ui_choose_sim)
            } else {
                feedback.text = getString(R.string.incoming_ui_permission_denied)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::simPicker.isInitialized) {
            outState.putInt(DRAFT_MODE,
                window.decorView.findViewWithTag<Spinner>("incoming-mode-spinner")?.selectedItemPosition ?: 0)
            outState.putBoolean(DRAFT_RECORD,
                window.decorView.findViewWithTag<CheckBox>("incoming-recording")?.isChecked ?: false)
            outState.putString(DRAFT_ACCOUNT,
                simAccounts.getOrNull(simPicker.selectedItemPosition - 1)
                    ?.let(IncomingAssistantPolicyStore::accountKey) ?: persistedAccountKey)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::simPicker.isInitialized) {
            simPermissionAction.visibility = if (hasPhonePermission()) View.GONE else View.VISIBLE
            if (hasPhonePermission() && simAccounts.isEmpty()) reloadAccounts()
        }
    }

    private companion object {
        const val REQUEST_PHONE_STATE = 1002
        const val DRAFT_MODE = "settings_draft_mode"
        const val DRAFT_RECORD = "settings_draft_record"
        const val DRAFT_ACCOUNT = "settings_draft_account"
    }
}
