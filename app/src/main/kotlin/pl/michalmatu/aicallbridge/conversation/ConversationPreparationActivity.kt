package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiSelect
import pl.michalmatu.aicallbridge.R
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity

/** Editing one preparation profile never authorizes or starts a phone call. */
class ConversationPreparationActivity : UiChromeActivity(AppTab.AGENT) {
    private lateinit var store: AndroidConversationPreparationStore
    private var profileId = ConversationPreparation.OUTGOING_ID
    private lateinit var notes: EditText
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private val skills = linkedMapOf<ConversationSkill, CheckBox>()
    private val fields = linkedMapOf<IdentityFieldId, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = try { AndroidConversationPreparationStore(this) } catch (failure: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.prep_ui_open_error, failure.javaClass.simpleName)
            })
            return
        }
        profileId = savedInstanceState?.getString("profile_id") ?: ConversationPreparation.OUTGOING_ID
        showProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("profile_id", profileId)
        super.onSaveInstanceState(outState)
    }

    private fun showProfile() {
        val pad = UiDesign.dp(this, UiDesign.CONTENT_INSET)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        status = UiDesign.caption(this, "").apply { tag = "preparation-status" }

        content.addView(UiDesign.heading(this, getString(R.string.prep_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this, getString(R.string.prep_ui_intro)),
            UiDesign.spaceAfter(this, 20))
        content.addView(UiDesign.caption(this, getString(R.string.prep_ui_profile)), UiDesign.spaceAfter(this, 8))
        val options = listOf(ConversationPreparation.OUTGOING_ID, ConversationPreparation.INCOMING_ID)
        val selector = Spinner(this).apply {
            tag = "preparation-direction-picker"
            UiSelect.setOptions(this, listOf(getString(R.string.prep_ui_outgoing), getString(R.string.prep_ui_incoming)))
            setSelection(options.indexOf(profileId).coerceAtLeast(0))
            contentDescription = getString(R.string.prep_ui_profile)
        }
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = options[position]
                if (chosen != profileId) {
                    profileId = chosen
                    showProfile()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        content.addView(selector, UiDesign.spaceAfter(this, 20))

        val profile = try { store.load(profileId) } catch (failure: Exception) {
            status.text = getString(R.string.prep_ui_read_error, failure.javaClass.simpleName)
            content.addView(status)
            return
        }
        val contextCard = UiDesign.card(this)
        contextCard.addView(UiDesign.heading(this, getString(R.string.prep_ui_general)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        contextCard.addView(UiDesign.caption(this,
            if (profile.direction == ConversationDirection.INCOMING)
                getString(R.string.prep_ui_incoming_help)
            else getString(R.string.prep_ui_outgoing_help)
        ), UiDesign.spaceAfter(this, 12))
        notes = EditText(this).apply {
            hint = getString(R.string.prep_ui_notes_hint)
            minLines = 4
            maxLines = 8
            setText(profile.notes)
            textSize = 15f
            setTextColor(UiDesign.ink)
            background = UiDesign.surfaceDrawable(this@ConversationPreparationActivity,
                UiDesign.softAccent, 12)
            setPadding(UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12),
                UiDesign.dp(this@ConversationPreparationActivity, 12))
        }
        contextCard.addView(notes)
        content.addView(contextCard, UiDesign.spaceAfter(this, 20))

        skills.clear()
        fields.clear()
        val skillsCard = UiDesign.card(this).apply { tag = "preparation-skills" }
        skillsCard.addView(UiDesign.heading(this, "Skills").apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        ConversationSkill.entries.forEach { skill ->
            val choice = CheckBox(this).apply {
                text = skill.title
                isChecked = skill in profile.skills
                minimumHeight = UiDesign.dp(this@ConversationPreparationActivity, 48)
            }
            skills[skill] = choice
            skillsCard.addView(choice)
        }
        content.addView(skillsCard, UiDesign.spaceAfter(this, 20))

        val fieldsCard = UiDesign.card(this).apply { tag = "preparation-vault-fields" }
        fieldsCard.addView(UiDesign.heading(this, getString(R.string.prep_ui_vault)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        fieldsCard.addView(UiDesign.caption(this,
            getString(R.string.prep_ui_vault_intro)
        ), UiDesign.spaceAfter(this, 8))
        IdentityFieldId.entries.forEach { field ->
            val choice = CheckBox(this).apply {
                text = when (field) {
                    IdentityFieldId.PHONE -> getString(R.string.prep_ui_phone)
                    IdentityFieldId.FIRST_NAME -> getString(R.string.prep_ui_first_name)
                    IdentityFieldId.LAST_NAME -> getString(R.string.prep_ui_last_name)
                    IdentityFieldId.EMAIL -> getString(R.string.prep_ui_email)
                    IdentityFieldId.ADDRESS -> getString(R.string.prep_ui_address)
                    IdentityFieldId.DATE_OF_BIRTH -> getString(R.string.prep_ui_birth)
                    IdentityFieldId.PESEL -> "PESEL"
                }
                isChecked = field in profile.dataFields
                minimumHeight = UiDesign.dp(this@ConversationPreparationActivity, 48)
            }
            fields[field] = choice
            fieldsCard.addView(choice)
        }
        content.addView(fieldsCard, UiDesign.spaceAfter(this, 20))

        fun selection() = profile.withSettings(
            notes.text.toString(),
            skills.filterValues { it.isChecked }.keys,
            fields.filterValues { it.isChecked }.keys,
        )
        val actions = UiDesign.card(this).apply { tag = "preparation-actions" }
        actions.addView(UiDesign.action(this, getString(R.string.prep_ui_save), UiActionTone.PRIMARY) {
            try {
                store.save(selection())
                status.text = getString(R.string.prep_ui_saved)
            } catch (failure: Exception) {
                status.text = getString(R.string.prep_ui_save_error, failure.javaClass.simpleName)
            }
        }, UiDesign.spaceAfter(this, 12))
        content.addView(actions, UiDesign.spaceAfter(this, 12))
        val secondaryActions = UiDesign.card(this).apply { tag = "preparation-secondary-actions" }
        secondaryActions.addView(UiDesign.action(this, getString(R.string.prep_ui_preview)) {
            val selected = selection()
            val prompt = ConversationInstructionBuilder.build(
                selected, "Cel pochodzi z konkretnego zlecenia rozmowy.",
                if (selected.direction == ConversationDirection.INCOMING)
                    ConversationResponseFormat.TOOL_REPLY else ConversationResponseFormat.SPOKEN_TEXT)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = UiDesign.dp(this@ConversationPreparationActivity, 20)
                setPadding(pad, pad, pad, pad)
            }
            panel.addView(UiDesign.heading(this, getString(R.string.ux07_preview_scope)).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 8))
            panel.addView(UiDesign.caption(this, getString(R.string.ux07_preview_count,
                selected.skills.size, selected.dataFields.size)).apply {
                textSize = 15f
                setTextColor(UiDesign.ink)
            }, UiDesign.spaceAfter(this, 16))
            panel.addView(UiDesign.heading(this, getString(R.string.ux07_preview_prompt)).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 8))
            panel.addView(TextView(this).apply {
                text = prompt
                textSize = 14f
                setTextColor(UiDesign.secondary)
                setTextIsSelectable(true)
            })
            AlertDialog.Builder(this@ConversationPreparationActivity)
                .setTitle(getString(R.string.prep_ui_preview_title))
                .setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton(android.R.string.ok, null).show()
        }, UiDesign.spaceAfter(this, 12))
        secondaryActions.addView(UiDesign.action(this, getString(R.string.prep_ui_edit_phone)) {
            startActivity(Intent(this@ConversationPreparationActivity,
                PhoneEnrollmentActivity::class.java))
        }, UiDesign.spaceAfter(this, 12))
        secondaryActions.addView(UiDesign.action(this, getString(R.string.prep_ui_presets)) {
            startActivity(Intent(this@ConversationPreparationActivity,
                ConversationSkillsActivity::class.java))
        })
        content.addView(secondaryActions, UiDesign.spaceAfter(this, 12))
        content.addView(status)
    }
}
