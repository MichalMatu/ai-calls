package pl.michalmatu.aicallbridge.audioengines

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.telecom.TelecomManager
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/** One draft and one explicit save. File readiness never masquerades as a saved preference. */
internal class ModelVoiceSettings(
    private val activity: UiChromeActivity,
    state: Bundle?,
    private val canProbe: () -> Boolean,
    private val changed: () -> Unit,
) : AutoCloseable {
    private val store = AudioEngineProfileStore(activity)
    private var saved = store.load()
    var draft = saved ?: AudioEngineProfile.STABLE
        private set
    val dirty get() = draft != (saved ?: AudioEngineProfile.STABLE)
    private val probe = ModelCompatibilityProbe(activity)
    private var active = false
    private var checking = false
    private var lastAttempt: String? = null
    private var voiceGeneration = 0
    private var tts: TextToSpeech? = null
    private var voicesReady = false
    private var localVoices = emptyList<Voice>()
    private val voiceWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "model-voice-inventory").apply { isDaemon = true }
    }
    private var voiceIds = emptyList<String?>()
    private var engineIds = listOf<String?>(null)
    private var engineLabels = listOf(activity.getString(R.string.mv_system_engine))
    private var suppress = false
    private var previewWhenReady = false
    private val knownImports = UserLiteRtModelStore(activity).models().map { it.id }.toMutableSet()

    val status = UiDesign.caption(activity, "").apply {
        tag = "model-voice-readiness"
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    val feedback = UiDesign.caption(activity, "").apply { tag = "engine-feedback" }
    val inputView = UiDesign.card(activity)
    val voiceView = UiDesign.card(activity)
    val advancedVoiceView = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val actionsView = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val advancedInput = UiDesign.caption(activity, "").apply { tag = "model-voice-advanced-input" }
    private val inputs = RadioGroup(activity).apply { tag = "model-voice-inputs" }
    private val voices = Spinner(activity).apply { tag = "model-voice-picker" }
    private val engines = Spinner(activity).apply { tag = "model-voice-engine" }

    init {
        if (state?.containsKey("mv_input") == true) {
            draft = draft.copy(
                input = SpeechInputId.entries.firstOrNull { it.name == state.getString("mv_input") } ?: draft.input,
                dialogue = DialogueModelId.entries.firstOrNull { it.name == state.getString("mv_model") } ?: draft.dialogue,
                customModelId = state.getString("mv_custom"), voiceId = state.getString("mv_voice"),
                ttsEnginePackage = state.getString("mv_engine"))
        }
        inputView.addView(UiDesign.heading(activity, activity.getString(R.string.mv_input)))
        inputView.addView(advancedInput)
        inputView.addView(inputs)
        voiceView.addView(UiDesign.heading(activity, activity.getString(R.string.mv_voice)))
        voiceView.addView(voices)
        voiceView.addView(UiDesign.action(activity, activity.getString(R.string.eng_ui_preview)) { preview() }
            .apply { tag = "model-voice-preview" })
        advancedVoiceView.addView(UiDesign.heading(activity, activity.getString(R.string.mv_engine)))
        advancedVoiceView.addView(engines)
        actionsView.addView(feedback)
        actionsView.addView(UiDesign.action(activity, activity.getString(R.string.eng_ui_save)) {
            val failure = AudioEngineRegistry.incompatibility(draft)
            feedback.text = when {
                failure != null -> reason(activity, failure)
                store.save(draft) -> {
                    saved = draft
                    activity.getString(R.string.mv_saved)
                }
                else -> activity.getString(R.string.eng_ui_save_error)
            }
            refresh(); changed()
        }.apply { tag = "engine-save" })
        actionsView.addView(UiDesign.action(activity, activity.getString(R.string.mv_retry)) {
            lastAttempt = null; refresh()
        }.apply { tag = "model-voice-retry" })
        voices.onItemSelectedListener = selection { position ->
            val id = voiceIds.getOrNull(position)
            if (id != draft.voiceId) { draft = draft.copy(voiceId = id); edit() }
        }
        engines.onItemSelectedListener = selection { position ->
            val id = engineIds.getOrNull(position)
            if (id != draft.effectiveTtsEnginePackage) {
                draft = draft.copy(output = SpeechOutputId.ANDROID_TTS_PL,
                    ttsEnginePackage = id, voiceId = null)
                loadVoices(); edit()
            }
        }
        rebuildInputs()
        updateEnginePicker()
        updateVoices(emptyList())
    }

    private fun selection(selected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (!suppress && position >= 0 && parent?.selectedItemPosition == position) selected(position)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    fun selectModel(id: DialogueModelId, custom: String? = null) {
        draft = draft.copy(dialogue = id, customModelId = custom)
        rebuildInputs(); edit()
    }

    fun selectInput(id: SpeechInputId) {
        draft = draft.copy(input = id)
        rebuildInputs(); edit()
    }

    private fun edit() {
        probe.close(); checking = false; lastAttempt = null
        feedback.text = activity.getString(R.string.mv_unsaved)
        refresh(); changed()
    }

    private fun rebuildInputs() {
        inputs.setOnCheckedChangeListener(null)
        inputs.removeAllViews()
        val selectedAdvanced = AudioEngineRegistry.inputs.firstOrNull { it.id == draft.input.name && it.advanced }
        advancedInput.text = activity.getString(R.string.mv_parakeet_input)
        advancedInput.visibility = if (selectedAdvanced != null) View.VISIBLE else View.GONE
        val choices = AudioEngineRegistry.inputs.filter { !it.advanced }
        for (choice in choices) inputs.addView(RadioButton(activity).apply {
            id = View.generateViewId(); tag = choice.id
            text = activity.getString(when (choice.id) {
                "ANDROID_STT" -> R.string.mv_android_input
                "GEMMA_AUDIO_DIRECT" -> R.string.mv_gemma_input
                else -> R.string.mv_parakeet_input
            })
            minimumHeight = UiDesign.dp(activity, 52)
            isEnabled = choice.id != "GEMMA_AUDIO_DIRECT" || AudioEngineRegistry.supportsAudio(draft)
            isChecked = choice.id == draft.input.name
        })
        inputs.setOnCheckedChangeListener { group, id ->
            val choice = group.findViewById<RadioButton>(id)?.tag as? String
            if (choice != null && choice != draft.input.name) selectInput(SpeechInputId.valueOf(choice))
        }
    }

    private fun updateEnginePicker() {
        val current = draft.effectiveTtsEnginePackage
        val ids = engineIds.toMutableList()
        val labels = engineLabels.toMutableList()
        if (current !in ids) { ids.add(current); labels.add(activity.getString(R.string.mv_engine_missing)) }
        engineIds = ids; engineLabels = labels
        suppress = true
        UiSelect.setOptions(engines, labels)
        engines.setSelection(ids.indexOf(current).coerceAtLeast(0))
        suppress = false
    }

    private fun updateVoices(names: List<String>) {
        val selected = draft.voiceId
        voiceIds = listOf(null) + names + if (selected != null && selected !in names) listOf(selected) else emptyList()
        val labels = listOf(activity.getString(R.string.eng_ui_voice_auto)) +
            names.mapIndexed { index, _ -> activity.getString(R.string.mv_local_voice, index + 1) } +
            if (selected != null && selected !in names) listOf(activity.getString(R.string.mv_saved_voice_unavailable)) else emptyList()
        suppress = true
        UiSelect.setOptions(voices, labels)
        voices.setSelection(voiceIds.indexOf(selected).coerceAtLeast(0))
        suppress = false
    }

    private fun loadVoices() {
        val generation = ++voiceGeneration
        voicesReady = false
        localVoices = emptyList()
        tts?.shutdown(); tts = null
        updateVoices(emptyList())
        val requested = draft.effectiveTtsEnginePackage
        val holder = arrayOfNulls<TextToSpeech>(1)
        val initialized = TextToSpeech.OnInitListener { result -> activity.mainExecutor.execute {
            val engine = holder[0] ?: return@execute
            if (generation != voiceGeneration || activity.isDestroyed || activity.isFinishing) {
                engine.shutdown(); return@execute
            }
            if (result != TextToSpeech.SUCCESS) {
                engine.shutdown(); tts = null; voicesReady = true; previewWhenReady = false
                feedback.text = activity.getString(R.string.mv_voice_missing)
                refresh(); return@execute
            }
            voiceWorker.execute {
                val inventory = runCatching {
                    val installed = engine.engines
                    require(requested == null || installed.any { it.name == requested })
                    installed to engine.voices.orEmpty().filter {
                        it.locale.language == "pl" && !it.isNetworkConnectionRequired &&
                            (requested != PiperServiceContract.PACKAGE || "piperVoice" in it.features)
                    }.sortedBy { it.name }
                }.getOrNull()
                activity.mainExecutor.execute ready@{
                    if (generation != voiceGeneration || activity.isDestroyed || activity.isFinishing) return@ready
                    voicesReady = true
                    localVoices = inventory?.second.orEmpty()
                    if (inventory == null) feedback.text = activity.getString(R.string.mv_voice_missing)
                    else {
                        engineIds = listOf(null) + inventory.first.map { it.name }
                        engineLabels = listOf(activity.getString(R.string.mv_system_engine)) + inventory.first.map { it.label }
                        updateEnginePicker()
                    }
                    updateVoices(localVoices.map { it.name })
                    refresh()
                    if (previewWhenReady) { previewWhenReady = false; preview() }
                }
            }
        } }
        holder[0] = if (requested == null) TextToSpeech(activity, initialized)
            else TextToSpeech(activity, initialized, requested)
        tts = holder[0]
    }

    fun refresh() {
        val names = if (voicesReady) localVoices.map { it.name }.toSet() else null
        val result = AudioEngineReadinessProbe.check(activity, draft, names)
        status.text = if (checking) activity.getString(R.string.mv_checking_model)
            else if (result.available) activity.getString(R.string.mv_ready)
            else reason(activity, result.reason)
        if (active && !checking) {
            val imported = UserLiteRtModelStore(activity).models().firstOrNull { it.id !in knownImports }
            if (imported != null) {
                knownImports.add(imported.id)
                verify(draft.copy(input = SpeechInputId.ANDROID_STT,
                    dialogue = DialogueModelId.USER_LITERT, customModelId = imported.id), false)
            } else {
                val file = runCatching { Gemma4LiteRtTextBackendFactory.modelFile(activity, draft) }.getOrNull()
                if (file != null && file.isFile) {
                    val cache = ModelCompatibilityStore(activity)
                    val needsText = draft.dialogue == DialogueModelId.USER_LITERT && !cache.verified(file, false)
                    val needsAudio = draft.input == SpeechInputId.GEMMA_AUDIO_DIRECT &&
                        AudioEngineRegistry.supportsAudio(draft) && !cache.verified(file, true)
                    if (needsAudio || needsText) verify(draft, needsAudio)
                }
            }
            actionsView.findViewWithTag<View>("model-voice-retry").visibility =
                if (lastAttempt != null && !checking && !result.available) View.VISIBLE else View.GONE
        }
    }

    private fun verify(profile: AudioEngineProfile, audio: Boolean) {
        if (audio && !voicesReady) return
        val file = runCatching { Gemma4LiteRtTextBackendFactory.modelFile(activity, profile) }.getOrNull() ?: return
        val stamp = ModelCompatibilityStamp.of(file) ?: return
        val attempt = "$stamp:$audio:${profile.customModelId}"
        if (lastAttempt == attempt || !canProbe()) return
        lastAttempt = attempt; checking = true
        status.text = activity.getString(R.string.mv_checking_model)
        probe.check(file, profile, audio) { error ->
            checking = false
            if (error != null) feedback.text = reason(activity, error)
            refresh(); changed()
        }
    }

    private fun preview() {
        if (!active) return
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            runCatching { activity.getSystemService(TelecomManager::class.java)?.isInCall != false ||
                activity.getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_IN_CALL }.getOrDefault(true)) {
            feedback.text = activity.getString(R.string.eng_ui_preview_disabled); return
        }
        val engine = tts
        if (engine == null || !voicesReady) { previewWhenReady = true; if (engine == null) loadVoices(); return }
        val selected = localVoices.firstOrNull { draft.voiceId == null || it.name == draft.voiceId }
        if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {
            feedback.text = activity.getString(R.string.mv_voice_missing); return
        }
        engine.speak(activity.getString(R.string.mv_voice_sample), TextToSpeech.QUEUE_FLUSH, null, "model-voice-preview")
    }

    fun saveState(state: Bundle) {
        state.putString("mv_input", draft.input.name); state.putString("mv_model", draft.dialogue.name)
        state.putString("mv_custom", draft.customModelId); state.putString("mv_voice", draft.voiceId)
        state.putString("mv_engine", draft.effectiveTtsEnginePackage)
    }
    fun syncSavedProfile() {
        val current = store.load()
        if (!dirty && current != null && current != saved) {
            val engineChanged = draft.effectiveTtsEnginePackage != current.effectiveTtsEnginePackage
            saved = current; draft = current; rebuildInputs()
            updateEnginePicker(); updateVoices(localVoices.map { it.name })
            if (engineChanged && active) loadVoices()
        }
    }
    fun resume() {
        active = true
        syncSavedProfile()
        loadVoices(); refresh()
    }
    fun pause() {
        active = false; previewWhenReady = false
        if (checking) lastAttempt = null
        probe.close(); checking = false; tts?.stop()
    }
    override fun close() { pause(); ++voiceGeneration; tts?.shutdown(); tts = null; voiceWorker.shutdownNow() }

    companion object {
        fun reason(context: Context, code: String?): String = context.getString(when (code) {
            "dialogue_model_missing_or_invalid", "user_model_missing" -> R.string.mv_model_missing
            "model_compatibility_required" -> R.string.mv_compatibility_required
            "model_audio_check_required" -> R.string.mv_audio_check_required
            "model_audio_input_unavailable" -> R.string.mv_audio_unavailable
            "model_container_invalid" -> R.string.mv_invalid_container
            "parakeet_model_missing_or_incomplete" -> R.string.models_ui_parakeet_required
            "parakeet_model_sha256_mismatch" -> R.string.models_ui_parakeet_invalid
            "android_offline_stt_unavailable" -> R.string.models_ui_android_stt_unavailable
            "tts_offline_voice_probe_required" -> R.string.mv_loading_voices
            "polish_offline_voice_missing", "selected_voice_missing" -> R.string.mv_voice_missing
            else -> R.string.mv_compatibility_failed
        })
    }
}
