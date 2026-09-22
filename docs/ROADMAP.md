# Roadmap

This is the authoritative execution order for `MichalMatu/android-ai-call-bridge`. Detailed experiment history belongs in Git history and `.agent/results`; session-transfer state belongs in `docs/HANDOFF_NEXT_CHAT.md`.

Evidence labels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — relevant behavior was physically reproduced on the target Samsung S22+;
- ServicePack `VERIFIED` — the external node/edge was physically observed;
- `service_route_verified=true` — the intended external route was physically proven;
- `PRODUCT_READY` — the bounded product task is proven, fail-safe and suitable for normal use.

## Foundation

### Cellular/media

Status: `DONE / PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Do not redesign this path during Gate D. Read `docs/PHASE2D_FREEZE_2026-09-18.md` first.

### Local speech/text and deterministic fast path

Status: `DONE / HOST_GREEN / LIVE PATH PROVEN_S22`.

Established owners include `LocalSpeechTextPipeline`, `TextCallTurnController`, `TextOutputApprovalPolicy`, `CallPlan`, `PhraseMatrix`, `CallWorkflow`, `CallConfirmationPolicy` and `CallCommitmentGate`.

Gate D composes these boundaries. It does not replace them.

### Orange ServicePack

Status: `CHECKPOINTED / PROVEN_S22 EVIDENCE / NOT THE MAIN ROADMAP`.

Durable graph: `service-packs/orange/service_tree.v1.json`.

Preserve existing observed nodes/edges and evidence. Resume broad Orange mapping only when a concrete user task, ServicePack feature, route-regression check or TaskGraph/ServicePack integration test justifies it.

## ACTIVE Gate D — hybrid multi-turn Task Engine

Primary acceptance task:

```text
BOOK_APPOINTMENT
```

Core authority rule:

```text
model/parser/matcher output
 -> candidate only
 -> application-owned validation
 -> authoritative state only after validation
