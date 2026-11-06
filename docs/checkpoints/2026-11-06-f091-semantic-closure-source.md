# AI Calls — F-091 semantic closure action-selection (2026-11-06)

## Baseline and controlled evidence

- Repository: MichalMatu/ai-calls, starting main/origin 73702c6ee69b90e1db0223f8c257630d9ea9871b.
- Mac: macbook-michal / cfea4cc3-b54c-4b6d-b9a6-758d12733e98.
- Local Agent aicalls-20261106-f091-native-offcall-preflight-v1: DONE/0, clean main, S22 detected; GSM property unavailable.
- Local Agent aicalls-20261106-f091-native-test-readiness-v1: DONE/0, existing app and instrumentation installed; two telephony states 0 (IDLE).
- Local Agent aicalls-20261106-f091-native-offcall-baseline-v1: DONE/0, existing synthetic multi-turn instrumented test with real selected model PASS in 41 s. No Telecom, no new calls, no install, no STT/TTS. Installed test APK not attested against current HEAD.
- Prior private F-091 evidence: 8 structured SPEAK, 0 END_CALL in inspected files; not proof of executor failure and may span multiple evidence files.

## RED → GREEN

- RED: aicalls-20261106-f091-semantic-closure-red-v1 DONE/0 with an expected targeted JVM assertion failure. The previously absent context/finality contract was detected before production change.
- GREEN: ConversationReplyProtocol instruction distinguishes explicit request/final confirmation from quotation, correction, pending summary and additional business. End-call proposal and farewell share one JSON response. This is a semantic prompt contract, not a keyword disconnect rule.
- Extended AndroidConversationToolProtocolTest with a synthetic additional-business story plus the existing correction, quoted farewell, summary, final farewell and direct disconnect stories.
- Local Agent aicalls-20261106-f091-semantic-closure-green-gate-v1: focused JVM GREEN, AndroidTest compilation, full host gate (Kotlin, lint, APK, Python/audio audit) and product friction PASS are required conditions for committing this checkpoint. No new native test APK deployment.

## Preserved safety and limits

Only model instructions, prompt contract unit regression and native synthetic test story plus status docs changed. No change to Telecom, STOP, permissions, Samsung/Shizuku, recording, audio, TTS, executor or playback-complete → END_CALL ordering. No physical calls or installation performed.

Status F-091: source improvement HOST_PASS when the recorded gate is successful; **post-change native model behavior and live reliability remain QA_PENDING**. The earlier installed native PASS is a baseline only, not a post-change success. F-080, F-048, F-045 and F-047 remain separate backlog. Next step is a separately authorized APK/native off-call A/B measurement with explicit positive and negative closure intents before a newly scoped physical call.

## Next modular skill stage

Versionable ConversationSkill for message completeness, clarification and natural closure, compiled as an immutable per-call snapshot. Compare decision accuracy, false-end rate, unnecessary clarification turns and latency using the same synthetic multi-turn corpus across selected models. Do not move app-owned authority into skills or add keyword-triggered disconnects.
