package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.incoming.IncomingSecretaryScenario
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Runs the selected on-device model on reproducible multi-turn text stories.
 * This activity never owns a Telecom Call, captures audio, speaks, or executes tools.
 * A model tool value is only observed, never dispatched.
 */
class ConversationSkillLabActivity : UiChromeActivity(AppTab.LAB) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var model: TextCallAgentBackend? = null
    private var worker: Thread? = null
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private lateinit var naturalness: Spinner
    private var lastObservations: List<SkillLabObservation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 28), UiDesign.dp(this@ConversationSkillLabActivity, 28), UiDesign.dp(this@ConversationSkillLabActivity, 28), UiDesign.dp(this@ConversationSkillLabActivity, 36))
        }
        content.addView(TextView(this).apply { text = "Laboratorium skillsów"; textSize = 23f })
        content.addView(TextView(this).apply {
            text = "Wieloturowe scenariusze syntetyczne na wybranym lokalnym modelu. " +
                "Brak połączeń telefonicznych, STT, TTS i wykonania END_CALL. " +
                "Porównuj te same scenariusze między presetami. Wyniki są tylko w pamięci tego ekranu."
        })
        val library = try { AndroidConversationPreparationStore(this).loadSkillLibrary() }
        catch (error: Exception) {
            setContentView(TextView(this).apply { text = "Błąd biblioteki: " + error.javaClass.simpleName })
            return
        }
        val presets = library.presets
        val picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationSkillLabActivity,
                android.R.layout.simple_spinner_dropdown_item, presets.map { it.name })
            setSelection(presets.indexOfFirst { it.id == library.activePresetId })
        }
        content.addView(picker)
        runButton = Button(this).apply {
            text = "Uruchom testy wieloturowe bez połączenia"
            setOnClickListener { runLab(presets[picker.selectedItemPosition].id) }
        }
        content.addView(runButton)
        content.addView(Button(this).apply {
            text = "Zatrzymaj test"
            setOnClickListener { stopLab(); status.text = "Anulowano test. Nie uruchomiono telefonu." }
        })
        status = TextView(this).apply { text = "Gotowe. Model wybierany jest w ustawieniach silników." }
        output = TextView(this)
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "Ocena naturalności — ręczna (1: nienaturalna, 5: bardzo naturalna)."
        })
        naturalness = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationSkillLabActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Bez oceny", "1", "2", "3", "4", "5"))
        }
        content.addView(naturalness)
        content.addView(Button(this).apply {
            text = "Zastosuj ocenę naturalności"
            setOnClickListener { if (lastObservations.isNotEmpty()) showMetrics() }
        })
        content.addView(output)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun runLab(presetId: String) {
        if (worker?.isAlive == true) return
        cancelled.set(false)
        lastObservations = emptyList()
        naturalness.setSelection(0)
        output.text = ""
        runButton.isEnabled = false
        status.text = "Uruchamianie modelu offline…"
        worker = Thread({
            val observed = mutableListOf<SkillLabObservation>()
            try {
                val context = applicationContext
                val profile = AudioEngineProfileStore(context).load()
                    ?: error("Brak wybranego profilu modelu w ustawieniach silników")
                val store = AndroidConversationPreparationStore(context)
                val library = store.loadSkillLibrary()
                val preparation = store.load(ConversationPreparation.INCOMING_ID)
                    .withSkillSnapshot(library.snapshot(presetId))
                val instruction = ConversationInstructionBuilder.build(
                    preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY)
                val provider = DialogueEngineProviders.resolve(profile)
                for (story in SkillLabCorpus.stories) {
                    if (cancelled.get()) break
                    val active = provider.createConversationReply(context, instruction, setOf(ConversationToolId.END_CALL))
                    model = active
                    try {
                        for ((index, turn) in story.turns.withIndex()) {
                            if (cancelled.get()) break
                            val started = System.nanoTime()
                            val latch = CountDownLatch(1)
                            val response = AtomicReference<String>()
                            val failure = AtomicReference<String>()
                            active.generate(turn.utterance, object : TextCallAgentBackend.Listener {
                                override fun onComplete(text: String) { response.set(text); latch.countDown() }
                                override fun onError(reason: String) { failure.set(reason); latch.countDown() }
                            })
                            if (!latch.await(120, TimeUnit.SECONDS)) error("model_response_timeout")
                            if (cancelled.get()) break
                            failure.get()?.let { error("model_error_" + it) }
                            val reply = ConversationReplyProtocol.decode(checkNotNull(response.get()))
                            val record = SkillLabObservation(story.title, index + 1, turn.shouldEnd,
                                reply.tool == ConversationToolId.END_CALL, reply.text,
                                (System.nanoTime() - started) / 1_000_000L, turn.expectQuestion)
                            observed.add(record)
                            runOnUiThread {
                                if (!cancelled.get() && !isDestroyed)
                                    status.text = "Sprawdzono " + observed.size + " tur…"
                            }
                        }
                    } finally { model = null; active.close() }
                }
                runOnUiThread {
                    if (!isDestroyed && !cancelled.get()) {
                        lastObservations = observed.toList()
                        showMetrics()
                        status.text = "Test zakończony. Żadnego END_CALL nie wykonano."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !cancelled.get())
                        status.text = "Test przerwany: " + (error.message ?: error.javaClass.simpleName)
                }
            } finally {
                runOnUiThread { if (!isDestroyed) runButton.isEnabled = true }
            }
        }, "aicalls-skills-lab").apply { start() }
    }

    private fun showMetrics() {
        val results = lastObservations
        val rating = naturalness.selectedItemPosition.takeIf { it > 0 }
        val lines = results.joinToString("\n\n") {
            it.story + " / tura " + it.index +
                " — oczekiwano END_CALL=" + it.expectedEnd + ", model=" + it.actualEnd +
                ", " + it.elapsedMs + " ms\nOdpowiedź: " + it.reply
        }
        output.text = SkillLabScoring.score(results).describe(rating) + "\n\n" + lines
    }

    private fun stopLab() {
        cancelled.set(true)
        worker?.interrupt()
        model?.cancel()
    }

    override fun onStop() { stopLab(); super.onStop() }
}
