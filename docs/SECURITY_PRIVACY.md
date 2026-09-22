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

## Hybrid LLM supervisor boundary

The Gate D supervisor is interpretation assistance, not a dialogue authority owner.

Allowed result shape is bounded structured data such as:

```text
existing transition ID
+ typed slot values
+ confidence/diagnostic metadata
```

The supervisor must not own or smuggle through:

- arbitrary telephony speech/utterance text;
- target numbers or dialing instructions;
- new graph/service/action IDs;
- credentials or new authorized facts;
- confirmation/commitment/completion decisions.

Reject stale generations, low/invalid confidence, unknown IDs, state-incompatible transitions, invalid slots, constraint violations and metadata carrying authority.

The existing `serviceintent/` resolver is the security precedent: bounded candidates, unsafe-metadata rejection, stale-result rejection, registry revalidation and a separate execution validator.

## Skills boundary

Skills may help build/update a bounded `CallTask`, select an existing TaskGraph/ServicePack, gather missing pre-call preferences, or suggest existing transitions/slots.

Skills must not directly:

- dial arbitrary targets;
- widen allowlists;
- emit arbitrary telephony speech;
- invent credentials or sensitive facts;
- confirm bookings/purchases;
- bypass `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` or output approval.

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
- unrelated counterparty identifiers;
- unnecessary personal/medical details.

Prefer state transitions, typed slots, sizes/timings, sanitized failure reasons, local correlation IDs and bounded text only when needed for explicit evidence.

## Speculative / partial speech rule

Partial STT or speculative inference may reduce latency only while quarantined:

```text
partial transcript -> optional cancelable preparation
final endpoint -> deterministic/supervisor interpretation
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

`HOST_GREEN` is not `PROVEN_S22`. ServicePack edge verification is not task completion. A real commitment is not authorized merely because the dialogue path is known.
