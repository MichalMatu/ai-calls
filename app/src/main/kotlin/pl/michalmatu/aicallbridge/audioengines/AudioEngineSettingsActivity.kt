package pl.michalmatu.aicallbridge.audioengines

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
class AudioEngineSettingsActivity : Activity() {
    private lateinit var store: AudioEngineProfileStore
    private lateinit var feedback: TextView
    private lateinit var active: TextView
    private lateinit var voicePicker: Spinner
    private var tts: TextToSpeech? = null
    private var probedVoices: Set<String>? = null
    private var voiceNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AudioEngineProfileStore(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 36)
        }
        val current = store.load()
        fun label(value: String, large: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = if (large) 20f else 15f
                setPadding(0, 10, 0, 10)
            }
        content.addView(label("Ustawienia silników audio AI", true))
        content.addView(label("Preferencja urządzenia, nie autoryzacja rozmowy. " +
            "Zmiana nie przełącza trwającego połączenia."))
        fun section(title: String, choices: List<AudioEngineChoice>, selected: String): RadioGroup {
            content.addView(label(title, true))
            val group = RadioGroup(this)
            for (choice in choices) {
                val button = RadioButton(this).apply {
                    text = choice.title + " · " + choice.languageTag + "\n" +
                        (if (choice.integrated) "Zintegrowany — sprawdź offline" else
                            "NIEDOSTĘPNY — " + choice.detail)
                    isEnabled = choice.integrated
                    tag = choice.id
                    isChecked = choice.id == selected
                    setPadding(8, 6, 8, 8)
                }
                group.addView(button)
            }
            content.addView(group)
            return group
        }
        section("1. Wejście (RX / STT)", AudioEngineRegistry.inputs,
            current?.input?.name ?: "ANDROID_STT")
        section("2. Model rozmowy", AudioEngineRegistry.dialogues,
            current?.dialogue?.name ?: "GEMMA_4_E2B")
        section("3. Wyjście (TX / TTS)", AudioEngineRegistry.outputs,
            current?.output?.name ?: "ANDROID_TTS_PL")
        content.addView(label("Głos Android TTS offline (pl-PL)"))
        voicePicker = Spinner(this)
        content.addView(voicePicker)
        setVoices(emptyList(), current?.voiceId)
        content.addView(label("Inne silniki i głosy nie są aktywowane samym wyborem z katalogu."))
        feedback = label("Gotowość: niezweryfikowana. Naciśnij „Sprawdź offline”.")
        active = label("")
        content.addView(active)
        content.addView(feedback)
        content.addView(Button(this).apply {
            text = "Sprawdź offline (bez połączenia)"
            setOnClickListener { checkOffline() }
        })
        content.addView(Button(this).apply {
            text = "Odsłuch wybranego głosu (poza połączeniem)"
            setOnClickListener { previewVoice() }
        })
        content.addView(Button(this).apply {
            text = "Zapisz konfigurację"
            setOnClickListener {
                val requested = AudioEngineProfile(voiceId = selectedVoice())
                val result = AudioEngineReadinessProbe.check(
                    this@AudioEngineSettingsActivity, requested, probedVoices,
                )
                if (!result.available) {
                    feedback.text = "Nie zapisano: " + (result.reason ?: "not_ready")
                } else {
                    feedback.text = if (store.save(requested)) {
                        "Zapisano na następną autoryzowaną rozmowę. Nie zmieniono aktywnej tury."
                    } else "Nie udało się zapisać profilu."
                    refreshActive()
                }
            }
        })
        content.addView(Button(this).apply {
            text = "Przywróć stabilny wariant Android STT → Gemma E2B → Android TTS"
            setOnClickListener {
                if (store.save(AudioEngineProfile.STABLE)) {
                    feedback.text = "Przywrócono stabilny wariant dla następnej rozmowy."
                } else feedback.text = "Błąd zapisu preferencji."
                refreshActive()
            }
        })
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
        refreshActive()
    }

    private fun selectedVoice(): String? =
        voiceNames.getOrNull(voicePicker.selectedItemPosition - 1)

    private fun setVoices(values: List<String>, selected: String?) {
        voiceNames = values
        val labels = listOf("Automatyczny lokalny głos pl-PL") + values
        voicePicker.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, labels,
        )
        voicePicker.setSelection((values.indexOf(selected) + 1).coerceAtLeast(0))
    }

    private fun checkOffline() {
        probedVoices = null
        tts?.shutdown()
        tts = null
        feedback.text = "Sprawdzanie lokalnego Android TTS, STT i pliku Gemma E2B…"
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(this) { status ->
            runOnUiThread {
                val engine = holder[0] ?: return@runOnUiThread
                if (isFinishing || isDestroyed) {
                    engine.shutdown()
                    return@runOnUiThread
                }
                if (status != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    feedback.text = "Android TTS: nie można zainicjalizować silnika."
                    return@runOnUiThread
                }
                val voices = engine.voices.orEmpty()
                    .filter { it.locale.language == "pl" && !it.isNetworkConnectionRequired }
                    .sortedBy { it.name }
                val names = voices.map { it.name }
                tts = engine
                probedVoices = names.toSet()
                setVoices(names, store.load()?.voiceId)
                val result = AudioEngineReadinessProbe.check(
                    this@AudioEngineSettingsActivity,
                    AudioEngineProfile(voiceId = store.load()?.voiceId),
                    probedVoices,
                )
                feedback.text = if (result.available) {
                    "PASS lokalne: Android STT / Gemma E2B / głos pl-PL (" +
                        names.size + "). To nie jest test rozmowy."
                } else "OPEN: " + (result.reason ?: "unknown")
            }
        }
    }

    private fun previewVoice() {
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        if (telecom?.isInCall == true || audio?.mode == AudioManager.MODE_IN_CALL) {
            feedback.text = "Odsłuch wyłączony podczas połączenia."
            return
        }
        val engine = tts ?: run {
            feedback.text = "Najpierw sprawdź głosy offline."
            return
        }
        val name = selectedVoice()
        val local = engine.voices.orEmpty().filter {
            it.locale.language == "pl" && !it.isNetworkConnectionRequired
        }.sortedBy { it.name }
        val voice = if (name == null) local.firstOrNull() else local.firstOrNull { it.name == name }
        if (voice == null || engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            feedback.text = "Wybrany głos offline nie jest dostępny."
            return
        }
        engine.speak("To jest próbka lokalnego głosu AI Calls.", TextToSpeech.QUEUE_FLUSH, null,
            "aicalls-voice-preview")
    }

    private fun refreshActive() {
        val profile = store.load()
        active.text = if (profile == null) {
            "Zapisany profil jest nieznany lub niezgodny — użyj resetu."
        } else "Zapisana konfiguracja: " + profile.input.name + " → " +
            profile.dialogue.name + " → " + profile.output.name +
            " · głos: " + (profile.voiceId ?: "automatyczny")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
