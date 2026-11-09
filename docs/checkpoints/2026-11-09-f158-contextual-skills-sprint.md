# AI Calls — F-158 contextual Skills A/B initial implementation (2026-11-09)

## Implemented source and pinned inputs

- Last previously accepted scope: F-156 VERIFIED_SCOPED; E2B/E4B 18/18 native turns at 2048 on S22, all previously accepted. Starting GitHub/Mac main was clean and exactly `0fd46c4abd88b9939cb1825df020fb603b7967a1`.
- `a3b0fba42b1f879c00764433aece421b2b4780b0`: Lab-only `SkillLoadingMode.ALL_ACTIVE | CONTEXTUAL`, deterministic no-Gemma selector, per-session version-pinned selection, diagnostic selection time in microseconds and selected count, editable mode toggle in existing Lab. Static ALL_ACTIVE is the default. Existing presets and production Call prompt path are unchanged.
- `f6f0bafa3adbf7156dc65466f1b5cf207e55feb5`: dedicated real-E2B AndroidTest A/B, synthetic-only 2 scenarios × 2 modes × 3 independent sessions × 2 turns = 24 planned native turns. Reported measurements are model cold/warm, prompt length, selector microseconds, GPU backend and compliance booleans; no transcript or user data in log.
- Six new JVM tests cover byte-for-byte static preservation, relevant vs generic selection, disabled skills, revisions/unknown custom skills and safety ordering.
- Initial heuristic: ConversationStyle/NaturalClosure always; MessageCapture only for incoming or message-goal keywords, Clarification only for incoming or uncertainty/correction keywords. Unknown custom skills are not silently dropped. **This is session-start routing, not mid-conversation lazy loading, not model-based discovery, not SKILL.md import or executable tools.** Later work must make routing metadata editable and consider per-turn routing only after native lifecycle design.

## Verified evidence and remaining gates

- `aicalls-f158-agent-skills-preflight-20261109-v1` via the exact Mac Local Agent installation: **DONE/0**. Mac `main`, HEAD and `origin/main` matched starting SHA; working tree clean; source present.
- `aicalls-f158-contextual-skills-host-qa-20261109-v1`: requested targeted JVM test, `verify_host.sh`, friction audit and clean-tree check on source `a3b0fba`. **DONE/0 (receipt verified 2026-11-09)**: targeted `ConversationSkillRoutingTest`, full `bash scripts/verify_host.sh`, anti-friction and clean Git PASS on `a3b0fba`. The later `f6f0baf` native AndroidTest was not part of this QA task. Physical S22 A/B remains NOT_RUN.
- The AndroidTest code in `f6f0baf` and physical E2B A/B on S22: **NOT_RUN**. No device install, model generation or telephone call was performed for this sprint at checkpoint time.
- No new actual product friction violation established. Existing `BLOCKERS.md` authority and F-148 open areas preserved.

## Immediate continuation

1. Read exact Mac QA task result, reconcile failing test/compile output and repair source if necessary; do not confuse a prior-SHA PASS with a later-SHA PASS.
2. Compile `app:assembleDebugAndroidTest` after `f6f0baf`. With verified S22 `SM-S906B`, serial `RFCT70L7E8J`, IDLE, no app-owned campaign, install test APK without wipe and run `AndroidContextualSkillsABNativeTest`. Never execute real Calls.
3. Evaluate 24 native turns, 3 independent repeats for each scenario/mode; record selector timing vs full prompt and actual behavior. A valid JSON/technical PASS is not human semantic quality PASS. Retain private text on device/Mac.
4. Only after evidence, update this checkpoint, handoff and relevant QA status. Do not claim fully dynamic Agent Skills or change normal Call until separately designed and accepted.

## Operating constraints

GitHub Connector + designated Mac Local Agent only; `main` with `[skip ci]`, no Codex/Work/GitHub Actions, no new branches, force push or private data in Git. Observe `AGENTS.md`, Android Call ownership, STOP, grant/privacy and `docs/AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md`.

## Physical F-158 closure (receipt 2026-11-09)
`aicalls-g0-f158-s22-20261109-v1` **DONE/0** at `main@59101e9`. New AndroidTest APK compile PASS; real SM-S906B (Exynos s5e9925), IDLE/no app campaign; debug APK and AndroidTest installed without wipe. Native `AndroidContextualSkillsABNativeTest` **1/1 PASS**, all **24 planned turns** reached valid JSON, no proposed tools. 12 A/B measurement records + 4 summaries; GPU/2048. Generic ALL_ACTIVE marker on first turn but none on warm (0/3 both), CONTEXTUAL none (0/3). Message both modes marker both turns **3/3**, thus correct contextual inclusion under tested message intention. Cold ~9.7–14.0 s and warm ~1.9–2.5 s here; not p95. **G0 TECHNICAL S22_PASS; human-level naturalness and broader scenarios remain OPEN.** Do not misrepresent marker as semantic guarantee.

G1 `aicalls-g1-qwen35-2b-metadata-hardware-20261109-v1` **DONE/0** read-only. HF `litert-community/Qwen3.5-2B` revision `f24013ba6844bd39be2d1f68e8d2421a77eed1f2`, `Qwen3.5-2B_int8.litertlm` 2,119,843,840 B, LFS SHA-256 `c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49`, declared Apache-2.0. S22 `MemTotal=7445972 KiB`, `MemAvailable=2853956 KiB` at probe, /data free ~122,318,548 KiB. **Feasibility metadata PASS, native initialization NOT_RUN.** External model card documents Mali 8 GB GPU out-of-memory risk and LiteRT-LM 0.19.0 recurrent engine reuse defect. Policy for G2: experimental **CPU-only**, new Engine after reset; Call remains E2B.
