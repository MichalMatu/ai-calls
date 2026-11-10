package pl.michalmatu.aicallbridge.campaign

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tripwire against reintroducing arbitrary limits already removed by the owner.
 * This checks wiring, not just an isolated policy helper that could remain unused.
 */
class NoArtificialCampaignBlockersContractTest {
    private fun source(relative: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/kotlin/pl/michalmatu/aicallbridge/$relative"),
            File(cwd, "app/src/main/kotlin/pl/michalmatu/aicallbridge/$relative"),
            File(cwd, "../app/src/main/kotlin/pl/michalmatu/aicallbridge/$relative"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("Missing production source: $relative", file != null)
        return checkNotNull(file).readText()
    }

    @Test fun authorizedCampaignDoesNotStopAtTenDialogueTurns() {
        val probe = source("conversation/AndroidConversationRuntime.kt")
        val executor = source("campaign/AppOwnedClirCampaignExecutor.kt")
        assertFalse(probe.contains("const val MAX_TURNS = 10"))
        assertFalse(probe.contains("maxTurns in 1..MAX_TURNS"))
        assertTrue(probe.contains("const val TASK_SCOPED_TURNS = Int.MAX_VALUE"))
        assertTrue(executor.contains("maxTurns = AndroidConversationRuntime.TASK_SCOPED_TURNS"))
        assertTrue(probe.contains("maxTurns != TASK_SCOPED_TURNS && turnsCompleted >= maxTurns"))
        assertTrue(probe.contains("callStillActive()"))
        assertTrue(executor.contains("ownedCall.isExactCallActive()"))
    }

    @Test fun duplexWavAndTranscriptAreNotArbitrarilyClipped() {
        val evidence = source("developerrelay/CallSessionEvidenceRecorder.kt")
        val ui = source("campaign/AppointmentMvpActivity.kt")
        for (removed in listOf("MAX_TURN_BYTES", "MAX_SESSION_BYTES", "RETAIN_SESSIONS",
            "MAX_TEXT_CHARS", "text.take(MAX_TEXT_CHARS)", "drop(RETAIN_SESSIONS - 1)")) {
            assertFalse("Arbitrary capture ceiling reappeared: $removed", evidence.contains(removed))
        }
        assertTrue(evidence.contains("MIN_OS_FREE_BYTES"))
        assertTrue(evidence.contains("ensureFreeSpace"))
        assertFalse(ui.contains("input.toList().takeLast(100)"))
        assertFalse(ui.contains("lines.joinToString(\"\\n\\n\").takeLast(24_000)"))
    }

    @Test fun recordedTurnDataStillRequiresOwnedScopedCall() {
        val executor = source("campaign/AppOwnedClirCampaignExecutor.kt")
        assertTrue(executor.contains("authorizationStore.isAuthorizedInFlight("))
        assertTrue(executor.contains("ownedCall.isExactCallActive()"))
        assertTrue(executor.contains("evidenceRecorder = if (grant.recordCallEvidence)"))
    }

    @Test fun explicitTaskIntakeHasNoSeparateGrantOrRenewalStep() {
        val ui = source("campaign/AppointmentMvpActivity.kt")
        val control = source("campaign/CampaignAgentControl.kt")
        val grant = source("campaign/CampaignAuthorizationGrant.kt")
        assertFalse(ui.contains("showAuthorizationConfirmation"))
        assertFalse(ui.contains("Authorize CLIR enable campaign"))
        assertFalse(grant.contains("expiresAtEpochMs"))
        assertFalse(control.contains("NO_VALID_EXACT_GRANT"))
        assertTrue(ui.contains("grants.save(task.grant("))
        assertTrue(ui.contains("ACTION_START_BOOK_APPOINTMENT"))
        assertTrue(source("DiagnosticProbeActivity.kt").contains("CampaignAgentControl.run("))
        assertTrue(control.contains("!requestStart -> \"NOT_REQUESTED\""))
        assertTrue(control.contains("store.save(ClirCampaignSpec.acceptEnableTask("))
        assertTrue(control.contains("AndroidTelecomOwnedCallController.canControl(context)"))
    }

    @Test fun optionalEvidenceBelongsToTheAcceptedTaskNotPerCallArming() {
        val ui = source("campaign/AppointmentMvpActivity.kt")
        val grant = source("campaign/CampaignAuthorizationGrant.kt")
        val store = source("campaign/CampaignAuthorizationStore.kt")
        val executor = source("campaign/AppOwnedClirCampaignExecutor.kt")
        val recorder = source("developerrelay/CallSessionEvidenceRecorder.kt")
        assertTrue(ui.contains("grants.save(task.grant("))
        assertTrue(ui.contains("selectedFields.filterValues { it.isChecked }"))
        assertTrue(store.contains("preferences.getBoolean(KEY_RECORD_CALL_EVIDENCE, false)"))
        assertTrue(store.contains("fun disableEvidenceRecording()"))
        assertTrue(grant.contains("val recordCallEvidence: Boolean = false"))
        assertTrue(executor.contains("evidenceRecorder = if (grant.recordCallEvidence)"))
        assertFalse(ui.contains("CallSessionEvidenceConsent"))
        assertFalse(executor.contains("CallSessionEvidenceConsent"))
        assertFalse(recorder.contains("ARM_TTL_MS"))
        assertFalse(recorder.contains("armNextCall"))
        assertFalse(recorder.contains("consume(context"))
    }

    @Test fun supervisedPilotDoesNotAddSecondDialogOrAutoStartOnReturn() {
        val pilot = source("developerrelay/SupervisedCallPilotActivity.kt")
        assertTrue(pilot.contains("startPilot(gemmaMode.isChecked)"))
        assertFalse(pilot.contains("Uruchomić AI w rozmowie?"))
        assertFalse(pilot.contains("confirmAttach() // The explicit consent dialog remains mandatory"))
        val resumeBlock = pilot.substringAfter("override fun onResume()")
            .substringBefore("private fun refreshModelStatus()")
        assertFalse("returning from dialer must not auto-start AI", resumeBlock.contains("confirmAttach()"))
        assertFalse("returning from dialer must not retry attach automatically", resumeBlock.contains("awaitAudioRoute()"))
    }

    @Test fun supervisedPilotCannotDestroyWaitingCampaignContinuation() {
        val service = source("campaign/AppOwnedCampaignService.kt")
        val pilot = service.substringAfter("private fun startSupervisedAudio()")
            .substringBefore("override fun onDestroy()")
        assertTrue(pilot.contains("campaignRequested || continuationStore.load() != null"))
        assertTrue(pilot.contains("statusStore.save("))
        assertFalse(pilot.substringBefore("val telecom =").lineSequence()
            .any { it.trimStart().startsWith("finish(") })
        assertFalse(pilot.substringBefore("val telecom =").contains("continuationStore.clear()"))
    }

    @Test fun duplicateStartAndNewGrantHandoffCannotStrandAnOwnedCall() {
        val service = source("campaign/AppOwnedCampaignService.kt")
        val reconciliation = source("campaign/CampaignStartReconciliationPolicy.kt")
        assertTrue(service.contains("CampaignStartReconciliationPolicy.decide("))
        assertTrue(service.contains("CampaignStartDisposition.ALREADY_RUNNING"))
        assertTrue(service.contains("CampaignStartDisposition.REUSE_SAVED -> Unit"))
        assertTrue(service.contains("CampaignStartDisposition.QUEUE_REPLACEMENT"))
        assertTrue(service.contains("awaitPhoneIdle()"))
        assertTrue(service.contains("continuationHandler.postDelayed(retry, PHONE_IDLE_RECHECK_MS)"))
        assertTrue(service.contains("getSystemService(TelecomManager::class.java)?.isInCall == true"))
        assertTrue(reconciliation.contains("CampaignStartDisposition.REUSE_SAVED"))
        assertTrue(reconciliation.contains("CampaignStartDisposition.QUEUE_REPLACEMENT"))
        assertFalse(service.contains("if (AppOwnedClirCampaignExecutor.isRunning()) return"))
    }

    @Test fun continuationRespectsExactGrantAndStopWithoutAnotherApprovalDialog() {
        val service = source("campaign/AppOwnedCampaignService.kt")
        val continuation = source("campaign/CampaignContinuationStore.kt")
        val executor = source("campaign/AppOwnedClirCampaignExecutor.kt")
        assertTrue(service.contains("val restoring = intent == null && continuationStore.load() != null"))
        assertTrue(service.contains("continuationStore.begin(grant.grantId, accountScope, now)"))
        assertTrue(service.contains("isContinuationAuthorized(state)"))
        assertTrue(service.contains("continuationStore.planRetry(grantId"))
        assertTrue(service.contains("continuationStore.clear()"))
        assertTrue(service.contains("continuationGeneration != generation"))
        assertTrue(continuation.contains("fun prepareAttempt("))
        assertTrue(continuation.contains("fun planRetry("))
        assertTrue(executor.contains("onAttemptFinished(probeSuccess && carrierReportedSuccess"))
    }

}
