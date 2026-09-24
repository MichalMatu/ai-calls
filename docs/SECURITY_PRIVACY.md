# Security and privacy

## Objective

The agent may dial, speak, disclose data or change external state only inside authority explicitly granted by the user and enforced by application-owned policy. Failure must narrow capability or return control to the human; it must never broaden authority.

## Frozen privilege/media boundary

Samsung call-audio access remains inside the privileged helper / Shizuku UserService. Continuous PCM crosses through transferred PFDs; Binder/AIDL is control only.

This path is `PROVEN_S22 / FROZEN`. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing it.

## Authority owners

- `CallTask` — exact task, constraints, preferences and authorized facts;
- `CallResolvedTarget` — exact target;
- `CallExternalEffectValidator` — exact effect/task/target binding;
- `CallCommitmentGate` — single one-shot commitment permit store;
- `CallExternalEffectCompletionTracker` — exact post-consumption success evidence;
- workflow/TaskGraph owner — terminal task state;
- `FactDisclosurePolicy` — plaintext personal-data disclosure;
- application output approval — final text release before TTS/TX.

TaskGraph definitions, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers, storage and diagnostics are not independent authority owners.

## External-effect rule

```text
user task authorization
 != effect validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

A model may help extract or propose effect data. It cannot create authority, widen the effect, declare success or complete the workflow.

Do not create per-service commitment gates such as `ClirCommitmentGate`.

## Confirmation rule

Do not require a redundant second confirmation when the current-chat instruction already exactly authorizes the same concrete effect and no material term changed.

If the counterparty introduces new material terms, application policy decides whether fresh confirmation is required.

## Identity and disclosure

A value existing in IdentityVault is not permission to disclose it.

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Plaintext identity values stay out of TaskGraph definitions, ServicePacks, ordinary logs/evidence, Local Agent JSON, Git history and model/supervisor context by default.

## Dialogue/model boundary

```text
STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skill
 -> supervisor fallback when unresolved
 -> application validation/output approval
 -> TTS/TX
```

Gemma and supervisor cannot independently dial, widen target/task/effect scope, disclose unauthorized facts, bypass output approval, issue/consume commitment authority, mark external success or complete the task.

## Live-call policy

A call must fail closed before dialing unless the exact task/target is freshly authorized in the current chat and required readiness gates pass.

Rules:

- one active cellular call at a time;
- exact target only; no model/tool target widening;
- bounded duration/retries;
- do not touch emergency, urgent-care, crisis, premium-rate or unrelated critical-service targets;
- do not terminate unrelated pre-existing calls;
- no old chat, handoff, ServicePack, connected phone or `.agent/results` file carries dialing permission forward;
- if a physical call is made, cleanup must return the owned call to `IDLE`.

## Orange G5 split

G5b route discovery is read-only:

- fresh authorization is required for the exact Orange target and discovery task;
- pre-dial readiness must confirm microphone + Shizuku state;
- use only reviewed caller-ID information speech followed by `OBSERVE_ONLY`;
- do not issue/consume a commitment permit;
- do not change CLIR or account state;
- stop on authentication/customer-data/commitment barriers.

G5c account-changing CLIR execution is a separate step. It requires authorization covering `SET_SERVICE(CLIR=true)` and must use the generic one-shot permit + separate factual external-success evidence lifecycle. Route discovery is never success evidence.

## Data minimization

Do not retain by default raw PCM, recordings, full transcripts, credentials, plaintext identity values or unrelated counterparty data. Prefer typed IDs, redacted evidence, sizes/timings and bounded correlation IDs.

## TAKE OVER / failure ordering

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/supervisor generation
 -> best-effort cancel remaining work
 -> hang up only the call owned by this session when appropriate
```

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. A connected phone is not live-call authorization. Permit consumption is not external success. The CLIR task is not complete until exact factual external-success evidence has been accepted by application-owned completion logic.