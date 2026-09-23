# Security and privacy

## Objective

The agent may dial, speak, disclose data, confirm or commit only inside authority explicitly granted by the user/operator and existing application owners. Failure must narrow capability, fail closed or return control to the human; it must never broaden authority.

## Frozen privilege/media boundary

Protected Samsung call-audio access stays inside the privileged helper / Shizuku UserService. Continuous PCM crosses through transferred PFDs; Binder/AIDL is control only. The helper has no model networking or business-policy authority.

This path is `PROVEN_S22 / FROZEN`. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing it.

## Authority owners

Do not create parallel authority in TaskGraph, ServicePack, CallPlan/PhraseMatrix, model/shadow, model storage/import, parser, storage or Skills.

- `CallTask` — immutable task, hard constraints, preferences and authorized scope.
- `CallResolvedTarget` — exact target; cannot widen a live allowlist.
- `CallWorkflow` — proposal/user-decision state and terminal structured outcome.
- `CallConfirmationPolicy` — deterministic evaluation of a typed proposal.
- `CallCommitmentGate` — exact one-shot permit bound to one concrete proposal.
- `FactDisclosurePolicy` — application-owned personal-data disclosure decision.
- output approval — final text release before TTS/TX.

## Gemma model integrity and ownership

Local model bytes are data, not authority. The production runtime must not depend on another app's sandbox or trust an unverified model file merely because it has the expected filename.

The reviewed import path pins the expected Gemma 4 E2B identity and SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Import ordering is fail-closed:

```text
user-selected SAF source
 -> app-owned staging file
 -> streaming SHA-256 verification
 -> flush + fsync
 -> atomic replacement request
 -> active app-owned model path
```

Rules:

- unreadable, empty or wrong-hash data never activates;
- verification happens before active-path replacement;
- failed writes/verification remove staging data;
- activation failure preserves the previous active model;
- do not silently weaken an atomic activation failure to an ordinary non-atomic overwrite;
- Edge Gallery/ADB may be development sources for bytes but never runtime authority or a required production dependency;
- model import does not grant dial, disclosure, commitment, completion or speech-release authority.

This import/activation boundary is `HOST_GREEN / PROVEN_S22` on the S22 filesystem.

## Model readiness fail-closed rule

The active model has one application-owned readiness result: `MISSING / INVALID / READY`. Ordinary readiness uses cheap pinned catalog metadata (including expected size); full SHA-256 remains mandatory before activation/import. A missing or invalid `LOCAL_GEMMA_4` model fails product call preparation before STT/TTS/backend construction and before lazy LiteRT engine initialization. Readiness is data, not authority.

Developer/diagnostic backend constructors do not become product readiness owners. Their existence never grants dialing permission, and every real call still requires the separate fresh live-call authorization gate.

## Model network acquisition security

The reviewed acquisition source is pinned to an immutable Hugging Face revision, not `main`. The current artifact is public/non-authenticated, so Android must not attach credentials or bearer tokens to the model request. Secrets must never be stored in the acquisition catalog, Git, Local Agent tasks/results or ordinary logs.

Acquisition rules:

- initial request uses HTTPS and immutable repository revision;
- redirects may complete only over HTTPS to the reviewed Hugging Face/CDN host family;
- HTTP failure or known wrong `Content-Length` fails before model body activation;
- response bytes stream directly into the existing installer rather than being held in memory;
- source metadata and HTTP headers are hints/rejection gates only; full installer SHA-256 and expected-size checks remain authoritative;
- partial/cancelled/network-failed downloads cannot replace the active model;
- no automatic multi-gigabyte download is triggered by readiness state; normal UI must require explicit user initiation;
- future resume support must still verify the complete final byte stream before activation.

The source/downloader, cancellation/progress lifecycle and explicit confirmation UI are `HOST_GREEN / PROVEN_S22`. The operator explicitly authorized and initiated the full 2,588,147,712-byte transfer through the reviewed UI. The installer accepted only the exact pinned size/SHA-256, atomically replaced the prior model, left no staging file, and preserved app-owned UID/SELinux labeling. A subsequent clean UI restart reported `READY` and no-call inference remained green. This proof does not authorize dialing or widen any call/data authority.

## Identity and disclosure

