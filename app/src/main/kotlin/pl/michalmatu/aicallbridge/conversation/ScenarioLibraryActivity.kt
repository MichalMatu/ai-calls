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
import android.widget.PopupMenu
import android.widget.TextView
import java.util.UUID
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.PersonalScenario
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDefinition
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft
import pl.michalmatu.aicallbridge.presentation.scenarios.ScenarioCallPreparationPolicy
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

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
                card.addView(UiDesign.caption(this, template.title(language)))
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

    /** Delete is deliberately a single menu action, never part of the editor form. */
    private fun confirmDeletePersonal(personal: PersonalScenario) {
        AlertDialog.Builder(this)
            .setTitle(R.string.scenario_ui_delete)
            .setMessage(R.string.scenario_ui_delete_simple_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.scenario_ui_delete) { _, _ ->
                val result = runCatching {
                    store.savePersonalScenarios(store.loadPersonalScenarios().delete(personal.id))
                }
                showCatalog(if (result.isFailure) getString(R.string.scenario_ui_save_failed) else "")
            }.show()
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
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        titleRow.addView(UiDesign.heading(this, if (creating)
            getString(R.string.scenario_ui_new_title)
            else personal?.current?.title ?: definition.title(language)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (personal != null) {
            val options = TextView(this).apply {
                tag = "scenario-delete-options"
                text = "⋮"
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.scenario_ui_more)
                background = UiDesign.touchSurface(this@ScenarioLibraryActivity, UiDesign.softAccent, 12)
                setOnClickListener {
                    PopupMenu(this@ScenarioLibraryActivity, this).apply {
                        menu.add(getString(R.string.scenario_ui_delete))
                        setOnMenuItemClickListener {
                            confirmDeletePersonal(personal)
                            true
                        }
                        show()
                    }
                }
            }
            titleRow.addView(options, LinearLayout.LayoutParams(
                UiDesign.dp(this, 48), UiDesign.dp(this, 48)))
        }
        root.addView(titleRow, UiDesign.spaceAfter(this, 10))
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
        // Only the existing wizard authorizes numbers, SIM and a call.
        val prepare = if (!creating) UiDesign.action(this,
                getString(R.string.scenario_ui_prepare_call),
                if (personal == null) UiActionTone.PRIMARY else UiActionTone.SECONDARY) {
                val result = ScenarioCallPreparationPolicy.prepare(
                    definition = definition,
                    draft = draft(),
                    language = language,
                    title = personalTitle?.text?.toString() ?: definition.title(language),
                    goal = personalGoal?.text?.toString() ?: definition.goal(language),
                )
                when (result) {
                    is ScenarioCallPreparationPolicy.Result.Missing -> {
                        feedback.text = getString(R.string.scenario_ui_missing,
                            result.fieldIds.joinToString { id ->
                                definition.fields.first { it.id == id }.title(language)
                            })
                    }
                    ScenarioCallPreparationPolicy.Result.Invalid ->
                        feedback.text = getString(R.string.scenario_call_invalid)
                    is ScenarioCallPreparationPolicy.Result.Ready ->
                        startActivity(Intent(this, ScenarioOutgoingCallActivity::class.java)
                            .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_ID, definition.id)
                            .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_TITLE, result.title)
                            .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_GOAL, result.goal)
                            .putExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_BRIEF, result.brief))
                }
            }.apply { tag = "scenario-prepare-outgoing" } else null
        // A factory template leads with Prepare, while personal edits lead with Save.
        if (personal == null && prepare != null) {
            root.addView(prepare, UiDesign.spaceAfter(this, 12))
        }
        var saving = false
        val save = UiDesign.action(this, getString(when {
            creating -> R.string.scenario_ui_create
            personal != null -> R.string.scenario_ui_save_changes
            else -> R.string.scenario_ui_save_personal
        }), if (creating || personal != null) UiActionTone.PRIMARY
            else UiActionTone.SECONDARY) {
            if (!saving) {
                saving = true
                val next = draft()
                if (personal != null) {
                    val name = personalTitle?.text?.toString()?.trim().orEmpty()
                    val goal = personalGoal?.text?.toString()?.trim().orEmpty()
                    if (name.length !in 2..140 || goal.length !in 5..300) {
                        feedback.text = getString(R.string.scenario_ui_new_invalid)
                        saving = false
                    } else {
                        val changed = runCatching {
                            val library = store.loadPersonalScenarios()
                            val updated = library.update(personal.id, name, goal, next)
                            if (updated != library) store.savePersonalScenarios(updated)
                            updated.items.single { it.id == personal.id }
                        }
                        changed.onSuccess {
                            showEditor(definition, it)
                            window.decorView.findViewWithTag<TextView>("scenario-form-status")
                                ?.text = missingMessage(next)
                        }.onFailure {
                            feedback.text = getString(R.string.scenario_ui_save_failed)
                            saving = false
                        }
                    }
                } else {
                    val name = if (creating)
                        personalTitle?.text?.toString()?.trim().orEmpty()
                        else definition.title(language)
                    val goal = if (creating)
                        personalGoal?.text?.toString()?.trim().orEmpty()
                        else definition.goal(language)
                    if (name.length !in 2..140 || goal.length !in 5..300) {
                        feedback.text = getString(R.string.scenario_ui_new_invalid)
                        saving = false
                    } else {
                        val result = runCatching {
                            val library = store.loadPersonalScenarios()
                            // Reopening an identical saved scenario is not a second Save.
                            val existing = library.items.firstOrNull {
                                it.templateId == definition.id &&
                                it.current.title == name &&
                                it.current.goal == goal &&
                                it.current.values == next.values
                            }
                            existing ?: run {
                                val id = UUID.randomUUID().toString()
                                val updated = library.create(definition, next, id, name, goal)
                                store.savePersonalScenarios(updated)
                                updated.items.single { it.id == id }
                            }
                        }
                        result.onSuccess { showEditor(definition, it) }
                            .onFailure {
                                feedback.text = getString(R.string.scenario_ui_save_failed)
                                saving = false
                            }
                    }
                }
            }
        }.apply { tag = "scenario-save" }
        root.addView(save, UiDesign.spaceAfter(this, 12))
        if (personal != null && prepare != null) {
            root.addView(prepare, UiDesign.spaceAfter(this, 12))
        }
        root.addView(feedback)
        install(root)
        restoredInputs = null
    }
}
