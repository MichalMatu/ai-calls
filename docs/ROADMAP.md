# Roadmap

## Status vocabulary

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — targeted/canonical host evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a concrete root cause.

## Stable completed foundation

The following are stable and should remain frozen unless a new root cause requires reopening them:

- Samsung cellular RX/TX and `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS foundation — `PROVEN_S22`;
- IdentityVault encryption/disclosure boundary — `PROVEN_S22`;
- existing Gate D `BOOK_APPOINTMENT` proposal/confirmation/commitment/completion flow — `DONE / HOST_GREEN / PROVEN_S22 / MERGED`;
- direct Gemma 4 LiteRT-LM inference — `HOST_GREEN / PROVEN_S22`;
- application-owned Gemma import/download/readiness lifecycle — `HOST_GREEN / PROVEN_S22`.

Do not redownload/reprove Gemma or rewrite frozen media as part of the next task.

## Product target

Build a **generic autonomous phone task engine**, not a collection of service-specific bots.

Examples that must converge on the same architecture:

- enable Orange CLIR;
- book a clinic appointment under user constraints;
- change/cancel a reservation;
- resolve a bounded service request;
- read-only information calls.

Dialogue stack remains:

```text
STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skills
 -> supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
```

The next missing layer is a generic application-owned external-effect/commitment subject. See `docs/GENERIC_PHONE_TASK_AUTHORITY.md`.

## G1 — preimplementation audit: generic commitment subject

**NEXT. Do this before production behavior changes.**

Audit every place where appointment-shaped `CallProposal` is treated as the universal commitment subject:

- `CallCommitmentGate`;
- realtime commitment handler;
- Gate D product integration;
- confirmation/consumption/completion evidence;
- `LocalTextCallSession` appointment-specific public/internal seams;
- tests and docs.

Output: exact coupling map and minimal migration plan preserving all existing `BOOK_APPOINTMENT` invariants.

No live call required.

## G2 — generic external-effect authority core

Introduce the narrowest generic typed commitment subject, conceptually `ExternalEffectCandidate`, without creating a second authority store.

Requirements:

- exact effect type, target and typed parameters;
- deterministic validation against `CallTask`, target and constraints;
- one-shot permit bound to the exact candidate;
- permit issuance, consumption, external success and completion remain distinct;
- no redundant second confirmation when the original user instruction already exactly authorizes the concrete effect;
- existing appointment behavior/tests remain green through an adapter/compatibility layer.

Run targeted RED -> GREEN tests and `bash scripts/verify_host.sh`.

## G3 — CLIR as first generic effect adapter

Add `SET_SERVICE(CLIR=true)` as an adapter/use case of the generic authority core.

Do not create `ClirCommitmentGate` and do not encode Orange exact phrases as the product architecture.

Synthetic/no-call proof must cover:

- exact target/task binding;
- task-level authorization;
- candidate validation;
- one-shot commitment permit;
- app-owned reviewed commitment speech;
- success-evidence parsing/validation;
- factual completion;
- rejection of target/task/effect widening.

## G4 — full product live runner

Unify the physical runner so one call can exercise:

```text
Orange/other target RX
 -> STT
 -> PhraseMatrix/CallPlan/TaskGraph
 -> Gemma 4 when unresolved
 -> supervisor/ChatRelay fallback when Gemma cannot resolve
 -> app-owned output approval
 -> TTS/TX
 -> generic effect authority
 -> success evidence
```

Developer ChatRelay remains fallback/supervision infrastructure, not business authority.

Before dialing, prove the full runner no-call/synthetic path on the S22.

## G5 — Orange CLIR acceptance

Only after G1–G4 are green, and only with **fresh explicit live-call authorization** for the exact Orange target and CLIR task:

- run one bounded live call;
- collect redacted evidence for dial -> RX -> STT -> dialogue source -> approved TTS -> commitment -> success evidence -> hangup;
- do not broaden the task or make unrelated account changes.

CLIR is an acceptance case, not the final product shape.

## G6 — clinic booking acceptance

Next broaden the same architecture with a clinic task requiring real negotiation, e.g. specialty/date/time/price constraints.

This gate proves the generic model is not secretly a fixed carrier-service workflow.

## Current physical checkpoint — 2026-09-24

Recent Orange testing reconfirmed:

- dial and `OFFHOOK`;
- real Orange downlink audio;
- hardened relay handling for transient ADB audio/hangup failures.

After a fresh APK install, microphone permission had to be granted again. A subsequent no-call probe confirmed Shizuku + microphone permission and completed normally.

A CLIR account change has **not** been completed yet.

## Authority invariant

Gemma, supervisor/ChatRelay, ServicePacks, TaskGraph helpers, matchers, parsers and model/storage layers cannot independently:

- dial or widen a target;
- disclose plaintext identity;
- widen the task/effect scope;
- release speech without output approval;
- create or consume commitment authority outside the application-owned gate;
- infer external success;
- complete the workflow/task.

## Live-call stop line

Every real call requires fresh explicit authorization in the current chat for one concrete target/number and task. Handoff, previous calls, connected hardware or stored evidence never carry that authorization forward.
