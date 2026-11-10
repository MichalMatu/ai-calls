package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.appointment.AppointmentInterpreter
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

/** One explicitly accepted outbound task; no model text can widen its target, SIM or facts. */
internal data class AppointmentMvpTask(
    val grantId: String,
    val target: String,
    val subscriptionId: Int,
    val accountScope: String,
    val goal: String,
    val notBefore: ZonedDateTime,
    val notAfter: ZonedDateTime,
    val conditions: String,
    val disclosureFields: Set<IdentityFieldId>,
) {
    init {
        require(grantId.isNotBlank())
        require(target.matches(Regex("\\+[1-9][0-9]{6,14}"))) { "target_e164_required" }
        require(subscriptionId >= 0 && accountScope.isNotBlank())
        require(goal.trim().length in 5..280)
        require(notBefore.toInstant() < notAfter.toInstant())
        require(conditions.length <= 500)
        require(disclosureFields.all { it in ALLOWED_FIELDS })
    }
    fun toCallTask(): CallTask = CallTask(
        goal, ACTION, "appointment",
        CallConstraints(listOf(CallTimeWindow(notBefore, notAfter)), null, emptySet()),
        CallPreferences.none(), emptyMap(),
    )
    fun request() = CampaignExecutionRequest(
        target = target, action = ACTION, service = CallService.APPOINTMENT,
        enabled = true, accountScope = accountScope, disclosureFields = disclosureFields,
    )
    fun grant(nowEpochMs: Long) = CampaignAuthorizationGrant(
        grantId = grantId, version = 1, createdAtEpochMs = nowEpochMs,
        revokedAtEpochMs = null, accountScope = accountScope,
        allowedTargets = setOf(target), allowedActions = setOf(ACTION),
        allowedServices = setOf(CallService.APPOINTMENT),
        allowedEffectValues = setOf(true), allowedDisclosureFields = disclosureFields,
        attemptsUsed = 0, recordCallEvidence = false,
        issuerEvidenceRef = "appointment-ui:" + grantId,
    )
    override fun toString() = "AppointmentMvpTask(target=REDACTED, goal=REDACTED, facts=REDACTED)"
    companion object {
        const val ACTION = "BOOK_APPOINTMENT"
        val ALLOWED_FIELDS = setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.LAST_NAME,
            IdentityFieldId.PHONE, IdentityFieldId.EMAIL)
        fun create(target: String, subscriptionId: Int, scope: String, goal: String,
            from: ZonedDateTime, until: ZonedDateTime, terms: String,
            fields: Set<IdentityFieldId>) = AppointmentMvpTask(
            UUID.randomUUID().toString(), target.trim(), subscriptionId, scope,
            goal.trim(), from, until, terms.trim(), fields.toSet(),
        )
    }
}

/** A result requires human review; it NEVER claims booking or provider confirmation. */
internal data class AppointmentMvpReview(
    val at: ZonedDateTime,
    val observedTerms: String,
    val status: String = REVIEW_REQUIRED,
) {
    override fun toString() = "AppointmentMvpReview(status=REVIEW_REQUIRED, details=REDACTED)"
    companion object { const val REVIEW_REQUIRED = "REVIEW_REQUIRED" }
}

/** Pure app-owned validation of receptionist STT, not a simulated booking provider. */
internal class AppointmentMvpReviewReducer(private val task: AppointmentMvpTask) {
    private val callTask = task.toCallTask()
    private val interpreter = AppointmentInterpreter(ZoneId.of("Europe/Warsaw"))
    fun observe(text: String, referenceDate: java.time.LocalDate): AppointmentMvpReview? {
        val at = interpreter.parseOffer(text, referenceDate)?.scheduledAt ?: return null
        if (at.toInstant() < task.notBefore.toInstant() ||
            at.toInstant() > task.notAfter.toInstant()) return null
        val proposal = CallProposal(at, null, null, null, null)
        val decision = pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy()
            .evaluate(callTask, proposal)
        if (decision.reasons().isNotEmpty()) return null
        return AppointmentMvpReview(at, text.take(480))
    }
}

/** App-private state: an exact grant ID binds task and review. Never logs the number or transcript. */
internal class AppointmentMvpStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("appointment_mvp_v1", Context.MODE_PRIVATE)
    fun save(task: AppointmentMvpTask): Boolean = p.edit()
        .putString("grant", task.grantId).putString("target", task.target)
        .putInt("subscription", task.subscriptionId).putString("scope", task.accountScope)
        .putString("goal", task.goal).putString("from", task.notBefore.toString())
        .putString("to", task.notAfter.toString()).putString("conditions", task.conditions)
        .putStringSet("fields", task.disclosureFields.map { it.name }.toSet())
        .remove("review_at").remove("review_terms").commit()
    fun load(): AppointmentMvpTask? = runCatching {
        AppointmentMvpTask(
            grantId = p.getString("grant", null)!!,
            target = p.getString("target", null)!!,
            subscriptionId = p.getInt("subscription", -1),
            accountScope = p.getString("scope", null)!!,
            goal = p.getString("goal", null)!!,
            notBefore = ZonedDateTime.parse(p.getString("from", null)!!),
            notAfter = ZonedDateTime.parse(p.getString("to", null)!!),
            conditions = p.getString("conditions", "")!!,
            disclosureFields = p.getStringSet("fields", emptySet())!!.map(IdentityFieldId::valueOf).toSet(),
        )
    }.getOrNull()
    fun saveReview(grantId: String, review: AppointmentMvpReview): Boolean {
        if (p.getString("grant", null) != grantId) return false
        return p.edit().putString("review_at", review.at.toString())
            .putString("review_terms", review.observedTerms).commit()
    }
    fun review(grantId: String): AppointmentMvpReview? {
        if (p.getString("grant", null) != grantId) return null
        return runCatching {
            AppointmentMvpReview(ZonedDateTime.parse(p.getString("review_at", null)!!),
                p.getString("review_terms", "")!!)
        }.getOrNull()
    }
    fun clearReview() = p.edit().remove("review_at").remove("review_terms").apply()
}
