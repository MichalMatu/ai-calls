# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to selectable AI engines without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Proven foundation

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- product-owned local runtime/readiness/prepared-call boundary: `DONE`;
- live endpointing signals: physically proven, production endpoint state machine still separate/open.

The frozen Samsung implementation is documented in `docs/PHASE2D_FREEZE_2026-09-18.md`. Do not change it during ordinary Gate C work.

## Current product direction — Gate C / deterministic fast path

The deterministic CallPlan policy core and final-STT product wiring are `DONE / HOST_GREEN`.

CallPlan reuses the existing authority model rather than replacing it:

- `CallTask` — immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` — concrete target only, no allowlist expansion;
- `CallWorkflow` — progress, proposals, user decisions and terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — exact one-shot commitment permit;
- application-owned output approval — required before TTS/TX.

Models, helpers, matchers and Agent Skills remain proposal/classification-only.

### Host-green final-STT wiring

The product path now contains:

- `CallPlanTurnCoordinator` — deterministic decision + workflow mutation boundary;
- prepared-call CallPlan binding;
- `TextCallTurnController.submitCandidateText(...)` — exact deterministic text through existing approval without backend generation;
- session-owned consecutive-unknown fallback state;
- `TextCallFinalTurnDispatcher` with exactly three routes:
  - `Generate` — ordinary backend generation;
  - `Candidate(text)` — exact pre-determined text through existing approval;
  - `Consumed` — no generation and stale controller/backend work invalidation;
- `CallPlanFinalTurnRouteMapper`:
  - `SAY` -> exact `Candidate(text)`;
  - `ASK_REPEAT` / `PROPOSAL` / `COMPLETE` / `TAKE_OVER` -> `Consumed` plus the exact structured result;
- an optional product-owned final-turn selector at `LocalSpeechTextPipeline` final STT.

The default/no-plan path remains behavior-compatible `Generate`. A plan-bound final transcript is now intercepted before backend generation while still using exactly one `TextCallTurnController`, one approval path and one cancellation lifecycle.

Latest full host checkpoint:

```text
.agent/results/chatgpt-gate-c-final-stt-selector-green-v51-20260921.json
```

## Next engineering slice

The next Gate C extension is the **Phrase / Intent Matrix fast path**. Start with the tiny native CallBridge baseline in `docs/PHRASE_MATRIX_ENGINE_RESEARCH.md`, then compare it with an isolated RiveScript Java experiment on the same Polish corpus.

Target contract:

```text
final STT
 -> normalize
 -> deterministic PhraseMatrix
 -> existing ruleId + confidence
 -> validate against bound CallPlan
 -> CallPlan / workflow / output approval
 -> Candidate / Consumed
```

The matrix must not emit arbitrary speech, invent facts, widen target/commitment authority or create a parallel workflow state store. ChatScript is currently a source of matcher/dialogue ideas rather than the default dependency; KStateMachine is deferred unless non-authority dialogue-stage state becomes complex enough to justify it.

Exact continuation instructions live in `docs/HANDOFF_NEXT_CHAT.md`.

## Frozen / deferred experiments

- general-purpose phone-local llama.cpp model sweep: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only;
- `OPENAI_TEXT` / `OPENAI_REALTIME_AUDIO`: preserved but deferred;
- `LOCAL_MAC_LLM`: preserved provider option;
- future local realtime audio: later gate.

Live Orange calls require explicit current-session operator authorization and an exact allowlisted destination; do not resume unrelated live-call experiments by default.

## Repository workflow

Authoritative current docs:

- `AGENTS.md` — work rules and active engineering constraints;
- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation point;
- `docs/ROADMAP.md` — gate matrix;
- `docs/ARCHITECTURE.md` — ownership boundaries;
- `docs/SECURITY_PRIVACY.md` — authority/privacy rules;
- `docs/PHASE2D_FREEZE_2026-09-18.md` — frozen Samsung media invariants.

Durable product code/docs live on `main`. Local Agent tasks/results stay on `agent-control`.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

`HOST_GREEN` is not `PROVEN_S22`; hardware/OEM claims require physical S22 evidence.
