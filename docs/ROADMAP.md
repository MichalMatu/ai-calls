# Roadmap

This is the authoritative execution plan. Detailed experiment history belongs in Git history and `.agent/results`. Orange mapping is now a checkpointed evidence pack and side track, not the main product goal.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Foundation

### Cellular media

Status: `DONE / PROVEN_S22 / FROZEN`

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Frozen checkpoint: `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`.

Do not redesign this path during model/planning/dialogue/service-pack work. See `docs/PHASE2D_FREEZE_2026-09-18.md`.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

Established owners:

- `LocalSpeechTextPipeline` — STT/TTS lifecycle + outer generation cancellation;
- `TextCallTurnController` — complete text generation/candidate approval + controller generation invalidation;
- `TextOutputApprovalPolicy` — application-owned release decision.

### Endpointing

Status: `SIGNALS PROVEN_S22 / PRODUCT STATE MACHINE OPEN`

Real Orange evidence proved that fixed capture durations and short trailing-silence thresholds are not complete IVR semantics. Resumed speech must invalidate an end candidate; a later stable end plus bounded hangover closes a turn; long watchdog is safety-only.

Endpointing hardening remains required for product-quality multi-turn calls, but it no longer blocks building the next product layer on host/simulation first.

## Completed gates / frozen experiments

### Gate A — product readiness / prepared local text call

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

### Gate B — general-purpose phone-local model sweep

Status: `DONE / PROVEN_S22 / FROZEN`

The tested general-purpose llama.cpp models on the current S22 are not the product direction. Preserve runtime/benchmark infrastructure only.

### Interactive ChatGPT relay

Status: `DONE / PROVEN_S22 / DEVELOPER-ONLY`

Benchmark infrastructure only; not a production/background backend.

### Edge Gallery + Agent Skills feasibility

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

Do not continue live Edge probe work by default. Skills become relevant again only as bounded task builders/supervisors behind application-owned authority.

## Gate C — deterministic fast path

Status: `DONE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22 / CHECKPOINTED`

Gate C proved that common bounded turns can execute deterministically while all authority remains application-owned.

### Authority ownership

- `CallTask` — immutable task, constraints, preferences, authorized facts;
- `CallResolvedTarget` — concrete target, no allowlist expansion;
- `CallWorkflow` — progress, proposal/user-decision state, terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — one exact one-shot commitment permit;
- application-owned output approval — final release before TTS/TX.

`CallPlan`, PhraseMatrix, models, helpers, service-pack explorers and Agent Skills do not replace these owners.

### Deterministic CallPlan + PhraseMatrix path

Status: `HOST_GREEN / LIVE PATH PROVEN_S22`

Completed product wiring includes:

- deterministic CallPlan policy core and coordinator;
- prepared-call CallPlan binding;
- final-STT selector before backend generation;
- neutral `Generate` / `Candidate` / `Consumed` dispatch;
- exact candidate text through existing output approval;
- structured actions remain structured;
- native `PhraseMatrix` exact/alias matching with fail-closed collision handling;
- explicit optional previous-rule context owned by the session only after validated decisions;
- bounded opt-in one-edit fuzzy matching with token-count/length/ambiguity guards;
- Android readiness binding for optional `CallPlan + PhraseMatrix`;
- no-plan/default path remains ordinary `Generate`.

Matcher direction remains native Kotlin `PhraseMatrix`. Do not broaden fuzzy matching merely to make a specific live prompt pass.

### Controlled live proof

Exact allowlisted target used for physical proof:

```text
510100100
```

Orange physically proved:

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

Required safety markers included `gate_c_fast_path=true`, `gate_c_call_plan_bound=true`, `backend_generate_calls=0`, exact reviewed approved text and nonzero telephony TX.

Do not repeat Orange work whose only purpose is proving this path again.

## Orange Service Pack — checkpointed side track

Status: `CHECKPOINTED / LIVE EVIDENCE PROVEN_S22 / NOT THE MAIN ROADMAP`.

Durable graph: `service-packs/orange/service_tree.v1.json`.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- manual network selection reached the activation clarification barrier and is closed;
- all other currently closed reviewed diagnostic/informational wordings ended in a known root reprompt;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- latest physical call used `backend_generate_calls=0`, then `OBSERVE_ONLY`, then cleanup to `IDLE`.

Orange remains useful for:

- regression of deterministic IVR handling;
- evidence-backed service-pack format experiments;
- future data-driven route catalogs;
- testing a known machine counterparty.

Orange must not block the product task engine. Continue mapping only when it directly exercises a new generic product capability or when a specific Orange route becomes a real target use case.

### Generic service-intent resolver

Status: `HOST_GREEN / PRESERVED`.

The `serviceintent/` layer implements a provider-neutral classifier boundary, bounded `ServiceRegistry`, fail-closed resolver and separate execution validator. A model may classify only into existing bounded IDs. It cannot gain speech, action, target or commitment authority.

