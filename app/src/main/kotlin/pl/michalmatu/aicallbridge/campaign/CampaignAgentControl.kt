package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** App-owned intake for an explicit user task from UI or the shell/DUMP-protected Codex entry.
 * Preview never accepts a task. Automatic continuation never calls this intake.
 */
internal object CampaignAgentControl {
    const val REPORT_FILE_NAME = "campaign-agent-report.txt"

    fun stopOwnedCampaign(context: Context): String = runCatching {
        context.startService(Intent(context, AppOwnedCampaignService::class.java)
            .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI))
        "campaign_agent_stop=DISPATCHED_TO_INTERNAL_SERVICE\n"
    }.getOrElse { "campaign_agent_stop=DISPATCH_FAILED_${it.javaClass.simpleName}\n" }

    fun run(
        context: Context,
        requestStart: Boolean,
        dialogueMode: CampaignDialogueMode? = CampaignDialogueMode.SCRIPT_GEMMA,
        recordCallEvidence: Boolean? = null,
    ): String {
        val permissionsPresent = listOf(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val scope = if (permissionsPresent) CampaignAccountScope.currentVoiceSubscription(context) else null
        val roleReady = runCatching { AndroidTelecomOwnedCallController.canControl(context) }.getOrDefault(false)
        val disposition = if (dialogueMode == null) CampaignAgentReadiness.UNKNOWN_DIALOGUE_MODE else
            CampaignAgentLaunchPolicy.decide(permissionsPresent, scope != null, roleReady)
        val commandStatus = when {
            !requestStart -> "NOT_REQUESTED"
            disposition != CampaignAgentReadiness.READY -> "NOT_DISPATCHED"
            else -> runCatching {
                val store = CampaignAuthorizationStore(context)
                val existing = store.load()
                val now = System.currentTimeMillis()
                val request = ClirCampaignSpec.enableRequest(checkNotNull(scope))
                val matching = existing?.takeIf { CampaignAuthorizationPolicy.matchesExactTask(it, request) }
                // START is the explicit delegated task, not a request for another approval.
                // Keep recording opt-in only for the same accepted task or an explicit choice.
                store.save(ClirCampaignSpec.acceptEnableTask(scope, now,
                    recordCallEvidence = recordCallEvidence ?: matching?.recordCallEvidence ?: false,
                    existing = existing))
                CampaignRuntimeStatusStore(context).save("Starting user-requested task")
                context.startForegroundService(Intent(context, AppOwnedCampaignService::class.java)
                    .setAction(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE)
                    .putExtra(AppOwnedCampaignService.EXTRA_DIALOGUE_MODE, checkNotNull(dialogueMode).name))
                "DISPATCHED_TO_INTERNAL_SERVICE"
            }.getOrElse { "DISPATCH_FAILED_${it.javaClass.simpleName}" }
        }
        return "campaign_agent_readiness=${disposition.name}\n" +
            "campaign_agent_start=$commandStatus\n" +
            "campaign_agent_scope=OWNER_TASK_ON_START\n" +
            "campaign_agent_dialogue_mode=${dialogueMode?.name ?: "UNKNOWN"}\n"
    }
}

internal enum class CampaignAgentReadiness {
    MISSING_RUNTIME_PERMISSION, NO_VOICE_SUBSCRIPTION, DIALER_ROLE_REQUIRED, UNKNOWN_DIALOGUE_MODE, READY,
}

internal object CampaignAgentLaunchPolicy {
    fun decide(
        permissionsPresent: Boolean,
        voiceSubscriptionPresent: Boolean,
        dialerRoleReady: Boolean,
    ): CampaignAgentReadiness = when {
        !permissionsPresent -> CampaignAgentReadiness.MISSING_RUNTIME_PERMISSION
        !voiceSubscriptionPresent -> CampaignAgentReadiness.NO_VOICE_SUBSCRIPTION
        !dialerRoleReady -> CampaignAgentReadiness.DIALER_ROLE_REQUIRED
        else -> CampaignAgentReadiness.READY
    }
}
