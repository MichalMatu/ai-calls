# Roadmap

This is the authoritative execution plan. Detailed experiment history belongs in Git history and `.agent/results`. Session transfer rules live in `docs/HANDOFF_PROTOCOL.md`.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- ServicePack `VERIFIED` node/edge — that external node/transition was physically observed;
- `service_route_verified=true` — the intended external service route was physically proven;
- `PRODUCT_READY` — the bounded product task is proven, fail-safe and acceptable for normal use.

## Foundation

### Cellular media

Status: `DONE / PROVEN_S22 / FROZEN`

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Frozen checkpoint: `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`.

Do not redesign this path during Gate D. See `docs/PHASE2D_FREEZE_2026-09-18.md`.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

Established owners:

- `LocalSpeechTextPipeline` — STT/TTS lifecycle + outer generation cancellation;
- `TextCallTurnController` — complete text generation/candidate approval + controller generation invalidation;
- `TextOutputApprovalPolicy` — application-owned release decision.

### Endpointing

Status: `SIGNALS PROVEN_S22 / PRODUCT STATE MACHINE OPEN`

Real Orange evidence proved that fixed capture durations and short trailing-silence thresholds are not complete IVR semantics. Resumed speech must invalidate an end candidate; a later stable end plus bounded hangover closes a turn; long watchdog is safety-only.

Endpointing hardening remains required for product-quality human conversations, but it does not block host/simulation work on the task engine.

## Completed gates / frozen experiments

### Gate A — product readiness / prepared local text call

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

### Gate B — general-purpose phone-local model sweep

Status: `DONE / PROVEN_S22 / FROZEN`

The tested general-purpose llama.cpp models on the current S22 are not the product direction. Preserve runtime/benchmark infrastructure for bounded supervisor experiments only.

### Interactive ChatGPT relay

Status: `DONE / PROVEN_S22 / DEVELOPER-ONLY`

Benchmark infrastructure only; not a production/background backend.

### Edge Gallery / Gemma experiments

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

Do not continue live Edge probing by default.

## Gate C — deterministic fast path

Status: `DONE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22 / CHECKPOINTED`

Gate C proved that bounded turns can execute deterministically while all authority remains application-owned.

Authority remains owned by:

- `CallTask`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

Physically proven path:

```text
cellular RX
 -> local STT
 -> PhraseMatrix
 -> existing CallPlan rule validation
 -> application-owned approval
 -> local TTS
 -> cellular TX
 -> bounded next-turn handling
 -> cleanup to IDLE
```

Required safety markers included exact reviewed approved text, nonzero telephony TX and `backend_generate_calls=0` on the deterministic route.

Do not repeat work whose only purpose is proving this transport/fast path again.

## Orange ServicePack — persistent checkpointed side track

Status: `CHECKPOINTED / LIVE EVIDENCE PROVEN_S22 / PERSISTENT PRODUCT KNOWLEDGE / NOT THE MAIN ROADMAP`.

Durable graph: `service-packs/orange/service_tree.v1.json`.

Orange is the project's first persistent evidence-backed IVR ServicePack. It is **not a discarded test**. Preserve its mapping and evidence for:

- future real Orange tasks;
- deterministic IVR navigation;
- regression of machine-counterparty handling;
- ServicePack schema/runtime development;
- route freshness/staleness work;
- future operator/service catalogs.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- manual network selection reached the activation clarification barrier and is closed;
- other closed reviewed diagnostic/informational wordings ended in known root reprompts;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- deterministic live calls used `backend_generate_calls=0`, `OBSERVE_ONLY` follow-up and cleanup to `IDLE`.

Orange mapping must not block Gate D. Resume it only when:

1. a specific Orange task becomes a real product target;
2. a new generic ServicePack feature needs physical validation;
3. a new TaskGraph/ServicePack integration capability needs a known IVR counterparty;
4. route freshness/regression needs intentional evidence.

When resumed, follow `docs/ORANGE_MAPPING_RUNBOOK.md`. Never discard or overwrite historical physical evidence merely to fit a new schema; migrate it losslessly.

### Generic service-intent resolver

Status: `HOST_GREEN / PRESERVED`.

The `serviceintent/` layer implements a provider-neutral bounded classifier + authoritative registry + separate execution validator. A model may classify only into existing bounded IDs and cannot gain speech/action/target/commitment authority.

This is the design precedent for the Gate D supervisor.

## ACTIVE Gate D — hybrid multi-turn Task Engine

Status: `ACTIVE / NEXT MAIN PRODUCT MILESTONE`.

