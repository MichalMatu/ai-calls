package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.session.S8LocalModelFourTurnProbe
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * DUMP-gated physical LiteRT text-only diagnostic. Zero real calls, zero
 * account operations, zero model downloads, zero transcripts in report.
 * The explicit caller must first install the pinned model in this S8
 * package sandbox, not the stable dialer's private storage.
 */
class S8LocalModelFourTurnSmokeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { action ->
        Thread(action, "s8-local-native-model-qa").apply { isDaemon = true }
    }
    private var probe: S8LocalModelFourTurnProbe? = null
    private var completed = false
    private val timeout = Runnable { complete("timeout", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        write("running", 0)
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val allowed = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
            audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
            audio.mode != AudioManager.MODE_IN_COMMUNICATION
        if (!allowed) {
            complete("blocked_not_idle_or_permission", 0)
            return
        }

        val state = try {
            AndroidGemma4ModelReadiness.check(this, DialogueModelId.GEMMA_4_E2B).state
        } catch (_: Throwable) {
            Gemma4ModelReadinessState.INVALID
        }
        when (state) {
            Gemma4ModelReadinessState.MISSING -> {
                complete("model_missing", 0)
                return
            }
            Gemma4ModelReadinessState.INVALID -> {
                complete("model_invalid", 0)
                return
            }
            Gemma4ModelReadinessState.READY -> Unit
        }

        handler.postDelayed(timeout, MAX_NATIVE_TEST_MS)
        try {
            probe = S8LocalModelFourTurnProbe(
                factory = {
                    Gemma4LiteRtTextBackendFactory.create(
                        applicationContext,
                        systemInstruction = "Jesteś lokalnym asystentem testowym. " +
                            "Odpowiadaj zwięźle po polsku. Nie wywołuj narzędzi, " +
                            "nie proponuj połączeń telefonicznych.",
                        modelId = DialogueModelId.GEMMA_4_E2B,
                    )
                },
                worker = worker,
                onDone = { result ->
                    runOnUiThread {
                        complete(when (result.status) {
                            S8LocalModelFourTurnProbe.Status.PASS -> "pass"
                            S8LocalModelFourTurnProbe.Status.FAILED -> "failed"
                            S8LocalModelFourTurnProbe.Status.STOPPED -> "stopped"
                        }, result.completedTurns)
                    }
                },
            ).also { it.start() }
        } catch (_: Throwable) {
            complete("failed", 0)
        }
    }

    private fun complete(status: String, turns: Int) {
        if (completed) return
        completed = true
        handler.removeCallbacks(timeout)
        probe?.close()
        probe = null
        write(status, turns)
        worker.shutdown() // Scheduled native cancellation is allowed to complete.
        finish()
    }

    private fun write(status: String, turns: Int) {
        PrivateDiagnosticReportStore.write(filesDir, REPORT_FILE, buildString {
            appendLine("probe=s8_local_litert_four_turn")
            appendLine("backend=app_owned_litert")
            appendLine("completed_turns=${turns.coerceIn(0, 4)}")
            appendLine("status=$status")
            appendLine("model_downloaded=false")
            appendLine("real_call=false")
            appendLine("remote_model=false")
            appendLine("report_complete=${status != "running"}")
        })
    }

    override fun onStop() {
        if (!completed) complete("stopped", 0)
        super.onStop()
    }

    companion object {
        const val REPORT_FILE = "s8-local-litert-four-turn-report.txt"
        private const val MAX_NATIVE_TEST_MS = 300_000L
    }
}
