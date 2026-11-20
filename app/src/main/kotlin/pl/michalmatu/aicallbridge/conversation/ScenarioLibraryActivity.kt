package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.content.Intent
import pl.michalmatu.aicallbridge.campaign.ScenarioOutgoingCallActivity
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.UUID
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.PersonalScenario
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDefinition
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/** Local preparation only. Drafts do not create grants, dial, or confirm outside effects. */
class ScenarioLibraryActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.scenario_ui_title)
    private lateinit var store: AndroidConversationPreparationStore
    private var selectedId: String? = null
    private var selectedPersonalId: String? = null
    private var creatingNewScenario = false
    private var templatesExpanded = false
    private var restoredInputs: Bundle? = null
    private var personalTitle: EditText? = null
    private var personalGoal: EditText? = null
    private var savedValues: Map<String, String> = emptyMap()
    private var savedTitle: String? = null
    private var savedGoal: String? = null
    private val inputs = linkedMapOf<String, EditText>()
    private val language: String get() = resources.configuration.locales[0].language

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = try { AndroidConversationPreparationStore(this) } catch (error: Exception) {
            setContentView(TextView(this).apply { text = getString(R.string.scenario_ui_read_failed) })
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedId != null) leaveEditor() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        selectedId = savedInstanceState?.getString("scenario_id")
        selectedPersonalId = savedInstanceState?.getString("personal_id")
        creatingNewScenario = savedInstanceState?.getBoolean("creating_new_scenario") ?: false
        templatesExpanded = savedInstanceState?.getBoolean("templates_expanded") ?: false
        restoredInputs = savedInstanceState
        val definition = selectedId?.let(ScenarioCatalog::byId)
        val personal = selectedPersonalId?.let { id ->
            runCatching { store.loadPersonalScenarios().items.firstOrNull { it.id == id } }.getOrNull()
        }
        if (definition == null) showCatalog() else if (selectedPersonalId != null && personal == null)
            showCatalog(getString(R.string.scenario_ui_read_failed))
        else showEditor(definition, personal, creating = creatingNewScenario)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectedId?.let { outState.putString("scenario_id", it) }
        selectedPersonalId?.let { outState.putString("personal_id", it) }
        outState.putBoolean("creating_new_scenario", creatingNewScenario)
        outState.putBoolean("templates_expanded", templatesExpanded)
        personalTitle?.let { outState.putString("personal_title", it.text.toString()) }
        personalGoal?.let { outState.putString("personal_goal", it.text.toString()) }
        inputs.forEach { (id, edit) ->
            outState.putString("scenario_input_$id", edit.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun consumeChromeBack(): Boolean {
        if (selectedId == null) return false
        leaveEditor()
        return true
    }

    private fun leaveEditor() {
        val changed = inputs.any { (id, edit) ->
            edit.text.toString() != savedValues[id].orEmpty()
        } || (personalTitle?.text?.toString()?.let { it != savedTitle } == true) ||
            (personalGoal?.text?.toString()?.let { it != savedGoal } == true)
        if (!changed) {
            showCatalog()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.scenario_ui_unsaved_title)
            .setMessage(R.string.scenario_ui_unsaved_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.scenario_ui_discard) { _, _ -> showCatalog() }
            .show()
    }

    private fun body(): LinearLayout = UiDesign.content(this)

    private fun install(body: LinearLayout) {
        setContentView(ScrollView(this).apply {
            addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun showCatalog(message: String = "") {
        selectedId = null
        selectedPersonalId = null
        creatingNewScenario = false
        restoredInputs = null
        personalTitle = null
        personalGoal = null
        inputs.clear()
        savedValues = emptyMap()
        savedTitle = null
        savedGoal = null
        val root = body()
        if (message.isNotBlank()) root.addView(UiDesign.caption(this, message),
            UiDesign.spaceAfter(this, 12))
        val mine = runCatching { store.loadPersonalScenarios().items }.getOrElse {
            root.addView(UiDesign.caption(this, getString(R.string.scenario_ui_read_failed)),
                UiDesign.spaceAfter(this, 12))
            emptyList()
        }
        root.addView(UiDesign.heading(this, getString(R.string.scenario_ui_mine)),
            UiDesign.spaceAfter(this, 10))
        root.addView(UiDesign.action(this, getString(R.string.scenario_ui_new), UiActionTone.PRIMARY) {
            showEditor(ScenarioCatalog.custom, creating = true)
        }.apply { tag = "scenario-new" }, UiDesign.spaceAfter(this, 12))
        if (mine.isEmpty()) {
            root.addView(UiDesign.caption(this, getString(R.string.scenario_ui_none)),
                UiDesign.spaceAfter(this, 12))
        } else {
            mine.forEach { personal ->
                val template = ScenarioCatalog.byId(personal.templateId) ?: return@forEach
                val card = UiDesign.card(this).apply {
                    tag = "scenario-personal:" + personal.id
                    isClickable = true
                    isFocusable = true
                    contentDescription = getString(R.string.scenario_ui_open_item, personal.current.title)
                    background = UiDesign.touchSurface(this@ScenarioLibraryActivity)
                    setOnClickListener { showEditor(template, personal) }
                }
                val header = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                header.addView(UiDesign.heading(this, personal.current.title).apply {
                    textSize = 17f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(TextView(this).apply {
                    text = "›"
                    textSize = 24f
                    setTextColor(UiDesign.secondary)
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                card.addView(header, UiDesign.spaceAfter(this, 5))
                card.addView(UiDesign.caption(this,
                    getString(R.string.scenario_ui_based_on, template.title(language),
                        personal.current.number)))
                root.addView(card, UiDesign.spaceAfter(this, 12))
            }
        }
        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "scenario-presets-container"
            visibility = if (templatesExpanded) View.VISIBLE else View.GONE
        }
        val presetToggle = UiDesign.action(this, getString(
            if (templatesExpanded) R.string.scenario_ui_hide_templates
            else R.string.scenario_ui_show_templates, ScenarioCatalog.templates.size)) {
            templatesExpanded = !templatesExpanded
            presets.visibility = if (templatesExpanded) View.VISIBLE else View.GONE
            (window.decorView.findViewWithTag<View>("scenario-presets-toggle") as? TextView)
                ?.text = getString(if (templatesExpanded) R.string.scenario_ui_hide_templates
                    else R.string.scenario_ui_show_templates, ScenarioCatalog.templates.size)
        }.apply { tag = "scenario-presets-toggle" }
        root.addView(presetToggle, UiDesign.spaceAfter(this, 12))
        ScenarioCatalog.templates.forEach { definition ->
            val card = UiDesign.card(this).apply {
                tag = "scenario-preset:" + definition.id
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.scenario_ui_open_item, definition.title(language))
                background = UiDesign.touchSurface(this@ScenarioLibraryActivity)
                setOnClickListener { showEditor(definition) }
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            header.addView(UiDesign.heading(this, definition.title(language)).apply {
                textSize = 17f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(TextView(this).apply {
                text = "›"
                textSize = 24f
                setTextColor(UiDesign.secondary)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            card.addView(header, UiDesign.spaceAfter(this, 5))
            card.addView(UiDesign.caption(this, definition.goal(language)))
            presets.addView(card, UiDesign.spaceAfter(this, 12))
        }
        root.addView(presets, UiDesign.spaceAfter(this, 8))
        install(root)
    }

    private fun showEditor(
        definition: ScenarioDefinition,
        personal: PersonalScenario? = null,
        creating: Boolean = false,
    ) {
        require(!creating || (personal == null && definition.id == ScenarioCatalog.custom.id))
        selectedId = definition.id
        selectedPersonalId = personal?.id
        creatingNewScenario = creating
        inputs.clear()
        personalTitle = null
        personalGoal = null
        var readFailed = false
        val stored = if (personal != null) ScenarioDraft(definition.id, personal.current.values)
        else if (creating) definition.draft()
        else runCatching { store.loadScenarioDraft(definition.id) }.getOrElse {
            readFailed = true
            null
        } ?: definition.draft()
        savedValues = stored.values.toMap()
        savedTitle = if (creating) "" else personal?.current?.title
        savedGoal = if (creating) "" else personal?.current?.goal
        val root = body()
        root.addView(UiDesign.heading(this, if (creating) getString(R.string.scenario_ui_new_title)
            else personal?.current?.title ?: definition.title(language)),
            UiDesign.spaceAfter(this, 10))
        if (definition.callAdapterId != null) {
            root.addView(UiDesign.caption(this, getString(R.string.scenario_ui_clir_note)),
                UiDesign.spaceAfter(this, 14))
        }

        if (personal != null || creating) {
            val headline = UiDesign.card(this).apply { tag = "scenario-personal-details" }
            headline.addView(UiDesign.caption(this, getString(R.string.scenario_ui_name)),
                UiDesign.spaceAfter(this, 5))
            personalTitle = EditText(this).apply {
                tag = "scenario-personal-name"
                setSingleLine(true)
                setText(restoredInputs?.getString("personal_title") ?:
                    if (creating) "" else personal?.current?.title.orEmpty())
                hint = getString(R.string.scenario_ui_name)
                background = UiDesign.surfaceDrawable(this@ScenarioLibraryActivity, UiDesign.softAccent)
                setPadding(UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12))
            }
            headline.addView(personalTitle, UiDesign.spaceAfter(this, 12))
            headline.addView(UiDesign.caption(this, getString(R.string.scenario_ui_goal)),
                UiDesign.spaceAfter(this, 5))
            personalGoal = EditText(this).apply {
                tag = "scenario-personal-goal"
                minLines = 2
                maxLines = 6
                setText(restoredInputs?.getString("personal_goal") ?:
                    if (creating) "" else personal?.current?.goal.orEmpty())
                hint = getString(R.string.scenario_ui_goal)
                background = UiDesign.surfaceDrawable(this@ScenarioLibraryActivity, UiDesign.softAccent)
                setPadding(UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12))
            }
            headline.addView(personalGoal)
            root.addView(headline, UiDesign.spaceAfter(this, 14))
        } else {
            root.addView(UiDesign.caption(this, definition.goal(language)),
                UiDesign.spaceAfter(this, 12))
        }

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
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        fun draft(): ScenarioDraft = ScenarioDraft(definition.id,
            inputs.mapValues { (_, field) -> field.text.toString() })
        fun missingMessage(next: ScenarioDraft): String {
            val missing = next.missingRequiredFields(definition)
            if (missing.isEmpty()) return getString(R.string.scenario_ui_saved)
            return getString(R.string.scenario_ui_saved_incomplete, missing.joinToString {
                id -> definition.fields.first { it.id == id }.title(language)
            })
        }
        // This only opens a private confirmation form; it never dials.
        val prepareButton = UiDesign.action(this, getString(R.string.scenario_ui_prepare_call), UiActionTone.PRIMARY) {
            val next = draft()
            val missing = next.missingRequiredFields(definition)
            if (missing.isNotEmpty()) {
                feedback.text = getString(R.string.scenario_ui_missing,
                    missing.joinToString { id ->
                        definition.fields.first { it.id == id }.title(language)
                    })
            } else {
                val title = (personalTitle?.text?.toString() ?: definition.title(language)).trim()
                val goal = (personalGoal?.text?.toString() ?: definition.goal(language)).trim()
                val summary = if (personalTitle == null) next.summary(definition, language)
                    else (listOf(goal) + definition.fields.mapNotNull { field ->
                        next.values[field.id]?.trim()?.takeIf(String::isNotBlank)
                            ?.let { field.title(language) + ": " + it }
                    }).joinToString("\n")
                if (title.length !in 2..140 || goal.length !in 5..300 ||
                    summary.length !in 5..3500
                ) {
                    feedback.text = getString(R.string.scenario_call_invalid)
                } else {
                    startActivity(Intent(this, ScenarioOutgoingCallActivity::class.java)
                        .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_ID, definition.id)
                        .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_TITLE, title)
                        .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_GOAL, goal)
                        .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_BRIEF, summary))
                }
            }
        }.apply { tag = "scenario-prepare-outgoing" }

        if (!creating) root.addView(prepareButton, UiDesign.spaceAfter(this, 12))
        val saveButton = UiDesign.action(this, getString(
            if (creating) R.string.scenario_ui_create
            else if (personal == null) R.string.scenario_ui_save
            else R.string.scenario_ui_save_version),
            if (creating) UiActionTone.PRIMARY else UiActionTone.SECONDARY) {
            val next = draft()
            if (creating) {
                val title = personalTitle?.text?.toString()?.trim().orEmpty()
                val goal = personalGoal?.text?.toString()?.trim().orEmpty()
                if (title.length !in 2..140 || goal.length !in 5..300) {
                    feedback.text = getString(R.string.scenario_ui_new_invalid)
                } else {
                    val created = runCatching {
                        val id = UUID.randomUUID().toString()
                        val updated = store.loadPersonalScenarios().create(
                            definition, next, id, title, goal)
                        store.savePersonalScenarios(updated)
                        updated.items.single { it.id == id }
                    }
                    created.onSuccess { showEditor(definition, it) }
                        .onFailure { feedback.text = getString(R.string.scenario_ui_save_failed) }
                }
            } else if (personal == null) {
                feedback.text = if (runCatching { store.saveScenarioDraft(next) }.isSuccess) {
                    savedValues = next.values.toMap()
                    missingMessage(next)
                } else getString(R.string.scenario_ui_save_failed)
            } else {
                val title = personalTitle?.text?.toString().orEmpty()
                val goal = personalGoal?.text?.toString().orEmpty()
                val saved = runCatching {
                    val library = store.loadPersonalScenarios()
                    val updated = library.revise(personal.id, title, goal, next)
                    store.savePersonalScenarios(updated)
                    updated.items.single { it.id == personal.id }
                }
                saved.onSuccess { savedPersonal ->
                    showEditor(definition, savedPersonal)
                    window.decorView.findViewWithTag<TextView>("scenario-form-status")
                        ?.text = missingMessage(next)
                }.onFailure {
                    feedback.text = getString(R.string.scenario_ui_save_failed)
                }
            }
        }.apply { tag = "scenario-save" }

        val advanced = LinearLayout(this).apply {
            tag = "scenario-advanced-actions"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        advanced.addView(UiDesign.action(this, getString(R.string.scenario_ui_duplicate)) {
            val next = draft()
            val titleInput = EditText(this).apply {
                tag = "scenario-copy-name"
                setSingleLine(true)
                setText(personalTitle?.text?.toString() ?: definition.title(language))
                setPadding(UiDesign.dp(this@ScenarioLibraryActivity, 16),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12),
                    UiDesign.dp(this@ScenarioLibraryActivity, 16),
                    UiDesign.dp(this@ScenarioLibraryActivity, 12))
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.scenario_ui_duplicate)
                .setView(titleInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.scenario_ui_create) { _, _ ->
                    val title = titleInput.text.toString()
                    val goal = personalGoal?.text?.toString() ?: definition.goal(language)
                    val created = runCatching {
                        val id = UUID.randomUUID().toString()
                        val updated = store.loadPersonalScenarios().create(
                            definition, next, id, title, goal)
                        store.savePersonalScenarios(updated)
                        updated.items.single { it.id == id }
                    }
                    created.onSuccess { newPersonal -> showEditor(definition, newPersonal) }
                        .onFailure { feedback.text = getString(R.string.scenario_ui_save_failed) }
                }.show()
        }, UiDesign.spaceAfter(this, 12))

        advanced.addView(UiDesign.action(this, getString(R.string.scenario_ui_preview)) {
            val next = draft()
            val missing = next.missingRequiredFields(definition)
            val warning = if (missing.isEmpty()) "" else getString(R.string.scenario_ui_missing,
                missing.joinToString { id -> definition.fields.first { it.id == id }.title(language) }) + "\n\n"
            val goal = personalGoal?.text?.toString() ?: definition.goal(language)
            val fields = definition.fields.mapNotNull { field ->
                next.values[field.id]?.trim()?.takeIf(String::isNotBlank)
                    ?.let { field.title(language) to it }
            }
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = UiDesign.dp(this@ScenarioLibraryActivity, 20)
                setPadding(pad, pad, pad, pad)
            }
            fun fieldRow(label: String, value: String) {
                panel.addView(UiDesign.caption(this, label), UiDesign.spaceAfter(this, 5))
                panel.addView(TextView(this).apply {
                    text = value
                    textSize = 16f
                    setTextColor(UiDesign.ink)
                    setTextIsSelectable(true)
                }, UiDesign.spaceAfter(this, 14))
            }
            if (warning.isNotBlank()) panel.addView(UiDesign.caption(this, warning.trim()).apply {
                setTextColor(UiDesign.danger)
            }, UiDesign.spaceAfter(this, 14))
            fieldRow(getString(R.string.scenario_ui_goal), goal)
            fields.forEach { (name, value) -> fieldRow(name, value) }
            panel.addView(UiDesign.caption(this, getString(R.string.scenario_ui_preview_note)))
            AlertDialog.Builder(this)
                .setTitle(personalTitle?.text?.toString() ?: definition.title(language))
                .setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }, UiDesign.spaceAfter(this, 12))

        if (personal != null) {
            val versions = personal.versions
            val revisions = Spinner(this).apply {
                tag = "scenario-version-picker"
                UiSelect.setOptions(this, versions.map {
                    getString(R.string.scenario_ui_version, it.number)
                })
                setSelection(versions.lastIndex)
            }
            advanced.addView(revisions, UiDesign.spaceAfter(this, 8))
            advanced.addView(UiDesign.action(this, getString(R.string.scenario_ui_restore)) {
                val number = versions[revisions.selectedItemPosition].number
                val restored = runCatching {
                    val updated = store.loadPersonalScenarios().restore(personal.id, number)
                    store.savePersonalScenarios(updated)
                    updated.items.single { it.id == personal.id }
                }
                restored.onSuccess { showEditor(definition, it) }
                    .onFailure { feedback.text = getString(R.string.scenario_ui_save_failed) }
            }, UiDesign.spaceAfter(this, 12))
            advanced.addView(UiDesign.action(this, getString(R.string.scenario_ui_delete),
                UiActionTone.REVOCATION) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.scenario_ui_delete)
                    .setMessage(R.string.scenario_ui_delete_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.scenario_ui_delete) { _, _ ->
                        val deleted = runCatching {
                            store.savePersonalScenarios(store.loadPersonalScenarios().delete(personal.id))
                        }
                        if (deleted.isSuccess) showCatalog()
                        else feedback.text = getString(R.string.scenario_ui_save_failed)
                    }.show()
            }, UiDesign.spaceAfter(this, 12))
        }
        root.addView(saveButton, UiDesign.spaceAfter(this, 12))
        if (!creating) {
            root.addView(UiDesign.action(this, getString(R.string.scenario_ui_more)) {
                advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }.apply { tag = "scenario-more" }, UiDesign.spaceAfter(this, 10))
            root.addView(advanced, UiDesign.spaceAfter(this, 12))
        }
        root.addView(feedback)
        install(root)
        restoredInputs = null
    }
}
