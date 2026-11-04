# AI Calls — M1 source checkpoint, 2026-11-04

## Scope / immutable evidence

- Starting `main` HEAD: `b9b95d2ca9286366ed4bf46ff5c80f988c51c84a`. Source after F-050/F-051 fixes: `a616795e8dc9ec46f049ea700d91f6d97abc9ac6` (documentation update does not change runtime).
- `413676be`: typed preference, validation/registry and runtime readiness. `84c27082`: swappable Android STT/TTS adapters and JVM profile tests. `de0538d5`: separate screen, voice selector, offline probe and menu entry. `8ee9da04`: immutable profile snapshot across app-owned campaign and runtime. `8bf21c94`: isolated persistence/migration/invalid-ID Android instrumentation tests. `1f7f78fc`: exact-profile prewarm cache binding and STOP invalidation. `bc019ce`: preview READ_PHONE_STATE failure handling (F-050). `466d18ab`: offline UI instrumentation contract and cautious readiness wording. `990a8dd8`: typed `DialogueEngineProvider` for original E2B prewarm. `a616795e`: preserve `NativeConversationContextBackend.resetConversationContext` (F-051).
- Stable configuration is `ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL`, `pl-PL`, default automatic offline voice. No new grant; no new model downloader. Unsupported alternatives disabled rather than silently falling back.
- F-049 withdrawn by owner and not modified. No changes to VAD, ACK, RX/TX Samsung/Shizuku, authorization, output approval or chat transcript persistence.

## Actual QA receipts, not promises

- Mac Host Ops `aicalls-20261104-audio-m1-host-qa-v1`, pinned `8ee9da04fd86007e2470f224f933915ea41f72be`: **FAILED / exit 1** 2026-11-04 06:44:36 UTC. Deliberately mutated `AudioEngineRegistry` RED failed expected profile test, then restored source; JVM unit passed; `scripts/verify_host.sh` failed during lint (1 error, 236 warnings), location `AudioEngineSettingsActivity.previewVoice`, `MissingPermission` for `TelecomManager.isInCall`. No fabricated GREEN or S22 proof.
- F-050 source fix `bc019ce`: guard `READ_PHONE_STATE`, treat missing permission/phone status as unsafe only for optional *preview*, not a new permission or gate for campaign or saving profile.
- Mac `aicalls-20261104-audio-m1-postfix-host-qa-v2` at `bc019ce`: **DONE/0 PASS**, full Gradle test/lint/assemble, 248 Python, anti-friction. Mac `aicalls-20261104-audio-m1-final-head-host-qa-v3` at `466d18ab`: **DONE/0 PASS** full host QA and Android UI instrumentation APK compiled.
- Mac `aicalls-20261104-audio-m1-provider-final-host-qa-v4` at `990a8dd8`: **FAILED/1**, Kotlin compiler: generalized `TextCallAgentBackend` hides `resetConversationContext` used by script prewarm. This is **F-051**, not a regression in VAD/ACK or network. `a616795e` source fixes cast to original `NativeConversationContextBackend` and preserves prewarm context reset; exact Mac `aicalls-20261104-audio-m1-provider-postfix-host-qa-v5` **QUEUED / QA_PENDING**, not yet a PASS.
- Native S22 read-only preflight `aicalls-20261104-audio-m1-s22-readonly-preflight-v1`: **DONE/0**, exact S22+ and serial, 2x IDLE, dialer role, no running campaign, no pending retry and readable grant. The first install task `aicalls-20261104-audio-m1-s22-offcall-acceptance-v1` **FAILED SAFELY BEFORE INSTALL** because pinned Mac QA v4 was FAIL. The replacement task `...-acceptance-v2` has a hard dependency on v5 full QA PASS, then requires renewed on-device safety checks; **QA_PENDING**, no device test or install may be claimed yet.
- Exact S22 SM-S906B ADB RFCT70L7E8J new APK/instrumentation **OPEN / NOT YET VERIFIED**. Historical installed code `268be1f8f13502c7202822438b501706e0fcb0e1` remains only proven APK until new safe IDLE/role/retry/grant fingerprint double check and result.

## Remaining work / gates

1. Read full Mac receipt; fix any build/lint/test failure in source, re-run exact SHA; anti-friction check must be real.
2. After host PASS: physically verify S22 model+ADB serial, two IDLE samples, dialer role, no app-owned campaign or retry; fingerprint existing grants/skills before and after. Only then safe off-call install, isolated store instrumentation and four native synthetic Gemma RAW turns. **No real call.**
3. M2 Parakeet PL pinned model/MediaPipe runtime, M3 Piper PL, M4 multimodal PCM→Gemma require independent implementation and measurable S22 proof. F-048, F-047, F-045 remain separate OPEN; carrier-side CLIR result still unproven.

## Closure — 2026-11-04 (new results, no calls)

- Mac `aicalls-20261104-audio-m1-provider-postfix-host-qa-v5` **DONE/0 PASS**, pinned `a616795e8dc9ec46f049ea700d91f6d97abc9ac6`: full Gradle build/unit/lint/assemble + 248 Python, anti-friction. S22 `aicalls-20261104-audio-m1-s22-offcall-acceptance-v2` **DONE/0 PASS**, pinned to same source: exact `SM-S906B`, 2× IDLE, dialer and grant/retry/skills checks; both APKs installed, isolated store/UI native tests PASS and 4 synthetic Gemma RAW turns PASS, final IDLE and unchanged grant/skills/audio preferences. No outgoing call.
- **M1: PASS for source + device off-call only.** Separate engine menu, type-safe stored default, offline voice selector, Android STT/TTS ports and E2B dialogue provider tested. No real cellular speech/far-end TX, CLIR effect or live-model switching acceptance inferred from synthetic tests.
- F-050 and F-051 **VERIFIED for the Mac/off-call scope**, not broader physical live-call claims. Historical failed tasks remain intact.
- M2 Parakeet PL, M3 Piper PL, M4 Gemma Audio Direct remain OPEN. F-045/F-047/F-048 and external CLIR completion remain OPEN. F-049 withdrawn.
