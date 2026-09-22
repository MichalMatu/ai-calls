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

Preserve the existing observed nodes/edges and evidence. Resume broad Orange mapping only when a concrete user task, ServicePack feature, route-regression check or TaskGraph/ServicePack integration test justifies it. Follow `docs/ORANGE_MAPPING_RUNBOOK.md` when resumed.

## ACTIVE Gate D — hybrid multi-turn Task Engine

Primary acceptance task:

```text
BOOK_APPOINTMENT
```

Example user goal:

```text
Umów mnie do dentysty w przyszłym tygodniu, najlepiej po 16.
```

Core authority rule:

```text
model/parser/matcher output
 -> candidate only
 -> application-owned validation
 -> authoritative state only after validation
```

### Completed Gate D foundation on the active work branch

The following slices are complete at the current handoff checkpoint:

1. preimplementation ownership audit;
2. shared TaskGraph RED contracts;
3. engine decision: keep the minimal application-owned custom reducer; no KStateMachine runtime/dependency is carried in production code;
4. typed TaskGraph core with guards, state compatibility, bounded recovery, proposal/confirmation/commitment state kinds, terminal states, effects-as-data and deterministic versioned replay;
5. host `IdentityFieldId`, `AuthorizedFactSnapshot` and `FactDisclosurePolicy` contracts;
6. host `BOOK_APPOINTMENT` TaskGraph and deterministic receptionist simulator;
7. simulated proposal -> confirmation -> one-shot commitment -> completion path;
8. simulated disclosure decisions and recovery/cancel/takeover cases;
9. categorical explainable `DialogueFit` policy;
10. bounded `ShadowDialogueObservation` / hypothesis contracts;
11. fail-closed `SupervisorProposalValidator` for generation, allowed transition, slot scope, authority-bearing slot IDs and confidence;
12. read-only `LocalTextCallGateDRuntime` owned by `LocalTextCallSession`;
13. product composition of `TaskGraphDefinition + AuthorizedFactSnapshot` through Android/LocalPhone readiness -> coordinator -> prepared call -> session.

The current read-only runtime deliberately does **not** call `TaskGraphCore.reduce()` and has no graph-effect executor, workflow mutation, speech, dial, plaintext vault or commitment API.

## NEXT — exact execution order

### 1. Activate shadow observation on finalized turns

Start with a RED contract around the real product session path. When Gate D is bound, every relevant finalized turn should be able to produce one bounded `ShadowDialogueObservation` from session-owned deterministic state/context.

Constraints for this slice:

- existing PhraseMatrix/CallPlan deterministic behavior must remain unchanged;
- observation happens only for finalized turns;
- generation/state/allowed transition/fact scopes come from the bound session/runtime;
- no `TaskGraphCore.reduce()`;
- no supervisor candidate may release speech, mutate workflow or consume commitment authority;
- no plaintext identity value enters the observation.

### 2. Bind a quarantined observer lifecycle

Add the smallest session-owned observer seam needed to consume `ShadowDialogueObservation` and return `ShadowDialogueHypothesis`.

Prove:

- one-session ownership;
- stale/generation mismatch fail-closed;
- cancellation/close invalidates pending work;
- exceptions/timeouts degrade to deterministic behavior or safe recovery;
- diagnostic output does not leak transcript/identity plaintext through ordinary `toString`/logs.

Do not add a broad provider abstraction unless the existing backend/provider layer cannot safely host the bounded observer.

### 3. Feed validated shadow comparison into DialogueFit

Map deterministic turn evidence plus optional validated shadow hypothesis into `DialogueFitSignals`.

Keep the first integration categorical and explainable. Do not tune arbitrary numeric scoring in production before simulator/eval evidence exists.

Expected behavior:

```text
HIGH       -> current deterministic path
UNCERTAIN  -> clarification / optional supervisor evidence
LOW        -> bounded supervisor candidate required
BROKEN     -> recovery / TAKE_OVER / safe stop
```

At this checkpoint, a validated supervisor candidate is still **candidate data only**.

### 4. Only then add the application-owned TaskGraph apply bridge

After shadow lifecycle + DialogueFit are stable, add a separate RED/GREEN slice that converts an already-validated deterministic/supervisor candidate into an explicit typed TaskGraph event and calls the reducer.

This bridge must:

- re-check current generation/state;
- accept only an existing legal transition/event mapping;
- validate slot types/constraints/provenance;
- preserve `extract -> validate -> commit`;
- return effects as data;
- delegate proposal/confirmation/commitment/completion authority to existing owners;
- never directly render/release arbitrary model speech.

### 5. Finish generic appointment interpretation

Extract the useful parsing logic from the simulator into reusable typed parsers/normalizers for:

- dates/relative dates/weekdays;
- times/time ranges;
- offered appointment candidates;
- accept/reject/alternative semantics;
- common identity-field requests.

Add PhraseMatrix dialogue-act coverage where deterministic phrases are appropriate.

### 6. Expand deterministic replay/eval coverage

Add scripted scenarios for ambiguity, contradiction, repeated unknowns, unavailable slots, alternate offers, unauthorized/high-sensitivity fact requests, user rejection, takeover, cancellation and stale supervisor results.

Use those scenarios to calibrate DialogueFit/hysteresis before live use.

### 7. Android IdentityVault persistence

Only after the host disclosure contract is stable and before a real call requires personal data:

```text
app-private ciphertext storage
+ non-exportable Android Keystore key
+ authenticated encryption
+ versioned records
+ explicit backup/restore semantics
```

Do not implement new persistence with deprecated `EncryptedSharedPreferences` / `MasterKey` APIs.

### 8. Product integration verification

Run targeted tests plus the canonical host gate. Add Android/device tests only for boundaries actually changed.

Do not touch frozen Samsung media to make Gate D tests easier.

### 9. Real-world call gate

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