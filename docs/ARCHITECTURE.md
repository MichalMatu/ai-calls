# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping ownership boundaries explicit:

1. cellular media;
2. speech conversion;
3. deterministic dialogue routing and structured interpretation;
4. task/service knowledge;
5. task/workflow authority;
6. optional bounded LLM/Skill supervision;
7. personal-data disclosure authority;
8. user takeover and fail-safe cleanup.

Failure must move toward a normal human call or a safe stop. Counterparty speech, model text, service-pack data and helper/tool output never widen authority by themselves.

## Frozen media boundary

`CallMediaSessionCoordinator` owns one privileged RX+TX generation, bind/prepare/start ordering, heartbeat, endpoint leases, helper failure observation and whole-generation teardown.

Continuous PCM crosses the privilege boundary through transferred PFDs. Binder/AIDL is control only.

Internal format:

```text
signed PCM16LE
mono
16 kHz
```

Samsung implementation stays in `privileged-helper/`:

```text
RX: VOICE_DOWNLINK -> SamsungVoiceDownlinkCapture -> PFD
TX: PFD -> SamsungUplinkPipeSession -> SamsungCallAssistantTrack
    -> USAGE_CALL_ASSISTANT / TELEPHONY_TX
```

Stereo duplication exists only at the Samsung TX boundary. Before changing this layer, read `docs/PHASE2D_FREEZE_2026-09-18.md`.

This path is `DONE / PROVEN_S22 / FROZEN` and should not be redesigned while building Gate D.

## Authority boundary

The existing domain model remains authoritative:

- `CallTask` / `CallConstraints` / `CallPreferences` / `authorizedFacts`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

`CallPlan`, TaskGraph, PhraseMatrix, service packs, models and Skills reference or propose into those owners; none is a parallel authority store.

`CallPlanEngine` can produce only typed `SAY`, `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, or `TAKE_OVER` decisions. `CallPlanTurnCoordinator` applies typed proposal/completion decisions through existing workflow APIs and rejects task/target/state mismatch fail-closed.

## Personal identity and fact-disclosure boundary

Gate D must not treat persistent identity data as ordinary TaskGraph slots.

Keep three stores conceptually separate:

```text
IdentityVault
  durable user identity/contact facts
  e.g. first name, last name, phone, email, address, date of birth, PESEL

TaskState / CallTask
  facts and permissions authorized for this one task
  + task constraints/preferences

DialogueState
  transient facts learned during this call
  e.g. offered appointment = Thursday 17:30
```

### IdentityVault

`IdentityVault` is a product data source, not an authority owner and not model memory.

Target contract:

```text
IdentityFieldId
+ encrypted stored value
+ sensitivity metadata
+ provenance / last-updated metadata
        |
        v
per-task authorization
        |
        v
AuthorizedFactSnapshot / fact reference
        |
        v
CallTask
```

The first implementation should expose a narrow typed interface rather than a generic key/value bag. Persistent secret values must not be copied into TaskGraph definitions, ServicePacks, event logs or prompts.

The Android implementation should use app-private encrypted storage with cryptographic key material protected by Android Keystore. Do not build a new vault on deprecated `EncryptedSharedPreferences` / `MasterKey`. The storage format must be versioned and backup/restore behavior must be explicit because encrypted records are useless or dangerous if restored without the corresponding key semantics.

### FactDisclosurePolicy

Having a value in `IdentityVault` does not mean the call may disclose it.

A dedicated application-owned disclosure decision should evaluate at minimum:

```text
task id / purpose
+ exact target
+ current TaskGraph state
+ requested IdentityFieldId
+ field sensitivity
+ per-task authorization
+ live/test mode
+ optional user-auth requirement
```

Result should be typed, e.g.:

```text
ALLOW
ASK_USER
DENY
```

High-sensitivity identifiers such as PESEL should default to explicit per-task authorization and may additionally require device/user authentication at disclosure time.

The actual secret value should be resolved as late as practical, ideally only when an already-approved deterministic `SAY`/typed disclosure action is being rendered. A supervisor usually needs to know that an authorized field is available, not its plaintext value.

If a counterparty requests a fact not authorized for the current task, the correct behavior is `ASK_USER`, `TAKE_OVER`, defer, or fail closed — never model invention.

## Product knowledge layers

Gate D intentionally separates two durable knowledge structures.

### TaskGraph

`TaskGraph` describes **what the user wants to accomplish and how a bounded task progresses**.

Examples:

```text
BOOK_APPOINTMENT
RESERVE_TABLE
CHECK_ORDER_STATUS
CHANGE_APPOINTMENT
```

A TaskGraph owns product-level states/transitions/slot schemas/guards/recovery semantics such as:

```text
START
 -> REQUEST_APPOINTMENT
 -> COLLECT_CONSTRAINTS
 -> OFFER_RECEIVED
 -> PROPOSAL
 -> CONFIRMATION
 -> COMMITMENT
 -> COMPLETE
