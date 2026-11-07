package pl.michalmatu.aicallbridge.conversation

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.incoming.IncomingSecretaryScenario
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/**
 * Synthetic offline test only. Model tool decisions are observed and scored,
 * never executed; no Telecom calls, audio, permission changes or tool dispatch.
 */
class ConversationSkillLabActivity : UiChromeActivity(AppTab.LAB) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var model: TextCallAgentBackend? = null
    private var worker: Thread? = null
    private lateinit var status: TextView
    private lateinit var currentTurn: TextView
    private lateinit var scoreView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var timeline: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var follow: CheckBox
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var ratingGroup: LinearLayout
    private lateinit var naturalness: Spinner
    private var lastObservations: List<SkillLabObservation> = emptyList()
    private val allTurns = SkillLabCorpus.stories.sumOf { it.turns.size }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 16),
                UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 28))
        }
        content.addView(UiDesign.heading(this, getString(R.string.lab_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this, getString(R.string.lab_ui_intro)),
            UiDesign.spaceAfter(this, 16))

        val library = try { AndroidConversationPreparationStore(this).loadSkillLibrary() }
        catch (error: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.lab_ui_library_error, error.javaClass.simpleName)
            })
            return
        }
        val presets = library.presets
        val picker = Spinner(this).apply {
            tag = "lab-preset-picker"
            UiSelect.setOptions(this, presets.map { it.name })
            setSelection(presets.indexOfFirst { it.id == library.activePresetId }.coerceAtLeast(0))
        }
        content.addView(UiDesign.caption(this, getString(R.string.lab_ui_preset)),
            UiDesign.spaceAfter(this, 6))
        content.addView(picker, UiDesign.spaceAfter(this, 12))

        runButton = UiDesign.action(this, getString(R.string.lab_ui_run), UiActionTone.PRIMARY) {
            val selected = presets.getOrNull(picker.selectedItemPosition) ?: return@action
            runLab(selected.id)
        }.apply { tag = "lab-start" }
        content.addView(runButton, UiDesign.spaceAfter(this, 8))
        stopButton = UiDesign.action(this, getString(R.string.lab_ui_stop), UiActionTone.DANGER) {
            stopLab()
            status.text = getString(R.string.lab_ui_cancelled)
            currentTurn.text = getString(R.string.lab_ui_stopped_partial)
            stopButton.visibility = View.GONE
        }.apply { visibility = View.GONE; tag = "lab-stop" }
        content.addView(stopButton, UiDesign.spaceAfter(this, 12))

        val liveCard = UiDesign.card(this)
        liveCard.addView(UiDesign.heading(this, getString(R.string.lab_ui_live_title)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 6))
        status = UiDesign.caption(this, getString(R.string.lab_ui_ready)).apply {
            tag = "lab-status"
            textSize = 14f
            setTextColor(UiDesign.ink)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        liveCard.addView(status, UiDesign.spaceAfter(this, 8))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = allTurns
            progress = 0
            tag = "lab-live-progress"
            contentDescription = getString(R.string.lab_ui_progress, 0, allTurns)
        }
        liveCard.addView(progress, UiDesign.spaceAfter(this, 10))
        currentTurn = UiDesign.caption(this, getString(R.string.lab_ui_empty)).apply {
            tag = "lab-current-turn"
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
        }
        liveCard.addView(currentTurn)
        content.addView(liveCard, UiDesign.spaceAfter(this, 12))
        follow = CheckBox(this).apply {
            tag = "lab-follow"
            text = getString(R.string.lab_ui_follow)
            isChecked = true
            minimumHeight = UiDesign.dp(this@ConversationSkillLabActivity, 48)
        }
        content.addView(follow, UiDesign.spaceAfter(this, 10))

        scoreView = UiDesign.caption(this, "").apply {
            tag = "lab-score"
            textSize = 14f
            setTextColor(UiDesign.ink)
            visibility = View.GONE
            setTextIsSelectable(true)
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12))
            background = UiDesign.surfaceDrawable(this@ConversationSkillLabActivity)
        }
        content.addView(scoreView, UiDesign.spaceAfter(this, 12))

        ratingGroup = UiDesign.card(this).apply {
            tag = "lab-rating-group"
            visibility = View.GONE
            addView(UiDesign.heading(this@ConversationSkillLabActivity,
                getString(R.string.lab_ui_rating)).apply { textSize = 17f },
                UiDesign.spaceAfter(this@ConversationSkillLabActivity, 6))
            addView(UiDesign.caption(this@ConversationSkillLabActivity,
                getString(R.string.lab_ui_rating_help)),
                UiDesign.spaceAfter(this@ConversationSkillLabActivity, 8))
        }
        naturalness = Spinner(this).apply {
            UiSelect.setOptions(this, listOf(getString(R.string.lab_ui_unrated), "1", "2", "3", "4", "5"))
        }
        ratingGroup.addView(naturalness, UiDesign.spaceAfter(this, 10))
        ratingGroup.addView(UiDesign.action(this, getString(R.string.lab_ui_apply_rating)) {
            if (lastObservations.isNotEmpty()) showMetrics()
        })
        content.addView(ratingGroup, UiDesign.spaceAfter(this, 16))

        content.addView(UiDesign.heading(this, getString(R.string.lab_ui_timeline_title)).apply {
            textSize = 18f
        }, UiDesign.spaceAfter(this, 6))
        content.addView(UiDesign.caption(this, getString(R.string.lab_ui_timeline_hint)),
            UiDesign.spaceAfter(this, 10))
        timeline = LinearLayout(this).apply {
            tag = "lab-turn-timeline"
            orientation = LinearLayout.VERTICAL
        }
        content.addView(timeline)
        scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun runLab(presetId: String) {
        if (worker?.isAlive == true) return
        cancelled.set(false)
        lastObservations = emptyList()
        naturalness.setSelection(0)
        timeline.removeAllViews()
        scoreView.visibility = View.GONE
        ratingGroup.visibility = View.GONE
        runButton.isEnabled = false
        stopButton.visibility = android.view.View.VISIBLE
        progress.progress = 0
        progress.contentDescription = getString(R.string.lab_ui_progress, 0, allTurns)
        status.text = getString(R.string.lab_ui_starting)
        currentTurn.text = getString(R.string.lab_ui_waiting_initial)

        worker = Thread({
            val observed = mutableListOf<SkillLabObservation>()
            try {
                val context = applicationContext
                val profile = AudioEngineProfileStore(context).load()
                    ?: error(getString(R.string.lab_ui_missing_profile))
                val store = AndroidConversationPreparationStore(context)
                val library = store.loadSkillLibrary()
                val preparation = store.load(ConversationPreparation.INCOMING_ID)
                    .withSkillSnapshot(library.snapshot(presetId))
                val instruction = ConversationInstructionBuilder.build(
                    preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY)
                val provider = DialogueEngineProviders.resolve(profile)
                for ((storyIndex, story) in SkillLabCorpus.stories.withIndex()) {
                    if (cancelled.get()) break
                    val active = provider.createConversationReply(
                        context, instruction, setOf(ConversationToolId.END_CALL))
                    model = active
                    try {
                        for ((turnIndex, turn) in story.turns.withIndex()) {
                            if (cancelled.get()) break
                            val completedBefore = observed.size
                            runOnUiThread {
                                if (!cancelled.get() && !isDestroyed) {
                                    showTurnPending(storyIndex, story.title, turnIndex + 1,
                                        turn.utterance, completedBefore)
                                }
                            }
                            val started = System.nanoTime()
                            val latch = CountDownLatch(1)
                            val response = AtomicReference<String>()
                            val failure = AtomicReference<String>()
                            active.generate(turn.utterance, object : TextCallAgentBackend.Listener {
                                override fun onComplete(text: String) {
                                    response.set(text)
                                    latch.countDown()
                                }
                                override fun onError(reason: String) {
                                    failure.set(reason)
                                    latch.countDown()
                                }
                            })
                            if (!latch.await(120, TimeUnit.SECONDS)) error("model_response_timeout")
                            if (cancelled.get()) break
                            failure.get()?.let { error("model_error_" + it) }
                            // Decoding is protocol-aware; the UI shows model text and
                            // END_CALL as separate data, never a raw JSON/TOOL_REPLY dump.
                            val reply = ConversationReplyProtocol.decode(checkNotNull(response.get()))
                            val record = SkillLabObservation(story.title, turnIndex + 1,
                                turn.shouldEnd, reply.tool == ConversationToolId.END_CALL,
                                reply.text, (System.nanoTime() - started) / 1_000_000L,
                                turn.expectQuestion)
                            observed.add(record)
                            val completedNow = observed.size
                            runOnUiThread {
                                if (!cancelled.get() && !isDestroyed) {
                                    showTurnCompleted(storyIndex, record, completedNow)
                                }
                            }
                        }
                    } finally {
                        model = null
                        active.close()
                    }
                }
                runOnUiThread {
                    if (!isDestroyed && !cancelled.get()) {
                        lastObservations = observed.toList()
                        showMetrics()
                        ratingGroup.visibility = View.VISIBLE
                        status.text = getString(R.string.lab_ui_complete)
                        currentTurn.text = getString(R.string.lab_ui_progress,
                            observed.size, allTurns)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !cancelled.get()) {
                        status.text = getString(R.string.lab_ui_failed,
                            error.message ?: error.javaClass.simpleName)
                        currentTurn.text = getString(R.string.lab_ui_stopped_partial)
                    }
                }
            } finally {
                runOnUiThread {
                    if (!isDestroyed) {
                        runButton.isEnabled = true
                        stopButton.visibility = View.GONE
                    }
                }
            }
        }, "aicalls-skills-lab").apply { start() }
    }

    private fun showTurnPending(storyIndex: Int, story: String, index: Int,
                                utterance: String, completedBefore: Int) {
        status.text = getString(R.string.lab_ui_waiting, completedBefore + 1, allTurns)
        currentTurn.text = getString(R.string.lab_ui_working_on, story, index)
        val card = UiDesign.card(this, 12).apply { tag = "lab-turn:" + storyIndex + ":" + index }
        card.addView(UiDesign.heading(this, getString(R.string.lab_ui_turn_header,
            story, index)).apply { textSize = 15f }, UiDesign.spaceAfter(this, 8))
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_input)),
            UiDesign.spaceAfter(this, 4))
        card.addView(TextView(this).apply {
            text = utterance
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
        }, UiDesign.spaceAfter(this, 8))
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_waiting_response)).apply {
            tag = "lab-pending:" + storyIndex + ":" + index
        })
        timeline.addView(card, UiDesign.spaceAfter(this, 10))
        followCardIfEnabled(card)
    }

    private fun showTurnCompleted(storyIndex: Int, record: SkillLabObservation, completed: Int) {
        val card = timeline.findViewWithTag<LinearLayout>(
            "lab-turn:" + storyIndex + ":" + record.index) ?: return
        card.findViewWithTag<View>("lab-pending:" + storyIndex + ":" + record.index)?.let {
            card.removeView(it)
        }
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_output)),
            UiDesign.spaceAfter(this, 4))
        card.addView(TextView(this).apply {
            text = record.reply.trim().ifBlank { getString(R.string.lab_ui_empty_reply) }
            textSize = 15f
            setTextColor(UiDesign.ink)
            setLineSpacing(UiDesign.dp(this@ConversationSkillLabActivity, 3).toFloat(), 1f)
            setTextIsSelectable(true)
            tag = "lab-reply:" + storyIndex + ":" + record.index
        }, UiDesign.spaceAfter(this, 8))
        val matched = record.expectedEnd == record.actualEnd
        card.addView(TextView(this).apply {
            text = getString(R.string.lab_ui_decision,
                getString(if (record.expectedEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no),
                getString(if (record.actualEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no),
                record.elapsedMs) + " · " +
                getString(if (matched) R.string.lab_ui_match else R.string.lab_ui_mismatch)
            textSize = 12f
            setTextColor(if (matched) UiDesign.secondary else UiDesign.danger)
            tag = "lab-decision:" + storyIndex + ":" + record.index
        })
        progress.progress = completed
        progress.contentDescription = getString(R.string.lab_ui_progress, completed, allTurns)
        status.text = getString(R.string.lab_ui_progress, completed, allTurns)
        currentTurn.text = getString(R.string.lab_ui_last_result,
            record.story, record.index, record.elapsedMs)
        followCardIfEnabled(card)
    }

    private fun followCardIfEnabled(card: View) {
        if (follow.isChecked) scroll.post {
            if (!isDestroyed && follow.isChecked) {
                scroll.smoothScrollTo(0, card.bottom + timeline.top)
            }
        }
    }

    private fun showMetrics() {
        val results = lastObservations
        val metrics = SkillLabScoring.score(results)
        val naturalnessScore = naturalness.selectedItemPosition.takeIf { it > 0 }
            ?.let { getString(R.string.lab_ui_naturalness_score, it) }
            ?: getString(R.string.lab_ui_naturalness_unrated)
        scoreView.text = getString(R.string.lab_ui_score_title) + "\n" +
            getString(R.string.lab_ui_score_decisions,
                metrics.accurateEndDecisions, metrics.turns) + "\n" +
            getString(R.string.lab_ui_score_errors,
                metrics.prematureEnds, metrics.missingEnds, metrics.unexpectedQuestions) + "\n" +
            getString(R.string.lab_ui_score_latency, metrics.meanLatencyMs) + "\n" +
            naturalnessScore
        scoreView.visibility = View.VISIBLE
    }

    private fun stopLab() {
        cancelled.set(true)
        worker?.interrupt()
        model?.cancel()
    }

    override fun onStop() {
        stopLab()
        super.onStop()
    }
}
