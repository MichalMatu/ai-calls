# Security and privacy

## Objective

The agent may speak, dial, disclose data or commit only inside authority explicitly granted by the user/operator. Technical failure must disable AI injection, fail closed, or return control to the human caller; it must never broaden authority.

## Privilege boundary

Protected Samsung call-audio access stays inside the privileged helper / Shizuku UserService.

Continuous PCM crosses through transferred PFDs. Binder/AIDL is control only. The privileged helper has no model networking and no business-policy authority.

The frozen media path is documented in `docs/PHASE2D_FREEZE_2026-09-18.md` and is not part of ordinary Gate D work.

## Authority owners

Do not create parallel authority in TaskGraph, ServicePack, CallPlan, a matcher, model, helper or Skill.

- `CallTask` — immutable task, hard constraints, preferences and `authorizedFacts`;
- `CallResolvedTarget` — one concrete target, without authority to widen a live dial allowlist;
- `CallWorkflow` — task progress, proposal/user-decision state and terminal outcome;
- `CallConfirmationPolicy` — deterministic evaluation of one typed `CallProposal`;
- `CallCommitmentGate` — exact one-shot permit bound to one concrete proposal;
- application-owned output approval — final text release before TTS/TX.

Counterparty/model/helper/matcher/service-pack text cannot create new authorized facts, destinations, actions or commitments. Missing required user data must fail closed or escalate; never invent sensitive data to satisfy a prompt.

## IdentityVault and personal-data disclosure

Persistent identity/contact data must be separated from TaskGraph slots, ServicePack data, dialogue history and model memory.

Target separation:

```text
IdentityVault
  persistent user data

CallTask / AuthorizedFactSnapshot
  only fields authorized for this task

DialogueState
  transient data learned during the call
```

Typical `IdentityFieldId`s may include:

```text
FIRST_NAME
LAST_NAME
PHONE
EMAIL
ADDRESS
DATE_OF_BIRTH
PESEL
```

The list is typed and explicit. Do not expose the vault as an arbitrary string map.

### Storage

The Android vault should use app-private ciphertext storage with a non-exportable cryptographic key protected by Android Keystore. Prefer modern Android Keystore primitives such as an AES key restricted to authenticated encryption use; do not build new storage on deprecated `EncryptedSharedPreferences` / `MasterKey` APIs.

The encrypted record format must be versioned. Backup/restore behavior must be intentionally designed and tested; do not assume ciphertext restored to another installation/device will remain decryptable or safe.

If later key rotation/envelope encryption becomes necessary, evaluate a maintained cryptography library such as Tink rather than inventing a custom key-management format.

### Per-task authorization

A value existing in the vault does **not** grant permission to disclose it.

Before a call, build a bounded per-task authorization snapshot/reference set. It should identify which fields may be used for this exact task/target and under what disclosure policy.

For example:

```text
BOOK_APPOINTMENT
allowed:
  FIRST_NAME
  LAST_NAME
  PHONE
not authorized:
  PESEL
  ADDRESS
```

High-sensitivity identifiers such as PESEL should default to explicit per-task authorization. Product UX may additionally require device/user authentication at disclosure time for selected fields.

### FactDisclosurePolicy

A dedicated application-owned disclosure decision should evaluate:

- current task and purpose;
- exact resolved target;
- current TaskGraph state;
- requested `IdentityFieldId`;
- field sensitivity;
- per-task authorization;
- live/test mode;
- optional user-auth requirement.

Result must be typed, e.g. `ALLOW`, `ASK_USER`, or `DENY`.

Plaintext should be resolved as late as practical, ideally only for a validated and approved deterministic disclosure action. The LLM/supervisor normally needs to know that a field is available/authorized, not its secret value.

Do not put raw PESEL, address, birth date, phone/email values or other sensitive identity data into:

- TaskGraph definitions;
- ServicePack files;
- general event/evidence logs;
- supervisor prompts/context unless strictly necessary and explicitly authorized;
- Local Agent task JSON;
- Git history.

If a counterparty requests a non-authorized field, the system must ask the user, take over/defer, or fail closed. It must never infer or invent the value.

## TaskGraph boundary

TaskGraph is application-owned product logic, not model-generated runtime code.

A TaskGraph may define existing typed states, transitions, slot schemas, guards, recovery counters, proposal/confirmation/commitment stages and terminal outcomes.

A model/Skill may suggest only bounded existing transitions and typed slot values. Every suggestion must be revalidated against:

- current graph state;
- current task/session generation;
- slot schema/types;
- user constraints/preferences;
- authorized facts;
- confirmation/commitment requirements.

Unknown state/transition IDs, stale results, invalid slot data or authority-bearing metadata fail closed.