```

TaskGraph is provider/business independent where possible. It must be application-owned data/code, versionable and replayable. A runtime model may suggest only an existing transition or typed slot value; it cannot create executable graph structure at runtime.

### ServicePack / IVR knowledge graph

A ServicePack describes **how a particular counterparty/service environment behaves**.

Examples:

```text
service-packs/orange/
future service-packs/<operator-or-provider>/
```

A service pack may preserve:

- known IVR prompts and prompt variants;
- physically observed nodes/edges;
- reviewed responses/actions;
- service IDs;
- barriers such as auth/payment/activation;
- risk and evidence status;
- last verification/evidence metadata;
- route freshness/staleness in a future schema.

Orange is the first persistent evidence-backed IVR ServicePack. It is **not discarded as a test**. Its current broad mapping is checkpointed, but the durable graph remains valuable for future real Orange support, IVR regression, service-pack schema evolution and deterministic routing.

A future task may combine both layers:

```text
CallTask: ENABLE_ROAMING
        +
TaskGraph: bounded goal/proposal/commitment semantics
        +
Orange ServicePack: known IVR navigation/evidence
        +
bounded supervisor only for ambiguity/change
```

The ServicePack answers mainly **where/how to navigate this service**. TaskGraph answers mainly **what outcome is desired, what data is authorized, and when the task may commit/complete**.

## Prepared product session

Pre-dial ownership remains:

```text
CallWorkflow + explicit target authorization
 + CallTask
 + authorized fact references/snapshot
 + optional TaskGraph
 + optional service pack
 + optional CallPlan
 + optional PhraseMatrix (requires CallPlan)
        |
        v
LocalTextCallReadinessCoordinator
        |
        +-> STT/TTS preflight
        +-> selected backend/supervisor readiness if enabled
        +-> task/target/plan/graph consistency
        +-> required fact availability/authorization checks
        |
        v
PreparedLocalTextCall
        |
        v
product session owner / LocalTextCallSession
```

`PreparedLocalTextCall` is a one-shot handoff. PhraseMatrix cannot be carried without a bound CallPlan. The product session must not move task/workflow authority into the speech/media layer.

Current `LocalTextCallSession` owns prepared dialogue state such as consecutive-unknown counters and `previousValidatedRuleId`; Gate D should extend or compose a narrow real product session owner rather than promoting diagnostic probes into the orchestrator.

## Speech and final-text boundary

Current Android speech pipeline:

```text
PCM input
 -> OnDeviceSpeechInput
 -> final transcript
 -> product-owned interpretation plane
      deterministic matcher/parser first
      shadow supervisor observation in parallel when enabled
      DialogueFit evaluation
      bounded supervisor proposal only when escalation policy requests it
      validated existing rule/transition only
 -> CallPlan / typed decision
 -> TextCallFinalTurnDispatcher
 -> TextCallTurnController
 -> TextOutputApprovalPolicy
 -> LocalTtsSpeechOutput
 -> PCM output
```

`LocalSpeechTextPipeline` owns speech lifecycle and its outer generation gate. `TextCallTurnController` owns complete-text generation/candidate approval and controller-level generation invalidation. Product TaskGraph/CallPlan/workflow ownership stays outside `localspeech`.

`TextCallFinalTurnDispatcher` remains neutral:

```text
Generate -> TextCallTurnController.submitUserText(finalTranscript)
Candidate(text) -> TextCallTurnController.submitCandidateText(exact text)
Consumed -> TextCallTurnController.cancel(); no text generation
```

`CallPlanFinalTurnRouteMapper` maps `SAY` to an exact candidate and maps `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` to `Consumed` while preserving the exact structured result.

The deterministic RX -> STT -> CallPlan -> approved TTS -> TX chain is physically proven on S22. Gate D should reuse it rather than re-prove or redesign it.

## Native PhraseMatrix fast path

The selected production matcher is the small native Kotlin `PhraseMatrix`.

Current host-green contract:

```text
final transcript
 -> normalization
 -> exact phrase / explicit alias lookup
 -> optional previousRuleId constraint
 -> PhraseMatch(existing ruleId, confidence, metadata)
 -> CallPlan validation
 -> existing workflow/output path
