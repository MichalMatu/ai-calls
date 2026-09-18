package pl.michalmatu.aicallbridge.agent

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionTool
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionCallHandler
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder

/**
 * Consumes an app-issued one-shot commitment authorization.
 *
 * The model receives only an opaque permit after `evaluate_proposal` succeeds. It cannot resubmit
 * price, time, provider, or other proposal fields here, so it cannot swap the evaluated proposal
 * between policy evaluation and the commitment step.
 */
class CallRealtimeCommitmentFunctionHandler(
    private val workflow: CallWorkflow,
    private val commitmentGate: CallCommitmentGate,
) : CallRealtimeFunctionCallHandler {
    override fun onFunctionCall(
        call: RealtimeFunctionCall,
        responder: CallRealtimeFunctionResponder,
    ) {
        require(call.name == FUNCTION_NAME) { "unsupported Realtime function: ${call.name}" }
        check(workflow.snapshot().state() == CallWorkflowState.ACTIVE_NEGOTIATION) {
            "commitment is not allowed while workflow is ${workflow.snapshot().state()}"
        }

        val authorization = parseAuthorization(call.argumentsJson)
        commitmentGate.consume(authorization).getOrThrow()
        responder.submit(AUTHORIZED_OUTPUT).getOrThrow()
    }

    private fun parseAuthorization(argumentsJson: String): String {
        require(argumentsJson.isNotBlank()) { "commit_proposal arguments must not be blank" }
        try {
            JsonReader(StringReader(argumentsJson)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                var authorizationSeen = false
                var authorization: String? = null
                while (reader.hasNext()) {
                    when (val name = reader.nextName()) {
                        "authorization" -> {
                            if (authorizationSeen) {
                                throw IllegalArgumentException("duplicate commit_proposal field: authorization")
                            }
                            authorizationSeen = true
                            if (reader.peek() != JsonToken.STRING) {
                                throw IllegalArgumentException("authorization must be a string")
                            }
                            authorization = reader.nextString()
                        }
                        else -> throw IllegalArgumentException("unexpected commit_proposal field: $name")
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw IllegalArgumentException("commit_proposal arguments contain trailing JSON")
                }
                if (!authorizationSeen || authorization.isNullOrBlank()) {
                    throw IllegalArgumentException("commit_proposal requires authorization")
                }
                return authorization
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IOException) {
            throw IllegalArgumentException("invalid commit_proposal JSON", error)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("invalid commit_proposal JSON", error)
        }
    }

    companion object {
        const val FUNCTION_NAME = "commit_proposal"
        private const val AUTHORIZED_OUTPUT = "{\"commitment\":\"authorized\"}"
        private const val PARAMETERS_JSON =
            "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
                "\"authorization\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":256," +
                "\"pattern\":\"^[A-Za-z0-9_-]+$\"}},\"required\":[\"authorization\"]}"

        @JvmStatic
        fun tool(): RealtimeFunctionTool = RealtimeFunctionTool(
            FUNCTION_NAME,
            "Consume the one-shot commitment authorization returned by evaluate_proposal. " +
                "Never call this without an authorization returned by the application.",
            PARAMETERS_JSON,
        )
    }
}
