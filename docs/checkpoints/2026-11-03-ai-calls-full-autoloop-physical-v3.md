# AI Calls — autonomous S22 diagnostic loop, physical iteration (2026-11-03)

## Verified execution path

`MichalMatu/ai-calls` `main` was verified before edits. The Mac Local Agent installation is exactly `macbook-michal/cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; the only test handset is Samsung `SM-S906B` on `RFCT70L7E8J`. No Codex, ChatGPT Work or GitHub Actions.

The operator-free path now uses:

```text
GitHub Connector -> immutable Mac Local Agent Host Ops task
 -> exact S22/USB/ADB and IDLE check
 -> build + focused tests + lint + install debug APK
 -> Android DUMP-protected DiagnosticProbeActivity
 -> internal CampaignAgentControl / app-owned authorization check
 -> AppOwnedCampaignService / Telecom / exact InCallService Call
 -> local RX -> STT -> deterministic/Gemma -> TTS -> actual TX writer
 -> private numeric report + optional authorized WAV USB export
 -> failure classification -> source fix -> build/install/physical retest
```

The Android runtime owns all external effects and rechecks the exact grant, SIM and OS dialer role. Mac/ADB does not manufacture permissions, grant scope, or call targets. The developer agent applies code fixes between immutable source revisions; the current Python runner **does not autonomously invent arbitrary source-code patches** or promise unattended background operation without an active controller.

## Physical and source receipts

| Task / revision | Verified outcome |
| --- | --- |
| `aicalls-20261103-f028-headless-build-install-preflight-v1` / `a0370408` | Debug APK build/lint and exact S22 install PASS; DUMP-protected campaign preflight `READY`; no UI taps |
| `aicalls-20261103-full-autoloop-physical-cycle-v1` / `fd219de4` | Python QA, Gradle/build/install PASS; valid application grant and Android role; actual OFFHOOK, internal targeted STOP, final IDLE |
| `aicalls-20261103-private-call-pipeline-metrics-v1` | Real runtime counters: 5 turns complete, RX 1,142,400 B, TX 322,986 B, six Gemma decisions; failure `backend_supervisor_relay_disabled` |
| `aicalls-20261103-autoloop-runtime-phase-physical-retest-v2` / `f0e0fc4` | Python QA PASS, OFFHOOK, local runtime started, targeted STOP, IDLE; correctly no full-call PASS declaration |
| `aicalls-20261103-f029-local-recovery-full-s22-cycle-v3` / `ebcf814f` | Kotlin local-only fallback test/build/lint and S22 install PASS; authorized physical OFFHOOK → IDLE **without Mac forced STOP** |
| `aicalls-20261103-autonomous-retry-telecom-monitor-v5` | Further physical activity `0,2` → `0`; 7 turns complete, RX 2,209,280 B, TX 751,710 B, seven Gemma decisions, final `owned_call_ended`, not the disabled-relay error |
| `aicalls-20261103-f032-private-metrics-race-final-qa-v7` / `5c8dc40a` | Python parser/regression + anti-friction PASS; S22 historical read-only `TECHNICAL_COUNTS_PRESENT_HISTORICAL` with no redial |

## Verified fixes and exact limits

- **F-028**: Diagnostic and authorized app-owned START no longer depend on navigating or clicking the `Campaign tools` UI; DUMP permission limits the exported probe to shell/privileged callers.
- **F-029**: An uncertain local model decision no longer routes into the disabled supervisor backend and immediately aborts. A task-scoped, noncommittal local clarification continues under the same output-approval policy. Physical follow-up ends with `owned_call_ended` instead of `backend_supervisor_relay_disabled`.
- **F-030**: A clean build and IDLE cannot be misreported as live-call PASS.
- **F-031**: An elapsed Mac observation period does not automatically send STOP, erase task continuation, or terminate an active authorized call. Actual call lifecycle and targeted cleanup remain app-owned.
- **F-032**: The Mac script parses only numeric RX/STT/Gemma/TTS/TX evidence and machine codes from `files/chat-relay-live-call-report.txt`. Since this report can be written shortly **after** Telecom goes IDLE, it now polls for a completed current-session report; regression PASS. Fresh on-device asynchronous polling with the new Python revision is pending physical acceptance, even though historical S22 metrics were read successfully.

The existing grant has `record_call_evidence=false`. Therefore USB `NO_DATA` is **correct**, not an ADB transfer defect. This setting was not changed; no private audio, counterpart transcript, telephone number, grant ID or SIM identifier was published through Git/task-results. Real RX/TX PCM counters are not proof that the human counterparty heard TX. `chat_relay_live_call_success=false` and the external CLIR change remain unconfirmed. No synthetic, off-call or classifier evidence can promote these into a claimed end-to-end external-effect PASS.

## Next autonomous step

For the next accepted, still-authorized device session, execute the pinned current-source S22 cycle after exact IDLE/readiness checks, reuse unchanged PASS receipts, and validate F-032 **current-session** report correlation and the local recovery action/source. Analyze the resulting machine-only failure code and fix the next confirmed defect. Do not activate recording implicitly, replay any uncertain call, widen the grant, or force STOP at the end of a test observation. Keep private diagnostics on Mac under `~/Documents/AI-Calls/private-exports/`; update `BLOCKERS.md` with the actual test receipt.

A finished end-to-end acceptance still requires evidence of a real external task result and independent service/network verification where available. The current accomplishment is a physically exercised, developer-orchestrated diagnose → source fix → QA/build/install → retest loop and proof of real local data-plane turns.
