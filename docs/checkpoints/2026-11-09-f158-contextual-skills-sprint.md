# AI Calls — F-158 contextual Skills A/B initial implementation (2026-11-09)

## Implemented source and pinned inputs

- Last previously accepted scope: F-156 VERIFIED_SCOPED; E2B/E4B 18/18 native turns at 2048 on S22, all previously accepted. Starting GitHub/Mac main was clean and exactly `0fd46c4abd88b9939cb1825df020fb603b7967a1`.
- `a3b0fba42b1f879c00764433aece421b2b4780b0`: Lab-only `SkillLoadingMode.ALL_ACTIVE | CONTEXTUAL`, deterministic no-Gemma selector, per-session version-pinned selection, diagnostic selection time in microseconds and selected count, editable mode toggle in existing Lab. Static ALL_ACTIVE is the default. Existing presets and production Call prompt path are unchanged.
- `f6f0bafa3adbf7156dc65466f1b5cf207e55feb5`: dedicated real-E2B AndroidTest A/B, synthetic-only 2 scenarios × 2 modes × 3 independent sessions × 2 turns = 24 planned native turns. Reported measurements are model cold/warm, prompt length, selector microseconds, GPU backend and compliance booleans; no transcript or user data in log.
- Six new JVM tests cover byte-for-byte static preservation, relevant vs generic selection, disabled skills, revisions/unknown custom skills and safety ordering.
- Initial heuristic: ConversationStyle/NaturalClosure always; MessageCapture only for incoming or message-goal keywords, Clarification only for incoming or uncertainty/correction keywords. Unknown custom skills are not silently dropped. **This is session-start routing, not mid-conversation lazy loading, not model-based discovery, not SKILL.md import or executable tools.** Later work must make routing metadata editable and consider per-turn routing only after native lifecycle design.

## Verified evidence and remaining gates

- `aicalls-f158-agent-skills-preflight-20261109-v1` via the exact Mac Local Agent installation: **DONE/0**. Mac `main`, HEAD and `origin/main` matched starting SHA; working tree clean; source present.
- `aicalls-f158-contextual-skills-host-qa-20261109-v1`: requested targeted JVM test, `verify_host.sh`, friction audit and clean-tree check on source `a3b0fba`. **RESULT PENDING at this checkpoint; DO NOT mark PASS.**
- The AndroidTest code in `f6f0baf` and physical E2B A/B on S22: **NOT_RUN**. No device install, model generation or telephone call was performed for this sprint at checkpoint time.
- No new actual product friction violation established. Existing `BLOCKERS.md` authority and F-148 open areas preserved.

## Immediate continuation

1. Read exact Mac QA task result, reconcile failing test/compile output and repair source if necessary; do not confuse a prior-SHA PASS with a later-SHA PASS.
2. Compile `app:assembleDebugAndroidTest` after `f6f0baf`. With verified S22 `SM-S906B`, serial `RFCT70L7E8J`, IDLE, no app-owned campaign, install test APK without wipe and run `AndroidContextualSkillsABNativeTest`. Never execute real Calls.
3. Evaluate 24 native turns, 3 independent repeats for each scenario/mode; record selector timing vs full prompt and actual behavior. A valid JSON/technical PASS is not human semantic quality PASS. Retain private text on device/Mac.
4. Only after evidence, update this checkpoint, handoff and relevant QA status. Do not claim fully dynamic Agent Skills or change normal Call until separately designed and accepted.

## Operating constraints

GitHub Connector + designated Mac Local Agent only; `main` with `[skip ci]`, no Codex/Work/GitHub Actions, no new branches, force push or private data in Git. Observe `AGENTS.md`, Android Call ownership, STOP, grant/privacy and `docs/AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md`.
