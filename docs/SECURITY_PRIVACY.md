# Security and privacy

## Objective

The agent may dial, speak, disclose data or change external state only inside authority explicitly granted by the user and enforced by application-owned policy. Failure must narrow capability or return control to the human; it must never broaden authority.

`docs/AUTONOMOUS_OPERATION_MODE.md` defines the operational target: high autonomy without weakening application-owned authority boundaries.

## Frozen privilege/media boundary

Samsung call-audio access remains inside the privileged helper / Shizuku UserService. Continuous PCM crosses through transferred PFDs; Binder/AIDL is control only.

This path is `PROVEN_S22 / FROZEN`. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing it.

## Authority owners

- `CallTask` — exact task, constraints, preferences and authorized facts;
- `CallResolvedTarget` — exact target;
- application-owned authorization context / future durable campaign grant — exact scope in which repeated attempts are allowed;
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

## Durable campaign grant

The target autonomous product mode requires an explicit durable, scoped, revocable campaign grant owned by application policy.

A grant must bind at least:

- allowed target(s);
- allowed task/effect set;
- account/SIM scope when relevant;
- issue/revocation state;
- optional retry/expiry bounds;
- disclosure scope.

When a valid grant already covers an unchanged retry, the product should not ask for another redundant confirmation. A material widening of target, task, effect, account or disclosure scope remains fail-closed.

Chat prose, markdown files, ServicePacks, model output, connected hardware and diagnostic artifacts are not substitutes for this authority object.

External platform/tool confirmation requirements remain outside repository control and must not be bypassed.

## Confirmation rule

Do not require a redundant second confirmation when the active accepted authorization context already exactly covers the same concrete effect and no material term changed.

If the counterparty introduces new material terms, application policy decides whether new confirmation is required.

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

Supervisor fallback is permitted to continue the same already-authorized live task when script/Gemma cannot progress. Recurrent fallback cases should be moved into deterministic script/PhraseMatrix or bounded Gemma skills in later iterations.

## Live-call policy

A call must fail closed before dialing unless the exact task/target is covered by an accepted application authorization context and required readiness gates pass.

Rules:

- one active cellular call at a time;
- exact target only; no model/tool target widening;
- readiness immediately before dial;
- phone confirmed `IDLE` before dial;
- bounded duration/retries;
- do not touch emergency, urgent-care, crisis, premium-rate or unrelated critical-service targets;
- do not terminate unrelated pre-existing calls;
- do not infer authority from a ServicePack, allowlist, connected phone, prior call, handoff text, model output or `.agent/results`;
- if a physical call is made, cleanup must return the owned call to `IDLE`.

Operational autonomy means Local Agent/ADB/supervisor should perform the work directly rather than using the operator as a terminal/log relay. It does not mean bypassing authority or platform controls.

## Orange CLIR physical campaign

Historical G5a/G5b discovery established the initial route behavior. The active work is now physical enable/disable acceptance.

Requirements:

- exact Orange CLIR campaign target/task/effect only;
- pre-dial readiness + `IDLE`;
- script/PhraseMatrix first, Gemma second, live supervisor fallback when unresolved;
- late-bound identity disclosure only through application policy;
- exactly one shared `CallCommitmentGate` permit immediately before each real account-changing commitment;
- separate factual external-success evidence;
- independent CLIR network-state interrogation when technically available;
- cleanup to `IDLE`;
- no unrelated service/account widening.

Current physical evidence after the latest iteration says caller ID is still not restricted, so the enable task is not complete.

## Physical-first acceptance

During the active CLIR campaign, physical calls and independent state interrogation are the acceptance evidence. Synthetic/unit suites must not be treated as proof of live success or used as a substitute for the physical iteration loop.

## Data minimization

Do not retain by default raw PCM, recordings, full transcripts, credentials, plaintext identity values or unrelated counterparty data. Prefer typed IDs, redacted evidence, sizes/timings and bounded correlation IDs.

Transient supervisor relay branches may contain raw turn data only for the active session and must be deleted during cleanup. Sanitized evidence must be preserved before cleanup when needed for debugging.

## TAKE OVER / failure ordering

```text
stop accepting/releasing unsafe AI output
 -> supervisor may continue the same authorized dialogue when takeover is the intended fallback
 -> abort telephony media generation when the session must terminate
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/supervisor generation
 -> best-effort cancel remaining work
 -> hang up only the call owned by this session when appropriate
```

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. A connected phone is not authorization. Permit consumption is not external success. The CLIR task is not complete until exact factual external-success evidence has been accepted by application-owned completion logic and, when available, independent network-state verification confirms the expected state.