Goal: move from a proven phone-call transport/demo to a real autonomous bounded task executor.

Primary acceptance use case:

```text
BOOK_APPOINTMENT
```

Example user goal:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

The product must transform that into an application-owned task and conduct a real multi-turn call to a structured result without allowing an LLM or Skill to own execution authority.

### Product model: TaskGraph + ServicePack + IdentityVault

Keep the durable knowledge/data layers distinct:

```text
TaskGraph
  = what the user wants, constraints, slots, state, proposal,
    confirmation, commitment, completion

ServicePack
  = how a specific counterparty/service behaves: prompts, nodes,
    routes, reviewed actions, barriers, evidence

IdentityVault
  = durable encrypted personal/contact facts; never task authority by itself
```

A simple appointment to an ordinary reception may need TaskGraph + dialogue logic + a small authorized-facts snapshot from IdentityVault.

A future Orange task may combine:

```text
CallTask
 + TaskGraph
 + Orange ServicePack
 + authorized fact references
 + deterministic matcher/parsers
 + bounded supervisor on ambiguity
 + existing authority owners
```

Knowing a ServicePack route or possessing an identity value never authorizes disclosure or commitment by itself.

### Gate D target architecture

```text
User goal
  -> Skill / bounded intent resolver
  -> IdentityVault availability + per-task authorization
  -> CallTask + AuthorizedFactSnapshot + constraints + preferences
  -> TaskGraph
  -> CallWorkflow
  -> final STT
       -> deterministic PhraseMatrix / typed parsers
       -> shadow supervisor observes finalized turn when enabled
       -> DialogueFit / escalation policy
       -> bounded LLM proposal only when needed
  -> suggested existing transition / typed slots only
  -> deterministic validation
  -> FactDisclosurePolicy when identity data is requested
  -> CallPlan / typed decision
  -> output approval
  -> TTS / telephony
  -> proposal
  -> user confirmation when required
  -> CallCommitmentGate
  -> completion
```

Hybrid principle:

- deterministic path handles known/common turns;
- LLM may track finalized dialogue from the beginning in non-authoritative shadow mode;
- a separate explainable `DialogueFit` policy decides when deterministic understanding is no longer sufficient;
- LLM output is structured proposal/classification only;
- proposed transitions/slots/facts/IDs are revalidated;
- plaintext identity values stay outside model context by default;
- LLM never directly owns dialing, arbitrary speech release, credentials, fact disclosure, commitment or completion;
- Skills prepare tasks/request bounded fact permissions/suggest bounded actions but do not bypass authority.

### Implementation direction after external review

Reuse proven design patterns without importing their runtimes blindly:

- **Pipecat Flows**: graph/config owns legal transitions; handlers return structured results instead of arbitrary next-node authority; keep stage context/actions narrow.
- **XState/statecharts**: pure guards, explicit states/events/context, versionable serializable state and event replay.
- **LiveKit Tasks/TaskGroups**: small focused tasks return typed results and compose under a session owner; useful model for contact-data verification and appointment subtasks.
- **Rasa/form slot filling**: extraction creates a candidate value; explicit validation decides whether it may enter authoritative state; support required/dynamic slots and unhappy paths.
- **KStateMachine**: credible Kotlin Multiplatform statechart candidate with guarded transitions, nested states, coroutines and a small core dependency surface. Evaluate it, do not adopt it by default.

Keep the product implementation Kotlin-native unless a controlled host spike proves a dependency is worth its runtime/maintenance cost.

### TaskGraph engine decision spike

Before committing the production TaskGraph core to either a custom reducer or KStateMachine, run one host-only comparison against the **same contract tests**.

The spike must cover at least:

- sealed/typed state and event IDs;
- pure deterministic guards;
- state-compatible transition validation;
- bounded recovery counters;
- proposal/confirmation/commitment states;
- nested/composed state feasibility without hiding authority;
- versioned state/event serialization strategy;
- replay from event log;
- side effects outside transition evaluation;
- deterministic testability without Android dependencies.

Decision rule:

- choose KStateMachine only if it materially reduces state-machine complexity without taking ownership of authority, evidence logging, side effects or persistence semantics;
- otherwise keep a minimal custom TaskGraph reducer;
- never let the library's runtime state become the only source of truth: product events/evidence must remain explicit and replayable;
- do not add both abstractions long-term.

This spike is architectural due diligence, not permission for a broad framework migration.

### TaskGraph v1

Build a generic data-driven state machine independent of Orange.