```

Properties:

- classification only, no arbitrary response text;
- deterministic replay;
- normalized collisions rejected fail-closed;
- unknown input returns no match;
- previous-turn matching receives context explicitly and owns no authority;
- all rule IDs are revalidated against the bound CallPlan;
- rejected matcher IDs do not poison later-turn context.

Gate D adds deterministic typed parsers/normalizers for high-value appointment data such as date, weekday, time, time range, offered slot, acceptance/refusal and alternatives.

Do not broaden fuzzy matching simply to make unknown counterparty speech pass.

## Shadow supervisor + DialogueFit

Status: **ACTIVE DESIGN TARGET FOR GATE D**, after deterministic TaskGraph simulation is green.

The supervisor has two distinct roles that must not be conflated.

### Observation plane

When enabled, the LLM may observe every **finalized** counterparty turn from the beginning of the call in shadow mode. It receives bounded context such as:

```text
task goal / current TaskGraph state
+ allowed transitions
+ slot schema and already validated non-secret slots
+ availability of authorized identity fields (not plaintext secrets by default)
+ bounded recent dialogue / summary
+ final counterparty transcript
```

Shadow output is quarantined interpretation only. It cannot mutate TaskGraph, CallWorkflow, authorized facts, output text or commitment state.

This lets the supervisor maintain context before it is needed, so escalation does not start from a cold prompt.

### Decision plane

The deterministic engine remains primary. A separate application-owned `DialogueFit`/escalation policy decides whether the current turn is sufficiently understood.

Do **not** define DialogueFit as one opaque LLM confidence number or raw text similarity. It should combine observable signals such as:

- STT confidence/quality signal when available;
- PhraseMatrix match kind/confidence;
- typed parser completeness/confidence;
- compatibility with transitions expected from the current TaskGraph state;
- contradiction/negation signals;
- missing required slots;
- repeated unknown/recovery count;
- disagreement between deterministic interpretation and shadow observation.

Prefer an explainable categorical result initially, for example:

```text
HIGH       -> deterministic transition/action
UNCERTAIN  -> deterministic clarification or optional supervisor check
LOW        -> supervisor proposal required
BROKEN     -> recovery / TAKE_OVER / safe stop
```

Any internal numeric score/thresholds must be calibrated from scripted/simulated scenario evidence rather than guessed and should use hysteresis/debouncing to avoid flapping between deterministic and supervisor modes.

### Bounded supervisor proposal

When escalation policy requests help, the supervisor may propose only bounded structured data:

```text
existing TaskGraph transition ID
+ typed slot values
+ confidence/diagnostic metadata
```

Target shape:

```text
current TaskGraph state
+ bounded existing transitions
+ allowed slot schema
+ final counterparty transcript
+ shadow context
        |
        v
LLM supervisor
        |
        v
existing transition ID + typed slots + confidence/diagnostics
        |
        v
deterministic validator
        |
        v
TaskGraph / CallPlan / workflow authority
```

It may not own:

- target number or dial authorization;
- arbitrary runtime telephony speech;
- new graph transitions/actions/service IDs;
- credentials or authorized facts;
- confirmation/commitment/completion authority.

Validation must reject stale generation/session results, unknown transition IDs, invalid slot types, state-incompatible transitions, authority-bearing metadata, constraint violations and low-confidence outputs.

The existing `serviceintent/` resolver is the design precedent for this boundary: bounded candidate set, structured output, stale-result rejection, unsafe metadata rejection, authoritative registry revalidation and separate execution validation.

## Service intent resolver boundary

Status: `HOST_GREEN / PRESERVED`.

Generic code lives in `serviceintent/` and is intentionally independent of Orange, telephony and speech output.

```text
natural user text + bounded service catalog
 -> UserIntentClassificationModel
 -> existing service_id or null + confidence
 -> ModelBackedUserIntentResolver fail-closed checks
 -> ServiceRegistry revalidation
 -> ServiceIntentExecutionValidator
 -> existing authority owners
