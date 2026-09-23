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
14. finalized-turn selector can create one bounded Gate D observation for an explicitly bound shadow observer after deterministic PhraseMatrix/CallPlan routing is already decided;
15. session-owned shadow epoch/lifecycle invalidates older queued turns and pending work on cancel/close; observer failure degrades to unchanged deterministic behavior;
16. hypotheses are revalidated through `SupervisorProposalValidator` and exposed only as redacted candidate/`DialogueFit` diagnostics;
17. explicit application-owned `TaskGraphApplyBridge` re-checks graph version/state/generation, legal transition/event mapping, provenance, slot scope, authorization and schema before creating a typed event;
18. rejected apply candidates never call the reducer; accepted reductions return snapshot/event evidence/effects as data only;
19. reusable `AppointmentInterpreter` provides typed absolute/relative/weekday dates, times/time ranges, concrete offer candidates, deterministic accept/reject/alternative acts and identity-field request IDs;
20. relative appointment interpretation has no hidden clock: it requires an explicit caller-supplied `referenceDate` and otherwise fails closed;
21. `BookAppointmentSimulator` delegates offer parsing to the same interpreter, preserving existing validation/confirmation/commitment owners and `extract -> validate -> commit`;
22. appointment parser/matcher output remains candidate-only and has no TaskGraph/workflow/speech/dial/commitment/IdentityVault authority;
23. categorical `DialogueFitHysteresis` provides immediate deterioration and evidence-backed recovery without execution authority;
24. deterministic sequence-level Gate D evaluation corpus covers repeated unknowns/recovery exhaustion, ambiguity/recovery, unacceptable/alternate offers, user rejection, unauthorized/high-sensitivity disclosure, cancel/takeover, stale supervisor results and clean recovery;
25. host `PersistentIdentityVault` core provides typed redacted secret capability, versioned encrypted envelope/payload, AEAD + associated-data port, defensive copies, fail-closed decode/decrypt behavior and explicit `DEVICE_BOUND_NO_BACKUP` semantics;
26. Android IdentityVault production adapter provides `noBackupFilesDir` + `AtomicFile` ciphertext storage and Android Keystore AES-256/GCM with non-exportable key, key create/reuse, stable algorithm identity/AAD and fail-closed missing/invalid-key behavior;
27. reviewed internal Gate D product binding composes deterministic interpretation first, optional bounded shadow, `SupervisorProposalValidator`, application-owned current snapshot/slot authorization re-check and `TaskGraphApplyBridge`; deterministic rejection does not fall through to shadow and reducer effects remain inert result data;
28. `LocalTextCallSession.injectSyntheticFinalTranscript(...)` provides an explicit no-call test/diagnostic ingress for already-finalized text and converges with STT output at one shared finalized-turn product path;
29. physical S22 IdentityVault instrumentation proof is complete without a cellular call;
30. physical S22 synthetic reviewed-product proof is complete without a cellular call;
31. `BOOK_APPOINTMENT` proposal composition reuses the already-computed `CallWorkflow` proposal/policy result instead of evaluating policy a second time;
32. `PROPOSE_APPOINTMENT` is policy-neutral: the old proposal-evaluation graph effect/bridge was removed so TaskGraph does not acquire proposal-policy authority;
33. explicit app-owned user CONFIRM/REJECT staging re-checks graph state/generation/slot authorization and exact `CallWorkflow` pending proposal before owner mutation;
34. commitment authorization issues at most one opaque `CallCommitmentGate` permit for the exact proposal returned by `CallWorkflow.approvePendingProposal()` and does not consume it or claim completion;
35. commitment hardening re-checks the exact approving workflow is still `ACTIVE_NEGOTIATION` immediately before permit issuance and uses token-scoped revocation so cancel/close cannot clear a foreign/newer permit;
36. the complete proposal -> confirmation -> explicit user confirmation -> commitment-authorization owner chain is physically proven on the target S22 without speech/media/backend use and deliberately stops before completion.

### Verification checkpoints