## ServicePack / IVR knowledge boundary

A ServicePack such as the Orange pack stores evidence and navigation knowledge about a particular service/counterparty. It may contain reviewed speech/actions, known IVR nodes/edges, prompt variants, route status, risks and barriers.

ServicePack data does not itself authorize execution. In particular:

- a known route does not widen the dial target allowlist;
- a reviewed utterance does not authorize a new commitment;
- a `VERIFIED` node/edge does not imply a service route or task is authorized;
- stale/historical evidence must not silently override current live observations;
- authentication/payment/activation/contract barriers remain barriers even if a route is known.

Orange remains a durable future product ServicePack, but its existence is never permission to call Orange in a new session.

## CallPlan + PhraseMatrix boundary

`CallPlan` is product policy, not independent authority. Workflow mutation remains in `CallPlanTurnCoordinator` + existing `CallWorkflow` APIs.

Only `CallPlanAction.SAY` carries speech text. `ASK_REPEAT`, `PROPOSAL`, `COMPLETE` and `TAKE_OVER` do not carry implicit speech and must not silently become model fallback.

The native PhraseMatrix is classification-only:

```text
final transcript
 -> PhraseMatrix match(existing ruleId)
 -> deterministic validation
 -> typed CallPlanDecision
 -> existing workflow/output approval path
```

Security invariants:

- PhraseMatrix cannot release speech merely from a raw match;
- only validated rule IDs may advance dialogue context;
- matcher miss/ambiguity must not silently widen into a sensitive action;
- future fuzzy matching and LLM supervision remain subject to the same authority validation.

## Shadow supervisor and DialogueFit boundary

The Gate D LLM may observe finalized turns from the start of the conversation in **shadow mode**, but observation is not authority.

Shadow context must be bounded and privacy-minimized. Prefer:

- task goal and current state;
- existing allowed transitions;
- typed non-secret validated slots;
- names/availability of authorized identity fields rather than plaintext values;
- a bounded recent-turn window or sanitized summary;
- the final counterparty transcript.

Shadow output is quarantined diagnostic/interpretation state only. It must not directly:

- advance TaskGraph;
- mutate `CallWorkflow`;
- release speech;
- disclose facts;
- create commitments.

A separate application-owned `DialogueFit`/escalation policy decides whether deterministic interpretation is sufficient.

DialogueFit must not be one untrusted model score. Combine explainable signals such as matcher result, parser completeness, state compatibility, negation/contradiction checks, STT quality where available, repeated unknowns and disagreement between deterministic/shadow interpretations.

Prefer a small typed result initially:

```text
HIGH
UNCERTAIN
LOW
BROKEN
```

Thresholds/weights must be calibrated from scripted/simulated evaluation evidence, not guessed. Add hysteresis/debouncing so a noisy turn cannot oscillate control between deterministic and supervisor paths.

When `DialogueFit` requires escalation, the supervisor may propose only bounded structured data such as:

```text
existing transition ID
+ typed slot values
+ confidence/diagnostic metadata
```

It must not own or smuggle through:

- arbitrary telephony speech/utterance text;
- target numbers or dialing instructions;
- new graph/service/action IDs;
- credentials or new authorized facts;
- confirmation/commitment/completion decisions.

Reject stale generations, low/invalid confidence, unknown IDs, state-incompatible transitions, invalid slots, constraint violations and metadata carrying authority.

The existing `serviceintent/` resolver is the security precedent: bounded candidates, unsafe-metadata rejection, stale-result rejection, registry revalidation and a separate execution validator.

## Skills boundary

Skills may help build/update a bounded `CallTask`, select an existing TaskGraph/ServicePack, gather missing pre-call preferences, request authorization for specific identity fields, or suggest existing transitions/slots.

Skills must not directly:

- dial arbitrary targets;
- widen allowlists;
- emit arbitrary telephony speech;
- read/export the whole IdentityVault;
- invent credentials or sensitive facts;
- confirm bookings/purchases;
- bypass `CallWorkflow`, `FactDisclosurePolicy`, `CallConfirmationPolicy`, `CallCommitmentGate` or output approval.

## Final-text release boundary

The existing controller remains the single text release/generation owner:

```text
ordinary final transcript
 -> TextCallTurnController.submitUserText(...)
 -> backend.generate(...) when that path is explicitly selected
 -> TextOutputApprovalPolicy

exact deterministic candidate
 -> TextCallTurnController.submitCandidateText(...)
 -> TextOutputApprovalPolicy
```

`TextCallFinalTurnDispatcher` provides the neutral route seam:

- `Generate` — ordinary backend path;
- `Candidate(text)` — exact pre-determined candidate through the same approval;
- `Consumed` — no generation and controller cancellation.

`Consumed` invalidates stale work so an older backend/supervisor callback cannot be released after a structured decision.

## READY_TO_DIAL

A call must fail closed before dialing unless:

- task and target are valid;
- live target is explicitly authorized in the current operator session;
- task/target/workflow/plan/graph data are mutually consistent;
- required identity-field availability/authorization is known without widening authority;
- required STT/TTS readiness is proven;
- any selected local/network model provider is explicitly allowed and ready;
- required warm-up succeeds;
- the live-test mode and commitment policy are known.

Readiness must not silently switch to a provider with different privacy, cost or network semantics.

## Controlled live-call policy

Physical validation follows `AGENTS.md` and `docs/ROADMAP.md`.

General rules:

- fresh explicit operator authorization for the current target/task;
- one active cellular call at a time;
- bounded duration/retries;
- no model/tool expansion of the dial allowlist;
- no emergency, urgent-care, crisis, premium-rate or arbitrary short-code test destinations;
- a runner may hang up the bounded call it created;
- an unrelated pre-existing call must not be terminated without explicit authorization.

Authorization from an old chat, handoff, ServicePack or `.agent/results` never carries into a new session.

### Test-only real-world calls

For ordinary public business/reception validation, disclose the AI/test purpose at the **start** and ask whether a brief non-booking test is acceptable.

If consent is declined, thank the person and stop.

A test-only call must not create or hold a real appointment, ticket, order or other commitment. Do not wait until the end to reveal that it was only a test.

### Genuine user-authorized calls

If the user genuinely wants the appointment/task, the call may pursue a real outcome using only authorized facts and the normal proposal/confirmation/commitment path.

A genuine booking is not retracted merely because the interaction also provided product evidence.

### Per-target budget

Default development policy:

- one meaningful call per organization/reception in a slice;
- second call only after an early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff to tune wording;
- no broad unsolicited call campaigns;
- retain public target source, mode, call count and structured outcome.

## Personal and medical data

Appointment calls may naturally expose personal or medical context. Minimize it.

- Use only user-authorized facts necessary for the task.
- Do not infer or invent medical details.
- Do not retain unnecessary medical/reception transcripts.
- Prefer typed slots/events and minimal evidence over raw dialogue logs.
- Redact identity values from ordinary diagnostics by default.
- If a counterparty asks for information not authorized by `CallTask`, fail closed/escalate rather than improvise.

## Local speech privacy

Production local speech must:

- require on-device recognition rather than silently falling back to cloud STT;
- use local/non-network-required TTS voices unless a different provider is explicitly selected;
- bound and clean temporary PCM/files;
- close temporary PFDs on completion, cancellation and TAKE OVER;
- avoid retaining call recordings or full transcripts by default.

## Credentials

A standard OpenAI API key is host/backend-only if paid work is resumed. It must never be:

- embedded in source/resources/BuildConfig/APK;
- stored in Android app-private config;
- passed through Android Intent;
- passed in ADB argv/process arguments;
- logged by app/helper/scripts.

Do not silently switch from a local provider to a paid/network provider.

## Data minimization

Do not retain by default:

- raw PCM;
- call recordings;
- full transcripts;
- model/tool arguments containing user secrets;
- credentials;
- plaintext identity-vault values in general logs/events;
- unrelated counterparty identifiers;
- unnecessary personal/medical details.

Prefer state transitions, typed slot names, redacted/sanitized evidence, sizes/timings, local correlation IDs and bounded text only when needed for explicit evidence.

## Speculative / partial speech rule

Partial STT or speculative inference may reduce latency only while quarantined:

```text
partial transcript -> optional cancelable preparation
final endpoint -> deterministic/shadow interpretation
              -> DialogueFit/escalation policy
              -> validated policy
              -> approved text/action
              -> TTS/TX
```

Changed or resumed speech invalidates stale speculation. Partial text never grants authority and never triggers early telephony TX.

## TAKE OVER

Required local-first ordering:

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/supervisor generation
 -> best-effort cancel remote/local work
```

The first steps cannot wait for model/network acknowledgement. App/helper death must likewise disable injection.

## Handoff safety

Follow `docs/HANDOFF_PROTOCOL.md` when ending a major session.

Handoff and continuation-prompt files must never embed secrets, stale Local Agent bindings or implied future live-call authorization. A new chat must use a fresh Local Chat Bridge binding if that mode is active and obtain fresh authorization before physical calls.

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. ServicePack edge verification is not task completion. A real commitment or sensitive-data disclosure is not authorized merely because the dialogue path is known.
