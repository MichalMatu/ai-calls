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
class ConversationSkillsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var library: ConversationSkillLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            store = AndroidConversationPreparationStore(this)
            library = store.loadSkillLibrary()
            show()
        } catch (error: Exception) {
            setContentView(TextView(this).apply { text = "Nie można odczytać skillsów: " + error.javaClass.simpleName })
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
                .setMessage("Nie udało się zapisać zmian: " + error.javaClass.simpleName)
                .setPositiveButton("OK", null).show()
        }
    }

    private fun show(message: String = "") {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillsActivity, 20), UiDesign.dp(this@ConversationSkillsActivity, 16), UiDesign.dp(this@ConversationSkillsActivity, 20), UiDesign.dp(this@ConversationSkillsActivity, 28))
        }
        content.addView(UiDesign.heading(this, getString(R.string.skills_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            getString(R.string.skills_ui_intro).trim() + "\n\n" +
                getString(R.string.skills_ui_intro2).trim()),
            UiDesign.spaceAfter(this, 14))
        val activeCard = UiDesign.card(this, 12)
        activeCard.addView(UiDesign.caption(this, getString(R.string.skills_ui_active).trim()))
        activeCard.addView(UiDesign.heading(this, library.active.name + " · " +
            if (library.active.isFactory) getString(R.string.skills_ui_factory)
            else getString(R.string.skills_ui_custom)).apply { textSize = 16f })
        content.addView(activeCard, UiDesign.spaceAfter(this, 12))
        val options = library.presets
        val picker = Spinner(this).apply {
            tag = "skills-preset-picker"
            UiSelect.setOptions(this, options.map {
                it.name + " (" + getString(if (it.isFactory) R.string.skills_ui_factory else R.string.skills_ui_custom) + ")"
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
        }.apply {
            tag = "skills-activate"
            isEnabled = options.getOrNull(picker.selectedItemPosition)?.id != library.activePresetId
        }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                activate.isEnabled = options.getOrNull(position)?.id != library.activePresetId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { activate.isEnabled = false }
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
                            .onSuccess { applyChange(it, "Utworzono własny preset.") }
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
                AlertDialog.Builder(this@ConversationSkillsActivity).setTitle(library.active.name)
                    .setMessage(ConversationInstructionBuilder.build(profile,
                        "Przyjmij wiadomość dla właściciela telefonu.",
                        ConversationResponseFormat.TOOL_REPLY))
                    .setPositiveButton("OK", null).show()
            }
        })
        content.addView(secondaryActions, UiDesign.spaceAfter(this, 14))
        if (message.isNotBlank()) content.addView(UiDesign.caption(this, message),
            UiDesign.spaceAfter(this, 8))
        if (library.active.isFactory) content.addView(UiDesign.heading(this,
            getString(R.string.skills_ui_factory_readonly)).apply { textSize = 16f })
        for (section in SkillSection.entries) {
            content.addView(UiDesign.heading(this, section.title).apply { textSize = 18f },
                UiDesign.spaceAfter(this, 8))
            for (skill in library.skills.filter { it.section == section }) {
                val skillCard = UiDesign.card(this, 12)
                val activeVersion = library.active.versions[skill.id]
                val on = activeVersion != null
                if (library.active.isFactory) {
                    skillCard.addView(TextView(this).apply {
                        text = skill.name + " · " + getString(if (on)
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
                        text = skill.name + " · " + skill.id +
                            (if (on) " · v" + activeVersion else " · wyłączony")
                        isChecked = on
                        minimumHeight = UiDesign.dp(this@ConversationSkillsActivity, 48)
                        setOnClickListener {
                            applyChange(library.bindVersion(skill.id,
                                if (isChecked) skill.revisions.maxOf { it.version } else null),
                                "Zmieniono aktywne skillsy.")
                        }
                    }
                    skillCard.addView(choice)
                }
                skillCard.addView(TextView(this).apply {
                    text = skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions
                    textSize = 14f
                    setTextColor(UiDesign.secondary)
                    setLineSpacing(UiDesign.dp(this@ConversationSkillsActivity, 3).toFloat(), 1f)
                    setPadding(UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 6),
                        UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 16))
                })
                if (!library.active.isFactory) {
                    skillCard.addView(Button(this).apply {
                        UiDesign.styleAction(this, UiActionTone.SECONDARY)
                        text = "Edytuj " + skill.name + " — nowa wersja"
                        setOnClickListener {
                            val input = EditText(this@ConversationSkillsActivity).apply {
                                setText(skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions)
                                minLines = 4
                                maxLines = 12
                            }
                            AlertDialog.Builder(this@ConversationSkillsActivity).setTitle("Nowa wersja: " + skill.name)
                                .setView(input)
                                .setNegativeButton(getString(R.string.skills_ui_cancel), null)
                                .setPositiveButton(getString(R.string.skills_ui_revision)) { _, _ ->
                                    runCatching { library.reviseSkill(skill.id, input.text.toString()) }
                                        .onSuccess { applyChange(it, "Zapisano nową wersję " + skill.name + ".") }
                                        .onFailure { showError(it) }
                                }.show()
                        }
                    })
                    val versions = skill.revisions.sortedByDescending { it.version }
                    val revisionPicker = Spinner(this).apply {
                        UiSelect.setOptions(this, versions.map { "Wersja " + it.version })
                        setSelection(versions.indexOfFirst { it.version == activeVersion }.coerceAtLeast(0))
                    }
                    skillCard.addView(revisionPicker)
                    skillCard.addView(Button(this).apply {
                        UiDesign.styleAction(this, UiActionTone.SECONDARY)
                        text = "Wybierz / przywróć wersję " + skill.name
                        setOnClickListener {
                            applyChange(library.bindVersion(skill.id, versions[revisionPicker.selectedItemPosition].version),
                                "Wybrano zachowaną wersję.")
                        }
                    })
                }
                content.addView(skillCard, UiDesign.spaceAfter(this, 10))
            }
        }
        content.addView(UiDesign.caption(this,
            "Ważne: skillsy nie nadają prawa do narzędzi ani rozłączania. " +
            "Decyzje modelu nadal sprawdza aplikacja."))
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
