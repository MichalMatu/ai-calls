package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Immutable, owner-approved enquiry scope. One recipient is the same product as a campaign. */
internal data class PhoneTask(
    val id: String,
    val title: String,
    val scenarioId: String,
    val goal: String,
    val brief: String,
    val subscriptionId: Int,
    val accountScope: String,
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val retryDelayMs: Long?,
    val approvedAt: Long,
    val recipients: List<PhoneTaskRecipient>,
    val state: PhoneTaskState = PhoneTaskState.READY,
    val issue: PhoneTaskIssue? = null,
    // Nullable so campaigns approved before language pinning still load from Gson.
    val language: String? = null,
) {
    fun validated(): PhoneTask = apply {
        require(UUID.fromString(id).toString() == id)
        require(startAt > 0 && endAt > startAt && approvedAt > 0)
        require(state in PhoneTaskState.entries)
        require(language == null || language == "pl" || language == "en")
        ZoneId.of(zoneId)
        require(retryDelayMs == null || retryDelayMs > 0)
        require(recipients.isNotEmpty())
        require(recipients.map { it.id }.distinct().size == recipients.size)
        require(recipients.map { it.number }.distinct().size == recipients.size)
        recipients.forEach { recipient ->
            require(recipient.state in PhoneRecipientState.entries)
            require(UUID.fromString(recipient.id).toString() == recipient.id)
            require(recipient.name.length <= 140)
            scenarioTask(recipient, recipient.id).validated()
            require(recipient.attempts.map { it.id }.distinct().size == recipient.attempts.size)
            recipient.attempts.forEach { attempt ->
                require(UUID.fromString(attempt.id).toString() == attempt.id)
                require(attempt.startedAt > 0)
                require(attempt.stage in PhoneAttemptStage.entries)
                require((attempt.endedAt == null) == (attempt.outcome == null))
                require(attempt.endedAt == null || attempt.endedAt >= attempt.startedAt)
                require(attempt.answers.map { it.turn }.distinct().size == attempt.answers.size)
                require(attempt.answers.all { it.turn > 0 && it.text.isNotBlank() })
            }
            require(recipient.attempts.count { it.endedAt == null } <= 1)
            require((recipient.state == PhoneRecipientState.IN_FLIGHT) ==
                recipient.attempts.any { it.endedAt == null })
        }
        require(recipients.count { it.state == PhoneRecipientState.IN_FLIGHT } <= 1)
    }

    fun effectiveLanguage(): String = if (language == "en") "en" else "pl"

    fun scenarioTask(recipient: PhoneTaskRecipient, attemptId: String) = ScenarioOutgoingTask(
        attemptId, recipient.number, subscriptionId, accountScope, scenarioId, title, goal, brief,
        language = effectiveLanguage(),
    )

    override fun toString() = "PhoneTask(id=$id,state=$state,scope=REDACTED)"
}

internal enum class PhoneTaskState { READY, PAUSED, CANCELLED, FINISHED }
internal enum class PhoneRecipientState { QUEUED, IN_FLIGHT, ANSWERED, UNKNOWN, SKIPPED, EXPIRED }
internal enum class PhoneAttemptStage { PREPARING, DIALING, TALKING }
internal enum class PhoneAttemptOutcome { ANSWERED, BUSY, NO_ANSWER, BLOCKED, UNKNOWN, STOPPED }
internal enum class PhoneTaskIssue {
    PHONE_BUSY, PERMISSIONS, DIALER, SIM_CHANGED, MODEL, SHIZUKU, ALARM_PERMISSION,
    BACKGROUND_START, INTERRUPTED, STORAGE, TAKEOVER, EXECUTION,
}
internal data class PhoneTaskRecipient(
    val id: String,
    val name: String,
    val number: String,
    val state: PhoneRecipientState = PhoneRecipientState.QUEUED,
    val nextAt: Long = 0,
    val attempts: List<PhoneTaskAttempt> = emptyList(),
) {
    override fun toString() = "PhoneTaskRecipient(id=$id,state=$state,number=REDACTED)"
}
internal data class PhoneTaskAttempt(
    val id: String,
    val startedAt: Long,
    val stage: PhoneAttemptStage = PhoneAttemptStage.PREPARING,
    val endedAt: Long? = null,
    val outcome: PhoneAttemptOutcome? = null,
    val sessionId: String? = null,
    val answers: List<PhoneTaskAnswer> = emptyList(),
) {
    override fun toString() = "PhoneTaskAttempt(id=$id,stage=$stage,outcome=$outcome)"
}