- finalized-turn shadow lifecycle: `4f7dcdd9051bc2090b5dde9ede3d688d17655f1e`, Android CI #471 `success`;
- TaskGraph apply bridge GREEN: `052f20ea11d20b97ade324ee734a1cff1c43bec3`, Android CI #474 `success`;
- appointment interpretation integrated GREEN: `cd91a2dbaafdf570c9e3e67b132c02cb88c0cf96`, Android CI #482 `success`;
- DialogueFit hysteresis GREEN: `c2bd394df32f389aa6e2cb218df44a3b8b66de1d`;
- sequence evaluation corpus GREEN: `a86034c77df23cd9375cad20a04e7609aef13956`;
- persistent IdentityVault host core GREEN: `27457e3e103b89dac9f7e86a1b427f297128b7dc`, Android CI #488 `success`;
- Android IdentityVault adapter: `3b79d42012d80c7cc6956bac77f590bfae20dc72`, Android CI #494 `success`;
- reviewed Gate D product integration GREEN: `a355f604484a78d7a99d7594455f3344ca081e04`, Android CI #497 `success`;
- synthetic finalized-text ingress RED: `e10f5d3de036f8d76b2276cd1b61fc5ae45c7c1b`, Android CI #499 expected `failure`;
- synthetic finalized-text ingress GREEN: `79e6dabd62cb325b41bc37615252165fe563c3e4`, Android CI #500 `success`;
- Android synthetic Gate D product instrumentation contract: `313c6bb43251bc2cdd06e5e783a22154ac378f49`, Android CI #505 `success`;
- S22 IdentityVault physical proof: Local Agent result `chatgpt-gated-s22-identity-vault-proof-v004-20260923`, `BUILD SUCCESSFUL`, `IDENTITYVAULT_S22_PROVEN=true`;
- S22 synthetic reviewed-product physical proof: `chatgpt-gated-s22-synthetic-gated-product-proof-v005-20260923`, `BUILD SUCCESSFUL`, `SYNTHETIC_GATE_D_S22_PROVEN=true`;
- proposal owner reuse: `656951e6e9603a8af9e4ff12ec4f0f355817b388`, targeted/canonical green, Android CI #518 `success`;
- policy-neutral proposal cleanup: `6d77d385170ca85feb40485800526e74f0bfaaa4`, targeted green;
- bounded user decision: `2ffaf7dc696f5e21e7d77944e8f0af8d7105f039`, Android CI #522 `success`;
- commitment authorization: `061ed5071b127c9fb687e9532f7d1621f92fe1b5`, targeted/canonical green, Android CI #524 `success`;
- commitment ownership hardening: `c5266423ad4a3cb3cbd8b245c7774e1378d9265e`, targeted/canonical green, Android CI #526 `success`;
- Android no-call BOOK_APPOINTMENT commitment contract: `90a161c760c8267bd5625cba37373e6af9f9b07e`; physical S22 result `chatgpt-gated-s22-book-appointment-commitment-proof-v029-20260923`, `BUILD SUCCESSFUL`, `BOOK_APPOINTMENT_COMMITMENT_S22_PROVEN=true`.

Physical proof status now:

```text
Android IdentityVault                         PROVEN_S22
synthetic reviewed Gate D product ingress     PROVEN_S22
BOOK_APPOINTMENT owner chain through permit   PROVEN_S22 (no-call)
```

The last line means proposal/user-confirmation/permit issuance is proven. It does **not** mean a booking was committed or completed.

During earlier appointment verification the known `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` flake reappeared. Root-cause audit found a test milestone race; the correction was test-only and no production media/helper code changed. If it reappears, inspect test ordering/synchronization first.

The public Android `LocalTextCallSession.create(...)` path still binds no production shadow observer/provider and no product apply binding. Reviewed internal composition must supply `LocalTextCallGateDProductBinding` explicitly.

## NEXT — exact execution order

### 1. Commitment-consumption evidence boundary

Keep these facts distinct:

```text
permit issued != permit consumed != business success confirmed
```

`CallRealtimeCommitmentFunctionHandler` currently consumes a valid one-shot permit and returns `{"commitment":"authorized"}`. That is authorization consumption only. It must not itself drive `COMMIT_SUCCEEDED` or `CallWorkflow.complete(...)`.

Design and TDD a narrow application-owned signal/evidence seam for consumption of the exact BOOK_APPOINTMENT authorization. It must remain redacted, one-shot, proposal-bound and stale-safe. Do not create a generic effect executor.

### 2. Product-bound completion ordering

Audit and then TDD completion ordering before changing behavior. `CallPlanTurnCoordinator` currently handles `CallPlanAction.COMPLETE` by calling `CallWorkflow.complete(...)` before Gate D sees the finalized turn.

For the reviewed BOOK_APPOINTMENT product path, completion must require exact success evidence and must preserve `CallWorkflow` as the completion owner. Do not equate permit consumption with success. Keep the public/default coordinator path unchanged unless explicit reviewed wiring opts into the product-bound completion path.

Only after exact success evidence may TaskGraph move `COMMITMENT -> COMPLETE` and the workflow record the matching structured outcome.

### 3. Canonical + Android/S22 no-call proof

Run targeted tests, `bash scripts/verify_host.sh`, CI and the minimal relevant S22 instrumentation proof for every new completion/consumption boundary. Do not touch frozen media unless that boundary itself is under test.

### 4. Late disclosure wiring if required by acceptance task

Plaintext remains late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> plaintext resolution only after ALLOW
```

### 5. STOP before live call

Do not dial as part of Gate D preparation. A real call requires fresh explicit authorization for the concrete target and task in the current chat/session. Old docs, old Local Agent results, a connected phone or prior calls are not authorization.

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
- commitment consumption and factual success are distinguished;
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
