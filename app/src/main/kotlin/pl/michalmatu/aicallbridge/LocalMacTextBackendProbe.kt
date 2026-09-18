package pl.michalmatu.aicallbridge

import android.content.Context
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.textagent.LocalOpenAiCompatibleTextBackend
import pl.michalmatu.aicallbridge.textagent.LocalOpenAiTextBackendConfig
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision
import java.util.concurrent.atomic.AtomicBoolean

/** Off-call physical probe for S22 -> local Mac OpenAI-compatible text backend -> local S22 TTS. */
internal object LocalMacTextBackendProbe {
    private const val REPORT_FILE = "local-mac-text-backend-report.txt"

    fun run(
        context: Context,
        baseUrl: String,
        model: String,
        callback: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        val lines = mutableListOf(
            "probe=local_mac_text_backend",
            "call_required=false",
            "openai_api_used=false",
        )
        val finished = AtomicBoolean(false)
        val backend = try {
            LocalOpenAiCompatibleTextBackend(LocalOpenAiTextBackendConfig(baseUrl, model))
        } catch (error: Throwable) {
            val result = (lines + listOf(
                "backend_config_valid=false",
                "failure=${error.javaClass.simpleName}",
                "probe_complete=true",
            )).joinToString("\n")
            writeReport(appContext, result)
            callback(result)
            return
        }
        lines += "backend_config_valid=true"
        val controller = TextCallTurnController(
            backend,
            TextOutputApprovalPolicy { TextOutputDecision.RELEASE },
        )
        val tts = LocalTtsSpeechOutput(appContext)

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            if (reason != null) lines += "failure=$reason"
            lines += "local_mac_backend_success=$success"
            lines += "probe_complete=true"
            val result = lines.joinToString("\n")
            try { controller.close() } catch (_: Throwable) {}
            try { tts.close() } catch (_: Throwable) {}
            writeReport(appContext, result)
            callback(result)
        }

        controller.submitUserText(
            "Odpowiedz jednym krótkim zdaniem po polsku, że lokalny backend działa.",
            object : TextCallTurnController.Listener {
                override fun onApprovedResponse(text: String) {
                    lines += "backend_complete_response=true"
                    lines += "approved_text_nonblank=${text.isNotBlank()}"
                    tts.synthesize(text, object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            lines += "output_pcm_bytes=${pcm.size}"
                            lines += "approved_output_pcm_nonempty=${pcm.isNotEmpty()}"
                            finish(pcm.isNotEmpty())
                        }

                        override fun onError(reason: String) = finish(false, "tts_$reason")
                    })
                }

                override fun onDroppedResponse() = finish(false, "output_dropped")
                override fun onError(reason: String) = finish(false, reason)
            },
        )
    }

    private fun writeReport(context: Context, result: String) {
        try {
            context.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
                writer.write(result)
                writer.newLine()
            }
        } catch (_: Throwable) {}
    }
}
