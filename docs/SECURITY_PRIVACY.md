# Security and privacy

## Objective

The agent may dial, speak, disclose data, confirm or change external state only inside authority explicitly granted by the user and existing application owners. Failure must narrow capability, fail closed or return control to the human; it must never broaden authority.

## Stable privilege/media boundary

Protected Samsung call-audio access stays inside the privileged helper / Shizuku UserService. Continuous PCM crosses through transferred PFDs; Binder/AIDL is control only.

This path is `PROVEN_S22 / FROZEN`. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing it.

## Authority owners

- `CallTask` — exact task, hard constraints, preferences and authorized facts;
- `CallResolvedTarget` — exact target;
- application-owned task/effect policy — validates whether a concrete external effect is inside the task scope;
- `CallWorkflow` — proposal/user-decision state and terminal outcome;
- `CallCommitmentGate` — exact one-shot external commitment permit;
- `FactDisclosurePolicy` — plaintext personal-data disclosure;
- output approval — final text release before TTS/TX.

TaskGraph, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers, model storage/import and diagnostics are not authority owners.

## Generic external-effect rule

The next architecture generalizes commitment from appointment-shaped `CallProposal` into a typed application-owned external effect.

Keep these separate:

```text
user task authorization
 != candidate effect validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

A model/supervisor may help extract or propose candidate effect data. It cannot create the permit, widen the candidate, declare success or complete the workflow.

See `docs/GENERIC_PHONE_TASK_AUTHORITY.md`.

## No redundant confirmation

Do not require a second confirmation merely because a previously explicit, current-chat instruction reaches the commitment point later.

Example: if the user explicitly authorized `enable CLIR` for the exact current call target, and the application constructs exactly that effect with no new material term, policy may treat that instruction as the user decision.

If the counterparty introduces materially new terms outside the already-authorized scope, the application — not the model — decides whether new user confirmation is needed.

## Identity and disclosure

A value existing in IdentityVault is not permission to disclose it.

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext as late as practical
```

Plaintext identity values must stay out of TaskGraph definitions/context, ServicePacks, ordinary evidence/logs, Local Agent JSON, Git history and supervisor/model context by default.

Android vault storage remains app-private no-backup ciphertext backed by Android Keystore AES-256/GCM and is `PROVEN_S22`.

## Dialogue/model boundary

Current dialogue stack:

```text
STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skills
 -> supervisor fallback when unresolved
 -> application validation/output approval
 -> TTS/TX
```

Gemma and supervisor output are candidate dialogue data. They must not independently:

- dial or widen the target;
- disclose unauthorized facts;
- widen task/effect scope;
- bypass output approval;
- create/consume commitment authority;
- mark external success;
- complete the task.

## Model integrity/readiness

Gemma 4 model bytes are data, not authority. The app-owned import/download/readiness lifecycle is `HOST_GREEN / PROVEN_S22` and verifies the pinned model identity before activation.

A missing/invalid model fails product preparation before local model use. Do not reopen model acquisition/storage merely as part of the generic task refactor.

## ServicePack boundary

ServicePacks may provide service knowledge, IVR hints, typed parsers, effect adapters and success-evidence rules. A known route or phrase never authorizes dialing, disclosure or commitment.

Orange exact phrase mappings remain acceptance fixtures, not product authority.

## Live-call policy

A call must fail closed before dialing unless the exact task/target is valid, freshly authorized in the current chat and required readiness gates are satisfied.

Rules:

- one active cellular call at a time;
- bounded duration/retries;
- no emergency, urgent-care, crisis, premium-rate or unrelated critical-service test targets;
- no model/tool target widening;
- unrelated pre-existing calls must not be terminated;
- genuine user-authorized tasks stay within authorized facts/effects;
- no old chat, handoff, ServicePack, connected phone or `.agent/results` file carries dialing permission forward.

Repository tooling must not add a redundant second approval ceremony after a fresh exact authorization already exists for that same external action.

## Data minimization

Do not retain by default raw PCM, recordings, full transcripts, credentials, plaintext identity values, unnecessary medical details or unrelated counterparty identifiers.

Prefer:

- typed task/effect/state IDs;
- redacted success/consumption evidence;
- sizes/timings;
- bounded correlation IDs.

Transient ChatRelay raw turn files must be cleaned after the session.

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

`HOST_GREEN` is not `PROVEN_S22`. Compiled/packaged instrumentation is not physical proof. A connected device is not live-call authorization.

Existing S22 proof remains valid for frozen media, IdentityVault, Gemma lifecycle and appointment Gate D. The new generic external-effect abstraction must preserve those proofs and receive its own host/no-call evidence before any live acceptance call.
