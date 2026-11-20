package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.DialerRoleCard
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness
import androidx.activity.OnBackPressedCallback

/** Displays incoming-call preferences; all store/Telecom effects live in the controller. */
class IncomingAssistantSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var controller: IncomingAssistantSettingsController
    private lateinit var dialerRole: DialerRoleCard
    private lateinit var feedback: TextView
    private var gsmHostStatus: TextView? = null
    private lateinit var simPicker: Spinner
    private lateinit var simPermissionAction: View
    private var simAccounts: List<PhoneAccountHandle> = emptyList()
    private var draftAccountKey: String? = null
    private var simSelectionTouched = false
    private var previousSimIndex = -1
    private var savedSettings: IncomingSettingsSelection? = null
    private lateinit var modePicker: Spinner
    private lateinit var recordingCheck: CheckBox
    private lateinit var savedState: TextView
    private lateinit var unsavedState: TextView
    private lateinit var saveAction: Button

    override fun toolbarTitle(): String = getString(R.string.incoming_ui_title)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = IncomingAssistantSettingsController(this)
        feedback = TextView(this)
        val loadedPolicy = controller.current()
        val policy = loadedPolicy.getOrElse {
            feedback.text = getString(R.string.incoming_ui_read_error)
            IncomingAssistantPolicy("unavailable", AnsweringMode.MANUAL, null, false)
        }
        if (loadedPolicy.isSuccess) {
            savedSettings = IncomingSettingsSelection(policy.mode, policy.accountKey,
                policy.recordEvidence)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmLeave { finish() } }
        })
        val content = UiDesign.content(this)
        // Make the actual GSM prerequisite visible where AI answering is enabled.
        // The role request only launches after an explicit user tap.
        dialerRole = DialerRoleCard(this, showOnlyWhenMissing = true)
        content.addView(dialerRole.view, UiDesign.spaceAfter(this, 12))
        fun description(value: Int) = UiDesign.caption(this, getString(value)).apply {
            textSize = 14f
            setPadding(0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 7),
                0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 10))
        }
        content.addView(description(R.string.incoming_ui_intro))
        savedState = UiDesign.status(this, "").apply {
            tag = "incoming-saved-status"
        }
        content.addView(savedState, UiDesign.spaceAfter(this, 10))
        if (CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB) {
            val hostStatus = UiDesign.caption(this, "").apply {
                tag = "incoming-gsm-host-status"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            gsmHostStatus = hostStatus
            content.addView(hostStatus, UiDesign.spaceAfter(this, 10))
            refreshGsmHostStatus()
        }
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
        modePicker = mode
        draftAccountKey = if (savedInstanceState?.containsKey(DRAFT_ACCOUNT) == true)
            savedInstanceState.getString(DRAFT_ACCOUNT) else policy.accountKey
        simSelectionTouched = savedInstanceState?.getBoolean(DRAFT_SIM_TOUCHED) ?: false
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
        recordingCheck = recording
        unsavedState = UiDesign.caption(this, "").apply {
            tag = "incoming-unsaved-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(unsavedState, UiDesign.spaceAfter(this, 8))
        saveAction = UiDesign.action(this, getString(R.string.incoming_ui_save), UiActionTone.PRIMARY) {
            val selected = if (mode.selectedItemPosition == 1) AnsweringMode.AI else AnsweringMode.MANUAL
            val chosen = simAccounts.getOrNull(simPicker.selectedItemPosition - 1)
            val result = controller.save(selected, chosen, recording.isChecked)
            feedback.text = getString(when (result) {
                IncomingAssistantSettingsController.SaveResult.NEED_SIM -> R.string.incoming_ui_choose_sim
                IncomingAssistantSettingsController.SaveResult.SAVED_AI -> R.string.incoming_ui_saved_ai
                IncomingAssistantSettingsController.SaveResult.SAVED_MANUAL -> R.string.incoming_ui_saved_manual
                IncomingAssistantSettingsController.SaveResult.FAILED -> R.string.incoming_ui_save_failed
            })
            if (result == IncomingAssistantSettingsController.SaveResult.SAVED_AI ||
                result == IncomingAssistantSettingsController.SaveResult.SAVED_MANUAL) {
                savedSettings = IncomingSettingsSelection(selected,
                    chosen?.let(IncomingAssistantPolicyStore::accountKey), recording.isChecked)
                draftAccountKey = savedSettings?.accountKey
                simSelectionTouched = false
                refreshSavedStatus()
            }
            updateSaveAvailability()
        }.apply { tag = "incoming-save-mode" }
        content.addView(saveAction, UiDesign.spaceAfter(this, 8))
        feedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        feedback.setTextColor(UiDesign.secondary)
        feedback.setPadding(0, UiDesign.dp(this, 4), 0, UiDesign.dp(this, 12))
        content.addView(feedback)
        setContentView(ScrollView(this).apply { addView(content) })
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                position: Int, id: Long) { updateSaveAvailability() }
        }
        simPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                position: Int, id: Long) {
                if (position != previousSimIndex) {
                    simSelectionTouched = true
                    previousSimIndex = position
                }
                updateSaveAvailability()
            }
        }
        recording.setOnCheckedChangeListener { _, _ -> updateSaveAvailability() }
        refreshSavedStatus()
        updateSaveAvailability()
    }

    private fun currentSelection(): IncomingSettingsSelection {
        val chosen = simAccounts.getOrNull(simPicker.selectedItemPosition - 1)
        val chosenKey = chosen?.let(IncomingAssistantPolicyStore::accountKey)
        val missingSavedSim = !simSelectionTouched && chosenKey == null &&
            draftAccountKey != null && controller.selectedAccountIndex(simAccounts, draftAccountKey) < 0
        return IncomingSettingsSelection(
            if (modePicker.selectedItemPosition == 1) AnsweringMode.AI else AnsweringMode.MANUAL,
            if (missingSavedSim) draftAccountKey else chosenKey, recordingCheck.isChecked)
    }

    private fun hasUnsavedChanges(): Boolean = savedSettings?.let { it != currentSelection() } ?: false

    private fun updateSaveAvailability() {
        if (!::saveAction.isInitialized || !::simPicker.isInitialized) return
        val dirty = hasUnsavedChanges()
        saveAction.isEnabled = savedSettings != null && dirty
        unsavedState.text = if (dirty) getString(R.string.incoming_ui_unsaved) else ""
        unsavedState.visibility = if (dirty) View.VISIBLE else View.GONE
    }

    private fun refreshSavedStatus() {
        if (!::savedState.isInitialized) return
        val saved = savedSettings
        savedState.visibility = if (saved == null) View.GONE else View.VISIBLE
        savedState.text = when {
            saved == null -> ""
            saved.mode == AnsweringMode.MANUAL -> getString(R.string.incoming_ui_saved_state_manual)
            else -> {
                val index = controller.selectedAccountIndex(simAccounts, saved.accountKey)
                val sim = simAccounts.getOrNull(index)?.let(controller::displayAccount)
                    ?: getString(R.string.incoming_ui_saved_sim_unavailable)
                getString(R.string.incoming_ui_saved_state_ai, sim)
            }
        }
    }

    private fun confirmLeave(leave: () -> Unit) {
        if (!::saveAction.isInitialized || !hasUnsavedChanges()) { leave(); return }
        AlertDialog.Builder(this).setTitle(R.string.incoming_ui_unsaved_title)
            .setMessage(R.string.incoming_ui_unsaved_warning)
            .setNegativeButton(R.string.incoming_ui_keep_editing, null)
            .setPositiveButton(R.string.incoming_ui_discard) { _, _ -> leave() }
            .show()
    }

    override fun onTabNavigationRequested(action: () -> Unit) { confirmLeave(action) }

    override fun consumeChromeBack(): Boolean {
        confirmLeave { finish() }
        return true
    }

    private fun refreshGsmHostStatus() {
        gsmHostStatus?.setText(if (GsmMediaTransportReadiness.isAvailable(this))
            R.string.incoming_ui_gsm_host_ready else R.string.incoming_ui_gsm_host_unavailable)
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
        val knownIndex = controller.selectedAccountIndex(simAccounts, draftAccountKey)
        previousSimIndex = if (knownIndex < 0) 0 else knownIndex + 1
        simPicker.setSelection(previousSimIndex)
        if (draftAccountKey != null && knownIndex < 0 && simAccounts.isNotEmpty()) {
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
                refreshSavedStatus()
                updateSaveAvailability()
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
            outState.putString(DRAFT_ACCOUNT, currentSelection().accountKey)
            outState.putBoolean(DRAFT_SIM_TOUCHED, simSelectionTouched)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::dialerRole.isInitialized) dialerRole.refresh()
        refreshGsmHostStatus()
        if (::simPicker.isInitialized) {
            simPermissionAction.visibility = if (hasPhonePermission()) View.GONE else View.VISIBLE
            if (hasPhonePermission() && simAccounts.isEmpty()) reloadAccounts()
            refreshSavedStatus()
            updateSaveAvailability()
        }
    }

    private companion object {
        const val REQUEST_PHONE_STATE = 1002
        const val DRAFT_MODE = "settings_draft_mode"
        const val DRAFT_RECORD = "settings_draft_record"
        const val DRAFT_ACCOUNT = "settings_draft_account"
        const val DRAFT_SIM_TOUCHED = "settings_draft_sim_touched"
    }
}
