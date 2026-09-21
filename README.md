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

The deterministic CallPlan final-STT path and the native PhraseMatrix baseline are `HOST_GREEN`.

Authority remains in the existing product owners:

- `CallTask` — immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` — concrete target only, no allowlist expansion;
- `CallWorkflow` — progress, proposals, user decisions and terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — exact one-shot commitment permit;
- application-owned output approval — required before TTS/TX.

Models, helpers, matchers and Agent Skills remain proposal/classification-only.

### Host-green final-STT + PhraseMatrix path

The product path now contains:

- `CallPlanTurnCoordinator` as the deterministic CallPlan/workflow mutation boundary;
- `TextCallFinalTurnDispatcher` with exactly `Generate`, exact `Candidate(text)` and `Consumed`;
- `CallPlanFinalTurnRouteMapper`: `SAY` becomes an exact candidate, structured actions remain consumed + structured;
- one `TextCallTurnController`, one approval path and one generation/cancellation lifecycle;
- native `PhraseMatrix` normalization, exact phrases and explicit aliases;
- collision/unknown fail-closed behavior and deterministic replay;
- explicit optional `previousRuleId` context without matcher-owned dialogue state;
- validation of every matrix `ruleId` through the bound `CallPlan` before it can affect workflow/output;
- session-owned `previousValidatedRuleId`: raw/rejected matcher ids never become later-turn context;
- prepared-call/readiness transport for optional `CallPlan + PhraseMatrix`;
- Android readiness factories that can bind that pair into the prepared product session.

The no-plan path remains behavior-compatible `Generate`. A matrix hit does not bypass CallPlan or output approval, and structured results never silently fall back to backend generation.

Latest full host checkpoint:

```text
.agent/results/chatgpt-android-readiness-binding-green-v72-20260921.json
```

## Matcher engine decision

Keep the small native Kotlin `PhraseMatrix` as the production direction.

Host spikes on the same small Polish corpus measured approximately:

- native PhraseMatrix: `11.85 ms` init, `0.815 us` average match;
- RiveScript Java: `46.56 ms` init/sort, `88.85 us` average reply;
- RiveScript increased the clean debug APK by `134,132 bytes` and adds `slf4j-api`.

RiveScript did prove Polish UTF-8 and `%Previous` feasibility, but it can also emit arbitrary reply text and adds a broader scripting surface. It remains a reference/spike, not a production dependency. ChatScript remains a source of mature pattern/topic/rejoinder ideas rather than an embedded engine. KStateMachine remains deferred unless non-authority dialogue-stage state becomes complex enough to justify it.

These are host measurements, not S22 performance claims.

## Next engineering slice

Extend the native matcher conservatively with a **bounded deterministic fuzzy/pattern layer** only where corpus tests justify it. Unknown/ambiguous input must still fail closed. After that, define the bounded LLM-supervisor contract that may suggest only an existing `ruleId` and whose stale/speculative output is invalidated by newer speech/session/workflow state.

No physical call is required for those host-only slices.

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