Required concepts:

- typed node/state IDs;
- typed transitions/events;
- required/optional slots;
- user constraints/preferences;
- authorized fact references;
- deterministic pure guards;
- bounded retry/recovery counters;
- unknown/escalation state;
- proposal state;
- explicit confirmation state;
- commitment state;
- completion/failure/takeover terminal states;
- versioned replayable event/evidence log;
- side effects separated from transition evaluation.

The graph is application-owned product data. A model may suggest an existing transition or structured slot values but cannot invent executable graph structure at runtime.

### IdentityVault + AuthorizedFacts v1

Define the contract before the first real appointment call.

Persistent fields may include typed IDs such as:

```text
FIRST_NAME
LAST_NAME
PHONE
EMAIL
ADDRESS
DATE_OF_BIRTH
PESEL
```

Do not make all of them required for `BOOK_APPOINTMENT`. Requirements are task/counterparty dependent.

Required Gate D semantics:

- vault value existence != disclosure authorization;
- per-task authorization snapshot/reference list;
- sensitivity metadata;
- `FactDisclosurePolicy` with typed `ALLOW / ASK_USER / DENY`;
- no plaintext secrets in TaskGraph/ServicePack/general event log;
- LLM sees fact availability/name rather than plaintext by default;
- high-sensitivity identifiers such as PESEL default to explicit per-task approval and may require device/user authentication at disclosure time;
- unknown counterparty request for a non-authorized field triggers user decision/takeover/defer, never invention.

Android persistence target before real calls:

```text
app-private ciphertext storage
+ non-exportable Android Keystore key
+ authenticated encryption (AES/GCM class of primitive)
+ versioned records
+ explicit backup/restore policy
```

Do not implement new vault storage with deprecated `EncryptedSharedPreferences` / `MasterKey` APIs. Evaluate Tink only if key rotation/envelope/keyset management becomes valuable enough to justify the dependency.

### First graph — BOOK_APPOINTMENT

Implement on host/simulation before live calls.

Minimum flow:

```text
START
 -> REQUEST_APPOINTMENT
 -> identify/confirm requested service
 -> collect/express date constraints
 -> collect/express time constraints
 -> receive offered slot
 -> parse structured candidate date/time
 -> validate against constraints
    -> reject/request alternative when invalid
    -> create typed proposal when acceptable
 -> request/disclose only authorized identity facts as needed
 -> confirmation policy
 -> commitment gate
 -> COMMIT_APPOINTMENT
 -> COMPLETE
```

Required recovery paths:

- unexpected but harmless question;
- ambiguous date/time;
- unavailable requested time;
- alternative offer;
- STT uncertainty;
- request for information not authorized by `CallTask`;
- high-sensitivity fact needs user approval;
- human takeover/cancel;
- no suitable slot;
- explicit refusal by counterparty.

### Deterministic extraction first

Build typed parsers/normalizers before relying on the supervisor for:

- dates and relative dates;
- weekdays;
- clock times/time ranges;
- offered appointment candidates;
- accept/reject/alternative semantics;
- common service/type names;
- yes/no/confirmation with negation guards;
- requests for common identity fields (name, phone, email, PESEL etc.) without exposing their values.

PhraseMatrix remains the first path for known dialogue acts.

For any parsed slot/fact-like input, preserve a two-phase rule:

```text
extract candidate
 -> validate type/state/constraints/provenance
 -> only then commit to authoritative TaskState
```

An NLU/parser/LLM extraction result is never authoritative merely because it was produced confidently.

### Shadow supervisor + DialogueFit v1

After the deterministic TaskGraph/simulator is useful, enable a bounded shadow observer.

Shadow mode:

- may observe every finalized turn from the beginning;
- receives bounded task/state/allowed-transition context;
- receives validated non-secret slots and fact availability, not full vault values;
- maintains only quarantined interpretation/summary;
- cannot mutate TaskGraph/workflow or release speech.

Define `DialogueFit` as an application-owned explainable result, not raw text similarity and not one LLM score.

Initial signal set:

- STT quality/confidence when available;
- PhraseMatrix result;
- parser completeness/confidence;
- expected-transition/state compatibility;
- contradiction/negation signals;
- missing required slots;
- repeated unknown/recovery count;
- deterministic-vs-shadow disagreement.

Start with categorical decisions:

```text
HIGH       -> deterministic path
UNCERTAIN  -> clarification or optional supervisor check
LOW        -> supervisor proposal required
BROKEN     -> recovery / TAKE_OVER / safe stop
```

