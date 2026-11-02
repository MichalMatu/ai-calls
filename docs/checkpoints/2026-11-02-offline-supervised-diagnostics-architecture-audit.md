# AI Calls — offline code audit, modular diagnostics and next hardware gate

**Date:** 2026-11-02. **Mode:** GitHub Connector + exact Mac Local Agent. **Phone:** deliberately absent; no call, ADB, installation, CLIR, dialer change or physical audio test was attempted. This document is an **offline source checkpoint**, not a claim of completed real-call acceptance.

## Source baseline and exact scope

- Starting `main`: `ec743d9503e2246e7fa11a136bc4155c25b717ef`.
- New diagnostic code at `cce1fe99c7110f5c43a5f5406cdcae7b8238ed3e` (later documentation-only commits may move HEAD). Always verify fresh HEAD and actual tree.
- Inspected the currently active supervised path: `SupervisedCallPilotActivity`, `SupervisedCallPilotPolicy`, `ChatRelayLiveCallProbe`, `ChatRelayDownlinkPump`, `AuthorizedPcmUplinkWriter`, `SupervisedGemmaDialogueBackend`, `SupervisedReadOnlyDialogueBackend`, `LocalSpeechTextPipeline`, `OnDeviceSpeechInput`, `PcmEndOfUtteranceDetector`, `CallMediaSessionCoordinator`, `ShizukuCallMediaSessionBackend`, Samsung call-mode/watchdog and RX/TX entrypoints, app manifests, relevant focused tests and security/freeze rules. Examined repository layout and the current handoff/roadmap.
- **Audit limit:** this is a thorough targeted critical-path review, **not** proof that every one of the repository's hundreds of files is defect-free. Previously accepted S22 audio backend/frozen boundaries were inspected but not changed.

## Root-cause boundary — do not guess

The live symptom was: `AUDIO_ROUTE_NOT_READY` while the user reported a manually active Orange call. Code shows an explicit `TelecomManager.isInCall` + `AudioManager.MODE_IN_CALL` double gate, followed by equivalent deeper media safeguards. This supports a *mode/readiness mismatch*, **not** a proven cause such as Bluetooth, speakerphone, system race, Samsung attribution, or SIM routing. The only ADB mode sample in the last chat was taken **after** the call, at `mCallState=0 / MODE_NORMAL` and cannot answer the live question. The existing bounded 3 s route retry is safe but may not fix a persistent mismatch. Never bypass `MODE_IN_CALL` or change frozen Samsung media code based on this symptom alone.

## New source changes: focused diagnostics extraction

1. `SupervisedCallReadinessDiagnostics.kt` captures mic/phone permission, Shizuku readiness, Telecom in-call/managed-call and AudioManager mode into **one immutable observation**. Both failure decision and label/report derive from the same observation, preventing the previous double-read discrepancy. It emits only a fixed field schema into app-private `supervised-call-readiness-report.txt`.
2. `CallDiagnosticEvidence.kt` provides a synchronized session diagnostic ledger for concurrent RX/STT/LLM/TTS/TX callbacks; rendering no longer iterates a mutable list modified concurrently. A bounded line count/length and technical-code sanitizer limit accidental free-form values. The shared `PrivateDiagnosticReportStore` uses Android `AtomicFile` for latest-only, partial-write-safe persistence of both private reports.
3. `SupervisedCallPilotActivity.kt` now uses the extracted snapshot module in its bounded 3 s wait. It still **cannot start without a real call, Shizuku, phone/mic permission, MODE_IN_CALL, explicit on-screen consent**. It displays the actual observed mode after failure, and distinguishes report-write failure.
4. `ChatRelayLiveCallProbe.kt` now renders its synchronized ledger and persists the report atomically. Removed potentially content-bearing dialogue arguments and raw local-error strings; only presence metadata remains. Variable free-form failures use restricted technical codes or `redacted`.
5. Added focused `SupervisedCallReadinessDiagnosticsTest.kt` and `CallDiagnosticEvidenceTest.kt`: active call + wrong mode, off-call fail-closed, missing permissions, missing Shizuku, unknown mode, report schema/no transcript key, concurrent writers/snapshot, and bounded evidence.

**No new Gradle module.** This deliberately separates diagnostic responsibility within the existing `developerrelay` package first; introducing a Gradle module would impose dependency/circularity risk before physical first-call acceptance. The proven media/Shizuku, automatic call-ownership guard and product authority layers remain untouched.

## Verification / do not mislabel pending tests

- Historical **PASS, not rerun:** on-device Polish STT/TTS, locally approved Gemma 4, Shizuku, off-call media refusal, original no-call supervised UI and former full host gate. Their success applies only to the previous source and tested physical scope.
- Task `aicalls-diagnostics-modular-audit-host-20261102-v1`: **FAILED**. New source was pinned and clean but the first compile caught a leftover expression at end of `ChatRelayLiveCallProbe.kt`; **no new tests ran**. Syntax was corrected at `cce1fe99`.
- Task `aicalls-diagnostics-modular-audit-host-20261102-v2`: queued on the exact pinned source `cce1fe99` for **only** two new test classes, `:app:assembleDebug`, `:app:lintDebug`, static safety/privacy checks and clean-worktree proof; **no terminal result was published at checkpoint time**. Do not call it PASS or retry if its effects are UNKNOWN; inspect the exact immutable receipt first.
- Last observed Mac `local-agent:agent-control` status was `idle` at `2026-11-02T19:01:46Z`; it is a dated observation and **not proof of current host activity**. The v2 control task publication is an enqueue, not execution.
- **New APK NOT installed on S22**; no physical real-call RX/TX/STOP acceptance.

