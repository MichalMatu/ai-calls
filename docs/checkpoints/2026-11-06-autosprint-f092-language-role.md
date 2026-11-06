# AI Calls — autonomous sprint: F-092 language-role contract (2026-11-06)

## Baseline and scope

Fresh `MichalMatu/ai-calls` main = origin/main `614b0fadbd5df806127008cb0b01891f4a0bdc26` and clean checkout on Mac `macbook-michal` / `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Read-only preflight `aicalls-20261106-autosprint-f092-preflight-v1`: DONE/0.

Existing F-092 instructions already described an incoming female assistant and an already-spoken greeting, but still embedded the literal slash-gendered phrase it was trying to forbid, while the regression test required that literal. This reinforced an undesirable pattern and failed to test positive invariants in the EXPLAIN_GOAL skill.

## One narrowly scoped source correction

- `ConversationInstructionBuilder.kt`: replace the literal slash-gendered example with a natural gender-neutral instruction. Female self-reference and no second greeting remain explicit. No new role, tool or authority.
- `ConversationReplyProtocolTest.kt`: reject the literal gendered pattern, require the single greeting/female-role constraints, and verify EXPLAIN_GOAL does not re-introduce the outgoing first-reply instruction.
- No changes to model selection, prompt context values, task/grant/identity vault, conversation tools, STT, TTS, call ownership, Samsung/Shizuku, PCM/PFD, STOP, timeout 30+15 or full TX → end_call.

## Actual host QA

Mac task `aicalls-20261106-autosprint-f092-prompt-regression-v1`: **DONE / exit 0**, elapsed 217.568 s. `./gradlew --offline :app:testDebugUnitTest --tests '*ConversationReplyProtocolTest*' --tests '*ConversationPreparationTest*' :app:compileDebugAndroidTestKotlin` PASS. `bash scripts/verify_host.sh` PASS with `host_quality_gate_green=true`; three Gradle builds PASS (15s, 2m42s, 14s), 266 Python tests PASS. `audit_product_friction.py` **ANTI_FRICTION=PASS**; `git diff --check` and exact 2-source-file scope PASS.

Logs private on Mac: `~/Documents/AI-Calls/local-agent/2026-11-06-autosprint-f092`. No raw conversations, WAV, numbers or sensitive values were published.

## Acceptance and remaining work

F-092: **FIXED_IN_SOURCE / HOST_PASS; physical language behavior QA_PENDING**. Existing S22 APK predates modularization and this change; no install or live calls. Build results are not evidence of a changed model's exact spoken utterance.

Separate, genuinely unresolved product work: F-080 full-session audio/seek player, F-048 authorized typed fact access, F-045 semantic output-policy hardening and F-047 interrupted RX recovery. They require isolated design, tests, and where relevant physical verification. F-091 natural-close evidence and F-099 timeout event-level timeline also remain partial; do not claim blanket closure.

**Next one step:** inspect the existing private evidence of the latest F-091 natural-close attempt without exporting any transcript, and isolate the reason the model did not propose a typed end_call. Do not introduce phrase-based forced disconnects or run new calls without an exact authorization.
