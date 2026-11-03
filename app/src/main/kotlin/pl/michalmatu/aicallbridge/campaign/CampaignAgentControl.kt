package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Internal, effect-aware entry point shared with shell-only diagnostics.
 *
 * The exported DiagnosticProbeActivity is protected by Android's DUMP permission.
 * This class never creates a campaign grant, requests the dialer role or dials by itself.
 * The campaign service and executor revalidate the same authority before each call.
 */
internal object CampaignAgentControl {
    const val REPORT_FILE_NAME = "campaign-agent-report.txt"

    fun run(context: Context, requestStart: Boolean): String {
        val permissionsPresent = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        ).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val scope = if (permissionsPresent) {
            CampaignAccountScope.currentVoiceSubscription(context)
        } else null
        val authorization = if (scope != null) {
            CampaignAuthorizationPolicy.authorize(
                CampaignAuthorizationStore(context).load(),
                ClirCampaignSpec.enableRequest(scope),
                System.currentTimeMillis(),
            )
        } else null
        val roleReady = runCatching {
            AndroidTelecomOwnedCallController.canControl(context)
        }.getOrDefault(false)
        val disposition = CampaignAgentLaunchPolicy.decide(
            permissionsPresent = permissionsPresent,
            voiceSubscriptionPresent = scope != null,
            grantAuthorized = authorization is CampaignAuthorizationDecision.Authorized,
            dialerRoleReady = roleReady,
        )
        val commandStatus = when {
            !requestStart -> "NOT_REQUESTED"
            disposition != CampaignAgentReadiness.READY -> "NOT_DISPATCHED"
            else -> runCatching {
                CampaignRuntimeStatusStore(context).save("Starting previously authorized campaign")
                context.startForegroundService(
                    Intent(context, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE),
                )
                "DISPATCHED_TO_INTERNAL_SERVICE"
            }.getOrElse { "DISPATCH_FAILED_${it.javaClass.simpleName}" }
        }
        // Never place target, SIM identity, grant ID, transcript or user data in this report.
        return "campaign_agent_readiness=${disposition.name}\n" +
            "campaign_agent_start=$commandStatus\n" +
            "campaign_agent_scope=EXISTING_GRANT_ONLY\n"
    }
}

internal enum class CampaignAgentReadiness {
    MISSING_RUNTIME_PERMISSION,
    NO_VOICE_SUBSCRIPTION,
    NO_VALID_EXACT_GRANT,
    DIALER_ROLE_REQUIRED,
    READY,
}

/** Pure readiness policy for JVM regression tests. */
internal object CampaignAgentLaunchPolicy {
    fun decide(
        permissionsPresent: Boolean,
        voiceSubscriptionPresent: Boolean,
        grantAuthorized: Boolean,
        dialerRoleReady: Boolean,
    ): CampaignAgentReadiness = when {
        !permissionsPresent -> CampaignAgentReadiness.MISSING_RUNTIME_PERMISSION
        !voiceSubscriptionPresent -> CampaignAgentReadiness.NO_VOICE_SUBSCRIPTION
        !grantAuthorized ->
            CampaignAgentReadiness.NO_VALID_EXACT_GRANT
        !dialerRoleReady -> CampaignAgentReadiness.DIALER_ROLE_REQUIRED
        else -> CampaignAgentReadiness.READY
    }
}
