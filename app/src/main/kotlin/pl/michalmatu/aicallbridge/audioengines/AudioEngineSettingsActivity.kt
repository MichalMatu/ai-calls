package pl.michalmatu.aicallbridge.audioengines

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.R
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import pl.michalmatu.aicallbridge.ui.UiSelect
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/**
 * Separate configuration-only surface. Never grants a campaign, starts audio capture or places calls.
 * Other engines stay visible but disabled until real adapters and on-device evidence exist.
 */
class AudioEngineSettingsActivity : UiChromeActivity(AppTab.MODELS) {
    private lateinit var store: AudioEngineProfileStore
    private lateinit var feedback: TextView
    private lateinit var active: TextView
    private lateinit var voicePicker: Spinner
    private lateinit var inputGroup: RadioGroup
    private lateinit var outputGroup: RadioGroup
    private var tts: TextToSpeech? = null
    private var probedVoices: Set<String>? = null
    private var probedOutput: SpeechOutputId? = null
    private var voiceNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AudioEngineProfileStore(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@AudioEngineSettingsActivity, 20), UiDesign.dp(this@AudioEngineSettingsActivity, 20), UiDesign.dp(this@AudioEngineSettingsActivity, 20), UiDesign.dp(this@AudioEngineSettingsActivity, 28))
        }
        val current = store.load()
        fun label(value: String, large: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = if (large) 19f else 14f
                setTextColor(if (large) UiDesign.ink else UiDesign.secondary)
                setPadding(0, UiDesign.dp(this@AudioEngineSettingsActivity, 10),
                    0, UiDesign.dp(this@AudioEngineSettingsActivity, 10))
            }
        content.addView(UiDesign.heading(this, getString(R.string.eng_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            getString(R.string.eng_ui_intro)
        ), UiDesign.spaceAfter(this, 12))
        active = UiDesign.caption(this, "")
        content.addView(active, UiDesign.spaceAfter(this, 16))
        fun section(title: String, choices: List<AudioEngineChoice>, selected: String): RadioGroup {
            val card = UiDesign.card(this)
            card.addView(UiDesign.heading(this, title).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 8))
            val group = RadioGroup(this)
            val unavailable = mutableListOf<View>()
            for (choice in choices) {
                if (!choice.integrated && choice.id != selected) {
                    val info = UiDesign.caption(this, choice.title + " · " + localizedLanguageTag(choice.languageTag) +
                        " — " + localizedDetail(choice)).apply {
                        setTextColor(UiDesign.secondary)
                        visibility = View.GONE
                        minimumHeight = UiDesign.dp(this@AudioEngineSettingsActivity, 48)
                    }
                    unavailable.add(info)
                    group.addView(info)
                    continue
                }
                val button = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = choice.title + " · " + localizedLanguageTag(choice.languageTag) +
                        if (choice.integrated) "" else "\n" + getString(R.string.eng_ui_unavailable_detail, localizedDetail(choice))
                    isEnabled = choice.integrated
                    tag = choice.id
                    isChecked = choice.id == selected
                    minimumHeight = UiDesign.dp(this@AudioEngineSettingsActivity, 48)
                    setPadding(UiDesign.dp(this@AudioEngineSettingsActivity, 8),
                        UiDesign.dp(this@AudioEngineSettingsActivity, 8),
                        UiDesign.dp(this@AudioEngineSettingsActivity, 8),
                        UiDesign.dp(this@AudioEngineSettingsActivity, 8))
                    if (!choice.integrated && choice.id != selected) visibility = View.GONE
                }
                if (!choice.integrated) unavailable.add(button)
                group.addView(button)
            }
            card.addView(group)
            if (unavailable.isNotEmpty()) {
                val toggle = UiDesign.action(this,
                    getString(R.string.eng_ui_unavailable_count, unavailable.size)) {
                    val expanded = unavailable.any { it.visibility == View.GONE }
                    unavailable.forEach {
                        it.visibility = if (expanded) View.VISIBLE else View.GONE
                    }
                }.apply { tag = "engine-unavailable:" + title }
                card.addView(toggle, UiDesign.spaceAfter(this, 8))
            }
            content.addView(card, UiDesign.spaceAfter(this, 16))
            return group
        }
        inputGroup = section(getString(R.string.eng_ui_input), AudioEngineRegistry.inputs,
            current?.input?.name ?: "ANDROID_STT")
        content.addView(UiDesign.heading(this, getString(R.string.ux07_models_selected_heading)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 6))
        content.addView(UiDesign.caption(this,
            pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog[
                current?.dialogue ?: AudioEngineProfile.STABLE.dialogue].title
        ).apply { textSize = 15f; setTextColor(UiDesign.ink) }, UiDesign.spaceAfter(this, 8))
        content.addView(Button(this).apply {
            UiDesign.styleAction(this, UiActionTone.SECONDARY)
            text = getString(R.string.eng_ui_models)
            setOnClickListener {
                startActivity(android.content.Intent(this@AudioEngineSettingsActivity,
                    pl.michalmatu.aicallbridge.textagent.LanguageModelActivity::class.java))
            }
        })
        outputGroup = section(getString(R.string.eng_ui_output), AudioEngineRegistry.outputs,
            current?.output?.name ?: "ANDROID_TTS_PL")
        content.addView(label(getString(R.string.eng_ui_voice)))
        voicePicker = Spinner(this)
        content.addView(voicePicker, UiDesign.spaceAfter(this, 12))
        setVoices(emptyList(), current?.voiceId)
        feedback = label(getString(R.string.eng_ui_not_ready)).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(UiDesign.heading(this, getString(R.string.eng_ui_primary_heading)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        // Show readiness before Save, not underneath the action the user just tapped.
        content.addView(feedback, UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.eng_ui_save), UiActionTone.PRIMARY) {
                val requested = selectedProfile()
                val result = AudioEngineReadinessProbe.check(
                    this@AudioEngineSettingsActivity, requested,
                    probedVoices.takeIf { requested.output == probedOutput },
                )
                if (!result.available) {
                    feedback.text = getString(R.string.eng_ui_could_not_save, result.reason ?: "not_ready")
                } else {
                    feedback.text = if (store.save(requested)) {
                        getString(R.string.eng_ui_save_done)
                    } else getString(R.string.eng_ui_save_error)
                    refreshActive()
                }
        }, UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.eng_ui_reset)) {
                if (store.save(AudioEngineProfile.STABLE)) {
                    feedback.text = getString(R.string.eng_ui_restore_done)
                } else feedback.text = getString(R.string.eng_ui_save_error)
                refreshActive()
        }, UiDesign.spaceAfter(this, 12))

        content.addView(UiDesign.heading(this, getString(R.string.eng_ui_diagnostics_heading)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this, getString(R.string.eng_ui_diagnostics_caption)),
            UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.eng_ui_check)) {
            checkOffline()
        }, UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.eng_ui_preview)) {
            previewVoice()
        }, UiDesign.spaceAfter(this, 12))
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
        refreshActive()
    }

    private fun localizedLanguageTag(tag: String): String =
        if (tag == "brak pl-PL") getString(R.string.eng_ui_registry_missing_pl) else tag

    /** UI-only labels: engine registry remains the single owner of technical capabilities. */
    private fun localizedDetail(choice: AudioEngineChoice): String = when (choice.id) {
        "ANDROID_STT" -> getString(R.string.eng_ui_registry_android_stt)
        "PARAKEET_PL" -> getString(R.string.eng_ui_registry_parakeet)
        "GEMMA_AUDIO_DIRECT" -> getString(R.string.eng_ui_registry_direct)
        "ANDROID_TTS_PL" -> getString(R.string.eng_ui_registry_android_tts)
        "PIPER_PL" -> getString(R.string.eng_ui_registry_piper)
        "GEMMA_4_E2B" -> getString(R.string.models_ux05_detail_e2b)
        "GEMMA_4_E4B" -> getString(R.string.models_ux05_detail_e4b)
        "QWEN_3_1_7B" -> getString(R.string.models_ux05_detail_qwen17)
        "QWEN_3_0_6B" -> getString(R.string.models_ux05_detail_qwen06)
        "KOKORO", "POCKET_TTS" -> getString(R.string.eng_ui_registry_no_polish)
        else -> choice.detail
    }

    private fun selectedProfile(): AudioEngineProfile {
        fun selected(group: RadioGroup): String =
            (group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag as? String)
                ?: (0 until group.childCount).mapNotNull {
                    group.getChildAt(it) as? RadioButton
                }.first { it.isChecked }.tag as String
        return AudioEngineProfile(
            input = SpeechInputId.valueOf(selected(inputGroup)),
            dialogue = store.load()?.dialogue ?: AudioEngineProfile.STABLE.dialogue,
            output = SpeechOutputId.valueOf(selected(outputGroup)),
            voiceId = selectedVoice(),
        )
    }

    private fun selectedVoice(): String? =
        voiceNames.getOrNull(voicePicker.selectedItemPosition - 1)

    private fun setVoices(values: List<String>, selected: String?) {
        voiceNames = values
        val labels = listOf(getString(R.string.eng_ui_voice_auto)) + values
        UiSelect.setOptions(voicePicker, labels)
        voicePicker.setSelection((values.indexOf(selected) + 1).coerceAtLeast(0))
    }

    private fun checkOffline() {
        probedVoices = null
        probedOutput = null
        val selectedOutput = selectedProfile().output
        val selectedEngine = if (selectedOutput == SpeechOutputId.PIPER_PL)
            PiperServiceContract.PACKAGE else null
        tts?.shutdown()
        tts = null
        feedback.text = getString(R.string.eng_ui_checking)
        val holder = arrayOfNulls<TextToSpeech>(1)
        val initListener = TextToSpeech.OnInitListener { status ->
            runOnUiThread {
                val engine = holder[0] ?: return@runOnUiThread
                if (isFinishing || isDestroyed) {
                    engine.shutdown()
                    return@runOnUiThread
                }
                if (status != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    feedback.text = getString(R.string.eng_ui_tts_init_failed)
                    return@runOnUiThread
                }
                if (selectedEngine != null && (
                    engine.engines.none { it.name == selectedEngine } ||
                    engine.voices.orEmpty().none {
                        it.locale.language == "pl" && !it.isNetworkConnectionRequired &&
                            "piperVoice" in it.features
                    }
                )) {
                    engine.shutdown()
                    feedback.text = getString(R.string.eng_ui_piper_missing)
                    return@runOnUiThread
                }
                val voices = engine.voices.orEmpty()
                    .filter { it.locale.language == "pl" && !it.isNetworkConnectionRequired }
                    .filter { selectedEngine == null || "piperVoice" in it.features }
                    .sortedBy { it.name }
                val names = voices.map { it.name }
                tts = engine
                probedVoices = names.toSet()
                probedOutput = selectedOutput
                setVoices(names, store.load()?.voiceId)
                val result = AudioEngineReadinessProbe.check(
                    this@AudioEngineSettingsActivity,
                    selectedProfile(),
                    probedVoices.takeIf { selectedProfile().output == probedOutput },
                )
                feedback.text = if (result.available) {
                    getString(R.string.eng_ui_check_available, names.size)
                } else "OPEN: " + (result.reason ?: "unknown")
            }
        }
        holder[0] = if (selectedEngine == null)
            TextToSpeech(this, initListener)
        else TextToSpeech(this, initListener, selectedEngine)
    }

    private fun previewVoice() {
        // Preview is optional. A missing runtime permission must not crash the screen
        // or become an extra requirement to save the unchanged working configuration.
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            feedback.text = getString(R.string.eng_ui_phone_permission)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val inCall = runCatching { telecom?.isInCall ?: true }.getOrDefault(true)
        val mediaBusy = runCatching { audio?.mode == AudioManager.MODE_IN_CALL }
            .getOrDefault(true)
        if (inCall || mediaBusy) {
            feedback.text = getString(R.string.eng_ui_preview_disabled)
            return
        }
        val engine = tts ?: run {
            feedback.text = getString(R.string.eng_ui_voice_before)
            return
        }
        val name = selectedVoice()
        val local = engine.voices.orEmpty().filter {
            it.locale.language == "pl" && !it.isNetworkConnectionRequired
        }.sortedBy { it.name }
        val voice = if (name == null) local.firstOrNull() else local.firstOrNull { it.name == name }
        if (voice == null || engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            feedback.text = getString(R.string.eng_ui_voice_unavailable)
            return
        }
        engine.speak("To jest próbka lokalnego głosu AI Calls.", TextToSpeech.QUEUE_FLUSH, null,
            "aicalls-voice-preview")
    }

    override fun onResume() {
        super.onResume()
        refreshActive()
    }

    private fun refreshActive() {
        active.text = when (val snapshot = AudioEngineActiveProfilePresenter.present(store.load())) {
            AudioEngineActiveState.Invalid -> getString(R.string.eng_ui_invalid)
            is AudioEngineActiveState.Configured -> getString(R.string.eng_ui_summary,
                snapshot.inputTitle, snapshot.dialogueTitle, snapshot.outputTitle,
                snapshot.voiceId ?: getString(R.string.eng_ui_auto))
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
