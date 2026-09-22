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

### Product model: TaskGraph + ServicePack

Keep the two durable knowledge layers distinct:

```text
TaskGraph
  = what the user wants, constraints, slots, state, proposal,
    confirmation, commitment, completion

ServicePack
  = how a specific counterparty/service behaves: prompts, nodes,
    routes, reviewed actions, barriers, evidence
```

A simple appointment to an ordinary reception may need only TaskGraph + dialogue logic.

A future Orange task may combine:

```text
CallTask
 + TaskGraph
 + Orange ServicePack
 + deterministic matcher/parsers
 + bounded supervisor on ambiguity
 + existing authority owners
```

Knowing a ServicePack route never authorizes the task commitment by itself.

### Gate D target architecture

```text
User goal
  -> Skill / bounded intent resolver
  -> CallTask + authorizedFacts + constraints + preferences
  -> TaskGraph
  -> CallWorkflow
  -> final STT
       -> deterministic PhraseMatrix / typed parsers first
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

Hybrid principle:

- deterministic path handles known/common turns;
- local or selectable LLM supervisor handles ambiguity and natural-language variation;
- LLM output is structured proposal/classification only;
- proposed transitions/slots/facts/IDs are revalidated;
- LLM never directly owns dialing, arbitrary speech release, credentials, commitment or completion;
- Skills prepare tasks/suggest bounded actions but do not bypass authority.

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
    -> reject/request alternative when invalid
    -> create typed proposal when acceptable
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
- yes/no/confirmation with negation guards.

PhraseMatrix remains the first path for known dialogue acts.

### Bounded LLM supervisor v1

Add after the host TaskGraph path works deterministically.

Allowed output resembles:

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
- TaskGraph state compatibility;
- user constraints;
- authority-bearing metadata rejection;
- confidence threshold;
- stale result rejection.

### Skills integration

Skills are reintroduced after TaskGraph v1 exists.

Preferred role:

```text
User request
 -> Skill builds/updates bounded CallTask
 -> selects existing TaskGraph/ServicePack
 -> gathers missing pre-call facts/preferences
 -> may suggest existing transition/slot
 -> application authority validates everything
```

Skills must not directly dial arbitrary targets, widen allowlists, emit arbitrary telephony speech, invent credentials, confirm bookings/purchases or bypass `CallCommitmentGate`.

### Product orchestration

Do not promote `DiagnosticProbeActivity`, `LocalPhoneLlmLiveCallProbe`, Orange runners or other diagnostics into the product orchestrator.

Gate D must introduce or narrowly extend a real product session owner that composes readiness, `CallWorkflow`, TaskGraph, optional ServicePack, CallPlan/PhraseMatrix, supervisor and speech/media layers.

## Real-world appointment testing policy

After `BOOK_APPOINTMENT` is host-green against a simulated receptionist, real tests may use a small reviewed set of ordinary public business/reception numbers found from current public web sources.

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

If the user genuinely wants an appointment, the call may execute the real task using only authorized facts and the normal proposal/confirmation/commitment path.

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
- call count for that target;
- disclosure/consent status where applicable;
- structured dialogue events rather than unnecessary full transcripts;
- proposals/commitments and their authority evidence;
- cleanup/final call state.

Do not retain unnecessary personal or medical data.

## Gate D execution order

1. Keep Orange frozen at the current persistent ServicePack checkpoint; no broad mapping by default.
2. Specify `TaskGraph v1` models, invariants and event log with RED tests.
3. Implement `BOOK_APPOINTMENT` graph entirely on host.
4. Build a deterministic simulated receptionist harness with multiple scripted scenarios.
5. Add typed date/time/offer parsers and PhraseMatrix dialogue-act coverage.
6. Exercise proposal -> confirmation -> commitment -> completion entirely in simulation.
7. Add failure/recovery/takeover scenarios and make them deterministic/replayable.
8. Add bounded LLM supervisor interface returning only existing transition IDs + typed slots.
9. Test malicious/invalid/stale supervisor output fail-closed.
10. Integrate TaskGraph into a real product session owner without growing diagnostics into orchestrators.
11. Physically verify one non-committing real-world test-only call with disclosure/consent, or one genuine user-authorized appointment call.
12. Expand to a small number of distinct public reception targets, respecting the per-target call budget.
13. Add Skills as task builders/supervisors over the same authority boundary.
14. Standardize reusable TaskGraph + ServicePack formats and add additional domains.

## Gate D acceptance target

Gate D is complete when the system can take a natural goal such as:

```text
Umów mnie do dentysty w przyszłym tygodniu po 16.
```

and, on S22, complete a bounded real multi-turn call where:

- target is explicitly authorized;
- task/constraints/preferences are structural;
- common turns run deterministically;
- LLM supervisor is used only where needed and cannot gain authority;
- offered appointment data is parsed to typed fields;
- unsuitable offers are rejected by policy;
- an acceptable offer becomes a typed proposal;
- required user confirmation occurs before commitment;
- exactly one authorized commitment is released;
- structured result is returned to the user;
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
7. records real-world call evidence conservatively;
8. updates authoritative docs rather than proliferating status files;
9. leaves `main` clean.

When moving work to a new chat, follow `docs/HANDOFF_PROTOCOL.md`, refresh `docs/HANDOFF_NEXT_CHAT.md`, and create/update the ready-to-paste `docs/NEXT_CHAT_PROMPT.md`.

Orange-specific work additionally follows `docs/ORANGE_MAPPING_RUNBOOK.md`.
