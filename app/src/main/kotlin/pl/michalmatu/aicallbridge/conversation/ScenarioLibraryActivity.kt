package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDefinition
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Safe, editable preview catalog. Never dials, accepts a grant or commits a booking. */
class ScenarioLibraryActivity : UiChromeActivity(AppTab.HOME) {
    private lateinit var store: AndroidConversationPreparationStore
    private var selectedId: String? = null
    private var restoredInputs: Bundle? = null
    private val inputs = linkedMapOf<String, EditText>()
    private val language: String get() = resources.configuration.locales[0].language

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = try { AndroidConversationPreparationStore(this) } catch (error: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.scenario_ui_read_failed)
            })
            return
        }
        selectedId = savedInstanceState?.getString("scenario_id")
        restoredInputs = savedInstanceState
        val definition = selectedId?.let(ScenarioCatalog::byId)
        if (definition == null) showCatalog() else showEditor(definition)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectedId?.let { outState.putString("scenario_id", it) }
        inputs.forEach { (id, edit) ->
            outState.putString("scenario_input_$id", edit.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    private fun body(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = UiDesign.dp(this@ScenarioLibraryActivity, UiDesign.CONTENT_INSET)
        setPadding(pad, pad, pad, pad)
    }

    private fun install(body: LinearLayout) {
        setContentView(ScrollView(this).apply {
            addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun showCatalog() {
        selectedId = null
        restoredInputs = null
        inputs.clear()
        val root = body()
        root.addView(UiDesign.heading(this, getString(R.string.scenario_ui_title)),
            UiDesign.spaceAfter(this, 12))
        root.addView(UiDesign.caption(this, getString(R.string.scenario_ui_intro)),
            UiDesign.spaceAfter(this, 16))
        ScenarioCatalog.templates.forEach { definition ->
            val card = UiDesign.card(this).apply { tag = "scenario-preset:${definition.id}" }
            card.addView(UiDesign.heading(this, definition.title(language)).apply { textSize = 17f },
                UiDesign.spaceAfter(this, 6))
            card.addView(UiDesign.caption(this, definition.goal(language)),
                UiDesign.spaceAfter(this, 10))
            card.addView(UiDesign.action(this, getString(R.string.scenario_ui_edit)) {
                selectedId = definition.id
                showEditor(definition)
            })
            root.addView(card, UiDesign.spaceAfter(this, 12))
        }
        install(root)
    }

    private fun showEditor(definition: ScenarioDefinition) {
        selectedId = definition.id
        inputs.clear()
        var readFailed = false
        val stored = runCatching { store.loadScenarioDraft(definition.id) }.getOrElse {
            readFailed = true
            null
        } ?: definition.draft()
        val root = body()
        root.addView(UiDesign.heading(this, definition.title(language)),
            UiDesign.spaceAfter(this, 10))
        root.addView(UiDesign.caption(this, definition.goal(language)),
            UiDesign.spaceAfter(this, 10))
        root.addView(UiDesign.caption(this, getString(if (definition.callAdapterId == null)
            R.string.scenario_ui_draft_only else R.string.scenario_ui_clir_note)),
            UiDesign.spaceAfter(this, 16))
        val form = UiDesign.card(this).apply { tag = "scenario-form" }
        definition.fields.forEach { field ->
            form.addView(UiDesign.caption(this, field.title(language) +
                if (field.required) " • " + getString(R.string.scenario_ui_required) else ""),
                UiDesign.spaceAfter(this, 6))
            val value = restoredInputs?.getString("scenario_input_${field.id}")
                ?: stored.values[field.id].orEmpty()
            val input = EditText(this).apply {
                tag = "scenario-input:${field.id}"
                setSingleLine(field.id !in setOf("additional_instructions", "issue"))
                hint = field.placeholder(language)
                setText(value)
                textSize = 15f
                background = UiDesign.surfaceDrawable(this@ScenarioLibraryActivity, UiDesign.softAccent)
                val pad = UiDesign.dp(this@ScenarioLibraryActivity, 12)
                setPadding(pad, pad, pad, pad)
                minimumHeight = UiDesign.dp(this@ScenarioLibraryActivity, 48)
            }
            inputs[field.id] = input
            form.addView(input, UiDesign.spaceAfter(this, 14))
        }
        root.addView(form, UiDesign.spaceAfter(this, 16))
        val feedback = UiDesign.caption(this, if (readFailed)
            getString(R.string.scenario_ui_read_failed) else "").apply {
            tag = "scenario-form-status"
        }
        fun draft(): ScenarioDraft = ScenarioDraft(definition.id,
            inputs.mapValues { (_, field) -> field.text.toString() })
        root.addView(UiDesign.action(this, getString(R.string.scenario_ui_save), UiActionTone.PRIMARY) {
            val next = draft()
            val missing = next.missingRequiredFields(definition)
            if (missing.isNotEmpty()) {
                feedback.text = getString(R.string.scenario_ui_missing, missing.joinToString {
                    id -> definition.fields.first { it.id == id }.title(language)
                })
            } else {
                feedback.text = if (runCatching { store.saveScenarioDraft(next) }.isSuccess)
                    getString(R.string.scenario_ui_saved) else getString(R.string.scenario_ui_save_failed)
            }
        }, UiDesign.spaceAfter(this, 12))
        root.addView(UiDesign.action(this, getString(R.string.scenario_ui_preview)) {
            val next = draft()
            val missing = next.missingRequiredFields(definition)
            val warning = if (missing.isEmpty()) "" else getString(R.string.scenario_ui_missing,
                missing.joinToString { id -> definition.fields.first { it.id == id }.title(language) }) + "\n\n"
            AlertDialog.Builder(this)
                .setTitle(definition.title(language))
                .setMessage(warning + next.summary(definition, language) + "\n\n" +
                    getString(R.string.scenario_ui_preview_note))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }, UiDesign.spaceAfter(this, 12))
        root.addView(UiDesign.action(this, getString(R.string.scenario_ui_back)) {
            showCatalog()
        }, UiDesign.spaceAfter(this, 12))
        root.addView(feedback)
        install(root)
        restoredInputs = null
    }
}
