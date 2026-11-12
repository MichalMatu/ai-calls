package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.os.PowerManager
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.tools.ConversationToolSettingsStore
import rikka.shizuku.Shizuku
import java.util.UUID

/** Queue adapter owned by AppOwnedCampaignService. Uses its existing executor and audio runtime. */
internal class PhoneTaskExecution(
    private val service: AppOwnedCampaignService,
    private val finished: () -> Unit,
) {
    var busy = false
        private set
    private val epoch = PhoneTaskWork.generation.get()
    @Volatile private var claim: PhoneTaskQueue.Claim? = null
    private var completing = false
    private var typedOutcome: PhoneAttemptOutcome? = null
    private var warmupLock: PowerManager.WakeLock? = null
    private var windowExpiry: Runnable? = null
    private fun valid() = epoch == PhoneTaskWork.generation.get()
    private fun current(attemptId: String) = valid() && claim?.attemptId == attemptId

    fun runNext() {
        if (busy || !valid()) return
        busy = true
        PhoneTaskWork.io.execute {
            try {
                PhoneTaskWork.recover(service)
                val tasks = PhoneTaskWork.store(service).updatePhoneTasks { list -> list.map {
                    PhoneTaskQueue.settle(it, System.currentTimeMillis())
                } }
                PhoneTaskWork.main.post { if (valid()) prepareClaim(tasks) else close() }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun prepareClaim(tasks: List<PhoneTask>) {
        val now = System.currentTimeMillis()
        if (tasks.none { PhoneTaskQueue.nextAt(it)?.let { due -> due <= now } == true }) {
            finishAndSchedule()
            return
        }
        val missing = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO).any {
            service.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) { pauseDue(PhoneTaskIssue.PERMISSIONS); return }
        if (!AndroidTelecomOwnedCallController.canControl(service)) {
            pauseDue(PhoneTaskIssue.DIALER); return
        }
        val telecom = service.getSystemService(TelecomManager::class.java)
        if (telecom == null) { pauseDue(PhoneTaskIssue.PERMISSIONS); return }
        if (runCatching { telecom.isInCall }.getOrDefault(true) ||
            AppOwnedClirCampaignExecutor.isRunning() || CampaignContinuationStore(service).load() != null) {
            // Another person's Call is never touched. A fresh alarm rechecks availability.
            finishAndSchedule(now + 30_000L, PhoneTaskIssue.PHONE_BUSY)
            return
        }
        PhoneTaskWork.io.execute {
            try {
                if (!valid()) { PhoneTaskWork.main.post { close() }; return@execute }
                var selected: PhoneTaskQueue.Claim? = null
                PhoneTaskWork.store(service).updatePhoneTasks { list ->
                    selected = PhoneTaskQueue.claim(list, System.currentTimeMillis(), UUID.randomUUID().toString())
                    list.map { if (it.id == selected?.task?.id) requireNotNull(selected).task else it }
                }
                val next = selected
                PhoneTaskWork.main.post {
                    if (!valid()) { close(); return@post }
                    if (next == null) { finishAndSchedule(); return@post }
                    claim = next
                    completing = false
                    typedOutcome = null
                    PhoneTaskWork.activeTaskId = next.task.id
                    PhoneTaskWork.activeAttemptId = next.attemptId
                    prepare(next)
                }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun prepare(next: PhoneTaskQueue.Claim) {
        val task = next.task
        val remaining = (task.endAt - System.currentTimeMillis()).coerceAtLeast(1L)
        warmupLock = service.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AI Calls:task-preparation")?.apply {
                setReferenceCounted(false)
                acquire(remaining)
            }
        windowExpiry = Runnable {
            if (current(next.attemptId) && !completing) complete(PhoneAttemptOutcome.BLOCKED)
        }.also { PhoneTaskWork.main.postDelayed(it, remaining) }
        if (CampaignAccountScope.forSubscription(service, task.subscriptionId) != task.accountScope) {
            complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.SIM_CHANGED); return
        }
        val profile = AudioEngineProfileStore(service).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.MODEL); return
        }
        if (!runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.SHIZUKU); return
        }
        val outgoing = task.scenarioTask(task.recipients.first { it.id == next.recipientId }, next.attemptId)
        PhoneTaskWork.io.execute {
            try {
                val store = PhoneTaskWork.store(service)
                val context = store.load(ConversationPreparation.OUTGOING_ID)
                // Fresh context per recipient; no other recipient's answer is ever included.
                val preparation = ConversationPreparation("outgoing-scenario-" + task.scenarioId,
                    task.title, ConversationDirection.OUTGOING, context.notes, context.skills,
                    context.scopedDataFields(emptySet()))
                val goal = PhoneTaskInstruction.forTask(task)
                val instruction = ConversationInstructionBuilder.build(preparation, goal,
                    ConversationResponseFormat.TOOL_REPLY)
                val configuration = ConversationRunConfiguration.ModelDirect(profile, instruction, preparation)
                PhoneTaskWork.main.post {
                    if (!current(next.attemptId) || completing) return@post
                    CampaignGemmaDirectPreflight.prepare(service, instruction, profile,
                        ConversationToolSettingsStore(service).enabled()) { ready ->
                        PhoneTaskWork.main.post {
                            if (!current(next.attemptId) || completing) return@post
                            if (!ready) complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.MODEL)
                            else reserveAndDial(next, outgoing, configuration)
                        }
                    }
                }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun reserveAndDial(next: PhoneTaskQueue.Claim, outgoing: ScenarioOutgoingTask,
        configuration: ConversationRunConfiguration.ModelDirect) {
        PhoneTaskWork.io.execute {
            try {
                if (!current(next.attemptId) || completing) return@execute
                val store = PhoneTaskWork.store(service)
                val current = store.phoneTasks().first { it.id == next.task.id }
                if (current.state != PhoneTaskState.READY || System.currentTimeMillis() >= current.endAt) {
                    PhoneTaskWork.main.post { complete(PhoneAttemptOutcome.BLOCKED) }; return@execute
                }
                store.updatePhoneTasks { list -> list.map {
                    if (it.id == next.task.id) PhoneTaskQueue.progress(it, next.attemptId,
                        PhoneAttemptStage.DIALING) else it
                } }
                PhoneTaskWork.main.post {
                    if (!current(next.attemptId) || completing) return@post
                    if (System.currentTimeMillis() >= current.endAt) {
                        complete(PhoneAttemptOutcome.BLOCKED); return@post
                    }
                    if (service.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.PERMISSIONS); return@post
                    }
                    val telecom = service.getSystemService(TelecomManager::class.java)
                    if (telecom == null || runCatching { telecom.isInCall }.getOrDefault(true)) {
                        complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.PHONE_BUSY); return@post
                    }
                    if (CampaignAccountScope.forSubscription(service, current.subscriptionId) != current.accountScope) {
                        complete(PhoneAttemptOutcome.BLOCKED, PhoneTaskIssue.SIM_CHANGED); return@post
                    }
                    val grants = CampaignAuthorizationStore(service)
                    try {
                        // The durable claim precedes the grant and the irreversible Telecom edge.
                        // Keep matchesExactTask: a campaign never becomes a multi-target call grant.
                        check(CampaignAuthorizationPolicy.matchesExactTask(outgoing.grant(System.currentTimeMillis()), outgoing.request()))
                        grants.save(outgoing.grant(System.currentTimeMillis()))
                        windowExpiry?.let(PhoneTaskWork.main::removeCallbacks)
                        windowExpiry = null
                        AppOwnedClirCampaignExecutor.start(service, grants, outgoing.accountScope,
                            configuration, scenarioTask = outgoing,
                            taskAuthorized = { current(next.attemptId) },
                            taskDialAuthorized = { current(next.attemptId) && System.currentTimeMillis() < current.endAt },
                            taskObserver = object : PhoneTaskCallObserver {
                                override fun sessionStarted(sessionId: String) {
                                    releaseWarmup()
                                    progress(next.attemptId, sessionId, null)
                                }
                                override fun transcript(sessionId: String, turn: Int, text: String) {
                                    progress(next.attemptId, sessionId, PhoneTaskAnswer(turn, text))
                                }
                                override fun unanswered(outcome: PhoneAttemptOutcome) {
                                    if (current(next.attemptId)) typedOutcome = outcome
                                }
                            },
                            onAttemptFinished = { _, _ -> },
                        ) {
                            PhoneTaskWork.main.post {
                                if (current(next.attemptId) && !AppOwnedClirCampaignExecutor.isRunning() && !completing) {
                                    val beforeDial = grants.load()?.let { it.grantId == next.attemptId && it.attemptsUsed == 0 } == true
                                    complete(typedOutcome ?: if (beforeDial) PhoneAttemptOutcome.BLOCKED else PhoneAttemptOutcome.UNKNOWN,
                                        if (beforeDial) PhoneTaskIssue.EXECUTION else null)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        complete(PhoneAttemptOutcome.UNKNOWN, PhoneTaskIssue.EXECUTION)
                    }
                }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun progress(attemptId: String, sessionId: String, answer: PhoneTaskAnswer?) {
        if (!current(attemptId)) return
        if (answer != null && answer.text.isBlank()) return
        val current = claim ?: return
        PhoneTaskWork.io.execute {
            if (!current(attemptId)) return@execute
            try {
                PhoneTaskWork.store(service).updatePhoneTasks { list -> list.map {
                    if (it.id == current.task.id) PhoneTaskQueue.progress(it, current.attemptId,
                        PhoneAttemptStage.TALKING, sessionId, answer) else it
                } }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun complete(outcome: PhoneAttemptOutcome, issue: PhoneTaskIssue? = null) {
        val current = claim ?: return
        if (completing) return
        completing = true
        releaseWarmup()
        CampaignGemmaDirectPreflight.discardUnused()
        PhoneTaskWork.io.execute {
            if (!valid()) return@execute
            try {
                PhoneTaskWork.store(service).updatePhoneTasks { list -> list.map {
                    if (it.id != current.task.id) it else {
                        val answers = it.recipients.flatMap { r -> r.attempts }
                            .first { a -> a.id == current.attemptId }.answers
                        PhoneTaskQueue.complete(it, current.attemptId,
                            if (answers.isNotEmpty()) PhoneAttemptOutcome.ANSWERED else outcome,
                            System.currentTimeMillis(), issue)
                    }
                } }
                PhoneTaskWork.main.post {
                    if (!valid()) return@post
                    claim = null
                    PhoneTaskWork.activeTaskId = null
                    PhoneTaskWork.activeAttemptId = null
                    busy = false
                    if (issue != null && issue != PhoneTaskIssue.PHONE_BUSY) PhoneTaskScheduler.attention(service)
                    runNext()
                }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun pauseDue(issue: PhoneTaskIssue) {
        PhoneTaskWork.io.execute {
            try {
                PhoneTaskWork.store(service).updatePhoneTasks { list -> list.map {
                    if (PhoneTaskQueue.nextAt(it)?.let { due -> due <= System.currentTimeMillis() } == true)
                        it.copy(state = PhoneTaskState.PAUSED, issue = issue) else it
                } }
                PhoneTaskScheduler.attention(service)
                PhoneTaskScheduler.reconcile(service)
                PhoneTaskWork.main.post { close() }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun finishAndSchedule(deferUntil: Long = 0, issue: PhoneTaskIssue? = null) {
        PhoneTaskWork.io.execute {
            try {
                if (issue != null) PhoneTaskWork.store(service).updatePhoneTasks { list -> list.map {
                    if (it.state == PhoneTaskState.READY) it.copy(issue = issue) else it
                } }
                PhoneTaskScheduler.reconcile(service, deferUntil)
                PhoneTaskWork.main.post { close() }
            } catch (_: Exception) { storageFailure() }
        }
    }

    private fun storageFailure() {
        PhoneTaskWork.invalidate()
        PhoneTaskWork.main.post {
            AppOwnedClirCampaignExecutor.stop(ConversationStopReason.SESSION_CANCELLED) { }
            PhoneTaskScheduler.attention(service)
            close()
        }
    }

    fun destroyed() {
        releaseWarmup()
        if (claim != null && valid()) PhoneTaskWork.stop(service, claim?.task?.id,
            takeover = true, issue = PhoneTaskIssue.INTERRUPTED)
        PhoneTaskWork.activeTaskId = null
        PhoneTaskWork.activeAttemptId = null
        busy = false
    }

    private fun releaseWarmup() {
        windowExpiry?.let(PhoneTaskWork.main::removeCallbacks)
        windowExpiry = null
        warmupLock?.let { if (it.isHeld) it.release() }
        warmupLock = null
    }
    private fun close() { releaseWarmup(); busy = false; finished() }
}
