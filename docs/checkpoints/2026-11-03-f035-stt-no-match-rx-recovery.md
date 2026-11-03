# AI Calls — F-035 STT NO_MATCH and RX capture recovery, 2026-11-03

## Verified scope and source

- Repository `MichalMatu/ai-calls`; latest source at this checkpoint `7176f32bcb0e3f96dec99d790fd5159cc6427fa7`. **Check live `main` HEAD first on resume**, do not reuse pinned SHA blindly.
- Mac Local Agent: `MichalMatu/local-agent`, branch `agent-control`, host `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Test handset S22 `RFCT70L7E8J` / `SM-S906B`.
- Tooling restrictions: GitHub Connector + Mac Local Agent, no Codex, Work or GitHub Actions. Do not publish phone numbers, transcripts, grants, audio or raw private reports in Git.
- Mandatory source context: `AGENTS.md`, `BLOCKERS.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/USB_PRIVATE_DIAGNOSTICS.md`, this checkpoint and previous `docs/checkpoints/2026-11-03-f033-f034-fast-dialogue-prewarm-handoff.md`.

## Physically verified before this change

Mac `aicalls-20261103-f033-f034-fast-guard-physical-call-retest-v2` DONE/0 on installed `eb2f5058`: app-owned exact authorized call, `OFFHOOK`, 5 local turns, RX 2,161,920 bytes, TX 642,090 bytes, first EOS→TX 10,567 ms, maximum 12,175 ms, max repetitive `STATE_TASK_SUBJECT` streak 1, `IDLE`, app-owned STOP for subsequent retries only. Call ended with `stt_recognition_error_7`; **not** with external CLIR success. Thus F-033/034 narrowed, F-035 confirmed.

Mac `aicalls-20261103-f035-stt-nomatch-retry-build-install-v1` DONE/0 on `49d50fbfb22ab0434254386adea0f2df7e84d527`: anti-friction PASS, Python PASS, full Kotlin unit/build/lint PASS, APK installed on exact S22, app preflight READY and final `IDLE`. This is installed-source/build evidence, **not F-035 physical-call recovery acceptance**.

## Current fix after independent audit

1. `LocalSpeechRecoverableFailure.kt`: precisely Android STT errors 7 (`ERROR_NO_MATCH`) and 6 (`ERROR_SPEECH_TIMEOUT`) are transient while exact call + grant remain live; permission, pipe, model and other STT errors remain fatal/diagnosed.
2. `ChatRelayLiveCallProbe.kt`: a transient STT no-match no longer ends the session; it invokes the same call-owned listener again only after checking current call/media generation/authorization, without any fresh dial or artificial retry quota. The turn index is `turnsCompleted + 1`, so failed STT does **not** count as a completed dialogue turn.
3. Further audit found that the old `ChatRelayDownlinkPump.Capture` may still own RX frames when the recognizer pipe closes, causing `stt_pcm_write_failed` / `relay_downlink_capture_already_active`. `ChatRelayDownlinkPump.kt` now supports `abandonActiveCapture()`: detaches only that capture, never closes the shared call/stream; delayed old frames cannot terminate a new capture; `onAbandoned` closes optional private turn evidence. `ChatRelayLiveCallProbe.kt` calls this on transient STT errors before re-listening.
4. `ChatRelayDownlinkPumpTest.kt`: two new concurrency regressions for immediate reattachment and late in-flight frame after abandonment. `scripts/aicall_tools/diagnostics/s22_autoloop.py` exports only numeric `stt_transient_retries` and `stt_rx_capture_abandoned` from the private report; `scripts/tests/test_s22_autoloop.py` validates repeated events and absence of private text.
5. Source revision after these changes: `7176f32bcb0e3f96dec99d790fd5159cc6427fa7` (new file revisions listed in Git history). Do not claim these new concurrency fixes have been installed or physically accepted without evidence.

## Task now queued — retrieve results, do not duplicate

`aicalls-20261103-f035-rx-capture-detach-source-qa-v1` on Mac Local Agent control branch (task JSON under `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/`). Pinned source `7176f32bcb0e3f96dec99d790fd5159cc6427fa7`. Performs Python unit/anti-friction and full Kotlin unit/build/lint **only**; no installation, phone call, permission or grant changes. Check `task-results/aicalls-20261103-f035-rx-capture-detach-source-qa-v1.json` before re-running anything.

## Additional validated source QA, after checkpoint creation

Mac `aicalls-20261103-f035-rx-capture-detach-source-qa-v1` **DONE/0** (pinned `7176f32bcb0e3f96dec99d790fd5159cc6427fa7`): anti-friction PASS, Python regressions PASS, full Kotlin unit/assemble/lint **PASS** (`BUILD SUCCESSFUL in 2m 7s`), hardware effects NONE. This verifies the new concurrent RX capture release tests and the STT metadata-only parser. It does **not** verify in-call recovery or the remote carrier effect.

Safe off-call install task `aicalls-20261103-f035-rx-capture-detach-install-s22-offcall-v1` was queued in the same Local Agent path, pinned to `7176f32b`. It reuses the full QA receipt, assembles and installs only if exact S22 `SM-S906B`, no active cellular call/campaign continuation/service, and role is unchanged. It performs a headless preflight only — **no new phone call**. Check this task result before claiming current APK installed.

An attempted new physical-call task was **explicitly blocked by tool safety checks**. No new physical call was launched by that attempt; do not circumvent this denial via another task, tool, command, or alternate route. Read-only analysis and source-only QA may proceed.

## Physical installation acceptance (no call)

Mac `aicalls-20261103-f035-rx-capture-detach-install-s22-offcall-v1` **DONE/0** on source `7176f32bcb0e3f96dec99d790fd5159cc6427fa7`: APK assemble PASS, exactly one `SM-S906B` S22 attached, Telecom `IDLE`, default dialer ready, `APK_INSTALL=PASS`, headless `EXISTING_GRANT_READINESS=READY`, final `POST_INSTALL_IDLE=true`. No phone call, permission/grant change, recording or private content export. This supersedes the earlier *installation queued* note above.

F-035 is now source QA + hardware installation verified, **not live in-call STT recovery verified**. A physical-call task submitted earlier was expressly denied by tool safety checks; that attempt was not executed and must not be recreated via an alternate mechanism. External CLIR task success remains OPEN.

## Remaining acceptance

- **F-035:** pass source tests and, only via permitted authorized execution, show a live NO_MATCH event followed by a continued RX/STT turn **without** new dial, external authorization widening or corrupted captures. Until then status `FIXED_IN_SOURCE`, not physically VERIFIED.
- **F-033/034:** physical improvements in non-repetitive Gemma action and response latency are verified in their stated scope only; a 10–12 s local response is still slow for IVR.
- **End-to-end CLIR:** external task completion and independent remote audibility still OPEN. RX/TX byte counts and off-call probes are insufficient proof.
- `record_call_evidence=false` is an intentional existing grant condition; USB `NO_DATA` is expected and should not trigger silent recording.
- Keep canonical `BLOCKERS.md` updated with exact source SHA, test receipts, and remaining uncertainty.
