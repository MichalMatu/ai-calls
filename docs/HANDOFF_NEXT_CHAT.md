# Handoff — generic autonomous phone task authority

Date: 2026-09-24

## Repository

`MichalMatu/ai-calls`

Durable code/docs live on `main`; `agent-control` is Local Agent task/result transport only.

Always fetch fresh `origin/main` and fresh `.agent/status/daemon.json` / binding in the next chat.

The merged repository-cleanup checkpoint is:

```text
e998fb2e104265a9bd39f9e798c45924cfe39151
Repository cleanup before generic task development
```

Cleanup validation passed targeted `PhraseMatrix*` / `realtime-client` tests, the full `bash scripts/verify_host.sh` baseline, and GitHub `Android CI` / `Host quality gate` on PR #6.

## Repository cleanup checkpoint

Before further product development, the repository was normalized without changing the frozen Samsung/media or Gate D authority behavior:

- added a Gradle 9.6 wrapper and made `scripts/verify_host.sh` self-bootstrapping for the local Android SDK;
- aligned the Gradle root name and app label with `AI Calls`;
- removed transient/redundant handoff and experiment documents while keeping durable architecture, roadmap, security, runbook and freeze sources;
- consolidated new-chat continuation into `docs/HANDOFF_NEXT_CHAT.md` + `docs/HANDOFF_PROTOCOL.md` instead of maintaining a second copied prompt file;
- preindexed `PhraseMatrix` fuzzy/context/temperature lookup data so normalization, candidate filtering and token counts are not rebuilt on every turn;
- removed one obsolete nullable-response-body branch in the Realtime credential provider that is impossible with the current OkHttp API;
- archived 17 stale local experimental branch tips into the verified bundle `~/ai-calls-stale-branches-20260924.bundle`, then removed those local branch refs.

The audit also identified larger modularity debt that was intentionally **not** mixed into this cleanup because it crosses behavior-critical state machines:

- `CallRealtimeSessionOrchestrator` still combines bootstrap, state transitions, resource lifecycle, function-call handling and cleanup;
- `LocalTextCallGateDProductIntegration` still mixes generic apply/reducer plumbing with appointment-specific permit/evidence/completion logic;
- production app wiring still carries developer/probe surfaces used by physical acceptance workflows.

Treat those as focused RED -> GREEN refactors only when their boundary becomes the active task. Do not perform a broad rewrite before G1.

## Product direction now frozen for continuation

The product is a **generic autonomous phone task engine**, not an Orange-specific bot and not an appointment-only bot.

Examples using the same architecture:

- enable Orange CLIR;
- call a clinic and book within date/time/price constraints;
- change/cancel a reservation;
- handle a bounded service request;
- make read-only information calls.

Do not create separate authority stacks for each use case.

Read `docs/GENERIC_PHONE_TASK_AUTHORITY.md` before implementation.

## Stable proven foundation

Keep closed absent a concrete root cause:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator`: `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary: proven/frozen;
- local Polish STT/TTS foundation: proven;
- IdentityVault Android encryption/disclosure boundary: proven;
- Gate D `BOOK_APPOINTMENT`: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`;
- Gemma 4 direct LiteRT-LM runtime: proven;
- Gemma app-owned SAF/network acquisition, SHA verification, atomic activation and readiness: `HOST_GREEN / PROVEN_S22`.

Do not redownload Gemma merely to reprove it.

Model identity:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
runtime=LiteRT-LM
```

## Dialogue target

```text
STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skills
 -> supervisor/ChatRelay fallback when unresolved
 -> application output approval
 -> TTS/TX
```

Models/supervisor remain dialogue/proposal helpers only. They never gain business authority.

## Architecture decision from this chat

`CallTask` is already appropriately generic. The main coupling to remove is that `CallCommitmentGate` and related Gate D wiring use appointment-shaped `CallProposal` as the commitment subject.

Do **not** solve CLIR by adding `ClirCommitmentGate`.

Next architecture:

```text
CallTask + target + constraints + authorized facts
 -> typed external-effect candidate
 -> application validation
 -> user-decision policy if needed
 -> one-shot permit bound to exact effect
 -> reviewed execution/speech
 -> permit-consumption evidence
 -> external success evidence
 -> factual completion
```

Keep distinct:

```text
task authorization
 != candidate validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

If the user already explicitly authorized the exact concrete effect and no new material term was negotiated, do not add a redundant second confirmation. Application policy decides this; never Gemma/supervisor.

## Exact next implementation scope

### G1 — DONE: appointment coupling audit

Mapped `CallProposal` through `CallCommitmentGate`, the Realtime commitment path, Gate D product integration, consumption/completion evidence, `LocalTextCallSession`, tests and docs. The narrow migration point was confirmed to be the existing single commitment store rather than a new task-specific gate.

### G2 — DONE: generic commitment subject

`CallCommitmentGate` now stores one typed `CallExternalEffect`. Existing `BOOK_APPOINTMENT` code uses `CallExternalEffect.BookAppointment` through compatibility overloads on that same store. `CallCommitmentConsumptionEvidence` carries the typed effect while preserving the reviewed appointment view. Existing appointment behavior remains green; there is still exactly one authority store.

### G3 — DONE: synthetic CLIR effect lifecycle

Added `CallExternalEffect.SetService(target, CLIR, enabled)` and deterministic `CallExternalEffectValidator` binding the candidate to the exact `CallTask`, exact `CallResolvedTarget`, service and explicitly authorized `service.enabled` value. Permit consumption is separate from external success; `CallExternalEffectCompletionTracker` accepts completion only after exact matching success evidence and only once. Targeted tests and the full `scripts/verify_host.sh` baseline are green. This was synthetic/no-call only; no CLIR account change was attempted.

### G4 — DONE: full no-call acceptance runner

`GenericPhoneTaskAcceptanceProbe` now exercises the production ownership chain without dialing or applying a real service change:

```text
synthetic RX PCM -> on-device STT -> deterministic CallPlan TAKE_OVER
 -> LOCAL_GEMMA_4 bounded classifier -> supervisor fallback
 -> application output approval -> on-device TTS -> synthetic TX
 -> exact SET_SERVICE(CLIR=true) validation -> one-shot permit issuance/consumption
 -> exact synthetic external-success evidence -> factual effect completion
 -> explicit workflow completion