This pattern becomes the template for the hybrid supervisor in Gate D.

## ACTIVE Gate D — hybrid multi-turn Task Engine

Status: `ACTIVE / NEXT MAIN PRODUCT MILESTONE`.

Goal: move from a proven phone-call transport/demo to a real autonomous task executor that can complete bounded multi-turn tasks such as booking an appointment.

Primary acceptance use case:

```text
BOOK_APPOINTMENT
```

Example user goal:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

The product must transform that into an application-owned task and drive a real conversation to a structured result without allowing an LLM or Skill to own authority.

### Gate D target architecture

```text
User goal
  -> Skill / bounded intent resolver
  -> CallTask + authorizedFacts + constraints + preferences
  -> TaskGraph
  -> CallWorkflow
  -> final STT
       -> deterministic PhraseMatrix / parsers first
       -> bounded LLM supervisor only on ambiguity/unknown
  -> suggested existing transition / typed slots only
  -> deterministic validation
  -> CallPlan / typed decision
  -> output approval
  -> TTS / telephony
  -> proposal
  -> user confirmation when required
  -> CallCommitmentGate
  -> completion
```

The hybrid principle is explicit:

- deterministic path handles known/common turns;
- local or selectable LLM supervisor handles ambiguity and natural-language variation;
- LLM output is structured proposal/classification only;
- all proposed transitions, slots, facts and service/task IDs are revalidated;
- LLM never directly owns dialing, free-form speech release, credentials, commitment or completion;
- Skills prepare tasks and suggest bounded actions but do not bypass CallWorkflow/CallPlan/approval/commitment authority.

### TaskGraph v1

Build a generic data-driven state machine independent of Orange.

Required concepts:

- typed node/state IDs;
- typed transitions;
- required/optional slots;
- user constraints/preferences;
- authorized facts;
- deterministic guards;
- bounded retry/recovery counters;
- unknown/escalation state;
- proposal state;
- explicit confirmation state;
- commitment state;
- completion/failure/takeover terminal states;
- replayable event/evidence log.

The graph is application-owned product data. A model may suggest an existing transition or structured slot values but cannot invent executable graph structure at runtime.

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
    -> reject and request alternative when invalid
    -> create typed proposal when acceptable
 -> confirmation policy
 -> commitment gate
 -> COMMIT_APPOINTMENT
 -> COMPLETE
```

Required recovery paths:

- counterparty asks an unexpected but harmless question;
- ambiguous date/time;
- unavailable requested time;
- alternative offer;
- STT uncertainty;
- counterparty requests information not authorized by `CallTask`;
- human takeover/cancel;
- no suitable slot;
- explicit refusal by counterparty.

### Deterministic extraction first

Build small typed parsers/normalizers for high-value appointment data before relying on the supervisor:

- dates and relative dates;
- weekdays;
- clock times/time ranges;
- offered appointment candidate;
- accept/reject/alternative semantics;
- common service/type names;
- yes/no/confirmation with negation guards.

PhraseMatrix remains the first path for known dialogue acts.

### Bounded LLM supervisor v1

After the host TaskGraph path works deterministically, add a supervisor behind a narrow interface.

The supervisor may return only bounded structured data such as:

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

It must not return or own runtime TTS text, target number, authorization changes or commitment.

Validate:

- generation/session identity;
- existing transition ID;
- slot schema/types;
- task graph state compatibility;
- user constraints;
- authority-bearing metadata rejection;
- confidence threshold;
- stale result rejection.

The existing `serviceintent/` resolver is the design precedent for this boundary.

### Skills integration

Skills are reintroduced after TaskGraph v1 exists.

Preferred role:

```text
User request
 -> Skill builds/updates bounded CallTask
 -> selects existing TaskGraph/service pack
 -> gathers missing pre-call facts/preferences
 -> may suggest existing transition/slot during execution
 -> application authority validates everything
