package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import pl.michalmatu.aicallbridge.BuildConfig

internal object DebugAutoClirBootstrap {
    private const val PREFERENCES_NAME = "debug_auto_clir_bootstrap"
    private const val KEY_CONSUMED = "debug_auto_clir_e2e_20261008_v1_consumed"
    private const val GRANT_ID = "debug-auto-clir-e2e-20261008-v1"
    private const val ISSUER_EVIDENCE = "debug-one-shot-e2e:20261008-v1"

    fun maybeStart(context: Context): String? {
        if (!BuildConfig.DEBUG) return null

        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_CONSUMED, false)) {
            return null
        }

        val missingPermission = REQUIRED_PERMISSIONS.firstOrNull { permission ->
            appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermission != null) {
            return "Debug CLIR one-shot blocked: missing ${permissionLabel(missingPermission)} permission"
        }

        val accountScope = CampaignAccountScope.currentVoiceSubscription(appContext)
            ?: return "Debug CLIR one-shot blocked: default voice subscription unavailable"

        val nowEpochMs = System.currentTimeMillis()
        val grant = ClirCampaignSpec.newEnableGrant(
            accountScope = accountScope,
            nowEpochMs = nowEpochMs,
            grantId = GRANT_ID,
            issuerEvidenceRef = ISSUER_EVIDENCE,
        )
        CampaignAuthorizationStore(appContext).save(grant)

        val serviceIntent = Intent(appContext, AppOwnedCampaignService::class.java)
            .setAction(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE)

        return runCatching {
            appContext.startForegroundService(serviceIntent)
            check(
                preferences.edit()
                    .putBoolean(KEY_CONSUMED, true)
                    .commit(),
            ) { "debug_bootstrap_state_persist_failed" }
            "Debug one-shot CLIR campaign started"
        }.getOrElse { error ->
            "Debug CLIR one-shot start failed: ${error.javaClass.simpleName}"
        }
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.RECORD_AUDIO -> "microphone"
        Manifest.permission.CALL_PHONE -> "phone-call"
        Manifest.permission.READ_PHONE_STATE -> "phone-state"
        Manifest.permission.ANSWER_PHONE_CALLS -> "call-control"
        else -> "required"
    }

    private val REQUIRED_PERMISSIONS = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )
}
