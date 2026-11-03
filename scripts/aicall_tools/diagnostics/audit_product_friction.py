#!/usr/bin/env python3
"""Static anti-friction regression gate. Never accesses a device, network or private recordings.

Fail on *known* artificial blockers reintroduced into production, or on an absent
canonical ledger. New candidate thresholds are reported for human classification,
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
    ui = read(prefix + "campaign/CampaignToolActivity.kt")
    pilot = read(prefix + "developerrelay/SupervisedCallPilotActivity.kt")
    grant = read(prefix + "campaign/CampaignAuthorizationGrant.kt")
    store = read(prefix + "campaign/CampaignAuthorizationStore.kt")
    executor = read(prefix + "campaign/AppOwnedClirCampaignExecutor.kt")
    recorder = read(prefix + "developerrelay/CallSessionEvidenceRecorder.kt")
    probe = read(prefix + "developerrelay/ChatRelayLiveCallProbe.kt")
    android_test = read(
        "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/"
        "AndroidCampaignAuthorizationStoreContractTest.kt"
    )
    agents = read("AGENTS.md")
    ledger = read("BLOCKERS.md")
    history = read("docs/FRICTION_BLOCKER_LEDGER.md")

    def require(path: str, text: str, needle: str) -> None:
        if needle not in text:
            errors.append(f"{path}: missing expected anti-friction wiring: {needle}")

    def forbid(path: str, text: str, needle: str) -> None:
        if needle in text:
            errors.append(f"{path}: prohibited legacy blocker resurfaced: {needle}")

    if not agents.startswith("# ⛔ ZERO NOWYCH BLOKERÓW"):
        errors.append("AGENTS.md: mandatory anti-blocker rule must remain the first heading")
    require("AGENTS.md", agents, "BLOCKERS.md")
    require("BLOCKERS.md", ledger, "OPEN")
    require("BLOCKERS.md", ledger, "FIXED_IN_SOURCE")
    require("BLOCKERS.md", ledger, "VERIFIED")
    for identifier in range(1, 18):
        if not re.search(rf"\bF-{identifier:03d}\b", ledger):
            errors.append(f"BLOCKERS.md: F-{identifier:03d} must stay tracked")
    require("docs/FRICTION_BLOCKER_LEDGER.md", history, "BLOCKERS.md")

    # An automatic continuation must remain tied to the approved grant and STOP.
    continuation = read(prefix + "campaign/CampaignContinuationStore.kt")
    service = read(prefix + "campaign/AppOwnedCampaignService.kt")
    require("CampaignContinuationStore.kt", continuation, "fun planRetry(")
    require("CampaignContinuationStore.kt", continuation, "fun clear()")
    require("AppOwnedCampaignService.kt", service, "continuationStore.clear()")
    require("AppOwnedCampaignService.kt", service, "isContinuationAuthorized(state)")
    require("AppOwnedCampaignService.kt", service, "val restoring = intent == null && continuationStore.load() != null")
    require("campaign/CampaignToolActivity.kt", ui, "recordCallEvidence = evidenceChoice.isChecked")
    require("campaign/CampaignToolActivity.kt", ui, ".setView(evidenceChoice)")
    require("campaign/CampaignAuthorizationGrant.kt", grant, "val recordCallEvidence: Boolean = false")
    require("campaign/CampaignAuthorizationStore.kt", store, "getBoolean(KEY_RECORD_CALL_EVIDENCE, false)")
    require("campaign/CampaignAuthorizationStore.kt", store, "fun disableEvidenceRecording()")
    require("campaign/AppOwnedClirCampaignExecutor.kt", executor,
            "evidenceRecorder = if (grant.recordCallEvidence)")
    for path, data in (("ui", ui), ("executor", executor), ("recorder", recorder)):
        for token in ("CallSessionEvidenceConsent", "armNextCall", "ARM_TTL_MS"):
            forbid(path, data, token)
    forbid("campaign/CampaignToolActivity.kt", ui, "Zapisz następną rozmowę")
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
        forbid("ChatRelayLiveCallProbe.kt", text, token)
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

    print("ANTI_FRICTION=PASS; known blockers absent and canonical ledger linked")
    print("AUDIT_SCOPE=static_only; candidate model/transport timeouts need functional testing")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
