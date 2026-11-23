package pl.michalmatu.aicallbridge.ui.modelvoice

import pl.michalmatu.aicallbridge.audioengines.*

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.Locale
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceProfileController
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceEnginePickerPresenter
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceReadinessPresenter
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceVerificationController
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceSpeechPickerPresenter
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceModelPickerPresenter
import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceTtsAsyncState
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
    private val verification = ModelVoiceVerificationController()
    private val checking get() = verification.checking
    private val lastAttempt get() = verification.lastAttempt
    private val checkResult get() = verification.checkResult
    private val voiceState = ModelVoiceTtsAsyncState()
    private var tts: TextToSpeech? = null
    private val voicesReady: Boolean get() = voiceState.voicesReady
    private var localVoices = emptyList<Voice>()
    private val voiceWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "model-voice-inventory").apply { isDaemon = true }
    }
    private var engineIds = listOf<String?>(null)
    private var engineLabels = listOf(activity.getString(R.string.mv_system_engine))
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
                probe.close(); verification.reset()
                if (result.engineChanged) {
                    voiceState.onEngineChanged()
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
        ModelVoiceGenerationDialog(activity, draft.generation) { generation ->
            save(draft.copy(generation = generation))
        }.show()
    }

    private fun showLanguagePicker() {
        ModelVoiceLanguageDialog(activity, draft.languageTag) { selected ->
            if (selected == draft.languageTag) true
            else save(ModelVoiceSelectionPolicy.forLanguage(draft, selected))
        }.show()
    }

    private fun showModelPicker() {
        val library = UserLiteRtModelStore(activity)
        // Invalid imports remain visible in file management, never in the active picker.
        val imported = library.models().filter { model ->
            val file = library.file(model)
            library.isInstalled(model) && LiteRtModelContainer.hasHeader(file) &&
                ModelCompatibilityStore(activity).verified(file, false)
        }
        val picker = ModelVoiceModelPickerPresenter.present(
            draft = draft,
            builtIn = DialogueModelId.builtInChoices.map { it to LanguageModelCatalog[it].title },
            imported = imported.map { it.id to it.title },
        )
        ModelVoiceModelDialog(
            activity = activity,
            picker = picker,
            selectModel = ::selectModel,
            manageFiles = manageFiles,
        ).show()
    }

    private fun inputTitle(input: SpeechInputId): String = activity.getString(when (input) {
        SpeechInputId.ANDROID_STT -> R.string.mv_android_short
        SpeechInputId.GEMMA_AUDIO_DIRECT -> R.string.mv_gemma_short
        SpeechInputId.PARAKEET_PL -> R.string.mv_parakeet_short
    })

    private fun showInputPicker() {
        ModelVoiceInputDialog(
            activity,
            ModelVoiceSpeechPickerPresenter.inputOptions(draft, AudioEngineRegistry.inputs),
            ::inputTitle,
            ::selectInput,
        ).show()
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
        val picker = ModelVoiceSpeechPickerPresenter.voiceOptions(
            draft, engineIds, engineLabels, localVoices.map { it.name },
            voicesReady, activity.getString(R.string.mv_engine_missing))
        ModelVoiceVoiceOptionsView(activity, options).render(
            picker = picker,
            onChooseEngine = { voiceDialog?.dismiss(); showEnginePicker() },
            onSelectVoice = { id ->
                if (save(draft.copy(voiceId = id))) voiceDialog?.dismiss()
            },
            onPreview = ::preview,
        )
    }

    private fun showEnginePicker() {
        val options = ModelVoiceEnginePickerPresenter.present(
            engineIds, engineLabels, draft) { activity.getString(R.string.mv_engine_missing) }
        ModelVoiceEngineDialog(
            activity = activity,
            options = options,
            onSelectEngine = { packageName ->
                save(draft.copy(output = SpeechOutputId.ANDROID_TTS_PL,
                    ttsEnginePackage = packageName, voiceId = null))
            },
            onReturnToVoices = ::showVoicePicker,
        ).show()
    }

    private fun loadVoices() {
        val generation = voiceState.beginLoad()
        val language = Locale.forLanguageTag(draft.languageTag).language
        localVoices = emptyList()
        tts?.shutdown(); tts = null
        renderVoicePicker()
        val requested = draft.effectiveTtsEnginePackage
        val holder = arrayOfNulls<TextToSpeech>(1)
        val initialized = TextToSpeech.OnInitListener { result -> activity.mainExecutor.execute {
            val engine = holder[0] ?: return@execute
            if (!voiceState.accepts(generation) || activity.isDestroyed || activity.isFinishing) {
                engine.shutdown(); return@execute
            }
            if (result != TextToSpeech.SUCCESS) {
                engine.shutdown(); tts = null; voiceState.markReady(); voiceState.cancelPreview()
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
                    if (!voiceState.accepts(generation) || activity.isDestroyed || activity.isFinishing) return@ready
                    voiceState.markReady()
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
                    voiceState.takeQueuedPreview()?.let { preview(it.voiceId) }
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
        val ui = ModelVoiceReadinessPresenter.present(ModelVoiceReadinessPresenter.Input(
            modelInstalled = file?.isFile == true, downloading = downloading, checking = checking,
            attemptMatches = attemptMatches, hasAttemptResult = checkResult != null,
            verified = verified, requiresTextCheck = draft.dialogue.requiresTextCheck,
            audioMode = audioMode, reasonCode = result.reason, transferBusy = transfer.busy,
            importedModel = draft.dialogue == DialogueModelId.USER_LITERT, voicesReady = voicesReady,
        ))
        modelState.text = when (ui.modelLine) {
            ModelVoiceReadinessPresenter.ModelLine.DOWNLOAD_PROGRESS -> activity.getString(
                R.string.mv_download_progress,
                (transfer.bytes * 100 / checkNotNull(LanguageModelCatalog[draft.dialogue].spec.expectedBytes)).toInt().coerceIn(0, 100))
            ModelVoiceReadinessPresenter.ModelLine.CHECKING_AUDIO -> activity.getString(R.string.mv_checking_audio)
            ModelVoiceReadinessPresenter.ModelLine.CHECKING_MODEL -> activity.getString(R.string.mv_checking_model)
            ModelVoiceReadinessPresenter.ModelLine.ATTEMPT_RESULT -> checkNotNull(checkResult)
            ModelVoiceReadinessPresenter.ModelLine.VERIFIED_TEXT -> activity.getString(R.string.mv_verified_text)
            ModelVoiceReadinessPresenter.ModelLine.VERIFIED_AUDIO -> activity.getString(R.string.mv_verified_audio)
            ModelVoiceReadinessPresenter.ModelLine.REQUIRED_TEXT -> activity.getString(R.string.mv_compatibility_required)
            ModelVoiceReadinessPresenter.ModelLine.REQUIRED_AUDIO -> activity.getString(R.string.mv_audio_unverified)
            ModelVoiceReadinessPresenter.ModelLine.DOWNLOADED_UNVERIFIED -> activity.getString(R.string.mv_downloaded_unverified)
            ModelVoiceReadinessPresenter.ModelLine.MISSING -> activity.getString(R.string.mv_model_missing_short)
            ModelVoiceReadinessPresenter.ModelLine.READINESS_REASON -> reason(activity, result.reason)
        }
        voiceIssue.text = if (ui.voiceProblem) reason(activity, result.reason) else ""
        inputIssue.text = if (ui.inputProblem) reason(activity, result.reason) else ""
        voiceIssue.visibility = if (ui.voiceProblem) View.VISIBLE else View.GONE
        inputIssue.visibility = if (ui.inputProblem) View.VISIBLE else View.GONE
        modelAction.visibility = if (ui.primaryVisible) View.VISIBLE else View.GONE
        modelAction.text = activity.getString(when (ui.primaryAction) {
            ModelVoiceReadinessPresenter.PrimaryAction.CANCEL -> R.string.models_ui_cancel
            ModelVoiceReadinessPresenter.PrimaryAction.MANAGE_FILES -> R.string.mv_manage_files
            ModelVoiceReadinessPresenter.PrimaryAction.DOWNLOAD -> R.string.models_ui_download
        })
        modelAction.isEnabled = ui.primaryEnabled
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
        retry.visibility = if (ui.retryVisible) View.VISIBLE else View.GONE
        retry.isEnabled = ui.retryEnabled
        retry.text = activity.getString(when (ui.retryAction) {
            ModelVoiceReadinessPresenter.RetryAction.CHECKING -> R.string.mv_checking_short
            ModelVoiceReadinessPresenter.RetryAction.LOADING_VOICES -> R.string.mv_loading_voices
            ModelVoiceReadinessPresenter.RetryAction.RETRY_AUDIO -> R.string.mv_retry_audio
            ModelVoiceReadinessPresenter.RetryAction.CHECK_AUDIO -> R.string.mv_check_audio
            ModelVoiceReadinessPresenter.RetryAction.RETRY -> R.string.mv_retry
            ModelVoiceReadinessPresenter.RetryAction.CHECK_COMPATIBILITY -> R.string.mv_check_compatibility
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
        val decision = verification.begin(
            attempt = attempt, force = force, audio = audio, voicesReady = voicesReady,
            hasPhonePermission = {
                activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
            },
            canProbe = canProbe,
            blockedMessage = { block ->
                when (block) {
                    ModelVoiceVerificationController.BlockedReason.VOICES_UNAVAILABLE ->
                        reason(activity, "tts_offline_voice_probe_required")
                    ModelVoiceVerificationController.BlockedReason.PHONE_PERMISSION ->
                        activity.getString(R.string.mv_compatibility_phone_permission)
                    ModelVoiceVerificationController.BlockedReason.CALL_UNSAFE ->
                        activity.getString(R.string.mv_compatibility_blocked)
                }
            },
        )
        when (decision) {
            ModelVoiceVerificationController.Decision.Skip -> return
            is ModelVoiceVerificationController.Decision.Blocked -> {
                if (decision.requestPermission) {
                    // Grant alone never starts the native check: explicit second tap required.
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.READ_PHONE_STATE), COMPAT_PHONE_STATE_REQUEST)
                }
                return
            }
            ModelVoiceVerificationController.Decision.Start -> Unit
        }
        modelState.text = activity.getString(if (audio) R.string.mv_checking_audio else R.string.mv_checking_model)
        try {
            probe.check(file, profile, audio) { error ->
                verification.complete(if (error != null) reason(activity, error)
                    else activity.getString(if (audio) R.string.mv_audio_check_passed else R.string.mv_model_check_passed))
                refresh(); changed()
            }
        } catch (_: Throwable) {
            probe.close()
            verification.complete(reason(activity, "model_native_check_failed"))
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
            voiceState.queuePreview(voiceId)
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
        probe.close(); verification.reset()
        profiles.acceptExternalChange(change)
        if (change.engineChanged && active) loadVoices()
    }
    fun resume() { active = true; syncSavedProfile(); loadVoices(); refresh() }
    fun pause() {
        active = false; voiceState.cancelPreview()
        verification.pause { activity.getString(R.string.mv_check_interrupted) }
        probe.close(); tts?.stop(); voiceDialog?.dismiss()
    }
    override fun close() { pause(); voiceState.invalidate(); tts?.shutdown(); tts = null; voiceWorker.shutdownNow() }

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