## Edge-case inventory and decisions

| Severity | Case / reasoning | Current decision / next proof |
|---|---|---|
| P0 | Host source build/test/lint not yet terminal after diagnostics refactor | Read v2 result; repair only verified compile/lint/test failure; run the smallest impacted regression |
| P0 | S22 disconnected, new APK absent; live RX/TX/STOP not accepted | Do not infer `PROVEN_S22` from host compile. Resume after exact device reconnect and manual consenting call |
| P1 | Telecom indicates call but app AudioManager reports another mode | Keep fail-closed; capture one timestamp-correlated app snapshot and read-only `dumpsys audio` / `telephony.registry` **during** a human-initiated call; only then isolate app-versus-helper mode attribution |
| P1 | UI destroyed/backgrounded during capture, consent confirmation or route wait | Existing onStop revokes AI media, handler route wait is canceled; retain as safe-by-default; assess UX after physical test |
| P1 | STOP while a 20 ms PCM frame is already in the TX pipe | `AuthorizedPcmUplinkWriter` rechecks authorization per frame, but cannot retract already accepted frames; remote audible silence requires acoustic verification |
| P1 | Multi-threaded report lines and teardown could race | New synchronized ledger/atomic report; targeted concurrent unit tests must pass before acceptance |
| P1 | Model dialogue fields might contain private tokens | Remove argument/local error content; no numbers/audio/transcripts in diagnostics; keep future report fields strictly typed |
| P1 | `ChatRelayLiveCallProbe` mixes supervised mode, CLIR control, transcript handling, telemetry and media orchestration (~700 lines) | Plan split into `SupervisedSession` / `TurnEngine` / `DiagnosticEvidence` / separate campaign adapter, but **do not** refactor frozen physical boundary before live evidence |
| P2 | Calls outside `MODE_IN_CALL` may be genuine but incompatible with the frozen route | Never switch media APIs/phone role to silence a UI error; investigate independently |
| P2 | STT startup, Google packages, punctuation, split PCM, silence/noise, missing model | Previously covered or explicitly bounded; rerun only if new observed failure points to a changed component |
| P2 | Telephony turns > one, no-speech retries, model fallback, session lease/restart | Existing limits remain; the real first-call acceptance should capture counters for up to three turns, not run open-ended carrier loops |
| P2 | Diagnostic ledger cap (1024 lines, 256 chars/line) | Sufficient for current max-3-turn supervised pilot; if an overflow is observed, preserve terminal counters in a small separate final summary before extending logging |
| P2 | App-private report overwrite, stale reports and temporal correlation | `AtomicFile` prevents partial writes, but the next iteration may need per-attempt monotonic timestamp / status; avoid storing caller identity |
| P2 | Debug probes and their permissions | Main manifest keeps pilot activity non-exported, diagnostic probes require DUMP; no new exported components or network transport introduced |

## Modularity recommendation

**Already extracted:** readiness collection/policy snapshot; concurrent evidence ledger; single atomic storage adapter. This is the highest-value low-risk reduction of Activity/relay coupling.

**Next, conditional on real failure rather than speculative redesign:** split the monolithic `ChatRelayLiveCallProbe` behind an interface for (1) supervised safe dialogue, (2) audio turn orchestration, (3) immutable diagnostic timeline and (4) separately authorized CLIR workflow. No new public component, service, extra permission or second authority store. Preserve one exact speech approval gate, one callback generation/lease, synchronous STOP revocation, and fail-closed call ownership.

**Not recommended now:** rewrite Samsung/Shizuku RX/TX, change default dialer, broaden `MODE_IN_COMMUNICATION` to count as cellular audio, introduce a remote diagnostic server, store PCM/transcripts, or repeat the whole previous physical acceptance matrix.

## Recovery / exact next sprint

1. Read `docs/HANDOFF_NEXT_CHAT.md` and this checkpoint; resolve task `aicalls-diagnostics-modular-audit-host-20261102-v2` (check **terminal result**, not branch commit/heartbeat). If no result, check host liveness and report that preflight is blocked; do not assert PASS.
2. Run/fix only new diagnostics-focused tests, compile and lint for the exact reviewed source. No previous STT/TTS/Gemma or unrelated Python full-test rerun absent specific regression.
3. Once S22 physically reconnects, match exact `SM-S906B` / `RFCT70L7E8J`, verify `IDLE`, and use a scoped `adb install -r` with preserved data; no deployment while a call is active.
4. On a **user-started, human-consented** test call with explicit UI AI confirmation, capture a live snapshot before and after route check. If mismatch persists, read only sanitized report and contemporaneous system call/mode evidence; never dial `*100` or `510 100 100` on behalf of the user.
5. If media becomes active, assess RX bytes, `turn_*_stt_chars`, Gemma/script fallback count, TX bytes, remote perceived audibility and acoustic STOP; verify user still controls the Google Phone call and manually ends it.
6. Preserve a sanitized terminal result; only then label the **real call** `PASS` or `FAIL`, adjust the smallest actual defect and revalidate affected boundary.

**Checkpoint status: OFFLINE REVIEW/REFACTOR SOURCE SAVED; NEW HOST GATE PENDING, PHONE DEPLOY OPEN, SUPERVISED LIVE AUDIO/STOP OPEN, AUTONOMOUS OWNED DIAL BLOCKED.**
