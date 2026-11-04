# AI Calls — M1 source checkpoint, 2026-11-04

## Scope / immutable evidence

- Starting `main` HEAD: `b9b95d2ca9286366ed4bf46ff5c80f988c51c84a`. Source after F-050 fix: `bc019ce3921b4d23dd9946c9848952174363c685` (documentation update does not change runtime).
- `413676be`: typed preference, validation/registry and runtime readiness. `84c27082`: swappable Android STT/TTS adapters and JVM profile tests. `de0538d5`: separate screen, voice selector, offline probe and menu entry. `8ee9da04`: immutable profile snapshot across app-owned campaign and runtime. `8bf21c94`: isolated persistence/migration/invalid-ID Android instrumentation tests. `1f7f78fc`: exact-profile prewarm cache binding and STOP invalidation. `bc019ce`: preview READ_PHONE_STATE failure handling (F-050).
- Stable configuration is `ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL`, `pl-PL`, default automatic offline voice. No new grant; no new model downloader. Unsupported alternatives disabled rather than silently falling back.
- F-049 withdrawn by owner and not modified. No changes to VAD, ACK, RX/TX Samsung/Shizuku, authorization, output approval or chat transcript persistence.

## Actual QA receipts, not promises

- Mac Host Ops `aicalls-20261104-audio-m1-host-qa-v1`, pinned `8ee9da04fd86007e2470f224f933915ea41f72be`: **FAILED / exit 1** 2026-11-04 06:44:36 UTC. Deliberately mutated `AudioEngineRegistry` RED failed expected profile test, then restored source; JVM unit passed; `scripts/verify_host.sh` failed during lint (1 error, 236 warnings), location `AudioEngineSettingsActivity.previewVoice`, `MissingPermission` for `TelecomManager.isInCall`. No fabricated GREEN or S22 proof.
- F-050 source fix `bc019ce`: guard `READ_PHONE_STATE`, treat missing permission/phone status as unsafe only for optional *preview*, not a new permission or gate for campaign or saving profile.
- Follow-up Mac Host Ops `aicalls-20261104-audio-m1-postfix-host-qa-v2` pinned `bc019ce3921b4d23dd9946c9848952174363c685`: **QUEUED / QA_PENDING at checkpoint authorship**; must read terminal result and real full QA/anti-friction before claiming PASS.
- Exact S22 SM-S906B ADB RFCT70L7E8J new APK/instrumentation **OPEN / NOT INSTALLED**. Historical installed code `268be1f8f13502c7202822438b501706e0fcb0e1` remains only proven APK until new safe IDLE/role/retry/grant fingerprint double check and result.

## Remaining work / gates

1. Read full Mac receipt; fix any build/lint/test failure in source, re-run exact SHA; anti-friction check must be real.
2. After host PASS: physically verify S22 model+ADB serial, two IDLE samples, dialer role, no app-owned campaign or retry; fingerprint existing grants/skills before and after. Only then safe off-call install, isolated store instrumentation and four native synthetic Gemma RAW turns. **No real call.**
3. M2 Parakeet PL pinned model/MediaPipe runtime, M3 Piper PL, M4 multimodal PCM→Gemma require independent implementation and measurable S22 proof. F-048, F-047, F-045 remain separate OPEN; carrier-side CLIR result still unproven.
