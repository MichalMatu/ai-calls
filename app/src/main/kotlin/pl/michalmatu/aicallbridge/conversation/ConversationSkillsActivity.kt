package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect
import pl.michalmatu.aicallbridge.ui.UiActionTone
import android.graphics.Typeface
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.view.View
import pl.michalmatu.aicallbridge.R

/** Edits prompt-only skills and revision-pinned presets. Never starts a call or changes a grant. */
class ConversationSkillsActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.skills_ui_title)
    private fun visiblePresetName(preset: SkillPreset): String = if (!preset.isFactory) preset.name else when (preset.id) {
        "natural" -> getString(R.string.skills_ux05_default_natural)
        "formal" -> getString(R.string.skills_ux05_default_formal)
        "message" -> getString(R.string.skills_ux05_default_message)
        else -> preset.name
    }
    private fun visibleSkillName(skill: VersionedSkill): String = when (skill.id) {
        "ConversationStyle" -> getString(R.string.skills_ux05_name_style)
        "MessageCapture" -> getString(R.string.skills_ux05_name_capture)
        "Clarification" -> getString(R.string.skills_ux05_name_clarify)
        "NaturalClosure" -> getString(R.string.skills_ux05_name_closure)
        else -> skill.name
    }
    private fun visibleSectionTitle(section: SkillSection): String = getString(when (section) {
        SkillSection.STYLE -> R.string.skills_ux05_section_style
        SkillSection.MESSAGE -> R.string.skills_ux05_section_message
        SkillSection.CLOSURE -> R.string.skills_ux05_section_closure
    })

    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var library: ConversationSkillLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            store = AndroidConversationPreparationStore(this)
            library = store.loadSkillLibrary()
            show()
        } catch (error: Exception) {
            setContentView(TextView(this).apply { text = getString(R.string.skills_ux05_read_failed, error.javaClass.simpleName) })
        }
    }

    private fun label(text: String, size: Float = 16f) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(if (size < 16f) UiDesign.secondary else UiDesign.ink)
            setLineSpacing(UiDesign.dp(this@ConversationSkillsActivity, 3).toFloat(), 1f)
            setPadding(0, UiDesign.dp(this@ConversationSkillsActivity, 7),
                0, UiDesign.dp(this@ConversationSkillsActivity, 7))
        }

    private fun applyChange(newLibrary: ConversationSkillLibrary, message: String) {
        try {
            store.saveSkillLibrary(newLibrary)
            library = newLibrary
            show(message)
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle(getString(R.string.skills_ui_not_saved))
                .setMessage(getString(R.string.skills_ux05_save_failed, error.javaClass.simpleName))
                .setPositiveButton("OK", null).show()
        }
    }

    private fun show(message: String = "") {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillsActivity, 20), UiDesign.dp(this@ConversationSkillsActivity, 16), UiDesign.dp(this@ConversationSkillsActivity, 20), UiDesign.dp(this@ConversationSkillsActivity, 28))
        }
        content.addView(UiDesign.caption(this,
            getString(R.string.skills_ui_intro).trim() + "\n\n" +
                getString(R.string.skills_ui_intro2).trim()),
            UiDesign.spaceAfter(this, 14))
        val activeCard = UiDesign.card(this, 12)
        activeCard.addView(UiDesign.caption(this, getString(R.string.skills_ui_active).trim()))
        activeCard.addView(UiDesign.heading(this, visiblePresetName(library.active) + " · " +
            if (library.active.isFactory) getString(R.string.skills_ui_factory)
            else getString(R.string.skills_ui_custom)).apply { textSize = 16f })
        content.addView(activeCard, UiDesign.spaceAfter(this, 12))
        val options = library.presets
        val picker = Spinner(this).apply {
            tag = "skills-preset-picker"
            UiSelect.setOptions(this, options.map {
                visiblePresetName(it) + " (" + getString(if (it.isFactory) R.string.skills_ui_factory else R.string.skills_ui_custom) + ")"
            })
            setSelection(options.indexOfFirst { it.id == library.activePresetId })
        }
        content.addView(picker, UiDesign.spaceAfter(this, 8))
        val activate = UiDesign.action(this, getString(R.string.skills_ui_activate),
            UiActionTone.PRIMARY) {
            val selected = options.getOrNull(picker.selectedItemPosition) ?: return@action
            if (selected.id != library.activePresetId) {
                applyChange(library.select(selected.id),
                    getString(R.string.skills_ui_activated_feedback))
            }
        }.apply { tag = "skills-activate" }
        fun refreshActivation(position: Int) {
            val selected = options.getOrNull(position)
            val needsActivation = selected != null && selected.id != library.activePresetId
            activate.isEnabled = needsActivation
            activate.text = getString(if (needsActivation) R.string.skills_ui_activate
                else R.string.skills_ui_current_preset)
            UiDesign.styleAction(activate, if (needsActivation) UiActionTone.PRIMARY
                else UiActionTone.SECONDARY)
        }
        refreshActivation(picker.selectedItemPosition)
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshActivation(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                refreshActivation(-1)
            }
        }
        content.addView(activate, UiDesign.spaceAfter(this, 8))
        val secondaryActions = UiDesign.card(this, 12).apply {
            tag = "skills-secondary-actions"
        }
        secondaryActions.addView(Button(this).apply {
            text = getString(R.string.skills_ui_copy)
            UiDesign.styleAction(this, UiActionTone.SECONDARY)
            setOnClickListener {
                val name = EditText(this@ConversationSkillsActivity).apply {
                    setSingleLine(true)
                    setText(library.active.name + " — własny")
                }
                AlertDialog.Builder(this@ConversationSkillsActivity)
                    .setTitle(getString(R.string.skills_ui_new))
                    .setView(name)
                    .setNegativeButton(getString(R.string.skills_ui_cancel), null)
                    .setPositiveButton(getString(R.string.skills_ui_create)) { _, _ ->
                        runCatching { library.copyActive(name.text.toString()) }
                            .onSuccess { applyChange(it, getString(R.string.skills_ux05_preset_created)) }
                            .onFailure { showError(it) }
                    }.show()
            }
        }, UiDesign.spaceAfter(this, 8))
        secondaryActions.addView(Button(this).apply {
            text = getString(R.string.skills_ui_lab)
            UiDesign.styleAction(this, UiActionTone.SECONDARY)
            setOnClickListener {
                startActivity(Intent(this@ConversationSkillsActivity, ConversationSkillLabActivity::class.java))
            }
        }, UiDesign.spaceAfter(this, 8))
        secondaryActions.addView(Button(this).apply {
            text = getString(R.string.skills_ui_preview)
            UiDesign.styleAction(this, UiActionTone.SECONDARY)
            setOnClickListener {
                val profile = store.load(ConversationPreparation.INCOMING_ID)
                    .withSkillSnapshot(library.snapshot())
                val prompt = ConversationInstructionBuilder.build(profile,
                    "Przyjmij wiadomość dla właściciela telefonu.",
                    ConversationResponseFormat.TOOL_REPLY)
                val preview = LinearLayout(this@ConversationSkillsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val inset = UiDesign.dp(this@ConversationSkillsActivity, 20)
                    setPadding(inset, inset, inset, inset)
                }
                preview.addView(UiDesign.heading(this@ConversationSkillsActivity,
                    visiblePresetName(library.active)).apply { textSize = 17f },
                    UiDesign.spaceAfter(this@ConversationSkillsActivity, 8))
                preview.addView(UiDesign.caption(this@ConversationSkillsActivity,
                    getString(R.string.ux07_skills_preview_count,
                        library.active.versions.size)).apply {
                    textSize = 15f
                    setTextColor(UiDesign.ink)
                }, UiDesign.spaceAfter(this@ConversationSkillsActivity, 16))
                preview.addView(UiDesign.heading(this@ConversationSkillsActivity,
                    getString(R.string.ux07_skills_preview_prompt)).apply { textSize = 17f },
                    UiDesign.spaceAfter(this@ConversationSkillsActivity, 8))
                preview.addView(TextView(this@ConversationSkillsActivity).apply {
                    text = prompt
                    textSize = 14f
                    setTextColor(UiDesign.secondary)
                    setTextIsSelectable(true)
                })
                AlertDialog.Builder(this@ConversationSkillsActivity)
                    .setTitle(getString(R.string.skills_ui_preview))
                    .setView(ScrollView(this@ConversationSkillsActivity).apply { addView(preview) })
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        })
        content.addView(secondaryActions, UiDesign.spaceAfter(this, 14))
        if (message.isNotBlank()) content.addView(UiDesign.caption(this, message),
            UiDesign.spaceAfter(this, 8))
        if (library.active.isFactory) content.addView(UiDesign.heading(this,
            getString(R.string.skills_ui_factory_readonly)).apply { textSize = 16f })
        for (section in SkillSection.entries) {
            content.addView(UiDesign.heading(this, visibleSectionTitle(section)).apply { textSize = 18f },
                UiDesign.spaceAfter(this, 8))
            for (skill in library.skills.filter { it.section == section }) {
                val skillCard = UiDesign.card(this, 12)
                val activeVersion = library.active.versions[skill.id]
                val on = activeVersion != null
                if (library.active.isFactory) {
                    skillCard.addView(TextView(this).apply {
                        text = visibleSkillName(skill) + " · " + getString(if (on)
                            R.string.skills_ui_enabled else R.string.skills_ui_disabled) +
                            (if (on) " · v" + activeVersion else "")
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiDesign.ink)
                        minimumHeight = UiDesign.dp(this@ConversationSkillsActivity, 48)
                        tag = "skills-factory-state:" + skill.id
                    })
                } else {
                    val choice = CheckBox(this).apply {
                        text = visibleSkillName(skill) + " · " + skill.id +
                            (if (on) " · v" + activeVersion else " · " + getString(R.string.skills_ux05_disabled))
                        isChecked = on
                        minimumHeight = UiDesign.dp(this@ConversationSkillsActivity, 48)
                        setOnClickListener {
                            applyChange(library.bindVersion(skill.id,
                                if (isChecked) skill.revisions.maxOf { it.version } else null),
                                getString(R.string.skills_ux05_skills_changed))
                        }
                    }
                    skillCard.addView(choice)
                }
                val instructions = TextView(this).apply {
                    val complete = skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions
                    text = complete
                    contentDescription = complete
                    tag = "skills-instructions:" + skill.id
                    textSize = 14f
                    setTextColor(UiDesign.secondary)
                    setLineSpacing(UiDesign.dp(this@ConversationSkillsActivity, 3).toFloat(), 1f)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    visibility = if (on) View.VISIBLE else View.GONE
                    isClickable = on
                    isFocusable = on
                    setOnClickListener {
                        maxLines = if (maxLines == 3) Int.MAX_VALUE else 3
                    }
                    setPadding(UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 6),
                        UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 16))
                }
                if (!on) {
                    skillCard.addView(UiDesign.caption(this,
                        getString(R.string.ux09_skill_inactive)).apply {
                        textSize = 14f
                        tag = "skills-inactive-state:" + skill.id
                    }, UiDesign.spaceAfter(this, 6))
                    skillCard.addView(UiDesign.caption(this,
                        getString(R.string.ux09_skill_show_inactive)).apply {
                        tag = "skills-inactive-toggle:" + skill.id
                        textSize = 14f
                        setTextColor(UiDesign.primary)
                        minimumHeight = UiDesign.dp(this@ConversationSkillsActivity, 48)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val revealed = instructions.visibility != View.VISIBLE
                            instructions.visibility = if (revealed) View.VISIBLE else View.GONE
                            text = getString(if (revealed) R.string.ux09_skill_hide_inactive
                                else R.string.ux09_skill_show_inactive)
                        }
                    })
                }
                skillCard.addView(instructions)
                if (!library.active.isFactory) {
                    skillCard.addView(Button(this).apply {
                        UiDesign.styleAction(this, UiActionTone.SECONDARY)
                        text = getString(R.string.skills_ux05_edit_new_version, visibleSkillName(skill))
                        setOnClickListener {
                            val input = EditText(this@ConversationSkillsActivity).apply {
                                setText(skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions)
                                minLines = 4
                                maxLines = 12
                            }
                            AlertDialog.Builder(this@ConversationSkillsActivity).setTitle(getString(R.string.skills_ux05_version_title, visibleSkillName(skill)))
                                .setView(input)
                                .setNegativeButton(getString(R.string.skills_ui_cancel), null)
                                .setPositiveButton(getString(R.string.skills_ui_revision)) { _, _ ->
                                    runCatching { library.reviseSkill(skill.id, input.text.toString()) }
                                        .onSuccess { applyChange(it, getString(R.string.skills_ux05_version_saved, visibleSkillName(skill))) }
                                        .onFailure { showError(it) }
                                }.show()
                        }
                    })
                    val versions = skill.revisions.sortedByDescending { it.version }
                    val revisionPicker = Spinner(this).apply {
                        UiSelect.setOptions(this, versions.map { getString(R.string.skills_ux05_version_label, it.version) })
                        setSelection(versions.indexOfFirst { it.version == activeVersion }.coerceAtLeast(0))
                    }
                    skillCard.addView(revisionPicker)
                    skillCard.addView(Button(this).apply {
                        UiDesign.styleAction(this, UiActionTone.SECONDARY)
                        text = getString(R.string.skills_ux05_version_restore, visibleSkillName(skill))
                        setOnClickListener {
                            applyChange(library.bindVersion(skill.id, versions[revisionPicker.selectedItemPosition].version),
                                getString(R.string.skills_ux05_version_restored))
                        }
                    })
                }
                content.addView(skillCard, UiDesign.spaceAfter(this, 10))
            }
        }
        content.addView(UiDesign.caption(this,
            getString(R.string.skills_ux05_warning)))
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun showError(error: Throwable) {
        AlertDialog.Builder(this).setTitle(getString(R.string.skills_ui_not_saved))
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton("OK", null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::library.isInitialized) {
            runCatching { store.loadSkillLibrary() }.onSuccess {
                if (it.activePresetId != library.activePresetId) { library = it; show() }
            }
        }
    }
}
