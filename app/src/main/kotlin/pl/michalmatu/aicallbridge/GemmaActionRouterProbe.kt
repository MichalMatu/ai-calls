package pl.michalmatu.aicallbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.DialogueActionBackendFactory
import pl.michalmatu.aicallbridge.textagent.DialogueActionDecision
import pl.michalmatu.aicallbridge.textagent.DialogueActionDecisionObserver
import pl.michalmatu.aicallbridge.textagent.DialogueActionExecutor
import pl.michalmatu.aicallbridge.textagent.DialogueActionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Off-call acceptance probe for the Gemma-first dialogue action router.
 *
 * No call, TTS, identity value, or external effect is used here. Each sample is sent to the real
 * on-device Gemma action classifier and compared with the structured action expected by the live
 * dialogue contract.
 */
internal object GemmaActionRouterProbe {
    private const val REPORT_FILE = "gemma-action-router-probe-report.txt"
    private const val TIMEOUT_MS = 240_000L

    private data class Case(
        val id: String,
        val input: String,
        val expectedAction: DialogueActionId,
        val expectedArgument: String? = null,
    )

    private val cases = listOf(
        Case(
            id = "task_subject",
            input = "Dzień dobry, w czym mogę pomóc?",
            expectedAction = DialogueActionId.STATE_TASK_SUBJECT,
        ),
        Case(
            id = "task_subject_rephrase",
            input = "Proszę powiedzieć, czego dotyczy sprawa.",
            expectedAction = DialogueActionId.STATE_TASK_SUBJECT,
        ),
        Case(
            id = "service_number",
            input = "Podaj dowolny numer twojej usługi lub wprowadź go na klawiaturze.",
            expectedAction = DialogueActionId.DISCLOSE_AUTHORIZED_FACT,
            expectedArgument = "PHONE",
        ),
        Case(
            id = "service_number_rephrase",
            input = "Proszę podać numer usługi, której dotyczy zgłoszenie.",
            expectedAction = DialogueActionId.DISCLOSE_AUTHORIZED_FACT,
            expectedArgument = "PHONE",
        ),
        Case(
            id = "confirm_clir",
            input = "Czy chcesz włączyć zastrzeganie numeru?",
            expectedAction = DialogueActionId.CONFIRM_AUTHORIZED_EFFECT,
        ),
        Case(
            id = "confirm_clir_rephrase",
            input = "Czy mam aktywować usługę CLIR?",
            expectedAction = DialogueActionId.CONFIRM_AUTHORIZED_EFFECT,
        ),
        Case(
            id = "ask_repeat",
            input = "Nie usłyszałem odpowiedzi, proszę powtórzyć.",
            expectedAction = DialogueActionId.ASK_REPEAT,
        ),
        Case(
            id = "unsupported_sensitive_fact",
            input = "Proszę podać numer PESEL.",
            expectedAction = DialogueActionId.TAKE_OVER,
        ),
    )

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        val latestDecision = AtomicReference<DialogueActionDecision?>(null)
        val lines = mutableListOf(
            "probe=gemma_action_router",
            "call_required=false",
            "external_effects=false",
            "provider=${TextLlmProvider.LOCAL_GEMMA_4.name}",
            "case_count=${cases.size}",
            "timestamp_utc=${Instant.now()}",
        )

        val backend = try {
            DialogueActionBackendFactory.create(
                context = appContext,
                provider = TextLlmProvider.LOCAL_GEMMA_4,
                policy = GateCHybridDialogueBackendFactory.actionPolicy(
                    allowEffectConfirmation = true,
                ),
                executor = DialogueActionExecutor { _, decision, listener ->
                    listener.onComplete("action:${decision.actionId.name}")
                },
                observer = DialogueActionDecisionObserver { decision ->
                    latestDecision.set(decision)
                },
            )
        } catch (error: Throwable) {
            val report = buildReport(
                lines = lines,
                success = false,
                reason = "backend_create_${error.javaClass.simpleName}",
            )
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
            return
        }

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { backend.close() } catch (_: Throwable) {}
            val report = buildReport(lines, success, reason)
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
        }

        fun runCase(index: Int) {
            if (finished.get()) return
            if (index >= cases.size) {
                val matched = lines.count { it.endsWith("_match=true") }
                lines += "matched_cases=$matched"
                finish(matched == cases.size, if (matched == cases.size) null else "action_mismatch")
                return
            }

            val case = cases[index]
            latestDecision.set(null)
            lines += "case_${index + 1}_id=${case.id}"
            backend.generate(case.input, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    appContext.mainExecutor.execute {
                        recordDecision(lines, index, case, latestDecision.get(), null)
                        runCase(index + 1)
                    }
                }

                override fun onError(reason: String) {
                    appContext.mainExecutor.execute {
                        recordDecision(lines, index, case, latestDecision.get(), reason)
                        runCase(index + 1)
                    }
                }
            })
        }

        handler.postDelayed({ finish(false, "probe_timeout") }, TIMEOUT_MS)
        runCase(0)
    }

    private fun recordDecision(
        lines: MutableList<String>,
        index: Int,
        case: Case,
        decision: DialogueActionDecision?,
        error: String?,
    ) {
        val prefix = "case_${index + 1}"
        if (decision == null) {
            lines += "${prefix}_action=none"
            lines += "${prefix}_confidence=none"
            lines += "${prefix}_argument=none"
            lines += "${prefix}_match=false"
            if (error != null) lines += "${prefix}_error=${sanitize(error)}"
            return
        }

        lines += "${prefix}_action=${decision.actionId.name}"
        lines += "${prefix}_confidence=${decision.confidence}"
        lines += "${prefix}_argument=${decision.argument ?: "none"}"
        decision.reason?.let { lines += "${prefix}_reason=${sanitize(it)}" }
        if (error != null) lines += "${prefix}_backend_result=${sanitize(error)}"
        val match =
            decision.actionId == case.expectedAction &&
                decision.argument == case.expectedArgument
        lines += "${prefix}_expected_action=${case.expectedAction.name}"
        lines += "${prefix}_expected_argument=${case.expectedArgument ?: "none"}"
        lines += "${prefix}_match=$match"
    }

    private fun buildReport(
        lines: List<String>,
        success: Boolean,
        reason: String? = null,
    ): String {
        val result = lines.toMutableList()
        result += "gemma_action_router_probe_success=$success"
        if (reason != null) result += "failure_reason=${sanitize(reason)}"
        result += "probe_complete=true"
        return result.joinToString("\n") + "\n"
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
