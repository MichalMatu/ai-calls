# E2B — persistent conversation fact context (next independent sprint)

## Goal

Preserve user-supplied key facts, explicit corrections, uncertainty and turn attribution across long dialogues **without unbounded native KV-cache growth**. Work within the existing `SkillLabRunner`, `AndroidSkillLabSessionFactory`, `ConversationInstructionBuilder` and `Gemma4LiteRtTextBackend` boundaries; no duplicate conversation runner or new authority path.

## Proposed steps

1. **Evidence-bearing fact ledger (JVM, pure Kotlin):** typed `subject / field / value / origin-turn / correction-of / confidence-status`; last confirmed correction supersedes earlier value without deleting its provenance. Preserve distinctions among supplied facts, *model-proposed* inferences, contradicted values, and never-provided facts. Do not promote the model's answer or arbitrary STT text to a trusted fact automatically.
2. **Private checkpoint at a turn boundary:** keep the original transcript in the app-owned private store; prepare a short, grounded model-visible memory snapshot, anchored to source turns. Neither prior source text nor retained memory confers external effect permission, user identity disclosure or CALL authority. Review a nonconfirmable or ambiguous correction rather than silently choosing a value.
3. **Controlled native context rollover:** when approaching the measured native context budget, safely close/reset the old context at a completed-turn boundary and construct the next one from the exact existing role/skills/task plus verified ledger and latest conversational continuity. Preserve STOP, stale callback fencing, user ownership and observed progress. A rollover must never pretend that original native KV state survives or silently report a fully continuous context.
4. **Comparative device QA:** randomized fictional facts, multiple corrections, unknown data, conflicting instructions, 20–30+ turns, session interruption/STOP/restart, and N=3 repeats. Compare 4096 unchanged baseline, 8192 if physically accepted, and 4096 with the ledger. Report actual factual errors, human semantic assessments, PSS, MemAvailable, latency and JNI. No fixed user-facing turn cap.
5. **Only after this** evaluate E4B/Qwen with the identical independently graded corpus and RAM/latency receipts.

## Explicit non-goals

- Do not restore the four rejected default stories, auto-export transcripts, introduce public device APIs, scrape IdentityVault values, or perform any real call during Lab testing.
- Do not treat the Lexical 13-check diagnostic as automatic semantic truth. The eight `LabDialogueReport` dimensions remain `UNASSESSED` until turn-specific independent evidence is entered.
- Do not raise production-call context budgets without independent physical phone-workflow acceptance.

[Current 4096 / 8192 checkpoint](checkpoints/2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md).
