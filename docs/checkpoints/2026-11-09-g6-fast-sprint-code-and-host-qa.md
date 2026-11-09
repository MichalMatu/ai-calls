# AI Calls G6 — FAST sprint, code freeze and Mac host QA (2026-11-09)

**Source base:** `851cbe01b23f7f312f19fe0646823b49bc6df975`; **verified source HEAD:** `2bd841130ec2ca7b48172b5e676d0e900ee943fc` on `main`. No feature branches, force-push, Codex, Work, GitHub Actions, device install, real calls or APK package build.

## Integrated working code

1. `IncomingAssistantCoordinator`: reentrant readiness/ANSWERING/STARTING fences, exact Call active+exclusive check, revocation on competing call, preserve ENDED, five extra scenario tests.
2. `CallRealtimeAudioPump`: serialize barge-in flush and completed audio release under existing `gateLock`, preserving existing tombstones/bounded PCM queue; new canceled-old/fresh-response test. Does not add a local E2B TX speech-start monitor.
3. `RecordedConversationHistory`, `ArchivedConversationPlayer`, `ConversationHistoryActivity`: private per-turn RX/TX/ack WAVs ordered by timeline `elapsed_ms`; sequential playback of **available clips**, with canonical-path validation, idle/READ_PHONE_STATE checks, cancellation on pause/back; safe call-end + interrupted-RX reason display. Source recorder untouched; gaps and manual audio unresolved.
4. `OutgoingIvrDtmfGate`: bounded original Kotlin 256-step ledger, permitted digit and step validation, double exact-call/grant/STOP fence, typed effect statuses, no duplicate or automatic replay after UNKNOWN, deterministic 11 tests. **No Android tone emitted.** App-owned exact Telecom integration deferred until explicitly authorized.
5. `test-profile.sh`: FAST Android JVM now discovers same SDK paths as full host gate; no APK packaging, warm Gradle/daemon/cache retained, light pre-commit.

## Mac receipts (immutable remote results, no private WAV/transcripts)

- `aicalls-g6-sprint1-core-archive-focused-20261109-v1`: **FAILED** after focused incoming PASS because targeted Android Gradle could not locate SDK. This is a tooling RED, not a code compilation failure; root F-171.
- `aicalls-g6-sprint2-fast-auto-mac-20261109-v1`: **DONE/0**, `auto 851cbe0` selects changed `app`, `conversation-core`, Python/static; Android SDK fallback fixed, 14 executed/64 UP-TO-DATE, no APK.
- `aicalls-g6-sprint3-realtime-barge-focused-20261109-v1`: **DONE/0**, speech gate focused, 5 executed/71 UP-TO-DATE.
- `aicalls-g6-sprint4-ivr-gate-focused-20261109-v1`: **DONE/0**, 11 new tests, 3 s scoped Gradle.
- `aicalls-g6-sprint5-archive-outcome-focused-20261109-v1`: **DONE/0**, history focused, 13 executed/63 UP-TO-DATE.
- `aicalls-g6-sprint6-once-final-host-qa-20261109-v1`: **DONE/0**, source pinned `2bd8411`, preflight PASS, `full` unit+lint+Python+audio/security PASS, **398 Python tests OK**, 147 Gradle tasks (11 executed / 136 UP-TO-DATE), configuration-cache reused, daemon idle after gate, anti-friction PASS, no APK, clean Git. `UI_CAPTURE_RECEIPT=FAIL run_id_mismatch` is an expected negative test fixture inside passing Python suite, not a gate failure.

**Preserved without re-running:** G3 96/96 model turns, G5.3 18/18/36 WAV, native STOP, historic A8→S22 incoming samples, F-170 warm cache proof and 0/6 unassessed human audio reviews.

## Reuse/legal

`pipecat-ai/pipecat` BSD-2-Clause user-turn/speculation-gate ideas; `agent-next/call-use` MIT call lifecycle/outcome/BDD. No third-party code copied, no added dependency. Clinic Voice Agent has no confirmed copy license, DeskRoute is AGPL-3.0 (patterns only), LiveKit SIP/VoIP Apache-2.0 deferred; no Python/cloud worker imported. Existing Android/telephony/IdentityVault/CallCommitmentGate unchanged.

## Next single-track G6 acceptance

**SOURCE/HOST_QA_PASS; PHYSICAL_G6_OPEN.** On actual authorized S22: add/validate local E2B barge-in with observable RX speech-start and stale TTS cancel, exact INCOMING handover/resume without hangup, complete private archival coverage including manual segment if user consent/permissions permit (F-080), outgoing IVR/DTMF adapter bound to exact authorized grant, real call outcomes + independent booking confirmation, physically controlled INCOMING and OUTGOING. Test only new/changed contracts. Do not touch the device or build an APK until an actual IDLE authorized install/call is planned.
