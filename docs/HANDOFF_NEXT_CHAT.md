# Handoff — Gate C / CallPlan v1 product wiring

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

## Start here in the next chat

1. Read fresh `AGENTS.md`, this file, `README.md` and `docs/ROADMAP.md`.
2. Read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` before changing ownership or safety boundaries.
3. Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json`.
4. Trust only the current Bridge envelope plus fresh daemon binding for repo/binding identity.
5. Inspect the latest terminal Local Agent result before creating a successor task.
6. Keep `privileged-helper/` and the frozen Samsung media path untouched.

No further Orange live-call authorization is currently available.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text pipeline + application-owned output approval: `DONE / PROVEN_S22`;
- Gate A readiness/prepared-call boundary: `DONE`;
- general-purpose phone-local llama.cpp route: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills checkpoint: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only.

## Gate C checkpoint

The deterministic `CallPlan v1` policy core and the current host-side product-wiring checkpoint are `DONE / HOST_GREEN`.

Authority remains unchanged:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` does not grant or widen dial authority;
- `CallWorkflow` owns progress/proposals/user-decision/terminal outcome;
- `CallConfirmationPolicy` evaluates typed proposals;
- `CallCommitmentGate` owns exact one-shot commitment authorization;
- application-owned output approval remains mandatory before TTS/TX.

Models/helpers/skills are proposal-only.

### Completed wiring slices

1. `CallPlanTurnCoordinator`
   - final transcript + explicit unknown count -> `CallPlanEngine`;
   - `PROPOSAL` only through `CallWorkflow.evaluateProposal(...)`;
   - `COMPLETE` only through `CallWorkflow.complete(...)`;
   - task/target/state mismatch fails closed.

2. prepared-call plan binding
   - readiness carries one plan bound to the same task/target/workflow;
   - mismatched plan is rejected before backend/speech work.

3. deterministic candidate approval
   - `TextCallTurnController.submitCandidateText(...)` sends exact pre-determined text through the same application-owned approval without `backend.generate(...)`.

4. plan output/product routing
   - `CallPlanTextOutputRouter`: only `SAY` enters candidate approval;
   - `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` stay structured;
   - `CallPlanProductTurnRouter` composes coordinator + output routing.

5. session-owned bounded fallback state
   - `LocalTextCallSession.handlePlanFinalTranscript(finalTranscript)` owns the consecutive-unknown counter;
   - `ASK_REPEAT` increments it;
   - any recognized/terminal result resets it;
   - bounded escalation reaches `TAKE_OVER` correctly.

6. neutral final-turn dispatcher
   - `TextCallFinalTurnDispatcher` accepts exactly one route:
     - `Generate` -> ordinary backend generation;
     - `Candidate(text)` -> exact deterministic text through existing approval;
     - `Consumed` -> no text generation and immediate controller cancellation;
   - `Consumed` invalidates stale backend callbacks so an older LLM response cannot leak after a structured CallPlan turn.

Latest checkpoint evidence:

```text
.agent/results/chatgpt-gate-c-session-fallback-state-green-v45b-20260921.json
.agent/results/chatgpt-gate-c-final-text-dispatcher-red-v46-20260921.json
.agent/results/chatgpt-gate-c-final-text-dispatcher-green-v47-20260921.json
```

`v47` passed targeted regressions plus full `bash scripts/verify_host.sh` with `privileged-helper/` unchanged.

## Exact current product gap

The Android speech pipeline is still intentionally unchanged:

```text
LocalTextCallSession
  -> LocalSpeechTextPipeline
      -> final STT transcript
      -> TextCallTurnController.submitUserText(...)
      -> backend.generate(...)
      -> TextOutputApprovalPolicy
      -> local TTS
```

So CallPlan is fully host-wired up to a safe final-text routing primitive, but **final STT is not yet automatically intercepted before generative backend execution**.

Do not fix this by creating a second controller/generation gate in the session. There must remain one approval/cancellation path.

## Next exact engineering step

Start with one small host-only TDD slice that maps a structured CallPlan result to the neutral dispatcher route.

Required behavior:

```text
CallPlanAction.SAY
  -> TextCallFinalTurnRoute.Candidate(exact plan text)

ASK_REPEAT / PROPOSAL / COMPLETE / TAKE_OVER
  -> TextCallFinalTurnRoute.Consumed
  -> preserve the exact structured CallPlanTurnResult for the product owner
```

Rules:

- no invented retry/takeover/proposal/completion speech;
- no silent fallback to `Generate` for a plan-bound structured turn;
- reuse session-owned consecutive-unknown state;
- keep workflow mutations in the existing coordinator/workflow owners;
- no Android STT/TTS/media changes in this first mapper slice.

After that mapper is `HOST_GREEN`, the following slice may add an optional final-turn route selector to `LocalSpeechTextPipeline`:

- no-plan/default path remains `Generate` and behavior-compatible;
- plan-bound path selects `Candidate` or `Consumed` before backend generation;
- the pipeline continues to own a single `TextCallTurnController`, a single generation/cancellation lifecycle and the existing TTS path;
- product/session code owns CallPlan/workflow decisions;
- resumed speech/cancel/new generation must invalidate stale work.

Then run targeted regressions + full `bash scripts/verify_host.sh`. Physical S22 validation comes only after the host integration is complete and only with explicit authorization for any real call.

## Do not restart these paths by default

- Edge Gallery live-call experiments;
- old llama.cpp model sweep;
- paid OpenAI gates;
- Samsung media refactors;
- `privileged-helper/` work;
- new diagnostic probe growth.

Historical experiment detail belongs in Git history and `.agent/results`, not in new handoff documents.