```

The full `scripts/verify_host.sh` gate is green. The S22 no-call proof is also green with nonblank STT, one Gemma classifier turn, exactly one supervisor fallback, approved output, nonempty TTS PCM, synthetic TX, exact effect validation, permit issuance/consumption, external-success evidence, factual effect completion and separate workflow completion. The report explicitly records `call_required=false` and `external_effect_real_execution=false`. No call was placed and no CLIR account change was attempted.

### G5 prerequisite — DONE: live-call readiness and legacy authority hardening

PR #14 / `a3cd0dd99e977b3e5aca8fd4b7b19c43a8625563` added a DUMP-protected no-call readiness probe for `RECORD_AUDIO` plus Shizuku binder/runtime/app permission and a host reader that fails closed. The legacy Gate C `caller_id_restriction_enable` path is blocked at wire-id parsing, reviewed-speech access, fast-path construction and the host action allowlist; the dormant committing CLIR speech was removed from the legacy runtime enum. Read-only caller-ID restriction information remains available. Exact-head Local Agent verification and GitHub Android CI / Host quality gate were green. No call was placed and no account change was attempted.

### G5a — DONE host-only: CLIR route-discovery contract

PR #15 / `5037c21aadea06e3204f5d9db60476f1c8dbaba1` added `scripts/g5_clir_route_discovery_plan.py` and `docs/G5_CLIR_ROUTE_DISCOVERY.md`. The plan is bound to the existing exact Orange allowlist and requires:

```text
live_call_readiness_required=true
primary_action=caller_id_restriction_info
observe_next=true
external_effect_execution=false
commitment_permit_use=false
fresh_live_call_authorization_required=true
```

It validates only the exact reviewed read-only utterance plus a nonblank observation transcript. It does not dial, execute CLIR, issue a permit or create a second authority store. Exact-head Local Agent verification and GitHub Android CI / Host quality gate were green.

### G5b — NEXT: read-only live route discovery

Only with a **new fresh explicit live-call authorization** for the concrete Orange target and read-only CLIR route-discovery task, run the bounded caller-ID information turn followed by the existing `OBSERVE_ONLY` next turn. Before any dial, `scripts/live_call_readiness.py` must pass. The current Orange ServicePack proves only a read-only caller-ID information edge and still has `service_route_verified=false`; discovery evidence is not commitment authority and is not success evidence for CLIR activation.

### G5c — NEXT after route verification: generic CLIR effect execution

Execution must remain the existing generic authority lifecycle: exact `CallTask` + exact target + explicit `service.enabled=true` -> `CallExternalEffect.SetService(CLIR=true)` validation -> one-shot `CallCommitmentGate` permit -> exact permit consumption -> separate external success evidence -> factual effect completion -> workflow completion. Do not add `ClirCommitmentGate`. If the fresh user instruction does not already cover the concrete effect execution, obtain a separate explicit authorization before any account-changing call step.

### G6 — DONE: negotiated clinic booking authority proof

A host-only Gate D acceptance case now proves that appointment booking shares the same generic commitment store instead of relying on a parallel authority stack. The synthetic offer is inside the hard time/price/payment constraints but uses a non-preferred provider, so the existing application policy requires an explicit user decision. After confirmation, the exact proposal is consumed as `CallExternalEffect.BookAppointment` from the same `CallCommitmentGate`. Completion with a changed price or changed provider fails closed with `OUTCOME_MISMATCH`; only the exact scheduled time, price, provider and location completes the TaskGraph and `CallWorkflow`. Provider remains a soft preference by design rather than being silently promoted to a hard constraint. Targeted tests and the full `scripts/verify_host.sh` gate are green. No call was placed.

## Post-roadmap modular cleanup

Four narrow behavior-preserving slices are merged after G6:

- `8dfb324e7e7ee38508ecfe99309ce0e8946bfae1` / PR #10: `MainActivity` delegates developer/probe controls and the Shizuku probe listener lifecycle to `MainActivityDeveloperProbes`. Launcher behavior, probe labels, `run_probe`, permissions and manifest entries are unchanged.
- `534837e75ba8c1f19ed21469d8a1545b253152b6` / PR #11: pending Realtime function-call ID collection semantics live in `CallRealtimeFunctionCallTracker`. `CallRealtimeSessionOrchestrator` still owns generation, transport identity, state transitions, media cleanup and takeover ordering; duplicate IDs, one-shot response and stale-generation fail-closed behavior remain covered.
- `547b133c6cfeca77a6f457b96951df86db72808a` / PR #12: exact approved BOOK_APPOINTMENT terms vs factual outcome comparison lives in pure `BookAppointmentOutcomeMatcher`. Outcome SUCCESS validation, permit consumption, workflow state, authorization recheck, TaskGraph commit and workflow completion remain owned by Gate D integration.
- `519d66d7a8945122dcdd63cf38dd58189efae0dc` / PR #13: `close()` now reuses the same `takeOverNow()` cleanup path instead of maintaining a second copy. Added regression tests prove active close still aborts local media before Realtime cancel/close, and close during credential fetch invalidates the late credential. Orchestrator state definitions, generation checks and cleanup ownership remain unchanged.

Targeted tests, each full `scripts/verify_host.sh` gate and GitHub Android CI / Host quality gate are green for these slices. No live call or external account change was performed.

Remaining large behavior-critical owners include `CallRealtimeSessionOrchestrator` and `LocalTextCallGateDProductIntegration`; continue only with narrow RED→GREEN slices. Do not move session/media lifecycle authority out of the orchestrator, and do not move commitment/consumption/completion authority into pure Gate D helpers.

## Latest physical call checkpoint from this chat

Two live Orange attempts were used to diagnose the runner under an explicit authorization that is now spent and **does not carry into the next chat**.

What was established:

- real dialing and `OFFHOOK` work;
- real Orange downlink audio works;
- first attempt exposed transient ADB `dumpsys audio` / cleanup fragility;
- commit `ceefbf7c...` added bounded retries and hangup fallback;
- second attempt passed the audio probe but did not publish a first transcript;
- fresh APK diagnostics showed Android microphone permission needed to be granted again after reinstall;
- after the user re-granted microphone permission, a no-call probe confirmed Shizuku granted, `RECORD_AUDIO` granted and `chat_relay_probe_complete=true`.

No CLIR account change was completed. Do not claim otherwise.

## Branch/noise cleanup

Historical local branch tips with unique commits were preserved in the verified bundle:

```text
~/ai-calls-stale-branches-20260924.bundle
```

The 17 stale `chat-relay/*`, old Gate D and Gemma lifecycle local branches were removed. `agent-work` remains as Local Agent working infrastructure. The temporary repository-cleanup branch was merged through PR #6 and removed locally and remotely. The PR #14 readiness branch was removed after merge; the PR #15 G5a route-discovery branch should also be removed after merge. The intended remote branch set is exactly:

```text
main
agent-control
```

Keep that minimal remote-branch policy unless a new temporary branch is genuinely required.

## Authorization stop line

No live-call permission is transferred by this handoff.

A future real call requires fresh explicit authorization in the new chat for the concrete target/number and task. Connected S22, old call evidence, an allowlist, this handoff or a generic `continue` are not authorization.
