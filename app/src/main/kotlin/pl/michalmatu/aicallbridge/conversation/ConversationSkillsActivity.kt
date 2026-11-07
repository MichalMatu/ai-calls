package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
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
        TextView(this).apply { this.text = text; textSize = size; setPadding(0, UiDesign.dp(this@ConversationSkillsActivity, 10), 0, UiDesign.dp(this@ConversationSkillsActivity, 8)) }

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
            setPadding(UiDesign.dp(this@ConversationSkillsActivity, 28), UiDesign.dp(this@ConversationSkillsActivity, 28), UiDesign.dp(this@ConversationSkillsActivity, 28), UiDesign.dp(this@ConversationSkillsActivity, 36))
        }
        content.addView(label(getString(R.string.skills_ui_title), 23f))
        content.addView(label(
            getString(R.string.skills_ui_intro) +
                getString(R.string.skills_ui_intro2)
        ))
        content.addView(label(getString(R.string.skills_ui_active) + library.active.name + " · " +
            if (library.active.isFactory) getString(R.string.skills_ui_factory) else getString(R.string.skills_ui_custom), 18f))
        val options = library.presets
        val picker = Spinner(this).apply {
            tag = "skills-preset-picker"
            UiSelect.setOptions(this, options.map {
                it.name + if (it.isFactory) " (fabryczny)" else " (własny)"
            })
            setSelection(options.indexOfFirst { it.id == library.activePresetId })
        }
        content.addView(picker)
        content.addView(Button(this).apply {
            text = getString(R.string.skills_ui_activate)
            setOnClickListener {
                applyChange(library.select(options[picker.selectedItemPosition].id),
                    "Zmieniono preset dla następnych rozmów.")
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.skills_ui_copy)
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
        })
        content.addView(Button(this).apply {
            text = getString(R.string.skills_ui_lab)
            setOnClickListener {
                startActivity(Intent(this@ConversationSkillsActivity, ConversationSkillLabActivity::class.java))
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.skills_ui_preview)
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
        if (message.isNotBlank()) content.addView(label(message))
        if (library.active.isFactory) content.addView(UiDesign.heading(this,
            getString(R.string.skills_ui_factory_readonly)).apply { textSize = 16f })
        for (section in SkillSection.entries) {
            content.addView(label(section.title, 19f))
            for (skill in library.skills.filter { it.section == section }) {
                val activeVersion = library.active.versions[skill.id]
                val on = activeVersion != null
                if (library.active.isFactory) {
                    content.addView(TextView(this).apply {
                        text = skill.name + " · " + getString(if (on)
                            R.string.skills_ui_enabled else R.string.skills_ui_disabled) +
                            (if (on) " · v" + activeVersion else "")
                        textSize = 16f
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
                    content.addView(choice)
                }
                content.addView(TextView(this).apply {
                    text = skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions
                    textSize = 14f
                    setTextColor(UiDesign.ink)
                    setPadding(UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 6),
                        UiDesign.dp(this@ConversationSkillsActivity, 8),
                        UiDesign.dp(this@ConversationSkillsActivity, 16))
                })
                if (!library.active.isFactory) {
                    content.addView(Button(this).apply {
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
                    content.addView(revisionPicker)
                    content.addView(Button(this).apply {
                        text = "Wybierz / przywróć wersję " + skill.name
                        setOnClickListener {
                            applyChange(library.bindVersion(skill.id, versions[revisionPicker.selectedItemPosition].version),
                                "Wybrano zachowaną wersję.")
                        }
                    })
                }
            }
        }
        content.addView(label("Ważne: skillsy nie nadają prawa do narzędzi, danych, efektów ani rozłączania. " +
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