Calibrate any numeric internals and thresholds from simulator/eval evidence. Add hysteresis/debouncing before live use.

### Bounded LLM supervisor v1

When escalation policy asks for help, allowed output resembles:

```json
{
  "suggested_transition": "OFFER_RECEIVED",
  "slots": {
    "date": "2026-09-24",
    "time": "17:30"
  },
  "confidence": 0.91
}
```

It must not return or own runtime TTS text, target number, authorization changes, identity values or commitment.

Validate:

- generation/session identity;
- existing transition ID;
- slot schema/types;
- TaskGraph state compatibility;
- user constraints;
- authority-bearing metadata rejection;
- confidence threshold;
- stale result rejection;
- identity/value leakage rejection.

### Skills integration

Skills are reintroduced after TaskGraph v1 exists.

Preferred role:

```text
User request
 -> Skill builds/updates bounded CallTask
 -> selects existing TaskGraph/ServicePack
 -> gathers missing pre-call facts/preferences
 -> requests authorization for specific IdentityFieldIds
 -> may suggest existing transition/slot
 -> application authority validates everything
```

Skills must not directly dial arbitrary targets, widen allowlists, emit arbitrary telephony speech, read/export the whole IdentityVault, invent credentials, confirm bookings/purchases or bypass `FactDisclosurePolicy` / `CallCommitmentGate`.

### Product orchestration

Do not promote `DiagnosticProbeActivity`, `LocalPhoneLlmLiveCallProbe`, Orange runners or other diagnostics into the product orchestrator.

Gate D must introduce or narrowly extend a real product session owner that composes readiness, `CallWorkflow`, TaskGraph, optional ServicePack, authorized-fact snapshot, DialogueFit, CallPlan/PhraseMatrix, supervisor and speech/media layers.

## Real-world appointment testing policy

After `BOOK_APPOINTMENT` is host-green against a simulated receptionist and IdentityVault/disclosure handling required by the scenario is implemented, real tests may use a small reviewed set of ordinary public business/reception numbers found from current public web sources.

Do not use emergency, urgent-care, crisis, premium-rate or other critical-service lines for development testing.

### Test-only call

Disclose the AI/test purpose at the **start**, before asking staff to spend time checking schedules or creating a reservation.

Preferred opening intent:

```text
Dzień dobry, testuję automatycznego asystenta głosowego. Czy możemy przeprowadzić krótką rozmowę testową bez dokonywania rezerwacji?
```

Continue only if the person agrees. If they decline, thank them and hang up.

A test-only call must never create or hold a real appointment, ticket, order or other commitment. Do not wait until the end to reveal that it was only a test.

### Genuine user-authorized task

If the user genuinely wants an appointment, the call may execute the real task using only authorized facts and the normal disclosure/proposal/confirmation/commitment path.

A genuine booking must not be retracted merely because it also produced product evidence.

### Per-target call budget

Default:

- one meaningful call per organization/reception during a development slice;
- second call only after an early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff/organization to tune wording;
- avoid known peak/busy periods when practical;
- record public target source, purpose/mode, call count and structured outcome.

Do not automate broad unsolicited calling campaigns.

### Real-call evidence

Record at minimum:

- public source for the number;
- target organization/category;
- test mode (`TEST_ONLY_CONSENTED` or `GENUINE_TASK`);
- exact task/constraints;
- authorized identity-field names (not plaintext values);
- call count for that target;
- disclosure/consent status where applicable;
- structured dialogue events rather than unnecessary full transcripts;
- proposals/commitments and their authority evidence;
- cleanup/final call state.

Do not retain unnecessary personal or medical data.

## Gate D execution order

