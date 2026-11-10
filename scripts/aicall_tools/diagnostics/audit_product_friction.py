#!/usr/bin/env python3
"""Static anti-friction regression gate. Never accesses a device, network or private recordings.

Fail on *known* artificial blockers reintroduced into production, or on removed
current authorization rules. New candidate thresholds require technical classification,
not blanket-rejected: data integrity, Android permissions and resource limits matter.
"""
from __future__ import annotations

import re
from pathlib import Path


def audit(root: Path) -> list[str]:
    errors: list[str] = []

    def read(path: str) -> str:
        file = root / path
        if not file.is_file():
            errors.append(f"{path}: required source missing")
            return ""
        return file.read_text(encoding="utf-8")

    prefix = "app/src/main/kotlin/pl/michalmatu/aicallbridge/"
    ui = read(prefix + "campaign/AppointmentMvpActivity.kt")
    pilot = read(prefix + "developerrelay/SupervisedCallPilotActivity.kt")
    grant = read(prefix + "campaign/CampaignAuthorizationGrant.kt")
    store = read(prefix + "campaign/CampaignAuthorizationStore.kt")
    executor = read(prefix + "campaign/AppOwnedClirCampaignExecutor.kt")
    recorder = read(prefix + "developerrelay/CallSessionEvidenceRecorder.kt")
    probe = read(prefix + "conversation/AndroidConversationRuntime.kt")
    android_test = read(
        "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/"
        "AndroidCampaignAuthorizationStoreContractTest.kt"
    )
    agents = read("AGENTS.md")
    project = read("docs/PROJECT.md")
    security = read("docs/architecture/SECURITY.md")

    def require(path: str, text: str, needle: str) -> None:
        if needle not in text:
            errors.append(f"{path}: missing expected anti-friction wiring: {needle}")

    def forbid(path: str, text: str, needle: str) -> None:
        if needle in text:
            errors.append(f"{path}: prohibited legacy blocker resurfaced: {needle}")

    # Protect current operating rules, not a fossilized historical issue ledger.
    if not agents.startswith("# AI Calls — zasady pracy agentów"):
        errors.append("AGENTS.md: current operating rules must remain the first heading")
    require("AGENTS.md", agents, "Nie dodawaj sztucznych ograniczeń")
    require("AGENTS.md", agents, "docs/README.md")
    require("docs/PROJECT.md", project, "## Otwarte")
    require("docs/PROJECT.md", project, "G6.1")
    require("docs/architecture/SECURITY.md", security, "CallCommitmentGate")
    require("docs/architecture/SECURITY.md", security, "FactDisclosurePolicy")
    require("docs/architecture/SECURITY.md", security, "STOP")
    require("docs/architecture/SECURITY.md", security, "FLAG_SECURE")

    # An automatic continuation must remain tied to the approved grant and STOP.
    continuation = read(prefix + "campaign/CampaignContinuationStore.kt")
    service = read(prefix + "campaign/AppOwnedCampaignService.kt")
    require("CampaignContinuationStore.kt", continuation, "(context.applicationContext ?: context)")
    require("CampaignAuthorizationStore.kt", store, "preferencesName: String = PREFERENCES_NAME")
    require("CampaignContinuationStore.kt", continuation, "preferencesName: String = PREFERENCES_NAME")
    continuation_test = read(
        "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/"
        "CampaignContinuationStoreContractTest.kt"
    )
    require("AndroidCampaignAuthorizationStoreContractTest.kt", android_test, ".targetContext")
    require("CampaignContinuationStoreContractTest.kt", continuation_test, ".targetContext")
    require("AndroidCampaignAuthorizationStoreContractTest.kt", android_test,
            "campaign_authorization_grant_instrumentation_v1")
    require("CampaignContinuationStoreContractTest.kt", continuation_test,
            "campaign_continuation_instrumentation_v1")
    forbid("AndroidCampaignAuthorizationStoreContractTest.kt", android_test,
           'getSharedPreferences("campaign_authorization_grant_v1"')
    forbid("CampaignContinuationStoreContractTest.kt", continuation_test,
           'getSharedPreferences("campaign_continuation_v1"')
    require("CampaignAuthorizationStore.kt", store, "(context.applicationContext ?: context)")
    require("CampaignContinuationStore.kt", continuation, "fun planRetry(")
    require("CampaignContinuationStore.kt", continuation, "fun clear()")
    require("AppOwnedCampaignService.kt", service, "continuationStore.clear()")
    reconciliation = read(prefix + "campaign/CampaignStartReconciliationPolicy.kt")
    require("CampaignStartReconciliationPolicy.kt", reconciliation,
            "CampaignStartDisposition.QUEUE_REPLACEMENT")
    require("CampaignStartReconciliationPolicy.kt", reconciliation,
            "CampaignStartDisposition.REUSE_SAVED")
    require("AppOwnedCampaignService.kt", service, "CampaignStartReconciliationPolicy.decide(")
    require("AppOwnedCampaignService.kt", service, "awaitPhoneIdle()")
    require("AppOwnedCampaignService.kt", service, "checkSelfPermission(Manifest.permission.READ_PHONE_STATE)")
    require("AppOwnedCampaignService.kt", service,
            "continuationHandler.postDelayed(retry, PHONE_IDLE_RECHECK_MS)")
    forbid("AppOwnedCampaignService.kt", service,
           "if (AppOwnedClirCampaignExecutor.isRunning()) return")
    require("AppOwnedCampaignService.kt", service, "continuationGeneration != generation")
    require("AppOwnedCampaignService.kt", service,
            "campaignRequested || continuationStore.load() != null")
    require("AppOwnedCampaignService.kt", service, "isContinuationAuthorized(state)")
    require("AppOwnedCampaignService.kt", service, "val restoring = intent == null && continuationStore.load() != null")
    # A long transcript must survive both the Android caller and the host broker;
    # do not preserve the obsolete one-sided character limits.
    broker = read("scripts/aicall_tools/text/openai_text_broker.py")
    android_text = read(prefix + "textagent/BackendOpenAiTextBackend.kt")
    forbid("openai_text_broker.py", broker, "MAX_INPUT_CHARS = 4_000")
    forbid("openai_text_broker.py", broker, "MAX_OUTPUT_CHARS = 2_000")
    forbid("openai_text_broker.py", broker, "MAX_OUTPUT_TOKENS = 160")
    forbid("BackendOpenAiTextBackend.kt", android_text, "MAX_INPUT_CHARS = 4_000")
    require("openai_text_broker.py", broker, "MAX_CLIENT_BODY_BYTES = 256 * 1024")
    require("BackendOpenAiTextBackend.kt", android_text, "MAX_RESPONSE_CHARS = 1024 * 1024")
    require("campaign/AppointmentMvpActivity.kt", ui, "grants.save(task.grant(")
    require("campaign/AppointmentMvpActivity.kt", ui, "ACTION_START_BOOK_APPOINTMENT")
    require("campaign/AppointmentMvpActivity.kt", ui, "selectedFields.filterValues { it.isChecked }")
    require("campaign/AppointmentMvpActivity.kt", ui, "pendingAuthorize = true")
    forbid("campaign/CampaignAuthorizationGrant.kt", grant, "expiresAtEpochMs")
    require("campaign/CampaignAuthorizationGrant.kt", grant, "val recordCallEvidence: Boolean = false")
    require("campaign/CampaignAuthorizationStore.kt", store, "getBoolean(KEY_RECORD_CALL_EVIDENCE, false)")
    require("campaign/CampaignAuthorizationStore.kt", store, "fun disableEvidenceRecording()")
    require("campaign/AppOwnedClirCampaignExecutor.kt", executor,
            "evidenceRecorder = if (grant.recordCallEvidence)")
    for path, data in (("ui", ui), ("executor", executor), ("recorder", recorder)):
        for token in ("CallSessionEvidenceConsent", "armNextCall", "ARM_TTL_MS"):
            forbid(path, data, token)
    forbid("developerrelay/SupervisedCallPilotActivity.kt", pilot, "Uruchomić AI w rozmowie?")
    require("developerrelay/SupervisedCallPilotActivity.kt", pilot, "startPilot(gemmaMode.isChecked)")
    resume = pilot.split("override fun onResume()", 1)
    if len(resume) != 2:
        errors.append("SupervisedCallPilotActivity.kt: expected onResume")
    else:
        resume_body = resume[1].split("private fun refreshModelStatus()", 1)[0]
        forbid("SupervisedCallPilotActivity.onResume", resume_body, "confirmAttach()")
        forbid("SupervisedCallPilotActivity.onResume", resume_body, "awaitAudioRoute()")
    for text, token in ((probe, "const val MAX_TURNS = 10"),
                        (probe, "const val MAX_NO_SPEECH_RETRIES = 3")):
        forbid("AndroidConversationRuntime.kt", text, token)
    event_test = read("app/src/test/kotlin/pl/michalmatu/aicallbridge/developerrelay/CallDiagnosticEvidenceTest.kt")
    require("CallDiagnosticEvidenceTest.kt", event_test, "assertEquals(1100, log.render().lineSequence().count")
    forbid("CallDiagnosticEvidenceTest.kt", event_test, "assertEquals(1024, log.render()")
    require("AndroidCampaignAuthorizationStoreContractTest.kt", android_test,
            "assertEquals(10, successes)")
    forbid("AndroidCampaignAuthorizationStoreContractTest.kt", android_test,
           "assertEquals(5, successes)")
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[3]
    errors = audit(root)
    for error in errors:
        print("BLOCKER_REGRESSION: " + error)
    if errors:
        print(f"ANTI_FRICTION=FAIL ({len(errors)} known regressions)")
        return 1

    print("ANTI_FRICTION=PASS; known regressions absent and current rules preserved")
    print("AUDIT_SCOPE=static_only; candidate model/transport timeouts need functional testing")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
