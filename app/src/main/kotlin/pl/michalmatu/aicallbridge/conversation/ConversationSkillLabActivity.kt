package pl.michalmatu.aicallbridge.conversation

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import pl.michalmatu.aicallbridge.R
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
    private lateinit var runner: SkillLabRunner
    private var lastState = SkillLabUiState()
    private var renderedTurnCount = 0
    private val visiblePending = mutableSetOf<Pair<Int, Int>>()
    private var stopBannerShown = false
    private lateinit var status: TextView
    private lateinit var currentTurn: TextView
    private lateinit var scoreView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var timeline: LinearLayout
    private lateinit var scroll: ScrollView
    private var userReadingOlderTurns = false
    private lateinit var modePicker: Spinner
    private var currentTurnTotal = 2
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var ratingGroup: LinearLayout
    private lateinit var naturalness: Spinner

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
        content.addView(UiDesign.caption(this, getString(R.string.lab_ui_mode_label)),
            UiDesign.spaceAfter(this, 6))
        modePicker = Spinner(this).apply {
            tag = "lab-scope-picker"
            UiSelect.setOptions(this, listOf(
                getString(R.string.lab_ui_mode_quick),
                getString(R.string.lab_ui_mode_full)))
            setSelection(0)
        }
        content.addView(modePicker, UiDesign.spaceAfter(this, 12))

        runButton = UiDesign.action(this, getString(R.string.lab_ui_run), UiActionTone.PRIMARY) {
            val selected = presets.getOrNull(picker.selectedItemPosition) ?: return@action
            runLab(selected.id, quick = modePicker.selectedItemPosition == 0)
        }.apply { tag = "lab-start" }
        content.addView(runButton, UiDesign.spaceAfter(this, 8))
        stopButton = UiDesign.action(this, getString(R.string.lab_ui_stop), UiActionTone.DANGER) {
            stopLab()
        }.apply { visibility = View.GONE; tag = "lab-stop" }
        content.addView(stopButton, UiDesign.spaceAfter(this, 12))

        val transcriptCard = UiDesign.card(this).apply {
            tag = "lab-conversation-card"
        }
        transcriptCard.addView(UiDesign.heading(this, getString(R.string.lab_ui_timeline_title)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        status = UiDesign.caption(this, getString(R.string.lab_ui_ready)).apply {
            tag = "lab-status"
            textSize = 14f
            setTextColor(UiDesign.ink)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        transcriptCard.addView(status, UiDesign.spaceAfter(this, 8))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = currentTurnTotal
            progress = 0
            tag = "lab-live-progress"
            contentDescription = getString(R.string.lab_ui_progress, 0, currentTurnTotal)
        }
        transcriptCard.addView(progress, UiDesign.spaceAfter(this, 10))
        currentTurn = UiDesign.caption(this, getString(R.string.lab_ui_empty)).apply {
            tag = "lab-current-turn"
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
        }
        transcriptCard.addView(currentTurn, UiDesign.spaceAfter(this, 8))

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
            lastState.metrics?.let(::showMetrics)
        })
        timeline = LinearLayout(this).apply {
            tag = "lab-turn-timeline"
            orientation = LinearLayout.VERTICAL
            minimumHeight = UiDesign.dp(this@ConversationSkillLabActivity, 140)
        }
        transcriptCard.addView(timeline)
        content.addView(transcriptCard, UiDesign.spaceAfter(this, 12))
        content.addView(scoreView, UiDesign.spaceAfter(this, 12))
        content.addView(ratingGroup, UiDesign.spaceAfter(this, 16))
        scroll = ScrollView(this).apply {
            addView(content)
            setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    userReadingOlderTurns = true
                } else if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                    event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                    postDelayed({
                        val body = getChildAt(0)
                        val remaining = ((body?.height ?: 0) - height - scrollY).coerceAtLeast(0)
                        userReadingOlderTurns = remaining > UiDesign.dp(this@ConversationSkillLabActivity, 48)
                    }, 120L)
                }
                false
            }
        }
        setContentView(scroll)
        runner = SkillLabRunner(AndroidSkillLabSessionFactory(applicationContext),
            dispatchToUi = { action -> runOnUiThread(action) }, onState = ::renderState)
        renderState(runner.state)
    }

    private fun runLab(presetId: String, quick: Boolean) {
        if (::runner.isInitialized) runner.start(presetId, quick)
    }

    private fun renderState(next: SkillLabUiState) {
        if (isDestroyed) return
        lastState = next
        currentTurnTotal = next.totalTurns
        if (next.phase == SkillLabPhase.STARTING) {
            renderedTurnCount = 0
            userReadingOlderTurns = false
            visiblePending.clear()
            stopBannerShown = false
            timeline.removeAllViews()
            naturalness.setSelection(0)
            scoreView.visibility = View.GONE
            ratingGroup.visibility = View.GONE
        }

        // Snapshots contain both the input and output: skipped UI posts can be
        // reconstructed without losing the last turn or showing a stale response.
        next.completed.drop(renderedTurnCount).forEach { item ->
            val pending = item.pending
            val key = pending.storyIndex to pending.turnIndex
            if (visiblePending.add(key)) showTurnPending(
                pending.storyIndex, pending.storyTitle, pending.turnIndex,
                pending.utterance, renderedTurnCount)
            showTurnCompleted(pending.storyIndex, item.observation, renderedTurnCount + 1)
            renderedTurnCount++
        }
        next.pending?.let { pending ->
            val key = pending.storyIndex to pending.turnIndex
            if (visiblePending.add(key)) showTurnPending(
                pending.storyIndex, pending.storyTitle, pending.turnIndex,
                pending.utterance, next.completed.size)
        }
        progress.max = next.totalTurns
        progress.progress = next.completed.size
        progress.contentDescription = getString(R.string.lab_ui_progress,
            next.completed.size, next.totalTurns)
        runButton.isEnabled = next.canStart
        runButton.visibility = if (next.canStart) View.VISIBLE else View.GONE
        stopButton.visibility = if (!next.canStart && next.phase in
            setOf(SkillLabPhase.STARTING, SkillLabPhase.WAITING,
                SkillLabPhase.TURN_COMPLETED)) View.VISIBLE else View.GONE

        currentTurn.visibility = if (next.phase == SkillLabPhase.READY) View.GONE
            else View.VISIBLE
        when (next.phase) {
            SkillLabPhase.READY -> {
                status.text = getString(R.string.lab_ui_ready)
                currentTurn.text = getString(R.string.lab_ui_empty)
            }
            SkillLabPhase.STARTING -> {
                status.text = getString(R.string.lab_ui_starting)
                currentTurn.text = getString(R.string.lab_ui_waiting_initial)
            }
            SkillLabPhase.WAITING -> next.pending?.let { pending ->
                status.text = getString(R.string.lab_ui_waiting,
                    next.completed.size + 1, next.totalTurns)
                currentTurn.text = getString(R.string.lab_ui_working_on,
                    pending.storyTitle, pending.turnIndex)
            }
            SkillLabPhase.TURN_COMPLETED -> next.completed.lastOrNull()?.let { item ->
                status.text = getString(R.string.lab_ui_progress,
                    next.completed.size, next.totalTurns)
                currentTurn.text = getString(R.string.lab_ui_last_result,
                    item.observation.story, item.observation.index, item.observation.elapsedMs)
            }
            SkillLabPhase.COMPLETED -> {
                next.metrics?.let(::showMetrics)
                ratingGroup.visibility = View.VISIBLE
                status.text = getString(R.string.lab_ui_complete)
                currentTurn.text = getString(R.string.lab_ui_progress,
                    next.completed.size, next.totalTurns)
                followLatestCardIfEnabled()
            }
            SkillLabPhase.STOPPED -> {
                status.text = getString(R.string.lab_ui_cancelled)
                currentTurn.text = getString(R.string.lab_ui_stopped_partial)
                if (!stopBannerShown) {
                    showStopNotice()
                    stopBannerShown = true
                }
            }
            SkillLabPhase.FAILED -> renderFailure(next.error ?: "unknown_failure")
        }
    }

    /** Explicitly distinct error state, shared by model/timeout failures and UI tests. */
    private fun renderFailure(reason: String) {
        status.text = getString(R.string.lab_ui_failed, reason)
        currentTurn.text = getString(R.string.lab_ui_stopped_partial)
        followLatestCardIfEnabled()
    }

    private fun showStopNotice() {
        val note = UiDesign.caption(this, getString(R.string.lab_ui_stop_banner,
            progress.progress, currentTurnTotal)).apply {
            tag = "lab-stopped-banner"
            textSize = 15f
            setTextColor(UiDesign.ink)
            background = UiDesign.surfaceDrawable(this@ConversationSkillLabActivity)
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12))
        }
        timeline.addView(note, UiDesign.spaceAfter(this, 10))
        followCardIfEnabled(note)
    }

    private fun followLatestCardIfEnabled() {
        val last = timeline.getChildAt(timeline.childCount - 1) ?: return
        followCardIfEnabled(last)
    }

    private fun showTurnPending(storyIndex: Int, story: String, index: Int,
                                utterance: String, completedBefore: Int) {
        status.text = getString(R.string.lab_ui_waiting, completedBefore + 1, currentTurnTotal)
        currentTurn.text = getString(R.string.lab_ui_working_on, story, index)
        if (index == 1) {
            timeline.addView(UiDesign.heading(this, story).apply {
                textSize = 17f
                tag = "lab-story:" + storyIndex
            }, UiDesign.spaceAfter(this, 8))
        }
        val card = UiDesign.card(this, 12).apply { tag = "lab-turn:" + storyIndex + ":" + index }
        card.addView(UiDesign.heading(this, getString(R.string.lab_ui_turn_short,
            index)).apply { textSize = 15f }, UiDesign.spaceAfter(this, 8))
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
        progress.contentDescription = getString(R.string.lab_ui_progress, completed, currentTurnTotal)
        status.text = getString(R.string.lab_ui_progress, completed, currentTurnTotal)
        currentTurn.text = getString(R.string.lab_ui_last_result,
            record.story, record.index, record.elapsedMs)
        followCardIfEnabled(card)
    }

    private fun followCardIfEnabled(card: View) {
        if (userReadingOlderTurns) return
        // Follow latest only while the owner has not scrolled up to read older turns.
        scroll.post {
            if (isDestroyed || userReadingOlderTurns || !card.isAttachedToWindow) return@post
            scroll.post {
                if (isDestroyed || userReadingOlderTurns || !card.isAttachedToWindow) return@post
                val body = scroll.getChildAt(0) ?: return@post
                val top = card.top + timeline.top + (timeline.parent as View).top
                val target = (top + card.height - scroll.height + UiDesign.dp(this, 30))
                    .coerceAtLeast(0)
                val bottom = (body.height - scroll.height).coerceAtLeast(0)
                scroll.smoothScrollTo(0, target.coerceAtMost(bottom))
            }
        }
    }

    private fun showMetrics(metrics: SkillLabMetrics) {
        val naturalnessScore = naturalness.selectedItemPosition.takeIf { it > 0 }
            ?.let { getString(R.string.lab_ui_naturalness_score, it) }
            ?: getString(R.string.lab_ui_naturalness_unrated)
        scoreView.text = getString(R.string.lab_ui_score_title) + "\n" +
            getString(R.string.lab_ui_score_decisions,
                metrics.accurateEndDecisions, metrics.turns) + "\n" +
            getString(R.string.lab_ui_score_errors,
                metrics.prematureEnds, metrics.missingEnds, metrics.unexpectedQuestions) + "\n" +
            getString(R.string.lab_ui_score_latency, metrics.meanLatencyMs) + "\n" +
            naturalnessScore + "\n\n" + getString(R.string.lab_ui_quality_not_scored)
        scoreView.visibility = View.VISIBLE
    }

    private fun stopLab() {
        if (::runner.isInitialized) runner.stop()
    }

    override fun onStop() {
        stopLab()
        super.onStop()
    }

    override fun onDestroy() {
        if (::runner.isInitialized) runner.close()
        super.onDestroy()
    }
}
