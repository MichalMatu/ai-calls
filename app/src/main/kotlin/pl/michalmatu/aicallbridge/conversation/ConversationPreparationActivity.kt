package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.OnBackPressedCallback
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/**
 * Transient configuration-change cache. Intentionally never persists raw drafts
 * into the Android activity Bundle; saved profiles use the encrypted store only.
 */
internal class PreparationDraftViewModel : ViewModel() {
    var profileId: String? = null
    var notes: String? = null
    var fields: Set<IdentityFieldId>? = null
}

/**
 * Owner-only context and reference scope. This screen never authorizes a
 * disclosure, starts a call, or changes the active task's captured prompt.
 */
class ConversationPreparationActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.prep_ui_title)

    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var drafts: PreparationDraftViewModel
    private var profileId = ConversationPreparation.INCOMING_ID
    private lateinit var savedProfile: ConversationPreparation
    private lateinit var notes: EditText
    private lateinit var status: TextView
    private val fields = linkedMapOf<IdentityFieldId, CheckBox>()
    private var restoredNotes: String? = null
    private var restoredFields: Set<IdentityFieldId>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        store = try {
            AndroidConversationPreparationStore(this)
        } catch (failure: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.prep_ui_open_error, failure.javaClass.simpleName)
            })
            return
        }
        drafts = ViewModelProvider(this)[PreparationDraftViewModel::class.java]
        profileId = drafts.profileId ?: savedInstanceState?.getString("profile_id")
            ?: ConversationPreparation.INCOMING_ID
        restoredNotes = drafts.notes
        restoredFields = drafts.fields
        drafts.notes = null
        drafts.fields = null
        showProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("profile_id", profileId)
        if (::drafts.isInitialized && ::notes.isInitialized && ::savedProfile.isInitialized) {
            drafts.profileId = profileId
            drafts.notes = notes.text.toString()
            drafts.fields = chosenFields()
        }
        super.onSaveInstanceState(outState)
    }

    private fun chosenFields(): Set<IdentityFieldId> =
        fields.filterValues { it.isChecked }.keys.toSet()

    private fun isDirty(): Boolean =
        ::savedProfile.isInitialized && ::notes.isInitialized &&
            (notes.text.toString() != savedProfile.notes ||
                chosenFields() != savedProfile.dataFields)

    private fun resetDiscardedDraft() {
        if (::savedProfile.isInitialized && ::notes.isInitialized) {
            notes.setText(savedProfile.notes)
            for ((field, checkbox) in fields) {
                checkbox.isChecked = field in savedProfile.dataFields
            }
        }
        restoredNotes = null
        restoredFields = null
        if (::drafts.isInitialized) {
            drafts.notes = null
            drafts.fields = null
        }
    }

    private fun confirmDiscard(action: () -> Unit, onStay: () -> Unit = {}) {
        if (!isDirty()) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.prep_ui_unsaved_title)
            .setMessage(R.string.prep_ui_unsaved_message)
            .setNegativeButton(R.string.prep_ui_stay) { _, _ -> onStay() }
            .setPositiveButton(R.string.prep_ui_discard) { _, _ ->
                resetDiscardedDraft()
                action()
            }
            .setOnCancelListener { onStay() }
            .show()
    }

    override fun consumeChromeBack(): Boolean {
        if (!isDirty()) return false
        confirmDiscard({ finish() })
        return true
    }

    override fun onTabNavigationRequested(action: () -> Unit) {
        confirmDiscard(action)
    }

    private fun fieldName(field: IdentityFieldId): String = getString(when (field) {
        IdentityFieldId.PHONE -> R.string.prep_ui_phone
        IdentityFieldId.FIRST_NAME -> R.string.prep_ui_first_name
        IdentityFieldId.LAST_NAME -> R.string.prep_ui_last_name
        IdentityFieldId.EMAIL -> R.string.prep_ui_email
        IdentityFieldId.ADDRESS -> R.string.prep_ui_address
        IdentityFieldId.DATE_OF_BIRTH -> R.string.prep_ui_birth
        IdentityFieldId.PESEL -> R.string.ux09_agent_pesel
    })

    private fun showProfile() {
        val profile = try {
            store.load(profileId)
        } catch (failure: Exception) {
            setContentView(UiDesign.caption(this,
                getString(R.string.prep_ui_read_error, failure.javaClass.simpleName)))
            return
        }
        savedProfile = profile
        val currentDraft = restoredNotes ?: profile.notes
        val currentFields = restoredFields ?: profile.dataFields
        restoredNotes = null
        restoredFields = null

        val content = UiDesign.content(this)
        status = UiDesign.caption(this, "").apply { tag = "preparation-status" }
        content.addView(UiDesign.caption(this, getString(R.string.prep_ui_intro)),
            UiDesign.spaceAfter(this, 20))

        content.addView(UiDesign.heading(this, getString(R.string.prep_ui_profile)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 10))
        val options = listOf(ConversationPreparation.INCOMING_ID, ConversationPreparation.OUTGOING_ID)
        val group = RadioGroup(this).apply {
            tag = "preparation-direction-options"
            orientation = RadioGroup.VERTICAL
        }
        val buttons = options.mapIndexed { index, id ->
            RadioButton(this).apply {
                this.id = View.generateViewId()
                tag = "preparation-direction:" + (if (index == 0) "incoming" else "outgoing")
                text = getString(if (index == 0)
                    R.string.prep_ui_incoming else R.string.prep_ui_outgoing)
                textSize = 16f
                minimumHeight = UiDesign.dp(this@ConversationPreparationActivity, 48)
                isChecked = id == profileId
            }.also(group::addView)
        }
        var suppress = false
        group.setOnCheckedChangeListener { _, checkedId ->
            if (suppress || checkedId == View.NO_ID) return@setOnCheckedChangeListener
            val chosen = options[buttons.indexOfFirst { it.id == checkedId }.coerceAtLeast(0)]
            if (chosen == profileId) return@setOnCheckedChangeListener
            confirmDiscard({
                profileId = chosen
                showProfile()
            }, {
                suppress = true
                group.check(buttons[options.indexOf(profileId)].id)
                suppress = false
            })
        }
        content.addView(group, UiDesign.spaceAfter(this, 20))

        val context = UiDesign.card(this).apply { tag = "preparation-context-card" }
        context.addView(UiDesign.heading(this, getString(R.string.prep_ui_general)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        context.addView(UiDesign.caption(this, getString(
            if (profile.direction == ConversationDirection.INCOMING)
                R.string.prep_ui_incoming_help else R.string.prep_ui_outgoing_help)),
            UiDesign.spaceAfter(this, 10))
        notes = EditText(this).apply {
            tag = "preparation-context-notes"
            id = View.generateViewId()
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            hint = getString(R.string.prep_ui_notes_hint)
            minLines = 4
            maxLines = 8
            setText(currentDraft)
            textSize = 15f
            setTextColor(UiDesign.ink)
            background = UiDesign.surfaceDrawable(this@ConversationPreparationActivity,
                UiDesign.softAccent, 12)
            setPadding(UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12))
        }
        context.addView(notes, UiDesign.spaceAfter(this, 10))
        context.addView(UiDesign.caption(this, getString(R.string.prep_ui_sensitive_warning)))
        content.addView(context, UiDesign.spaceAfter(this, 16))

        fields.clear()
        val dataCard = UiDesign.card(this).apply { tag = "preparation-vault-fields" }
        dataCard.addView(UiDesign.heading(this, getString(R.string.prep_ui_vault)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        dataCard.addView(UiDesign.caption(this, getString(R.string.prep_ui_vault_intro)),
            UiDesign.spaceAfter(this, 12))
        for (field in IdentityFieldId.entries) {
            val checkbox = CheckBox(this).apply {
                tag = "preparation-field:" + field.name
                text = fieldName(field)
                isChecked = field in currentFields
                minimumHeight = UiDesign.dp(this@ConversationPreparationActivity, 48)
            }
            fields[field] = checkbox
            dataCard.addView(checkbox)
        }
        dataCard.addView(UiDesign.caption(this, getString(R.string.prep_ui_reference_only)),
            UiDesign.spaceAfter(this, 3))
        content.addView(dataCard, UiDesign.spaceAfter(this, 16))

        val actions = UiDesign.card(this).apply { tag = "preparation-actions" }
        actions.addView(UiDesign.action(this, getString(R.string.prep_ui_save),
            UiActionTone.PRIMARY) {
            val selected = profile.withSettings(
                notes.text.toString(), profile.skills, chosenFields())
            try {
                store.save(selected)
                savedProfile = selected
                status.text = getString(R.string.prep_ui_saved)
            } catch (failure: Exception) {
                status.text = getString(R.string.prep_ui_save_error, failure.javaClass.simpleName)
            }
        }.apply { tag = "preparation-save" }, UiDesign.spaceAfter(this, 10))
        actions.addView(UiDesign.action(this, getString(R.string.prep_ui_preview)) {
            val selectedFields = chosenFields().sortedBy { it.ordinal }
                .joinToString(" · ", transform = ::fieldName)
                .ifBlank { getString(R.string.ux09_agent_none) }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = UiDesign.dp(this@ConversationPreparationActivity, 20)
                setPadding(pad, pad, pad, pad)
                addView(UiDesign.caption(this@ConversationPreparationActivity,
                    getString(R.string.prep_ui_preview_notice)),
                    UiDesign.spaceAfter(this@ConversationPreparationActivity, 12))
                addView(UiDesign.heading(this@ConversationPreparationActivity,
                    getString(R.string.prep_ui_general)).apply { textSize = 16f },
                    UiDesign.spaceAfter(this@ConversationPreparationActivity, 8))
                addView(TextView(this@ConversationPreparationActivity).apply {
                    text = notes.text.toString().ifBlank {
                        getString(R.string.prep_ui_preview_empty)
                    }
                    setTextIsSelectable(true)
                }, UiDesign.spaceAfter(this@ConversationPreparationActivity, 14))
                addView(UiDesign.heading(this@ConversationPreparationActivity,
                    getString(R.string.prep_ui_vault)).apply { textSize = 16f },
                    UiDesign.spaceAfter(this@ConversationPreparationActivity, 8))
                addView(UiDesign.caption(this@ConversationPreparationActivity, selectedFields))
            }
            AlertDialog.Builder(this@ConversationPreparationActivity)
                .setTitle(R.string.prep_ui_preview_title)
                .setView(ScrollView(this).apply { addView(box) })
                .setPositiveButton(android.R.string.ok, null).show()
        }.apply { tag = "preparation-preview" })
        content.addView(actions, UiDesign.spaceAfter(this, 12))
        content.addView(status)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }
}
