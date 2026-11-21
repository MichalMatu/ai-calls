package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsSettingsController
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

class ConversationPromptSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.prompt_settings_title)

    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var settings: ConversationPromptSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = UiDesign.content(this)
        setContentView(ScrollView(this).apply { addView(body) })
        val initial = runCatching {
            store = AndroidConversationPreparationStore(this)
            store.loadPromptSettings()
        }.getOrElse {
            body.addView(UiDesign.caption(this, getString(R.string.prompt_settings_load_error)))
            return
        }
        settings = initial
        body.addView(UiDesign.caption(this, getString(R.string.prompt_settings_scope)), UiDesign.spaceAfter(this, 12))
        fun entry(title: Int, open: () -> Unit) {
            body.addView(UiDesign.navigationRow(this, getString(title), onClick = open)
                .apply { tag = resources.getResourceEntryName(title) }, UiDesign.spaceAfter(this, 8))
        }
        entry(R.string.prompt_settings_incoming) {
            edit(R.string.prompt_settings_incoming, settings.incomingRole,
                ConversationInstructionBuilder.defaultRole(ConversationDirection.INCOMING)) { current, next ->
                current.copy(incomingRole = next)
            }
        }
        entry(R.string.prompt_settings_outgoing) {
            edit(R.string.prompt_settings_outgoing, settings.outgoingRole,
                ConversationInstructionBuilder.defaultRole(ConversationDirection.OUTGOING)) { current, next ->
                current.copy(outgoingRole = next)
            }
        }
        entry(R.string.prompt_settings_permissions) {
            edit(R.string.prompt_settings_permissions, settings.additionalPermissionRules,
                reference = ConversationInstructionBuilder.permissionInstruction(ConversationResponseFormat.TOOL_REPLY),
                description = R.string.prompt_settings_permissions_hint) { current, next ->
                current.copy(additionalPermissionRules = next)
            }
        }
        entry(R.string.prompt_settings_reply) {
            val schema = runCatching {
                ConversationReplyProtocol.schema(ConversationToolsSettingsController(this).enabled())
            }.getOrElse { getString(R.string.prompt_settings_load_error) }
            edit(R.string.prompt_settings_reply, settings.spokenReplyStyle,
                reference = ConversationInstructionBuilder.responseInstruction(ConversationResponseFormat.TOOL_REPLY) +
                    "\n\n" + schema, description = R.string.prompt_settings_reply_hint) { current, next ->
                current.copy(spokenReplyStyle = next)
            }
        }
        entry(R.string.prompt_settings_tools) {
            startActivity(Intent(this, ConversationToolsActivity::class.java))
        }
    }

    private fun edit(title: Int, current: String?, baseline: String = "", reference: String? = null,
        description: Int? = null, patch: (ConversationPromptSettings, String?) -> ConversationPromptSettings,
    ) {
        val body = UiDesign.content(this)
        description?.let {
            body.addView(UiDesign.caption(this, getString(it)), UiDesign.spaceAfter(this, 8))
        }
        reference?.let { contract ->
            body.addView(UiDesign.action(this, getString(R.string.prompt_settings_reference)) {
                val details = UiDesign.content(this).apply {
                    addView(UiDesign.caption(this@ConversationPromptSettingsActivity, contract).apply {
                        setTextIsSelectable(true)
                    })
                }
                AlertDialog.Builder(this).setTitle(R.string.prompt_settings_reference)
                    .setView(ScrollView(this).apply { addView(details) })
                    .setPositiveButton(android.R.string.ok, null).show()
            }, UiDesign.spaceAfter(this, 8))
        }
        val text = EditText(this).apply {
            tag = "conversation-prompt-field"
            contentDescription = getString(title)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            maxLines = 10
            filters = arrayOf(InputFilter.LengthFilter(ConversationPromptSettings.MAX_TEXT_LENGTH))
            setText(current ?: baseline)
        }
        body.addView(text)
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.prep_ui_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.model_settings_defaults, null).create()
        fun save(next: String?) {
            runCatching { store.updatePromptSettings { patch(it, next) } }
                .onSuccess { settings = it; dialog.dismiss() }
                .onFailure { Toast.makeText(this, R.string.prompt_settings_save_error, Toast.LENGTH_SHORT).show() }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                save(text.text.toString().trim().takeIf { it.isNotEmpty() && it != baseline })
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(null) }
        }
        dialog.show()
    }
}
