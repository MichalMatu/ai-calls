package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiSelect
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
    private lateinit var stopButton: Button
    private lateinit var ratingGroup: LinearLayout
    private lateinit var naturalness: Spinner
    private var lastObservations: List<SkillLabObservation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20), UiDesign.dp(this@ConversationSkillLabActivity, 20), UiDesign.dp(this@ConversationSkillLabActivity, 20), UiDesign.dp(this@ConversationSkillLabActivity, 28))
        }
        content.addView(UiDesign.heading(this, "Laboratorium skillsów").apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            "Wieloturowy test lokalnego modelu. Bez połączeń i wykonywania narzędzi AI."
        ), UiDesign.spaceAfter(this, 20))
        val library = try { AndroidConversationPreparationStore(this).loadSkillLibrary() }
        catch (error: Exception) {
            setContentView(TextView(this).apply { text = "Błąd biblioteki: " + error.javaClass.simpleName })
            return
        }
        val presets = library.presets
        val picker = Spinner(this).apply {
            UiSelect.setOptions(this, presets.map { it.name })
            setSelection(presets.indexOfFirst { it.id == library.activePresetId })
        }
        content.addView(UiDesign.caption(this, "Preset"), UiDesign.spaceAfter(this, 8))
        content.addView(picker, UiDesign.spaceAfter(this, 16))
        runButton = UiDesign.action(this, "Uruchom test offline", UiActionTone.PRIMARY) {
            runLab(presets[picker.selectedItemPosition].id)
        }
        content.addView(runButton, UiDesign.spaceAfter(this, 12))
        stopButton = UiDesign.action(this, "Zatrzymaj test", UiActionTone.DANGER) {
            stopLab()
            status.text = "Anulowano test. Nie uruchomiono telefonu."
            stopButton.visibility = android.view.View.GONE
        }.apply { visibility = android.view.View.GONE; tag = "lab-stop" }
        content.addView(stopButton, UiDesign.spaceAfter(this, 12))
        status = UiDesign.caption(this, "Gotowe. Model wybrano w ustawieniach silników.").apply {
            tag = "lab-status"
        }
        content.addView(status, UiDesign.spaceAfter(this, 16))
        output = TextView(this).apply {
            setTextColor(UiDesign.ink)
            textSize = 14f
        }
        ratingGroup = UiDesign.card(this).apply {
            tag = "lab-rating-group"
            visibility = android.view.View.GONE
            addView(UiDesign.heading(this@ConversationSkillLabActivity, "Ocena naturalności").apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this@ConversationSkillLabActivity, 8))
            addView(UiDesign.caption(this@ConversationSkillLabActivity,
                "Ręczna ocena: 1 — nienaturalna, 5 — bardzo naturalna."
            ), UiDesign.spaceAfter(this@ConversationSkillLabActivity, 8))
        }
        naturalness = Spinner(this).apply {
            UiSelect.setOptions(this, listOf("Bez oceny", "1", "2", "3", "4", "5"))
        }
        ratingGroup.addView(naturalness, UiDesign.spaceAfter(this, 12))
        ratingGroup.addView(UiDesign.action(this, "Zastosuj ocenę") {
            if (lastObservations.isNotEmpty()) showMetrics()
        })
        content.addView(ratingGroup, UiDesign.spaceAfter(this, 16))
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
        stopButton.visibility = android.view.View.VISIBLE
        ratingGroup.visibility = android.view.View.GONE
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
                        ratingGroup.visibility = android.view.View.VISIBLE
                        status.text = "Test zakończony. Żadnego END_CALL nie wykonano."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !cancelled.get())
                        status.text = "Test przerwany: " + (error.message ?: error.javaClass.simpleName)
                }
            } finally {
                runOnUiThread {
                    if (!isDestroyed) {
                        runButton.isEnabled = true
                        stopButton.visibility = android.view.View.GONE
                    }
                }
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
