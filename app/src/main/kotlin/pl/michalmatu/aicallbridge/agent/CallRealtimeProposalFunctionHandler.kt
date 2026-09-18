package pl.michalmatu.aicallbridge.agent

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionTool
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionCallHandler
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

/**
 * App-owned bridge between the Realtime `evaluate_proposal` tool and deterministic call policy.
 *
 * Counterparty/model content is accepted only as a strictly typed proposal. It never changes task
 * constraints, preferences, or authorized facts. A proposal that needs user approval deliberately
 * keeps the Realtime function call open until the user approves or rejects that exact proposal.
 * A successful policy/user decision issues a separate one-shot commitment authorization; the model
 * cannot create or widen that authorization itself.
 */
class CallRealtimeProposalFunctionHandler(
    private val workflow: CallWorkflow,
    private val commitmentGate: CallCommitmentGate = CallCommitmentGate(),
) : CallRealtimeFunctionCallHandler {
    private val lock = Any()
    private var pendingDecision: PendingDecision? = null

    override fun onFunctionCall(
        call: RealtimeFunctionCall,
        responder: CallRealtimeFunctionResponder,
    ) {
        require(call.name == FUNCTION_NAME) { "unsupported Realtime function: ${call.name}" }

        // Any new proposal attempt invalidates an unused authorization from an older proposal.
        commitmentGate.clear()
        synchronized(lock) {
            check(pendingDecision == null) { "a proposal is already waiting for user decision" }
        }

        val proposal = parseProposal(call.argumentsJson)
        val decision = workflow.evaluateProposal(proposal)
        when (decision.action()) {
            CallPolicyAction.AUTONOMOUSLY_ALLOWED -> {
                val authorization = commitmentGate.authorize(proposal)
                val submitted = responder.submit(approvedOutput(AUTONOMOUSLY_ALLOWED, authorization))
                val submitError = submitted.exceptionOrNull()
                if (submitError != null) {
                    commitmentGate.clear()
                    throw submitError
                }
            }

            CallPolicyAction.NEEDS_USER_DECISION -> {
                synchronized(lock) {
                    check(pendingDecision == null) { "a proposal is already waiting for user decision" }
                    pendingDecision = PendingDecision(proposal, responder)
                }
            }
        }
    }

    fun hasPendingUserDecision(): Boolean = synchronized(lock) { pendingDecision != null }

    /**
     * Approves only the exact proposal currently waiting for the user. The generation-bound
     * responder is submitted before the workflow leaves NEEDS_USER_DECISION; a stale/failed
     * responder therefore cannot silently widen workflow authority. A commit permit is revoked on
     * every failure path.
     */
    fun approvePendingProposal(): Result<CallProposal> = resolvePending(approve = true)

    /** Rejects only the exact proposal currently waiting for the user. */
    fun rejectPendingProposal(): Result<CallProposal> = resolvePending(approve = false)

    private fun resolvePending(approve: Boolean): Result<CallProposal> {
        val pending = synchronized(lock) {
            val current = pendingDecision
                ?: return Result.failure(IllegalStateException("no proposal is waiting for user decision"))
            if (!current.claimed.compareAndSet(false, true)) {
                return Result.failure(IllegalStateException("pending proposal decision is already being resolved"))
            }
            current
        }

        val workflowSnapshot = workflow.snapshot()
        if (
            workflowSnapshot.state() != CallWorkflowState.NEEDS_USER_DECISION ||
            workflowSnapshot.pendingProposal() !== pending.proposal
        ) {
            commitmentGate.clear()
            return Result.failure(IllegalStateException("workflow pending proposal no longer matches Realtime call"))
        }

        val authorization = if (approve) commitmentGate.authorize(pending.proposal) else null
        val outputJson = if (approve) {
            approvedOutput(USER_APPROVED, authorization!!)
        } else {
            commitmentGate.clear()
            USER_REJECTED_OUTPUT
        }

        val submitted = pending.responder.submit(outputJson)
        val submitError = submitted.exceptionOrNull()
        if (submitError != null) {
            commitmentGate.clear()
            return Result.failure(submitError)
        }

        return try {
            val proposal = if (approve) {
                workflow.approvePendingProposal()
            } else {
                workflow.rejectPendingProposal()
            }
            synchronized(lock) {
                if (pendingDecision === pending) {
                    pendingDecision = null
                }
            }
            Result.success(proposal)
        } catch (error: Throwable) {
            commitmentGate.clear()
            Result.failure(error)
        }
    }

    private fun parseProposal(argumentsJson: String): CallProposal {
        require(argumentsJson.isNotBlank()) { "evaluate_proposal arguments must not be blank" }
        try {
            JsonReader(StringReader(argumentsJson)).use { reader ->
                reader.isLenient = false
                reader.beginObject()

                var scheduledSeen = false
                var priceSeen = false
                var paymentModeSeen = false
                var providerSeen = false
                var locationSeen = false

                var scheduledAt: ZonedDateTime? = null
                var price: MoneyAmount? = null
                var paymentMode: CallPaymentMode? = null
                var provider: String? = null
                var location: String? = null

                while (reader.hasNext()) {
                    when (val name = reader.nextName()) {
                        "scheduled_at" -> {
                            checkNotDuplicate(scheduledSeen, name)
                            scheduledSeen = true
                            scheduledAt = readZonedDateTime(reader, name)
                        }

                        "price" -> {
                            checkNotDuplicate(priceSeen, name)
                            priceSeen = true
                            price = readPrice(reader)
                        }

                        "payment_mode" -> {
                            checkNotDuplicate(paymentModeSeen, name)
                            paymentModeSeen = true
                            paymentMode = readPaymentMode(reader)
                        }

                        "provider" -> {
                            checkNotDuplicate(providerSeen, name)
                            providerSeen = true
                            provider = readNullableNonBlankString(reader, name)
                        }

                        "location" -> {
                            checkNotDuplicate(locationSeen, name)
                            locationSeen = true
                            location = readNullableNonBlankString(reader, name)
                        }

                        else -> throw IllegalArgumentException("unexpected evaluate_proposal field: $name")
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw IllegalArgumentException("evaluate_proposal arguments contain trailing JSON")
                }

                if (!scheduledSeen || !priceSeen || !paymentModeSeen || !providerSeen || !locationSeen) {
                    throw IllegalArgumentException("evaluate_proposal requires all proposal fields explicitly")
                }

                return CallProposal(scheduledAt, price, paymentMode, provider, location)
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IOException) {
            throw IllegalArgumentException("invalid evaluate_proposal JSON", error)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("invalid evaluate_proposal JSON", error)
        }
    }

    private fun readPrice(reader: JsonReader): MoneyAmount? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw IllegalArgumentException("price must be an object or null")
        }
        reader.beginObject()
        var amountSeen = false
        var currencySeen = false
        var amount: String? = null
        var currency: String? = null
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "amount" -> {
                    checkNotDuplicate(amountSeen, "price.amount")
                    amountSeen = true
                    amount = readRequiredString(reader, "price.amount")
                }

                "currency" -> {
                    checkNotDuplicate(currencySeen, "price.currency")
                    currencySeen = true
                    currency = readRequiredString(reader, "price.currency")
                }

                else -> throw IllegalArgumentException("unexpected price field: $name")
            }
        }
        reader.endObject()
        if (!amountSeen || !currencySeen) {
            throw IllegalArgumentException("price requires amount and currency")
        }
        val parsedAmount = try {
            BigDecimal(amount)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("price.amount must be a decimal string", error)
        }
        return MoneyAmount(parsedAmount, currency!!)
    }

    private fun readPaymentMode(reader: JsonReader): CallPaymentMode? {
        val raw = readNullableNonBlankString(reader, "payment_mode") ?: return null
        return try {
            CallPaymentMode.valueOf(raw)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("unsupported payment_mode: $raw", error)
        }
    }

    private fun readZonedDateTime(reader: JsonReader, name: String): ZonedDateTime? {
        val raw = readNullableNonBlankString(reader, name) ?: return null
        return try {
            ZonedDateTime.parse(raw)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("$name must be an ISO-8601 zoned timestamp", error)
        }
    }

    private fun readNullableNonBlankString(reader: JsonReader, name: String): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return readRequiredString(reader, name)
    }

    private fun readRequiredString(reader: JsonReader, name: String): String {
        if (reader.peek() != JsonToken.STRING) {
            throw IllegalArgumentException("$name must be a string")
        }
        val value = reader.nextString().trim()
        if (value.isEmpty()) {
            throw IllegalArgumentException("$name must not be blank")
        }
        return value
    }

    private fun checkNotDuplicate(seen: Boolean, name: String) {
        if (seen) {
            throw IllegalArgumentException("duplicate evaluate_proposal field: $name")
        }
    }

    private data class PendingDecision(
        val proposal: CallProposal,
        val responder: CallRealtimeFunctionResponder,
        val claimed: AtomicBoolean = AtomicBoolean(false),
    )

    companion object {
        const val FUNCTION_NAME = "evaluate_proposal"

        private const val AUTONOMOUSLY_ALLOWED = "autonomously_allowed"
        private const val USER_APPROVED = "user_approved"
        private const val USER_REJECTED_OUTPUT = "{\"decision\":\"user_rejected\"}"

        private fun approvedOutput(
            decision: String,
            authorization: CallCommitmentAuthorization,
        ): String =
            "{\"decision\":\"$decision\",\"commitment_authorization\":\"${authorization.value}\"}"

        private const val PARAMETERS_JSON =
            "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
                "\"scheduled_at\":{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"null\"}]}" +
                ",\"price\":{\"anyOf\":[{\"type\":\"object\",\"additionalProperties\":false," +
                "\"properties\":{\"amount\":{\"type\":\"string\"},\"currency\":{\"type\":\"string\"}}," +
                "\"required\":[\"amount\",\"currency\"]},{\"type\":\"null\"}]}" +
                ",\"payment_mode\":{\"anyOf\":[{\"type\":\"string\",\"enum\":[\"NFZ\",\"PRIVATE\",\"INSURANCE\"]},{\"type\":\"null\"}]}" +
                ",\"provider\":{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"null\"}]}" +
                ",\"location\":{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"null\"}]}}" +
                ",\"required\":[\"scheduled_at\",\"price\",\"payment_mode\",\"provider\",\"location\"]}"

        @JvmStatic
        fun tool(): RealtimeFunctionTool = RealtimeFunctionTool(
            FUNCTION_NAME,
            "Evaluate one concrete counterparty proposal against application-owned user authority. " +
                "Call this before accepting, booking, purchasing, or otherwise committing to the proposal.",
            PARAMETERS_JSON,
        )
    }
}