A value existing in IdentityVault is not permission to disclose it.

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / graph state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext as late as practical
```

Plaintext identity values must stay out of TaskGraph definitions/context, ServicePacks, ordinary evidence/logs, Local Agent JSON, Git history and supervisor/model context by default.

Android vault storage is app-private, no-backup ciphertext using `AtomicFile`; encryption uses a non-exportable Android Keystore AES-256/GCM key. Missing/invalid keys and malformed ciphertext fail closed. This boundary is physically `PROVEN_S22`.

## TaskGraph and supervisor boundary

TaskGraph is application-owned orchestration state, not execution authority. Model/shadow output can only propose bounded existing transitions and typed non-secret slot values.

Before reducer entry, `TaskGraphApplyBridge` re-checks current graph version/state/generation, mapping, transition legality, provenance, slot scope, current slot authorization, required slots and schema. Unknown/stale/authority-bearing data fails closed.

A deterministic candidate rejection cannot fall through to shadow. A validated shadow candidate is still only candidate data.

## Final-text and speech boundary

The source of finalized text does not grant authority. Synthetic finalized text shares the normal post-STT deterministic product path while bypassing speech/audio/backend/TTS.

Only application-owned output approval may release text to TTS/TX. Partial/speculative text remains quarantined and may not trigger early telephony output.

## BOOK_APPOINTMENT commitment and completion boundary

Keep these facts separate:

```text
permit issued
 != permit consumed
 != business success confirmed
```

### Permit issuance

A commitment permit may be issued only for the exact proposal returned by the existing workflow user-confirmation owner while the exact workflow remains `ACTIVE_NEGOTIATION`. The permit is one-shot and opaque. Revocation is token-scoped; a session cannot globally clear a foreign/newer permit.

### Permit consumption

`CallRealtimeCommitmentFunctionHandler` validates and consumes the opaque permit through `CallCommitmentGate`. Only after successful consume may it emit redacted `CallCommitmentConsumptionEvidence` bound to the exact proposal.

Recording consumption:

- requires graph `COMMITMENT`;
- requires the exact approved proposal and workflow still active;
- requires an owned permit to have been issued;
- requires the gate authorization to be gone already;
- is one-shot;
- does not run `COMMIT_SUCCEEDED`;
- does not call `CallWorkflow.complete(...)`;
- does not itself prove external success.

### Deferred completion

Default/public CallPlan completion behavior remains unchanged. Only explicit reviewed product wiring may select `DEFER_TO_PRODUCT_OWNER`, leaving a structured COMPLETE result as data while the workflow stays active.

### Factual completion

`completeBookAppointment(outcome)` is the reviewed factual completion owner boundary. It requires:

- exact consumed proposal evidence;
- exact approving workflow still active;
- `CallOutcomeStatus.SUCCESS`;
- outcome time matching the approved proposal and graph slot;
- matching known currency/numeric price, provider and location fields;
- a legal, effect-free staged `commit-complete` graph transition.

Ordering is fail-closed:

```text
validate exact evidence
 -> stage TaskGraph COMPLETE candidate
 -> CallWorkflow.complete(outcome)
 -> commit staged TaskGraph snapshot only after owner success
```

Ordinary deterministic/shadow candidate application explicitly refuses `commit-complete`; classifier/reducer/model output cannot gain factual completion authority.

## Skills and ServicePack boundary

Skills and ServicePacks may supply bounded task/service knowledge or existing candidate IDs. They must not directly dial, widen target scope, emit arbitrary telephony speech, read/export the whole vault, invent credentials/facts, confirm a booking or bypass the existing workflow/disclosure/commitment/output owners.

A known IVR route never authorizes a call.

## Live-call policy

A call must fail closed before dialing unless the exact task/target is valid and freshly authorized in the current operator session and required readiness gates are satisfied.

Rules:

- one active cellular call at a time;
- bounded duration/retries;
- no emergency, urgent-care, crisis, premium-rate or arbitrary short-code test targets;
- no model/tool target widening;
- unrelated pre-existing calls must not be terminated;
- test-only public-business calls disclose AI/test purpose at the start and stop if consent is declined;
- genuine bookings require genuine fresh user authorization;
- no old chat, handoff, ServicePack, connected phone or `.agent/results` file carries dialing permission forward.

## Data minimization

Do not retain by default raw PCM, recordings, full transcripts, credentials, plaintext identity values, unnecessary personal/medical details or unrelated counterparty identifiers. Prefer typed states/slots, redacted evidence, sizes/timings and bounded correlation IDs.

## TAKE OVER / failure ordering

Local safety actions do not wait for model/network acknowledgement:

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/shadow generation
 -> best-effort cancel remaining work
```

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. Compiled/packaged instrumentation is not a physical device proof. A device being connected is not live-call authorization.

Gate D, direct Gemma no-call runtime, SAF import/atomic activation and the `MISSING / INVALID / READY` readiness boundary have physical S22 proof for their stated successful paths. Host tests cover missing/invalid readiness without destructively corrupting the physical model.
