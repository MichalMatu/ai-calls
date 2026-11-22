package pl.michalmatu.aicallbridge.audioengines

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.Locale
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceProfileController
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceEnginePickerPresenter
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog
import pl.michalmatu.aicallbridge.textagent.LanguageModelTransferService
import pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore
import pl.michalmatu.aicallbridge.textagent.LiteRtModelContainer
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Current saved choices. Pickers save immediately; readiness remains a separate native check. */
internal class ModelVoiceSettings(
    private val activity: UiChromeActivity,
    private val canProbe: () -> Boolean,
    private val manageFiles: () -> Unit,
    private val changed: () -> Unit,
) : AutoCloseable {
    private val store = AudioEngineProfileStore(activity)
    private val profiles = ModelVoiceProfileController(store::load, store::save)
    val draft: AudioEngineProfile get() = profiles.draft
    private val probe = ModelCompatibilityProbe(activity)
    private var active = false
    private var checking = false
    private var lastAttempt: String? = null
    private var checkResult: String? = null
    private var voiceGeneration = 0
    private var tts: TextToSpeech? = null
    private var voicesReady = false
    private var localVoices = emptyList<Voice>()
    private val voiceWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "model-voice-inventory").apply { isDaemon = true }
    }
    private var engineIds = listOf<String?>(null)
    private var engineLabels = listOf(activity.getString(R.string.mv_system_engine))
    private var previewWhenReady = false
    private var pendingPreviewVoice: String? = null
    private var voiceDialog: AlertDialog? = null
    private var voiceOptions: LinearLayout? = null
    private val knownImports = UserLiteRtModelStore(activity).models().map { it.id }.toMutableSet()
    private val modelName = value("model-voice-model-choice")
    private val generationSummary = value("model-generation-summary")
    private val inputName = value("model-voice-input-choice")
    private val languageName = value("model-voice-language-choice")
    private val voiceName = value("model-voice-voice-choice")
    private val modelState = UiDesign.caption(activity, "").apply { tag = "model-voice-readiness" }
    private val inputIssue = UiDesign.caption(activity, "")
    private val voiceIssue = UiDesign.caption(activity, "")
    val modelView = card(R.string.models_ui_dialogue_heading, modelName, ::showModelPicker).apply { tag = "model-voice-model" }
    val generationView = card(R.string.model_generation_title, generationSummary, ::showGenerationSettings)
        .apply { tag = "model-generation-settings" }
    val inputView = card(R.string.mv_input, inputName, ::showInputPicker).apply { tag = "model-voice-inputs" }
    val languageView = card(R.string.mv_speech_language, languageName, ::showLanguagePicker)
        .apply {
            tag = "model-voice-language"
            addView(UiDesign.caption(activity, activity.getString(R.string.mv_speech_language_hint)))
        }
    val voiceView = card(R.string.mv_voice, voiceName, ::showVoicePicker).apply { tag = "model-voice-voice" }
    private val modelAction = UiDesign.action(activity, "") { modelAction() }
    private val retry = UiDesign.action(activity, activity.getString(R.string.mv_retry)) {
        verify(draft, draft.input == SpeechInputId.GEMMA_AUDIO_DIRECT &&
            AudioEngineRegistry.supportsAudio(draft), force = true)
        refresh()
    }.apply { tag = "model-voice-compatibility-check" }

    init {
        modelView.addView(modelState, UiDesign.spaceAfter(activity, 4))
        modelView.addView(modelAction)
        modelView.addView(retry)
        inputView.addView(inputIssue)
        voiceView.addView(voiceIssue)
        voiceView.addView(UiDesign.action(activity, activity.getString(R.string.eng_ui_preview)) { preview() }
            .apply { tag = "model-voice-preview" }, UiDesign.spaceAfter(activity, 0).apply {
                topMargin = UiDesign.dp(activity, 12)
            })
    }

    private fun value(tagName: String) = TextView(activity).apply {
        tag = tagName; textSize = 16f; setTextColor(UiDesign.ink)
    }

    private fun card(title: Int, value: TextView, open: () -> Unit): LinearLayout =
        UiDesign.card(activity).apply {
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(UiDesign.heading(activity, activity.getString(title)).apply { textSize = 14f },
                    UiDesign.spaceAfter(activity, 6))
                addView(value)
            }
            row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_aicalls_chevron_right)
                setColorFilter(UiDesign.secondary)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(UiDesign.dp(activity, 20), UiDesign.dp(activity, 20)))
            addView(row, UiDesign.spaceAfter(activity, 4))
            isClickable = true; isFocusable = true
            background = UiDesign.touchSurface(activity, radiusDp = 16)
            setOnClickListener { open() }
        }

    private fun save(profile: AudioEngineProfile): Boolean {
        when (val result = profiles.select(profile)) {
            ModelVoiceProfileController.SaveResult.Unchanged -> return true
            is ModelVoiceProfileController.SaveResult.Rejected -> {
                notifyError(result.reason)
                return false
            }
            ModelVoiceProfileController.SaveResult.WriteFailed -> {
                Toast.makeText(activity, R.string.eng_ui_save_error, Toast.LENGTH_SHORT).show()
                return false
            }
            is ModelVoiceProfileController.SaveResult.Saved -> {
                tts?.stop()
                probe.close(); checking = false; lastAttempt = null; checkResult = null
                if (result.engineChanged) {
                    previewWhenReady = false
                    pendingPreviewVoice = null
                    if (active) loadVoices()
                }
                refresh(); changed()
                return true
            }
        }
    }

    fun selectModel(id: DialogueModelId, custom: String? = null): Boolean =
        save(draft.copy(dialogue = id, customModelId = custom))
    fun selectInput(id: SpeechInputId): Boolean = save(draft.copy(input = id))

    private fun showGenerationSettings() {
        val fields = UiDesign.content(activity)
        val current = draft.generation
        fields.addView(UiDesign.caption(activity, activity.getString(R.string.model_generation_hint)),
            UiDesign.spaceAfter(activity, 12))
        fun field(label: Int, initial: Number?, range: String, decimal: Boolean = false): EditText {
            fields.addView(UiDesign.caption(activity, activity.getString(label)))
            return EditText(activity).apply {
                tag = activity.resources.getResourceEntryName(label)
                contentDescription = activity.getString(label)
                inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                setSingleLine(true)
                setText(initial?.toString().orEmpty())
                hint = range
                fields.addView(this, UiDesign.spaceAfter(activity, 8))
            }
        }
        fun range(lower: Number, upper: Number) = activity.getString(R.string.model_generation_range, lower.toString(), upper.toString())
        fun automatic(lower: Number, upper: Number) = activity.getString(R.string.model_generation_auto_range, lower.toString(), upper.toString())
        val context = field(R.string.model_generation_context, current.contextTokens, automatic(2048, 8192))
        val output = field(R.string.model_generation_output, current.outputTokens, automatic(256, 1024))
        val temperature = field(R.string.model_generation_temperature, current.temperature, range(0, 1.5), decimal = true)
        val topK = field(R.string.model_generation_top_k, current.topK, range(1, 64))
        val topP = field(R.string.model_generation_top_p, current.topP, range(0.05, 1), decimal = true)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.model_generation_title)
            .setView(ScrollView(activity).apply { addView(fields) })
            .setPositiveButton(R.string.prep_ui_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.model_settings_defaults, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (save(draft.copy(generation = DialogueGenerationSettings.DEFAULTS))) dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val next = DialogueGenerationInputParser.parse(
                    context.text.toString(), output.text.toString(), temperature.text.toString(),
                    topK.text.toString(), topP.text.toString())
                if (next == null) {
                    Toast.makeText(activity, R.string.model_generation_invalid, Toast.LENGTH_SHORT).show()
                } else if (save(draft.copy(generation = next))) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showLanguagePicker() {
        val values = arrayOf("pl-PL", "en-US")
        val labels = arrayOf(activity.getString(R.string.mv_speech_language_pl),
            activity.getString(R.string.mv_speech_language_en))
        val selected = values.indexOf(draft.languageTag)
        AlertDialog.Builder(activity).setTitle(R.string.mv_speech_language)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                val target = values[index]
                if (target == draft.languageTag) {
                    dialog.dismiss()
                } else {
                    // English Parakeet, Gemma Audio and Polish Piper are unqualified.
                    // Language selection resets only incompatible engine choices.
                    val next = ModelVoiceSelectionPolicy.forLanguage(draft, target)
                    if (save(next)) dialog.dismiss()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showModelPicker() {
        val library = UserLiteRtModelStore(activity)
        // Invalid imports remain visible in file management, never in the active picker.
        val imported = library.models().filter { model ->
            val file = library.file(model)
            library.isInstalled(model) && LiteRtModelContainer.hasHeader(file) &&
                ModelCompatibilityStore(activity).verified(file, false)
        }
        val builtIn = DialogueModelId.builtInChoices
        val labels = builtIn.map { LanguageModelCatalog[it].title } + imported.map { it.title }
        val selected = if (draft.dialogue == DialogueModelId.USER_LITERT)
            imported.indexOfFirst { it.id == draft.customModelId }.takeIf { it >= 0 }
                ?.plus(builtIn.size) ?: -1 else builtIn.indexOf(draft.dialogue)
        AlertDialog.Builder(activity).setTitle(R.string.models_ui_dialogue_heading)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, position ->
                val saved = if (position < builtIn.size) selectModel(builtIn[position])
                    else selectModel(DialogueModelId.USER_LITERT, imported[position - builtIn.size].id)
                if (saved) dialog.dismiss()
                else (dialog as AlertDialog).listView.setItemChecked(selected, true)
            }
            .setNeutralButton(R.string.mv_manage_files) { _, _ -> manageFiles() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun inputTitle(input: SpeechInputId): String = activity.getString(when (input) {
        SpeechInputId.ANDROID_STT -> R.string.mv_android_short
        SpeechInputId.GEMMA_AUDIO_DIRECT -> R.string.mv_gemma_short
        SpeechInputId.PARAKEET_PL -> R.string.mv_parakeet_short
    })

    private fun showInputPicker() {
        val group = RadioGroup(activity).apply {
            val horizontal = UiDesign.dp(activity, 16)
            val vertical = UiDesign.dp(activity, 8)
            setPadding(horizontal, vertical, horizontal, vertical)
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.mv_input)
            .setView(group).setNegativeButton(android.R.string.cancel, null).create()
        val choices = AudioEngineRegistry.inputs.filter { !it.advanced } +
            AudioEngineRegistry.inputs.filter { it.advanced }
        for (choice in choices) {
            if (choice.advanced) group.addView(UiDesign.caption(activity, activity.getString(R.string.mv_additional)))
            val input = SpeechInputId.valueOf(choice.id)
            group.addView(RadioButton(activity).apply {
                text = inputTitle(input); tag = choice.id
                minimumHeight = UiDesign.dp(activity, 52)
                isChecked = draft.input == input
                isEnabled = (draft.languageTag != "en-US" || input == SpeechInputId.ANDROID_STT) &&
                    (input != SpeechInputId.GEMMA_AUDIO_DIRECT || AudioEngineRegistry.supportsAudio(draft))
                setOnClickListener { if (selectInput(input)) dialog.dismiss() }
            })
        }
        dialog.show()
    }

    private fun showVoicePicker() {
        voiceDialog?.dismiss()
        val options = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(activity, 20), 0, UiDesign.dp(activity, 20), 0)
        }
        voiceOptions = options
        voiceDialog = AlertDialog.Builder(activity).setTitle(R.string.mv_voice)
            .setView(ScrollView(activity).apply { addView(options) })
            .setNegativeButton(android.R.string.cancel, null).create()
        val dialog = checkNotNull(voiceDialog)
        dialog.setOnDismissListener {
            if (voiceDialog === dialog) { voiceOptions = null; voiceDialog = null }
        }
        dialog.show()
        renderVoicePicker()
    }

    private fun renderVoicePicker() {
        val options = voiceOptions ?: return
        options.removeAllViews()
        val current = draft.effectiveTtsEnginePackage
        val engineLabel = engineLabels.getOrNull(engineIds.indexOf(current)) ?: activity.getString(R.string.mv_engine_missing)
        options.addView(UiDesign.action(activity, activity.getString(R.string.mv_engine_value, engineLabel)) {
            voiceDialog?.dismiss(); showEnginePicker()
        }, UiDesign.spaceAfter(activity, 12))
        if (!voicesReady) {
            options.addView(UiDesign.caption(activity, activity.getString(R.string.mv_loading_voices))); return
        }
        val ids = listOf<String?>(null) + localVoices.map { it.name }
        val labels = listOf(activity.getString(R.string.mv_voice_auto)) +
            localVoices.indices.map { activity.getString(R.string.mv_local_voice, it + 1) }
        if (localVoices.isEmpty()) options.addView(UiDesign.caption(activity, activity.getString(
            if (draft.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE)
                R.string.mv_piper_voice_missing else R.string.mv_voice_missing)))
        ids.forEachIndexed { index, id ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(RadioButton(activity).apply {
                text = labels[index]; isChecked = draft.voiceId == id
                minimumHeight = UiDesign.dp(activity, 52)
                setOnClickListener { if (save(draft.copy(voiceId = id))) voiceDialog?.dismiss() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiDesign.action(activity, "▶") { preview(id) }.apply {
                contentDescription = activity.getString(R.string.eng_ui_preview)
                setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(UiDesign.dp(activity, 48), UiDesign.dp(activity, 48)))
            options.addView(row, UiDesign.spaceAfter(activity, 8))
        }
    }

    private fun showEnginePicker() {
        val options = ModelVoiceEnginePickerPresenter.present(
            engineIds, engineLabels, draft) { activity.getString(R.string.mv_engine_missing) }
        AlertDialog.Builder(activity).setTitle(R.string.mv_engine)
            .setSingleChoiceItems(options.labels.toTypedArray(), options.selected) { dialog, position ->
                if (save(draft.copy(output = SpeechOutputId.ANDROID_TTS_PL,
                        ttsEnginePackage = options.ids[position], voiceId = null))) {
                    dialog.dismiss(); showVoicePicker()
                }
            }.setNegativeButton(android.R.string.cancel) { _, _ -> showVoicePicker() }.show()
    }

    private fun loadVoices() {
        val generation = ++voiceGeneration
        val language = Locale.forLanguageTag(draft.languageTag).language
        voicesReady = false
        localVoices = emptyList()
        tts?.shutdown(); tts = null
        renderVoicePicker()
        val requested = draft.effectiveTtsEnginePackage
        val holder = arrayOfNulls<TextToSpeech>(1)
        val initialized = TextToSpeech.OnInitListener { result -> activity.mainExecutor.execute {
            val engine = holder[0] ?: return@execute
            if (generation != voiceGeneration || activity.isDestroyed || activity.isFinishing) {
                engine.shutdown(); return@execute
            }
            if (result != TextToSpeech.SUCCESS) {
                engine.shutdown(); tts = null; voicesReady = true; previewWhenReady = false
                voiceIssue.text = activity.getString(R.string.mv_voice_missing)
                renderVoicePicker(); refresh(); return@execute
            }
            voiceWorker.execute {
                val inventory = runCatching {
                    val installed = engine.engines
                    val voices = ModelVoiceTtsInventoryPolicy.availableOffline(
                        installedEngines = installed.map { it.name },
                        selectedEngine = requested,
                        voices = engine.voices.orEmpty(),
                        language = language,
                        piperRequired = requested == PiperServiceContract.PACKAGE,
                        languageOf = { it.locale.language },
                        networkRequired = { it.isNetworkConnectionRequired },
                        piperCapable = { "piperVoice" in it.features },
                        nameOf = { it.name },
                    )
                    Triple(installed, voices, engine.defaultEngine)
                }.getOrNull()
                activity.mainExecutor.execute ready@{
                    if (generation != voiceGeneration || activity.isDestroyed || activity.isFinishing) return@ready
                    voicesReady = true
                    localVoices = inventory?.second.orEmpty()
                    if (inventory == null) voiceIssue.text = activity.getString(R.string.mv_voice_missing)
                    else {
                        engineIds = listOf(null) + inventory.first.map { it.name }
                        val labels = inventory.first.map { ModelVoiceTtsInventoryPolicy.displayEngineName(it.name, it.label) }
                        val defaultIndex = inventory.first.indexOfFirst { it.name == inventory.third }
                        val automatic = labels.getOrNull(defaultIndex)?.let {
                            activity.getString(R.string.mv_system_engine_named, it)
                        } ?: activity.getString(R.string.mv_system_engine)
                        engineLabels = listOf(automatic) + labels
                    }
                    renderVoicePicker()
                    refresh()
                    if (previewWhenReady) { previewWhenReady = false; preview(pendingPreviewVoice) }
                }
            }
        } }
        holder[0] = if (requested == null) TextToSpeech(activity, initialized)
            else TextToSpeech(activity, initialized, requested)
        tts = holder[0]
    }

    fun refresh() {
        val library = UserLiteRtModelStore(activity)
        generationSummary.text = activity.getString(R.string.model_generation_summary,
            draft.generation.contextBudget(draft.dialogue, structured = true),
            draft.generation.outputBudget(structured = true), draft.generation.temperature)
        modelName.text = if (draft.dialogue == DialogueModelId.USER_LITERT)
            library.find(draft.customModelId)?.title ?: activity.getString(R.string.models_ui_user_missing)
            else LanguageModelCatalog[draft.dialogue].title
        inputName.text = inputTitle(draft.input)
        languageName.text = activity.getString(if (draft.languageTag == "en-US")
            R.string.mv_speech_language_en else R.string.mv_speech_language_pl)
        voiceName.text = if (draft.voiceId == null) activity.getString(R.string.mv_voice_auto)
            else localVoices.indexOfFirst { it.name == draft.voiceId }.takeIf { it >= 0 }
                ?.let { activity.getString(R.string.mv_local_voice, it + 1) }
                ?: activity.getString(R.string.mv_saved_voice_unavailable)
        val names = if (voicesReady) localVoices.map { it.name }.toSet() else null
        val result = AudioEngineReadinessProbe.check(activity, draft, names)
        val file = runCatching { Gemma4LiteRtTextBackendFactory.modelFile(activity, draft) }.getOrNull()
        val transfer = LanguageModelTransferService.status
        val builtIn = draft.dialogue in DialogueModelId.builtInChoices
        val downloading = transfer.busy && transfer.id == draft.dialogue && builtIn
        val audioMode = draft.input == SpeechInputId.GEMMA_AUDIO_DIRECT &&
            AudioEngineRegistry.supportsAudio(draft)
        val verified = file?.isFile == true && ModelCompatibilityStore(activity).verified(file, audioMode, draft)
        val selectedAttempt = file?.takeIf { it.isFile }
            ?.let { ModelCompatibilityStamp.of(it, draft, audioMode) }
            ?.let { "$it:$audioMode:${draft.customModelId}" }
        val attemptMatches = lastAttempt == selectedAttempt
        modelState.text = when {
            downloading -> activity.getString(R.string.mv_download_progress,
                (transfer.bytes * 100 / checkNotNull(LanguageModelCatalog[draft.dialogue].spec.expectedBytes)).toInt().coerceIn(0, 100))
            checking && attemptMatches -> activity.getString(if (audioMode)
                R.string.mv_checking_audio else R.string.mv_checking_model)
            attemptMatches && checkResult != null && file?.isFile == true -> checkNotNull(checkResult)
            file?.isFile == true -> activity.getString(when (ModelVerificationUi.status(
                verified, draft.dialogue.requiresTextCheck, audioMode)) {
                ModelVerificationStatus.VERIFIED_TEXT -> R.string.mv_verified_text
                ModelVerificationStatus.VERIFIED_AUDIO -> R.string.mv_verified_audio
                ModelVerificationStatus.REQUIRED_TEXT -> R.string.mv_compatibility_required
                ModelVerificationStatus.REQUIRED_AUDIO -> R.string.mv_audio_unverified
                ModelVerificationStatus.DOWNLOADED_UNVERIFIED -> R.string.mv_downloaded_unverified
            })
            else -> activity.getString(R.string.mv_model_missing_short)
        }
        val code = result.reason
        val voiceProblem = code in setOf("tts_offline_voice_probe_required", "polish_offline_voice_missing", "selected_voice_missing")
        val inputProblem = code?.startsWith("parakeet_") == true || code == "android_offline_stt_unavailable"
        voiceIssue.text = if (voiceProblem) reason(activity, code) else ""
        inputIssue.text = if (inputProblem) reason(activity, code) else ""
        voiceIssue.visibility = if (voiceProblem) View.VISIBLE else View.GONE
        inputIssue.visibility = if (inputProblem) View.VISIBLE else View.GONE
        if (code != null && !verified && checkResult == null && !voiceProblem && !inputProblem &&
            !checking && !downloading && file?.isFile == true)
            modelState.text = reason(activity, code)
        modelAction.visibility = if (downloading || file?.isFile != true) View.VISIBLE else View.GONE
        modelAction.text = activity.getString(when {
            downloading -> R.string.models_ui_cancel
            draft.dialogue == DialogueModelId.USER_LITERT -> R.string.mv_manage_files
            else -> R.string.models_ui_download
        })
        modelAction.isEnabled = !transfer.busy || downloading
        if (active && !checking) {
            val imported = library.models().firstOrNull { it.id !in knownImports }
            if (imported != null) {
                knownImports.add(imported.id)
                verify(draft.copy(input = SpeechInputId.ANDROID_STT,
                    dialogue = DialogueModelId.USER_LITERT, customModelId = imported.id), false)
            } else if (file?.isFile == true) {
                val cache = ModelCompatibilityStore(activity)
                val needsText = draft.dialogue.requiresTextCheck && !cache.verified(file, false)
                val needsAudio = draft.input == SpeechInputId.GEMMA_AUDIO_DIRECT &&
                    AudioEngineRegistry.supportsAudio(draft) && !cache.verified(file, true, draft)
                if (needsAudio || needsText) verify(draft, needsAudio)
            }
        }
        retry.visibility = if (ModelVerificationUi.checkVisible(file?.isFile == true, downloading))
            View.VISIBLE else View.GONE
        retry.isEnabled = !checking && (!audioMode || voicesReady)
        retry.text = activity.getString(when {
            checking -> R.string.mv_checking_short
            audioMode && !voicesReady -> R.string.mv_loading_voices
            audioMode && (verified || attemptMatches) -> R.string.mv_retry_audio
            audioMode -> R.string.mv_check_audio
            verified || attemptMatches -> R.string.mv_retry
            else -> R.string.mv_check_compatibility
        })
    }

    private fun modelAction() {
        if (draft.dialogue !in DialogueModelId.builtInChoices) { manageFiles(); return }
        val intent = Intent(activity, LanguageModelTransferService::class.java)
        if (LanguageModelTransferService.status.busy) {
            if (LanguageModelTransferService.status.id == draft.dialogue)
                activity.startService(intent.setAction(LanguageModelTransferService.CANCEL))
        }
        else activity.startForegroundService(intent.putExtra(LanguageModelTransferService.MODEL, draft.dialogue.name))
    }

    private fun verify(profile: AudioEngineProfile, audio: Boolean, force: Boolean = false) {
        if (checking) return
        val file = runCatching { Gemma4LiteRtTextBackendFactory.modelFile(activity, profile) }.getOrNull() ?: return
        val stamp = ModelCompatibilityStamp.of(file, profile, audio) ?: return
        val attempt = "$stamp:$audio:${profile.customModelId}"
        if (!force && lastAttempt == attempt) return
        if (audio && !voicesReady) {
            if (force) {
                lastAttempt = attempt
                checkResult = reason(activity, "tts_offline_voice_probe_required")
            }
            return
        }
        // A manual check must NEVER silently fall back to the neutral
        // "Downloaded / compatibility unverified" state when it was blocked.
        // S8 Lab intentionally does not inherit the stable dialer's permissions.
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            if (force) {
                lastAttempt = attempt
                checkResult = activity.getString(R.string.mv_compatibility_phone_permission)
                // Grant alone never authorizes native inference: second tap required.
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_PHONE_STATE), COMPAT_PHONE_STATE_REQUEST)
            }
            return
        }
        if (!canProbe()) {
            if (force) {
                lastAttempt = attempt
                checkResult = activity.getString(R.string.mv_compatibility_blocked)
            }
            return
        }
        lastAttempt = attempt
        checking = true
        checkResult = null
        modelState.text = activity.getString(if (audio) R.string.mv_checking_audio else R.string.mv_checking_model)
        try {
            probe.check(file, profile, audio) { error ->
                checking = false
                checkResult = if (error != null) reason(activity, error)
                    else activity.getString(if (audio) R.string.mv_audio_check_passed else R.string.mv_model_check_passed)
                refresh(); changed()
            }
        } catch (_: Throwable) {
            // An unavailable native library / provider must not leave the
            // check permanently running with an unverified generic label.
            probe.close()
            checking = false
            checkResult = reason(activity, "model_native_check_failed")
            refresh()
        }
    }

    private fun preview(voiceId: String? = draft.voiceId) {
        if (!active) return
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            runCatching { activity.getSystemService(TelecomManager::class.java)?.isInCall != false ||
                activity.getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_IN_CALL }.getOrDefault(true)) {
            Toast.makeText(activity, R.string.eng_ui_preview_disabled, Toast.LENGTH_SHORT).show(); return
        }
        val engine = tts
        if (engine == null || !voicesReady) {
            previewWhenReady = true; pendingPreviewVoice = voiceId
            if (engine == null) loadVoices()
            return
        }
        val selected = localVoices.firstOrNull { voiceId == null || it.name == voiceId }
        if (selected == null) {
            notifyError("polish_offline_voice_missing"); return
        }
        val deferPiperLoad = PiperServiceContract.canDeferVoiceLoad(
            draft.effectiveTtsEnginePackage, selected.name,
            engine.voices.orEmpty().map { it.name })
        if (!deferPiperLoad && engine.setVoice(selected) != TextToSpeech.SUCCESS) {
            notifyError("polish_offline_voice_missing"); return
        }
        engine.speak(activity.getString(if (draft.languageTag == "en-US")
            R.string.mv_voice_sample_english else R.string.mv_voice_sample),
            TextToSpeech.QUEUE_FLUSH, null, "model-voice-preview")
    }

    private fun notifyError(code: String) = Toast.makeText(activity, reason(activity, code), Toast.LENGTH_LONG).show()
    fun syncSavedProfile() {
        val change = profiles.pendingExternalChange() ?: return
        probe.close(); checking = false; lastAttempt = null; checkResult = null
        profiles.acceptExternalChange(change)
        if (change.engineChanged && active) loadVoices()
    }
    fun resume() { active = true; syncSavedProfile(); loadVoices(); refresh() }
    fun pause() {
        active = false; previewWhenReady = false
        if (checking) {
            // Keep the stamp of the interrupted attempt so its explanatory
            // state stays visible after the activity resumes. No PASS stored.
            checkResult = activity.getString(R.string.mv_check_interrupted)
        }
        probe.close(); checking = false; tts?.stop(); voiceDialog?.dismiss()
    }
    override fun close() { pause(); ++voiceGeneration; tts?.shutdown(); tts = null; voiceWorker.shutdownNow() }

    companion object {
        const val COMPAT_PHONE_STATE_REQUEST = 8112

        fun reason(context: Context, code: String?): String = context.getString(when (code) {
            "dialogue_model_missing_or_invalid", "user_model_missing" -> R.string.mv_model_missing
            "model_compatibility_required" -> R.string.mv_compatibility_required
            "model_audio_check_required" -> R.string.mv_audio_check_required
            "model_audio_input_unavailable" -> R.string.mv_audio_unavailable
            "model_container_invalid" -> R.string.mv_invalid_container
            "model_native_check_failed" -> R.string.mv_native_check_failed
            "model_reply_incompatible" -> R.string.mv_reply_check_failed
            "model_audio_reply_format_failed" -> R.string.mv_audio_reply_format_failed
            "model_audio_transcript_mismatch" -> R.string.mv_audio_transcript_mismatch
            "model_audio_tool_unexpected" -> R.string.mv_audio_tool_unexpected
            "model_changed_during_check", "model_check_state_unavailable" -> R.string.mv_check_result_unsaved
            "parakeet_model_missing_or_incomplete" -> R.string.models_ui_parakeet_required
            "parakeet_model_sha256_mismatch" -> R.string.models_ui_parakeet_invalid
            "android_offline_stt_unavailable" -> R.string.models_ui_android_stt_unavailable
            "tts_offline_voice_probe_required" -> R.string.mv_loading_voices
            "polish_offline_voice_missing", "selected_voice_missing" -> R.string.mv_voice_missing
            "english_android_stt_required", "english_android_tts_required", "unsupported_language" ->
                R.string.mv_english_route_required
            "polish_offline_voice_invalid_pcm" -> R.string.mv_audio_input_invalid
            "polish_offline_voice_audio_too_long" -> R.string.mv_audio_too_long
            else -> R.string.mv_compatibility_failed
        })
    }
}