```

Skills must not directly:

- dial arbitrary targets;
- widen target allowlists;
- emit arbitrary telephony speech;
- invent credentials/sensitive facts;
- confirm bookings/purchases on their own;
- bypass `CallCommitmentGate`.

### Product orchestration

Do not promote `DiagnosticProbeActivity`, `LocalPhoneLlmLiveCallProbe`, Orange runners or other diagnostics into the product orchestrator.

Gate D must introduce/narrowly extend a real product session owner that composes existing readiness, `CallWorkflow`, TaskGraph, CallPlan/PhraseMatrix, supervisor and speech/media layers.

## Real-world appointment testing policy

After `BOOK_APPOINTMENT` is host-green with a simulated receptionist, real tests may move beyond Orange to ordinary public business/reception numbers found from current public web sources.

Use only non-emergency, ordinary public contact numbers appropriate for appointment enquiries. Do not use emergency/urgent-care/crisis lines or otherwise burden critical services for testing.

### Two test modes

#### A. Test-only call

A test-only call must disclose the test at the **start**, before asking staff to spend time checking schedules or creating any reservation.

Preferred opening intent:

```text
Dzień dobry, testuję automatycznego asystenta głosowego. Czy możemy przeprowadzić krótką rozmowę testową bez dokonywania rezerwacji?
```

Continue only if the person agrees. If they decline, thank them and hang up.

Do not wait until the end to say “to był tylko test, proszę zignorować”. By then a person may already have spent time, entered data or held a real slot.

A test-only call must never create or hold a real appointment, ticket, order or other commitment.

#### B. Genuine user-authorized task

If the user genuinely wants an appointment, the call may execute the real task. The system should identify itself as an automated/AI assistant early in the conversation where practical, use only user-authorized facts, and reach a real booking only through the normal proposal/confirmation/commitment path.

A genuine booking must not be retracted at the end merely because it was also a product test.

### Per-target call budget

Default policy:

- one meaningful call per organization/reception during a development slice;
- a second call only when the first failed technically before meaningful interaction, or the counterparty explicitly agreed to another test;
- do not repeatedly probe the same staff/organization to tune wording;
- avoid known peak/busy periods when practical;
- record target source, purpose, call count and outcome to enforce the budget.

Do not automate broad unsolicited calling campaigns. Real-world validation should use a small, reviewed target set and bounded test plan.

### Real-call evidence

For every live business/reception test record at minimum:

- public source for the number;
- target organization/category;
- test mode (`TEST_ONLY_CONSENTED` or `GENUINE_TASK`);
- exact task/constraints;
- call count for that target;
- whether AI/test disclosure occurred;
- whether consent was given for test-only mode;
- structured dialogue events rather than unnecessary full transcripts;
- proposals/commitments and their authority evidence;
- cleanup/final call state.

Do not retain unnecessary personal or medical data from the counterparty.

## Gate D execution order

1. Freeze Orange as the current evidence pack; no more broad Orange mapping by default.
2. Specify `TaskGraph v1` models, invariants and event log with RED tests.
3. Implement `BOOK_APPOINTMENT` graph entirely on host.
4. Build a deterministic simulated receptionist harness with multiple scripted scenarios.
5. Add typed date/time/offer parsers and PhraseMatrix dialogue-act coverage.
6. Exercise proposal -> confirmation -> commitment -> completion entirely in simulation.
7. Add failure/recovery/takeover scenarios and make them deterministic/replayable.
8. Add bounded LLM supervisor interface returning only existing transition IDs + typed slots.
9. Test malicious/invalid/stale supervisor output fail-closed.
10. Integrate the TaskGraph into a real product session owner without growing diagnostics into orchestrators.
11. Physically verify one non-committing real-world test-only call with disclosure/consent, or one genuine user-authorized appointment call.
12. Expand to a small number of distinct public reception targets, respecting the per-target call budget.
13. Only after this works, add Skills as task builders/supervisors over the same authority boundary.
14. Generalize the appointment graph into reusable task templates and additional domains.

## Gate D acceptance target

Gate D is complete when the system can take a natural user goal such as:

```text
Umów mnie do dentysty w przyszłym tygodniu po 16.
```

and, on the S22, complete a bounded real multi-turn call where:

- the target is explicitly authorized;
- task/constraints/preferences are represented structurally;
- common turns run deterministically;
- the LLM supervisor is used only where needed and cannot gain authority;
- offered appointment data is parsed to typed fields;
- unsuitable offers are rejected by policy;
- an acceptable offer becomes a typed proposal;
- required user confirmation occurs before commitment;
- exactly one authorized commitment is released;
- the structured result is returned to the user;
- cancellation/takeover remains local-first and safe;
- call cleanup returns to `IDLE`.

## Later — reusable service/task packs

After Gate D proves the generic engine, standardize reusable data packs:

```text
TaskGraph template
+ reviewed speech catalog
+ PhraseMatrix/dialogue-act aliases
+ typed slot schema
+ recovery rules
+ commitment/barrier metadata
+ optional service-specific discovery graph
```

At that point large mappings can become valuable because they populate reusable packs rather than being the product itself.

Orange may then be converted to this standardized format incrementally.

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

1. starts from fresh `main`;
2. uses TDD where deterministic behavior is testable;
3. runs targeted regressions;
4. runs `bash scripts/verify_host.sh` before claiming `HOST_GREEN`;
5. runs only the physical gate required by the changed behavior;
6. keeps test targets and live-call authorization explicit and bounded;
7. records real-world call evidence conservatively;
8. updates authoritative docs rather than proliferating status files;
9. leaves `main` clean.

Orange-specific work additionally follows `docs/ORANGE_MAPPING_RUNBOOK.md`, but Orange mapping is no longer the default next task.
