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

## Current product direction — Gate C / CallPlan v1

The deterministic CallPlan policy core and the current host-side product-wiring checkpoint are `DONE / HOST_GREEN`.

CallPlan reuses the existing authority model rather than replacing it:

- `CallTask` — immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` — concrete target only, no allowlist expansion;
- `CallWorkflow` — progress, proposals, user decisions and terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — exact one-shot commitment permit;
- application-owned output approval — required before TTS/TX.

Models, helpers and Agent Skills remain proposal-only.

### Host-green CallPlan behavior

- known question -> exact authorized fact;
- missing fact -> fail-closed `TAKE_OVER`;
- unknown/ambiguous final -> bounded `ASK_REPEAT` / `TAKE_OVER`;
- known offer -> predeclared typed `CallProposal` only;
- completion -> predeclared typed `CallOutcome` only;
- helper -> may suggest only an existing `ruleId`;
- cross-kind collisions fail closed;
- no plan/model/helper may grant itself target, commitment or output authority.

### Host-green product wiring

The repository now contains:

- `CallPlanTurnCoordinator` — deterministic decision + workflow mutation boundary;
- prepared-call CallPlan binding;
- `TextCallTurnController.submitCandidateText(...)` — exact deterministic text through existing approval without backend generation;
- `CallPlanTextOutputRouter` — only `SAY` may enter candidate approval;
- `CallPlanProductTurnRouter` — coordinator + structured output routing;
- session-owned consecutive-unknown fallback state;
- `TextCallFinalTurnDispatcher` with exactly three routes:
  - `Generate` — ordinary backend generation;
  - `Candidate(text)` — exact pre-determined text through existing approval;
  - `Consumed` — no generation; cancel stale controller/backend work.

Latest full host checkpoint:

```text
.agent/results/chatgpt-gate-c-final-text-dispatcher-green-v47-20260921.json
```

## What is still open

`LocalSpeechTextPipeline` still follows the old default path:

```text
final STT
 -> TextCallTurnController.submitUserText(...)
 -> backend.generate(...)
 -> TextOutputApprovalPolicy
 -> local TTS
```

So CallPlan does **not yet automatically intercept real final STT before backend generation**.

The next work must keep exactly one controller/approval/generation-cancellation path. Do not bolt a second controller into `LocalTextCallSession`.

Exact continuation instructions live in `docs/HANDOFF_NEXT_CHAT.md`.

## Next engineering slice

First add a small host-tested mapping from `CallPlanTurnResult` to `TextCallFinalTurnRoute`:

```text
SAY -> Candidate(exact text)
ASK_REPEAT / PROPOSAL / COMPLETE / TAKE_OVER -> Consumed + structured result
```

No invented speech and no implicit model fallback.

Only after that mapper is green should `LocalSpeechTextPipeline` receive an optional product-owned route selector so plan-bound final STT can choose `Candidate`/`Consumed` before generation while the no-plan path remains `Generate`.

## Frozen / deferred experiments

- general-purpose phone-local llama.cpp model sweep: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only;
- `OPENAI_TEXT` / `OPENAI_REALTIME_AUDIO`: preserved but deferred;
- `LOCAL_MAC_LLM`: preserved provider option;
- future local realtime audio: later gate.

No further Orange live-call authorization is currently available. Do not resume live-call experiments by default.

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
