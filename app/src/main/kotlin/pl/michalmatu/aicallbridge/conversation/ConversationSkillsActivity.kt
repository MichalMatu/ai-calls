package pl.michalmatu.aicallbridge.conversation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*

/** Edits prompt-only skills and revision-pinned presets. Never starts a call or changes a grant. */
class ConversationSkillsActivity : Activity() {
    private lateinit var store: AndroidConversationPreparationStore
    private lateinit var library: ConversationSkillLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        try {
            store = AndroidConversationPreparationStore(this)
            library = store.loadSkillLibrary()
            show()
        } catch (error: Exception) {
            setContentView(TextView(this).apply { text = "Nie można odczytać skillsów: " + error.javaClass.simpleName })
        }
    }

    private fun label(text: String, size: Float = 16f) =
        TextView(this).apply { this.text = text; textSize = size; setPadding(0, 10, 0, 8) }

    private fun applyChange(newLibrary: ConversationSkillLibrary, message: String) {
        try {
            store.saveSkillLibrary(newLibrary)
            library = newLibrary
            show(message)
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("Nie zapisano")
                .setMessage("Nie udało się zapisać zmian: " + error.javaClass.simpleName)
                .setPositiveButton("OK", null).show()
        }
    }

    private fun show(message: String = "") {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 36)
        }
        content.addView(label("Skills i presety Gemmy", 23f))
        content.addView(label(
            "Instrukcje można edytować bez ponownej kompilacji aplikacji. " +
                "Presety fabryczne są tylko do odczytu. Zmiany obowiązują od następnej rozmowy."
        ))
        content.addView(label("Aktywny preset: " + library.active.name + " · " +
            if (library.active.isFactory) "fabryczny" else "własny", 18f))
        val options = library.presets
        val picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationSkillsActivity,
                android.R.layout.simple_spinner_dropdown_item, options.map {
                    it.name + if (it.isFactory) " (fabryczny)" else " (własny)"
                })
            setSelection(options.indexOfFirst { it.id == library.activePresetId })
        }
        content.addView(picker)
        content.addView(Button(this).apply {
            text = "Aktywuj wybrany preset"
            setOnClickListener {
                applyChange(library.select(options[picker.selectedItemPosition].id),
                    "Zmieniono preset dla następnych rozmów.")
            }
        })
        content.addView(Button(this).apply {
            text = "Utwórz własną kopię aktywnego presetu"
            setOnClickListener {
                val name = EditText(this@ConversationSkillsActivity).apply {
                    setSingleLine(true)
                    setText(library.active.name + " — własny")
                }
                AlertDialog.Builder(this@ConversationSkillsActivity)
                    .setTitle("Nowy preset")
                    .setView(name)
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Utwórz") { _, _ ->
                        runCatching { library.copyActive(name.text.toString()) }
                            .onSuccess { applyChange(it, "Utworzono własny preset.") }
                            .onFailure { showError(it) }
                    }.show()
            }
        })
        content.addView(Button(this).apply {
            text = "Laboratorium skillsów — testy Gemmy offline"
            setOnClickListener {
                startActivity(Intent(this@ConversationSkillsActivity, ConversationSkillLabActivity::class.java))
            }
        })
        content.addView(Button(this).apply {
            text = "Podgląd instrukcji dla sekretarki"
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
        if (library.active.isFactory) content.addView(label(
            "Aby zmienić instrukcję lub listę skillsów, najpierw utwórz własną kopię presetu."
        ))
        for (section in SkillSection.entries) {
            content.addView(label(section.title, 19f))
            for (skill in library.skills.filter { it.section == section }) {
                val activeVersion = library.active.versions[skill.id]
                val on = activeVersion != null
                val choice = CheckBox(this).apply {
                    text = skill.name + " · " + skill.id +
                        (if (on) " · v" + activeVersion else " · wyłączony")
                    isChecked = on
                    isEnabled = !library.active.isFactory
                    setOnClickListener {
                        applyChange(library.bindVersion(skill.id,
                            if (isChecked) skill.revisions.maxOf { it.version } else null),
                            "Zmieniono aktywne skillsy.")
                    }
                }
                content.addView(choice)
                content.addView(label(skill.at(activeVersion ?: skill.revisions.maxOf { it.version }).instructions))
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
                                .setNegativeButton("Anuluj", null)
                                .setPositiveButton("Zapisz wersję") { _, _ ->
                                    runCatching { library.reviseSkill(skill.id, input.text.toString()) }
                                        .onSuccess { applyChange(it, "Zapisano nową wersję " + skill.name + ".") }
                                        .onFailure { showError(it) }
                                }.show()
                        }
                    })
                    val versions = skill.revisions.sortedByDescending { it.version }
                    val revisionPicker = Spinner(this).apply {
                        adapter = ArrayAdapter(this@ConversationSkillsActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            versions.map { "Wersja " + it.version })
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
        AlertDialog.Builder(this).setTitle("Nie zapisano")
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