```

Unknown IDs, cross-pack IDs, low confidence, stale generations and authority-bearing metadata such as speech/action/target are rejected. A `DISCOVERED` service may classify but yields `ROUTE_NOT_VERIFIED`; only a `VERIFIED` route may become eligible, and existing application authority must separately allow execution.

Provider implementations can later be local, OpenAI, Gemma or deterministic without changing the contract.

## Skills boundary

Skills are introduced **after TaskGraph v1 exists**, as a layer above/beside the task engine rather than as a telephony authority owner.

Preferred role:

```text
User request
 -> Skill builds/updates bounded CallTask
 -> selects existing TaskGraph/service pack
 -> gathers missing pre-call facts/preferences
 -> requests per-task authorization for needed IdentityFieldIds
 -> may suggest existing transition/slot
 -> application authority validates everything
```

Skills do not directly widen target allowlists, speak arbitrary telephony text, invent credentials, read the entire IdentityVault, commit bookings/purchases, or bypass workflow/confirmation/commitment/output approval.

## External design patterns to adopt, not import blindly

Gate D should reuse proven ideas from mature conversation/workflow systems while keeping the current Kotlin/S22 authority boundaries.

### Pipecat Flows pattern

Adopt the separation where graph/configuration owns legal transitions and handlers return structured data/results instead of choosing arbitrary next nodes. Keep per-stage context/tool/action surfaces narrow. This maps well to TaskGraph + typed parser/supervisor results.

Do **not** import Pipecat as the Android product runtime; its useful contribution here is the flow contract and evaluation ideas.

### XState/statechart pattern

Adopt:

- pure deterministic guards;
- explicit states/events/context separation;
- serializable/versioned graph data where practical;
- event sourcing/replay for audit and deterministic simulation;
- side effects outside transition/guard logic.

The Kotlin TaskGraph can implement these semantics without a JavaScript runtime dependency.

### LiveKit task/workflow pattern

Adopt small, reusable tasks that return typed results rather than one mega-agent owning everything. Appointment subtasks such as name/contact verification, slot parsing, disclosure consent and final confirmation should compose into the parent TaskGraph while the parent session remains authoritative.

### Slot-filling pattern

Adopt the durable idea used by form/dialogue systems: explicit required/optional slots, typed extraction, validation after extraction, dynamic requirements and explicit unhappy-path handling. Do not accept a slot merely because an NLU/LLM extracted a value.

These references are design inputs, not new sources of authority and not automatic dependency choices.

## Provider boundary

Preserved text/model providers remain selectable infrastructure:

- `LOCAL_PHONE_LLM` — experimental infrastructure, potentially reusable for bounded supervisor work;
- `EDGE_GALLERY` — frozen experimental provider;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred.

`OPENAI_REALTIME_AUDIO` is preserved/frozen and `LOCAL_REALTIME_AUDIO` is future work. Provider selection never changes task, target, confirmation, commitment, output approval or TAKE OVER authority.

## Endpointing

Real Orange IVR evidence proved that short fixed trailing silence and a single recognizer end event are not valid universal turn boundaries:

```text
speech/begin -> cancel pending END
recognizer END -> candidate end
resumed speech/partial growth -> cancel candidate
stable later END + bounded hangover -> final turn
long watchdog -> safety only
```

Partial/speculative inference may prepare work but never creates authority or early TX. Changed/resumed speech invalidates stale work.

Endpointing remains a product state-machine area to harden as Gate D exercises real human conversations.

## Diagnostics separation

Diagnostic probes are evidence drivers only. Do not grow `DiagnosticProbeActivity`, `MainActivity`, `LocalPhoneLlmLiveCallProbe`, Orange runners, Edge harnesses or the ChatGPT relay into the Gate D product orchestrator.

Keep the frozen media coordinator cohesive. Do not refactor physically proven Samsung media merely for line count or stylistic cleanup.

## TAKE OVER invariant

Every engine preserves local-first cancellation:

```text
stop accepting/releasing AI output
 -> abort local telephony media generation
 -> stop speech/audio workers
 -> invalidate active text/model/supervisor generation
 -> best-effort cancel remote/local inference
```

The first steps never wait for model/network acknowledgement.

## Evidence rule

Evidence labels stay strict:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — the relevant behavior was physically reproduced on the target Samsung S22+;
- ServicePack `VERIFIED` node/edge — the physical node/transition was observed;
- `service_route_verified=true` — the intended service route itself was physically proven;
- `PRODUCT_READY` — the bounded product task is proven, fail-safe and acceptable for normal use.

Host-only matcher/TaskGraph/supervisor/vault-contract changes require physical S22 evidence only when a later slice actually changes or exercises Android/OEM/live-dialogue behavior.