1. Keep Orange frozen at the current persistent ServicePack checkpoint; no broad mapping by default.
2. Preimplementation audit of existing `CallTask` / `CallWorkflow` / CallPlan / prepared-session ownership for TaskGraph composition.
3. Specify TaskGraph contract tests first: typed states/events, pure guards, state compatibility, recovery, proposal/confirmation/commitment and versioned replay.
4. Run the host-only **custom reducer vs KStateMachine** decision spike against those same contract tests; choose one core and remove/avoid the loser.
5. Specify `IdentityVault`, `IdentityFieldId`, per-task `AuthorizedFactSnapshot` and `FactDisclosurePolicy` contracts with host tests; storage implementation can follow later.
6. Implement the chosen minimal TaskGraph core.
7. Implement `BOOK_APPOINTMENT` graph entirely on host.
8. Build a deterministic simulated receptionist harness with multiple scripted scenarios and typed results.
9. Add typed date/time/offer/identity-request parsers and PhraseMatrix dialogue-act coverage using `extract -> validate -> commit` semantics.
10. Exercise proposal -> confirmation -> commitment -> completion and disclosure decisions entirely in simulation.
11. Add failure/recovery/takeover/unauthorized-fact scenarios and make them deterministic/replayable.
12. Add shadow supervisor context tracking with **no execution authority**.
13. Implement and calibrate explainable `DialogueFit` using simulator/eval scenarios; test hysteresis and deterministic-supervisor disagreement.
14. Add bounded LLM supervisor proposal interface returning only existing transition IDs + typed non-secret slots.
15. Test malicious/invalid/stale/authority-bearing/identity-leaking supervisor output fail-closed.
16. Implement Android IdentityVault persistence with Android Keystore-protected authenticated encryption and explicit backup/restore behavior before a real call requires personal data.
17. Integrate TaskGraph/DialogueFit/supervisor/vault into a real product session owner without growing diagnostics into orchestrators.
18. Physically verify one non-committing real-world test-only call with disclosure/consent, or one genuine user-authorized appointment call.
19. Expand to a small number of distinct public reception targets, respecting the per-target call budget.
20. Add Skills as task builders/supervisors over the same authority boundary.
21. Standardize reusable TaskGraph + ServicePack formats and add additional domains.

## Gate D acceptance target

Gate D is complete when the system can take a natural goal such as:

```text
Umów mnie do dentysty w przyszłym tygodniu po 16.
```

and, on S22, complete a bounded real multi-turn call where:

- target is explicitly authorized;
- task/constraints/preferences are structural;
- required identity fields are available only through per-task disclosure authority;
- common turns run deterministically;
- LLM can observe from the beginning in shadow mode but cannot mutate authoritative state;
- DialogueFit/escalation is explainable and simulator-calibrated;
- supervisor is used for ambiguous/unknown turns and cannot gain authority;
- offered appointment data is parsed to typed fields;
- unsuitable offers are rejected by policy;
- an acceptable offer becomes a typed proposal;
- unauthorized/high-sensitivity identity requests fail closed or ask the user;
- required user confirmation occurs before commitment;
- exactly one authorized commitment is released;
- structured/redacted result is returned to the user;
- cancellation/takeover remains local-first and safe;
- call cleanup returns to `IDLE`.

## Later — reusable TaskGraph + ServicePack ecosystem

After Gate D proves the engine, standardize reusable data packs without losing existing evidence:

```text
TaskGraph template
+ reviewed speech catalog
+ PhraseMatrix/dialogue-act aliases
+ typed slot schema
+ recovery rules
+ commitment/barrier metadata
+ optional service-specific IVR/discovery graph
+ evidence/freshness metadata
```

Orange should be migrated incrementally into the standardized ServicePack format while preserving its historical observed nodes/edges/evidence. Future packs may cover other operators, insurers, banks, utilities or appointment providers where doing so supports real user tasks.

## Later — local audio-model experiments

Status: `LATER`.

Only after the hybrid text/task baseline is strong, evaluate mobile-feasible audio understanding / speech-to-speech candidates behind the same authority and frozen media boundaries.

## Preserved providers

- `LOCAL_PHONE_LLM` — experimental infrastructure / selectable supervisor candidate;
- `EDGE_GALLERY` — frozen experiment;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred supervisor option;
- `OPENAI_REALTIME_AUDIO` — preserved/frozen;
- `LOCAL_REALTIME_AUDIO` — future.

Provider selection never changes authority semantics. Do not put standard OpenAI API credentials on Android.

## Completion discipline

Every product behavior slice:

1. starts from fresh `origin/main`;
2. uses TDD where deterministic behavior is testable;
3. runs targeted regressions;
4. runs `bash scripts/verify_host.sh` before claiming `HOST_GREEN`;
5. runs only the physical gate required by changed behavior;
6. keeps test targets and live-call authorization explicit and bounded;
7. records real-world call evidence conservatively and redacts identity values;
8. updates authoritative docs rather than proliferating status files;
9. leaves `main` clean.

When moving work to a new chat, follow `docs/HANDOFF_PROTOCOL.md`, refresh `docs/HANDOFF_NEXT_CHAT.md`, and create/update the ready-to-paste `docs/NEXT_CHAT_PROMPT.md`.

Orange-specific work additionally follows `docs/ORANGE_MAPPING_RUNBOOK.md`.