/** Source excerpts, never numeric promises or proof of booking. Only far-end STT enters here. */
internal data class PhoneTaskAnswer(val turn: Int, val text: String) {
    override fun toString() = "PhoneTaskAnswer(turn=$turn,text=REDACTED)"
}
internal data class PhoneTaskComparison(
    val price: List<String>, val availability: List<String>, val terms: List<String>,
)
internal object PhoneTaskResults {
    fun comparison(answers: List<PhoneTaskAnswer>): PhoneTaskComparison {
        val price = Regex("(?iu)(\\b(cen\\p{L}*|koszt\\p{L}*|zł\\p{L}*|pln|eur|euro|price|cost|pounds?|dollars?)\\b|[€£$])")
        val time = Regex("(?iu)\\b(termin\\p{L}*|dostępn\\p{L}*|woln\\p{L}*|jutro|dzisiaj|poniedział\\p{L}*|wtorek|wtork\\p{L}*|środ\\p{L}*|czwartek|czwart\\p{L}*|piąt\\p{L}*|piątek|sobot\\p{L}*|niedziel\\p{L}*|godzin\\p{L}*|tygod\\p{L}*|available|availability|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
        // Preserve the whole utterance (including 'from', negation, currency, parts/labour).
        // No extraction from model output, no inferred zero, no automatic cheapest ranking.
        val texts = answers.map { it.text }.filter(String::isNotBlank).distinct()
        return PhoneTaskComparison(texts.filter(price::containsMatchIn),
            texts.filter(time::containsMatchIn), texts.filterNot {
                price.containsMatchIn(it) || time.containsMatchIn(it)
            })
    }
}

/** Spoken-enquiry policy belongs to the approved task, not the current device locale. */
internal object PhoneTaskInstruction {
    fun forTask(task: PhoneTask): String = if (task.effectiveLanguage() == "en") {
        "Goal: ${task.goal}\nDetails: ${task.brief}\n" +
            "You are an AI assistant calling on behalf of the user. Speak English. " +
            "Collect answers, exact prices with currency, service scope (parts/labour), " +
            "availability and conditions. Ask follow-up questions when unclear. " +
            "This call is for information only: do not book, buy, order or accept charges. " +
            "Do not disclose private vault data or invent answers or confirmations. " +
            "After collecting answers, thank the recipient and end the call."
    } else {
        "Cel: ${task.goal}\nSzczegóły: ${task.brief}\n" +
            "Jesteś asystentem AI dzwoniącym w imieniu użytkownika. Mów po polsku. " +
            "Zbierz odpowiedzi, dokładną cenę z walutą, zakres usługi (części/robocizna), " +
            "dostępność i warunki. Dopytaj o niejasne informacje. " +
            "To zapytanie informacyjne: nie rezerwuj, nie kupuj, nie zamawiaj " +
            "i nie akceptuj opłat. Nie ujawniaj danych z sejfu. " +
            "Nie wymyślaj odpowiedzi ani potwierdzenia wyniku. " +
            "Po zebraniu odpowiedzi podziękuj i zakończ rozmowę."
    }
}

internal object PhoneTaskInput {
    data class Recipients(val values: List<PhoneTaskRecipient>, val duplicates: Int)
    fun recipients(text: String): Recipients {
        val values = LinkedHashMap<String, PhoneTaskRecipient>()
        var duplicates = 0
        text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, line ->
            val parts = line.split(';')
            require(parts.size in 1..2) { "recipient_line:${index + 1}" }
            val name = if (parts.size == 2) parts[0].trim() else ""
            var number = parts.last().trim().replace(Regex("[ ()-]"), "")
            if (number.startsWith("00")) number = "+" + number.drop(2)
            require(number.matches(Regex("\\+[1-9][0-9]{6,14}")) && name.length <= 140) {
                "recipient_line:${index + 1}"
            }
            if (number in values) duplicates++ else values[number] = PhoneTaskRecipient(
                UUID.randomUUID().toString(), name, number,
            )
        }
        require(values.isNotEmpty()) { "recipient_missing" }
        return Recipients(values.values.toList(), duplicates)
    }

    /** Reject nonexistent/ambiguous local times instead of silently shifting the user's call. */
    fun instant(local: LocalDateTime, zone: ZoneId): Long {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1) { "schedule_dst_ambiguous" }
        return local.toInstant(offsets.single()).toEpochMilli()
    }
}

/** Pure queue transitions; side effects and persistence belong to the existing app service. */
internal object PhoneTaskQueue {
    data class Claim(val task: PhoneTask, val recipientId: String, val attemptId: String)

    fun settle(task: PhoneTask, now: Long): PhoneTask {
        if (task.state != PhoneTaskState.READY) return task
        val recipients = task.recipients.map {
            if (it.state == PhoneRecipientState.QUEUED && now >= task.endAt)
                it.copy(state = PhoneRecipientState.EXPIRED) else it
        }
        return task.copy(recipients = recipients, state = if (recipients.none {
            it.state == PhoneRecipientState.QUEUED || it.state == PhoneRecipientState.IN_FLIGHT
        }) PhoneTaskState.FINISHED else task.state)
    }