```

### Completed Gate D foundation on the active work branch

1. preimplementation ownership audit;
2. shared TaskGraph RED contracts;
3. engine decision: minimal application-owned custom reducer; no KStateMachine production dependency;
4. typed `CustomTaskGraphCore` with guards, bounded recovery, proposal/confirmation/commitment state kinds, terminal states, effects-as-data and deterministic versioned replay;
5. host `IdentityFieldId`, `AuthorizedFactSnapshot` and `FactDisclosurePolicy` contracts;
6. host `BOOK_APPOINTMENT` TaskGraph and deterministic receptionist simulator;
7. simulated proposal -> confirmation -> one-shot commitment -> completion path;
8. simulated disclosure decisions and recovery/cancel/takeover cases;
9. categorical explainable `DialogueFit` policy;
10. bounded `ShadowDialogueObservation` / hypothesis contracts;
11. fail-closed `SupervisorProposalValidator` for generation, allowed transition, slot scope, authority-bearing slot IDs and confidence;
12. read-only `LocalTextCallGateDRuntime` owned by `LocalTextCallSession`;
13. product composition of `TaskGraphDefinition + AuthorizedFactSnapshot` through Android/LocalPhone readiness -> coordinator -> prepared call -> session;
14. finalized-turn selector can create one bounded Gate D observation for an explicitly host-bound shadow observer after deterministic PhraseMatrix/CallPlan routing is already decided;
15. session-owned shadow epoch/lifecycle invalidates older queued turns and pending work on cancel/close; observer failure degrades to unchanged deterministic behavior;
16. hypotheses are revalidated through `SupervisorProposalValidator` and exposed only as redacted candidate/`DialogueFit` diagnostics;
17. explicit application-owned `TaskGraphApplyBridge` re-checks graph version/state/generation, legal transition/event mapping, provenance, slot scope, authorization and schema before creating a typed event;
18. rejected apply candidates never call the reducer; accepted reductions return snapshot/event evidence/effects as data only;
19. reusable `AppointmentInterpreter` provides typed absolute/relative/weekday dates, times/time ranges, concrete offer candidates, deterministic accept/reject/alternative acts and identity-field request IDs;
20. relative appointment interpretation has no hidden clock: it requires an explicit caller-supplied `referenceDate` and otherwise fails closed;
21. `BookAppointmentSimulator` delegates offer parsing to the same interpreter, preserving the existing validation/confirmation/commitment owners and `extract -> validate -> commit`;
22. appointment parser/matcher output remains candidate-only and has no TaskGraph/workflow/speech/dial/commitment/IdentityVault authority.

Verification checkpoints:

- finalized-turn shadow lifecycle: `4f7dcdd9051bc2090b5dde9ede3d688d17655f1e`, Android CI #471 `success`;
- TaskGraph apply bridge RED: `8041ffa57181a6a5bf75581134b85d6d10347758`, Android CI #473 Host quality gate failed as intended;
- TaskGraph apply bridge GREEN: `052f20ea11d20b97ade324ee734a1cff1c43bec3`, Android CI #474 `success`;
- appointment interpretation RED: `9a56ddf2bdc58b27b2ca52fae982637930a9b250`, Android CI #476 Host quality gate failed as intended;
- appointment interpretation integrated GREEN checkpoint: `cd91a2dbaafdf570c9e3e67b132c02cb88c0cf96`, Android CI #482 `success`.

During appointment verification the known `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` flake reappeared. Root-cause audit found no shared/static fixture or production media defect: the test waited for `transport.close()` although the session publishes `FAILED` only after cleanup returns. The test now waits on the existing `onTerminalState` callback. No production media/helper code changed.

The public Android session path still binds no production shadow observer/provider and does not automatically apply shadow output. Graph effects remain inert data until existing application owners explicitly consume them.

## NEXT — exact execution order

### 1. Expand deterministic replay/eval coverage

Build a host-only scripted Gate D evaluation corpus covering sequences rather than isolated turns:

- ambiguity and parser incompleteness;
- contradiction;
- repeated deterministic unknowns / recovery exhaustion;
- unavailable slots and alternate offers;
- unauthorized and high-sensitivity fact requests;
- user rejection;
- takeover and cancellation;
- stale supervisor results;
- clean recovery back toward deterministic handling.

For each scenario keep authoritative business outcome, TaskGraph replay, evidence ordering and `DialogueFit` reasons explicit. Do not invent model scores or execution authority.

Use the corpus to decide whether a small categorical hysteresis boundary is justified. If added, safety deterioration must be immediate; recovery must be evidence-backed and deterministic. Hysteresis remains diagnostic/routing policy only and must not approve a transition, proposal, speech, dial, disclosure or commitment.

### 2. Android IdentityVault persistence

Only after the host disclosure contract/eval corpus is stable and before a real call requires personal data:

```text
app-private ciphertext storage
+ non-exportable Android Keystore key
+ authenticated encryption
+ versioned records
+ explicit backup/restore semantics
```

Do not implement new persistence with deprecated `EncryptedSharedPreferences` / `MasterKey` APIs.

### 3. Product shadow/apply integration

After host shadow lifecycle, apply boundary, appointment interpretation and evaluation coverage are stable, bind an intentionally reviewed product observer/provider through an application-owned seam.

The product composition must keep this order:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> existing SupervisorProposalValidator
 -> application-owned apply policy / current snapshot re-check
 -> TaskGraphApplyBridge
 -> effects as data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Preserve session cancellation/generation rules and zero execution authority for the observer itself. Do not create a generic effect executor that bypasses existing owners.

### 4. Product integration verification

Run targeted tests plus the canonical host gate. Add Android/device tests only for boundaries actually changed.

Do not touch frozen Samsung media to make Gate D tests easier. If a media timing test reappears, audit test ordering/synchronization first and keep any correction test-only unless a separate production root cause is proven.

### 5. Real-world call gate

Only after host/simulation and required identity handling are strong:

- one small reviewed ordinary reception/business target at a time;
- `TEST_ONLY_CONSENTED`: disclose AI/test purpose at the start and obtain consent; never create a real booking;
- `GENUINE_TASK`: only with fresh user authorization, authorized facts and normal proposal/confirmation/commitment policy;
- never emergency/urgent/crisis/critical-service lines;
- default one meaningful call per organization, second only after early technical failure or explicit agreement to repeat.

Live-call authorization is session-scoped and never inherited from documentation or an old chat.

## Gate D acceptance target

Gate D is complete only when the S22 can complete a bounded real multi-turn task where:

- target is explicitly authorized;
- task/constraints/preferences are structural;
- identity disclosure is per-task and fail-closed;
- common turns stay deterministic;
- shadow observation has zero execution authority;
- DialogueFit is explainable and evidence-calibrated;
- supervisor suggestions are bounded and revalidated;
- unsuitable offers are rejected by policy;
- acceptable offers become typed proposals;
- required user confirmation occurs before commitment;
- exactly one authorized commitment is released;
- result/evidence is structured and redacted;
- takeover/cancel remain local-first;
- call cleanup returns to `IDLE`.

## Later

After Gate D is stable:

- Skills as task builders/supervisors over the same authority boundary;
- reusable TaskGraph + ServicePack formats across more domains;
- intentional Orange route expansion when it supports real tasks;
- mobile audio-model experiments only behind the same authority and frozen-media boundaries.

## Completion discipline

Every product behavior slice:

1. starts from fresh repository/branch state;
2. uses audit/root cause before changes;
3. uses TDD where behavior is deterministic: RED -> prove failure -> minimal GREEN -> regressions;
4. runs targeted tests and `bash scripts/verify_host.sh` before claiming `HOST_GREEN`;
5. runs only the physical gate required by changed behavior;
6. keeps live-call authorization explicit and session-scoped;
7. updates authoritative docs instead of proliferating status files;
8. does not casually modify `privileged-helper/` or physically proven media;
9. closes long sessions through `docs/HANDOFF_PROTOCOL.md`.
