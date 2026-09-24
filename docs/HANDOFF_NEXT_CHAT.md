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

### G5 — NEXT: live CLIR acceptance

Only with a **new fresh live-call authorization in that chat**, run one bounded Orange call to complete CLIR and collect redacted evidence.

### G6 — DONE: negotiated clinic booking authority proof

A host-only Gate D acceptance case now proves that appointment booking shares the same generic commitment store instead of relying on a parallel authority stack. The synthetic offer is inside the hard time/price/payment constraints but uses a non-preferred provider, so the existing application policy requires an explicit user decision. After confirmation, the exact proposal is consumed as `CallExternalEffect.BookAppointment` from the same `CallCommitmentGate`. Completion with a changed price or changed provider fails closed with `OUTCOME_MISMATCH`; only the exact scheduled time, price, provider and location completes the TaskGraph and `CallWorkflow`. Provider remains a soft preference by design rather than being silently promoted to a hard constraint. Targeted tests and the full `scripts/verify_host.sh` gate are green. No call was placed.

## Post-roadmap modular cleanup

`MainActivity` now delegates developer/probe controls and the Shizuku probe listener lifecycle to `MainActivityDeveloperProbes`. Launcher behavior, probe labels, `run_probe`, permissions and the manifest are unchanged; the change only separates diagnostic ownership from product runtime/model settings. Compile, lint and the full `scripts/verify_host.sh` gate are green.

Remaining large behavior-critical owners include `CallRealtimeSessionOrchestrator` and `LocalTextCallGateDProductIntegration`; refactor them only in narrow RED→GREEN slices that preserve the proven Samsung media and Gate D authority behavior.

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

The 17 stale `chat-relay/*`, old Gate D and Gemma lifecycle local branches were removed. `agent-work` remains as Local Agent working infrastructure. The temporary repository-cleanup branch was merged through PR #6 and removed locally and remotely. The remote branch set is exactly:

```text
main
agent-control
```

Keep that minimal remote-branch policy unless a new temporary branch is genuinely required.

## Authorization stop line

No live-call permission is transferred by this handoff.

A future real call requires fresh explicit authorization in the new chat for the concrete target/number and task. Connected S22, old call evidence, an allowlist, this handoff or a generic `continue` are not authorization.
