package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/**
 * User-facing settings for the incoming receptionist only.
 * Legacy presets are compacted into the current tone and personal guidance.
 * Never starts a call, changes a phone grant, or edits an outgoing task.
 */
class ConversationSkillsActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.skills_ui_title)

    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var library: ConversationSkillLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            store = SkillLabIsolatedTestStore.forContext(this)
            library = store.loadSkillLibrary()
            render()
        } catch (error: Exception) {
            setContentView(UiDesign.caption(this,
                getString(R.string.skills_ux05_read_failed, error.javaClass.simpleName)))
        }
    }

    private fun applyChange(next: ConversationSkillLibrary) {
        if (next === library) return
        try {
            store.saveSkillLibrary(next)
            library = store.loadSkillLibrary()
            render(getString(R.string.skills_consumer_changes_next_call))
        } catch (failure: Exception) {
            AlertDialog.Builder(this).setTitle(R.string.skills_ui_not_saved)
                .setMessage(getString(R.string.skills_ux05_save_failed,
                    failure.javaClass.simpleName))
                .setPositiveButton(android.R.string.ok, null).show()
            render()
        }
    }

    private fun editPersonalInstructions() {
        val value = EditText(this).apply {
            tag = "receptionist-personal-instructions-editor"
            setText(library.personalInstructions())
            hint = getString(R.string.skills_personal_hint)
            gravity = Gravity.TOP
            minLines = 5
            maxLines = 10
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = UiDesign.dp(this@ConversationSkillsActivity, 20)
            setPadding(padding, padding, padding, padding)
            addView(UiDesign.caption(this@ConversationSkillsActivity,
                getString(R.string.skills_personal_scope)),
                UiDesign.spaceAfter(this@ConversationSkillsActivity, 12))
            addView(value)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.skills_personal_title)
            .setView(ScrollView(this).apply { addView(wrap) })
            .setNegativeButton(R.string.skills_ui_cancel, null)
            .setPositiveButton(R.string.skills_personal_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = value.text.toString()
                if (entered.length > ConversationSkillLibrary.MAX_PERSONAL_INSTRUCTIONS_LENGTH) {
                    value.error = getString(R.string.skills_personal_too_long)
                    return@setOnClickListener
                }
                val updated = runCatching {
                    library.withPersonalInstructions(entered,
                        getString(R.string.skills_personal_auto_preset))
                }.getOrElse {
                    value.error = getString(R.string.skills_personal_save_error)
                    return@setOnClickListener
                }
                val saved = runCatching {
                    store.saveSkillLibrary(updated)
                    true
                }.getOrElse {
                    value.error = getString(R.string.skills_personal_save_error)
                    false
                }
                if (saved) {
                    library = store.loadSkillLibrary()
                    dialog.dismiss()
                    render(getString(R.string.skills_consumer_changes_next_call))
                }
            }
        }
        dialog.show()
    }

    private fun render(feedback: String = "") {
        val content = UiDesign.content(this)
        content.addView(UiDesign.caption(this, getString(R.string.skills_consumer_intro)),
            UiDesign.spaceAfter(this, 20))

        val toneCard = UiDesign.card(this).apply { tag = "receptionist-tone-card" }
        toneCard.addView(UiDesign.heading(this, getString(R.string.skills_tone_title)).apply {
            textSize = 18f
        }, UiDesign.spaceAfter(this, 10))
        val options = RadioGroup(this).apply {
            tag = "receptionist-tone-options"
            orientation = RadioGroup.VERTICAL
        }
        for (version in 1..2) {
            val choice = RadioButton(this).apply {
                id = View.generateViewId()
                tag = "receptionist-tone-choice:" + version
                text = getString(if (version == 1)
                    R.string.skills_tone_natural else R.string.skills_tone_formal)
                textSize = 16f
                isChecked = library.receptionistToneVersion() == version
                minimumHeight = UiDesign.dp(this@ConversationSkillsActivity, 48)
                setOnClickListener {
                    if (library.receptionistToneVersion() != version) {
                        applyChange(library.withReceptionistTone(version))
                    }
                }
            }
            options.addView(choice)
        }
        toneCard.addView(options, UiDesign.spaceAfter(this, 10))
        val toneVersion = library.receptionistToneVersion()
        if (toneVersion in 1..2) {
            toneCard.addView(UiDesign.caption(this, getString(
                if (toneVersion == 2) R.string.skills_tone_formal_detail
                else R.string.skills_tone_natural_detail)))
        } else {
            toneCard.addView(UiDesign.caption(this, getString(R.string.skills_tone_legacy_note)))
        }
        content.addView(toneCard, UiDesign.spaceAfter(this, 16))

        val personalCard = UiDesign.card(this).apply { tag = "receptionist-personal-card" }
        personalCard.addView(UiDesign.heading(this, getString(R.string.skills_personal_title)).apply {
            textSize = 18f
        }, UiDesign.spaceAfter(this, 10))
        personalCard.addView(UiDesign.caption(this, getString(
            if (library.personalInstructions().isBlank())
                R.string.skills_personal_empty else R.string.skills_personal_saved)),
            UiDesign.spaceAfter(this, 12))
        personalCard.addView(UiDesign.action(this, getString(
            if (library.personalInstructions().isBlank())
                R.string.skills_personal_add else R.string.skills_personal_edit),
            UiActionTone.SECONDARY) { editPersonalInstructions() }.apply {
            tag = "receptionist-personal-edit"
        })
        content.addView(personalCard, UiDesign.spaceAfter(this, 16))

        if (feedback.isNotBlank()) {
            content.addView(UiDesign.caption(this, feedback).apply {
                tag = "receptionist-save-feedback"
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, UiDesign.spaceAfter(this, 10))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::library.isInitialized) {
            runCatching { store.loadSkillLibrary() }.onSuccess {
                if (it.activePresetId != library.activePresetId ||
                    it.presets != library.presets || it.skills != library.skills) {
                    library = it
                    render()
                }
            }
        }
    }
}
