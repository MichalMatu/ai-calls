package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/**
 * Consumer-first editor for version-pinned skills. No call, runtime grant or external tool action.
 * Factory presets stay immutable; every change to a custom preset persists in the existing store.
 */
class ConversationSkillsAdvancedActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.skills_advanced_title)

    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var library: ConversationSkillLibrary
    private var editing = false
    private val expandedHistory = mutableSetOf<String>()

    private fun presetName(preset: SkillPreset) = if (!preset.isFactory) preset.name else getString(
        when (preset.id) {
            "natural" -> R.string.skills_ux05_default_natural
            "formal" -> R.string.skills_ux05_default_formal
            "message" -> R.string.skills_ux05_default_message
            else -> R.string.skills_ui_custom
        })

    private fun skillName(skill: VersionedSkill): String {
        val selected = library.active.versions[skill.id] ?: skill.revisions.maxOf { it.version }
        skill.at(selected).metadata?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return when (skill.id) {
            "ConversationStyle" -> getString(R.string.skills_ux05_name_style)
            "MessageCapture" -> getString(R.string.skills_ux05_name_capture)
            "Clarification" -> getString(R.string.skills_ux05_name_clarify)
            "NaturalClosure" -> getString(R.string.skills_ux05_name_closure)
            else -> skill.name
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editing = savedInstanceState?.getBoolean("skills_editing") == true
        savedInstanceState?.getStringArrayList("skills_expanded")?.let(expandedHistory::addAll)
        try {
            store = SkillLabIsolatedTestStore.forContext(this)
            library = store.loadSkillLibrary()
            if (library.active.isFactory) editing = false
            render()
        } catch (error: Exception) {
            setContentView(UiDesign.caption(this,
                getString(R.string.skills_ux05_read_failed, error.javaClass.simpleName)))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("skills_editing", editing)
        outState.putStringArrayList("skills_expanded", ArrayList(expandedHistory))
        super.onSaveInstanceState(outState)
    }

    private fun change(next: ConversationSkillLibrary, feedback: String) {
        try {
            store.saveSkillLibrary(next)
            library = next
            expandedHistory.clear()
            render(feedback)
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle(R.string.skills_ui_not_saved)
                .setMessage(getString(R.string.skills_ux05_save_failed, error.javaClass.simpleName))
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun copyForEditing() {
        val name = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.skills_consumer_custom_name)
            setText(presetName(library.active) + " — " + getString(R.string.skills_ui_custom))
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.skills_consumer_customize)
            .setView(name)
            .setNegativeButton(R.string.skills_ui_cancel, null)
            .setPositiveButton(R.string.skills_ui_create) { _, _ ->
                runCatching { library.copyActive(name.text.toString()) }
                    .onSuccess {
                        editing = true
                        change(it, getString(R.string.skills_ux05_preset_created))
                    }.onFailure(::showError)
            }.show()
    }

    private fun editInstructions(skill: VersionedSkill, revision: Int) {
        val instructions = EditText(this).apply {
            tag = "skills-metadata-instructions:" + skill.id
            setText(skill.at(revision).instructions)
            minLines = 4
            maxLines = 9
            gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val inset = UiDesign.dp(this, 20)
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(inset, inset / 2, inset, inset / 2)
            addView(UiDesign.caption(this@ConversationSkillsAdvancedActivity,
                getString(R.string.skills_consumer_changes_next_call)),
                UiDesign.spaceAfter(this@ConversationSkillsAdvancedActivity, 10))
            addView(instructions)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skills_ux05_version_title, skillName(skill)))
            .setView(ScrollView(this).apply { addView(holder) })
            .setNegativeButton(R.string.skills_ui_cancel, null)
            .setPositiveButton(R.string.skills_ui_revision) { _, _ ->
                runCatching { library.reviseSkill(skill.id, instructions.text.toString()) }
                    .onSuccess { change(it, getString(R.string.skills_ux05_version_saved, skillName(skill))) }
                    .onFailure(::showError)
            }.show()
    }

    private fun appendSkillRow(card: LinearLayout, skill: VersionedSkill) {
        val selected = library.active.versions[skill.id]
        val isOn = selected != null
        val title = skillName(skill)
        val editMode = editing && !library.active.isFactory
        if (editMode) {
            val check = CheckBox(this).apply {
                tag = "skills-enabled:" + skill.id
                text = title
                isChecked = isOn
                textSize = 15f
                minimumHeight = UiDesign.dp(this@ConversationSkillsAdvancedActivity, 48)
                setOnClickListener {
                    val nextVersion = if (isChecked) selected
                        ?: skill.revisions.maxOf { it.version } else null
                    runCatching { library.bindVersion(skill.id, nextVersion) }
                        .onSuccess { change(it, getString(R.string.skills_ux05_skills_changed)) }
                        .onFailure(::showError)
                }
            }
            card.addView(check, UiDesign.spaceAfter(this, 4))
            val commands = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            commands.addView(UiDesign.action(this,
                getString(R.string.skills_consumer_edit_instructions)) {
                editInstructions(skill, selected ?: skill.revisions.maxOf { it.version })
            }.apply { tag = "skills-revise:" + skill.id },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            commands.addView(UiDesign.action(this,
                getString(R.string.skills_consumer_history)) {
                if (!expandedHistory.add(skill.id)) expandedHistory.remove(skill.id)
                render()
            }.apply { tag = "skills-history-toggle:" + skill.id },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = UiDesign.dp(this@ConversationSkillsAdvancedActivity, 8)
                })
            card.addView(commands, UiDesign.spaceAfter(this, 7))
            if (skill.id in expandedHistory) {
                val versions = skill.revisions.sortedByDescending { it.version }
                val picker = Spinner(this).apply {
                    tag = "skills-versions:" + skill.id
                    UiSelect.setOptions(this, versions.map {
                        getString(R.string.skills_ux05_version_label, it.version)
                    })
                    setSelection(versions.indexOfFirst { it.version == selected }.coerceAtLeast(0))
                }
                card.addView(picker, UiDesign.spaceAfter(this, 6))
                card.addView(UiDesign.action(this,
                    getString(R.string.skills_consumer_restore_version)) {
                    val version = versions.getOrNull(picker.selectedItemPosition)?.version
                        ?: return@action
                    runCatching { library.bindVersion(skill.id, version) }
                        .onSuccess { change(it, getString(R.string.skills_ux05_version_restored)) }
                        .onFailure(::showError)
                }.apply { tag = "skills-restore:" + skill.id },
                    UiDesign.spaceAfter(this, 10))
            }
        } else {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = UiDesign.dp(this@ConversationSkillsAdvancedActivity, 49)
                contentDescription = "$title, " + getString(
                    if (isOn) R.string.skills_ui_enabled else R.string.skills_ui_disabled)
                tag = "skills-summary:" + skill.id
            }
            row.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(UiDesign.ink)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiDesign.caption(this, getString(
                if (isOn) R.string.skills_ui_enabled else R.string.skills_ui_disabled)).apply {
                tag = "skills-factory-state:" + skill.id
                textSize = 13f
                setTextColor(if (isOn) UiDesign.primary else UiDesign.secondary)
            })
            card.addView(row)
        }
        // One compact dividing line instead of a full card and raw instruction per skill.
        if (skill !== library.skills.last()) card.addView(View(this).apply {
            setBackgroundColor(UiDesign.border)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiDesign.dp(this, 1)).apply {
            topMargin = UiDesign.dp(this@ConversationSkillsAdvancedActivity, 4)
            bottomMargin = UiDesign.dp(this@ConversationSkillsAdvancedActivity, 4)
        })
    }

    private fun render(feedback: String = "") {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillsAdvancedActivity, 20),
                UiDesign.dp(this@ConversationSkillsAdvancedActivity, 16),
                UiDesign.dp(this@ConversationSkillsAdvancedActivity, 20),
                UiDesign.dp(this@ConversationSkillsAdvancedActivity, 24))
        }
        body.addView(UiDesign.caption(this, getString(R.string.skills_consumer_intro)),
            UiDesign.spaceAfter(this, 16))
        val modes = UiDesign.card(this).apply { tag = "skills-mode-card" }
        modes.addView(UiDesign.heading(this, getString(R.string.skills_consumer_mode)).apply {
            textSize = 18f
        }, UiDesign.spaceAfter(this, 8))
        val presets = library.presets
        val picker = Spinner(this).apply {
            tag = "skills-preset-picker"
            contentDescription = getString(R.string.skills_consumer_mode)
            UiSelect.setOptions(this, presets.map(::presetName))
            setSelection(presets.indexOfFirst { it.id == library.activePresetId }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                    position: Int, id: Long) {
                    val selection = presets.getOrNull(position) ?: return
                    if (selection.id == library.activePresetId) return
                    editing = false
                    expandedHistory.clear()
                    change(library.select(selection.id),
                        getString(R.string.skills_ui_activated_feedback))
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        modes.addView(picker, UiDesign.spaceAfter(this, 5))
        body.addView(modes, UiDesign.spaceAfter(this, 16))
        val summary = UiDesign.card(this).apply { tag = "skills-behavior-card" }
        summary.addView(UiDesign.heading(this, getString(R.string.skills_consumer_behavior)).apply {
            textSize = 18f
        }, UiDesign.spaceAfter(this, 10))
        library.skills.forEach { appendSkillRow(summary, it) }
        body.addView(summary, UiDesign.spaceAfter(this, 16))

        val customizing = !library.active.isFactory && editing
        body.addView(UiDesign.action(this, getString(
            if (customizing) R.string.skills_consumer_done else R.string.skills_consumer_customize),
            if (customizing) UiActionTone.SECONDARY else UiActionTone.PRIMARY) {
            if (library.active.isFactory) copyForEditing()
            else {
                editing = !editing
                expandedHistory.clear()
                render()
            }
        }.apply {
            tag = if (library.active.isFactory) "skills-copy-active" else "skills-customize"
        }, UiDesign.spaceAfter(this, 12))
        if (feedback.isNotBlank()) body.addView(UiDesign.caption(this, feedback).apply {
            tag = "skills-change-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }, UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this,
            getString(R.string.skills_consumer_changes_next_call)))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        })
    }

    private fun showError(error: Throwable) {
        AlertDialog.Builder(this).setTitle(R.string.skills_ui_not_saved)
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::library.isInitialized) {
            runCatching { store.loadSkillLibrary() }.onSuccess {
                if (it != library && (it.activePresetId != library.activePresetId ||
                    it.skills != library.skills || it.presets != library.presets)) {
                    library = it
                    if (library.active.isFactory) editing = false
                    render()
                }
            }
        }
    }
}