    fun nextAt(task: PhoneTask): Long? = if (task.state != PhoneTaskState.READY ||
        task.recipients.any { it.state == PhoneRecipientState.IN_FLIGHT }) null
    else task.recipients.filter { it.state == PhoneRecipientState.QUEUED }
        .minOfOrNull { maxOf(task.startAt, it.nextAt).coerceAtMost(task.endAt) }

    fun claim(tasks: List<PhoneTask>, now: Long, attemptId: String): Claim? {
        // A persisted in-flight reservation blocks a second Call even across duplicate alarms.
        if (tasks.any { t -> t.recipients.any { it.state == PhoneRecipientState.IN_FLIGHT } }) return null
        val task = tasks.filter { it.state == PhoneTaskState.READY && now in it.startAt until it.endAt }
            .sortedBy { it.startAt }.firstOrNull { t -> t.recipients.any {
                it.state == PhoneRecipientState.QUEUED && it.nextAt <= now
            } } ?: return null
        val recipient = task.recipients.first { it.state == PhoneRecipientState.QUEUED && it.nextAt <= now }
        return Claim(task.copy(issue = null, recipients = task.recipients.map {
            if (it.id != recipient.id) it else it.copy(state = PhoneRecipientState.IN_FLIGHT,
                attempts = it.attempts + PhoneTaskAttempt(attemptId, now))
        }), recipient.id, attemptId)
    }

    fun progress(task: PhoneTask, attemptId: String, stage: PhoneAttemptStage,
        sessionId: String? = null, answer: PhoneTaskAnswer? = null): PhoneTask =
        task.copy(recipients = task.recipients.map { r -> r.copy(attempts = r.attempts.map { a ->
            if (a.id != attemptId || a.endedAt != null) a else a.copy(
                stage = if (stage.ordinal > a.stage.ordinal) stage else a.stage,
                sessionId = sessionId ?: a.sessionId,
                answers = if (answer == null || a.answers.any { it.turn == answer.turn }) a.answers
                    else a.answers + answer,
            )
        }) })

    fun complete(task: PhoneTask, attemptId: String, outcome: PhoneAttemptOutcome,
        now: Long, issue: PhoneTaskIssue? = null): PhoneTask {
        if (task.recipients.none { r -> r.attempts.any { it.id == attemptId && it.endedAt == null } }) return task
        val recipients = task.recipients.map { r ->
            val attempt = r.attempts.lastOrNull()?.takeIf { it.id == attemptId && it.endedAt == null }
                ?: return@map r
            val retryAt = task.retryDelayMs?.let { delay ->
                if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay
            }
            val retry = outcome in setOf(PhoneAttemptOutcome.BUSY, PhoneAttemptOutcome.NO_ANSWER) &&
                attempt.answers.isEmpty() && retryAt != null && retryAt < task.endAt
            r.copy(state = when {
                retry -> PhoneRecipientState.QUEUED
                outcome == PhoneAttemptOutcome.ANSWERED || attempt.answers.isNotEmpty() -> PhoneRecipientState.ANSWERED
                outcome == PhoneAttemptOutcome.STOPPED -> PhoneRecipientState.SKIPPED
                outcome == PhoneAttemptOutcome.BLOCKED -> PhoneRecipientState.QUEUED
                else -> PhoneRecipientState.UNKNOWN
            }, nextAt = if (issue == PhoneTaskIssue.PHONE_BUSY) now + 30_000L
                else if (retry) requireNotNull(retryAt) else r.nextAt,
                attempts = r.attempts.dropLast(1) + attempt.copy(endedAt = maxOf(now, attempt.startedAt), outcome = outcome))
        }
        return settle(task.copy(recipients = recipients,
            state = if (issue != null && issue != PhoneTaskIssue.PHONE_BUSY) PhoneTaskState.PAUSED else task.state, issue = issue), now)
    }

    fun interrupt(task: PhoneTask, now: Long, cancel: Boolean, issue: PhoneTaskIssue?): PhoneTask {
        if (task.state == PhoneTaskState.CANCELLED || task.state == PhoneTaskState.FINISHED) return task
        var next = task
        task.recipients.flatMap { it.attempts }.filter { it.endedAt == null }.forEach {
            next = complete(next, it.id, if (it.stage == PhoneAttemptStage.PREPARING)
                PhoneAttemptOutcome.BLOCKED else PhoneAttemptOutcome.UNKNOWN, now)
        }
        return next.copy(state = if (cancel) PhoneTaskState.CANCELLED else PhoneTaskState.PAUSED, issue = issue,
            recipients = if (cancel) next.recipients.map {
                if (it.state == PhoneRecipientState.QUEUED) it.copy(state = PhoneRecipientState.SKIPPED) else it
            } else next.recipients)
    }

    fun recover(tasks: List<PhoneTask>, now: Long): List<PhoneTask> = tasks.map { task ->
        if (task.recipients.any { it.state == PhoneRecipientState.IN_FLIGHT })
            interrupt(task, now, false, PhoneTaskIssue.INTERRUPTED) else settle(task, now)
    }
}
