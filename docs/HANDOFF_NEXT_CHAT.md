# Handoff — generic autonomous phone task authority

Date: 2026-09-24

## Repository

`MichalMatu/android-ai-call-bridge`

Durable code/docs live on `main`; `agent-control` is Local Agent task/result transport only. Handoff cleanup is complete and the remote branch set is:

```text
main
agent-control
```

Always fetch fresh `origin/main` and fresh `.agent/status/daemon.json` / binding in the next chat.

The last code change before this documentation handoff is:

```text
ceefbf7cbd25e27510fa003638269d2132c0f645
Harden live relay against transient ADB failures
```

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

### G1 — preimplementation audit first

Before behavior changes, map every appointment-specific coupling of `CallProposal` through:

- `CallCommitmentGate`;
- realtime commitment handler;
- Gate D product integration;
- confirmation/consumption/completion evidence;
- `LocalTextCallSession` appointment-specific seams;
- tests/docs.

Produce the narrowest migration plan preserving all existing `BOOK_APPOINTMENT` invariants.

### G2 — generic commitment subject

Introduce the generic typed external-effect commitment subject with RED -> GREEN tests. Existing appointment behavior must remain green through an adapter/compatibility path. Do not create a second authority store.

### G3 — CLIR adapter

Add `SET_SERVICE(CLIR=true)` as the first new generic effect. Prove task/target binding, permit lifecycle and factual success evidence synthetically/no-call.

### G4 — full runner

Wire one real product acceptance runner through:

```text
RX -> STT -> deterministic routing -> Gemma -> supervisor fallback
 -> output approval -> TTS/TX -> generic effect authority -> success evidence
```

Do the no-call/S22 synthetic proof before dialing.

### G5 — live CLIR acceptance

Only with a **new fresh live-call authorization in that chat**, run one bounded Orange call to complete CLIR and collect redacted evidence.

### G6 — clinic booking

Use a clinic booking with negotiated time/price/provider constraints as the next acceptance case to prove the architecture is actually generic.

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

Cleanup is complete. Final remote branch enumeration is exactly:

```text
main
agent-control
```

No `chat-relay/*`, `work/*` or temporary documentation branches remain. Keep that minimal branch policy unless a new temporary branch is genuinely required.

## Authorization stop line

No live-call permission is transferred by this handoff.

A future real call requires fresh explicit authorization in the new chat for the concrete target/number and task. Connected S22, old call evidence, an allowlist, this handoff or a generic `continue` are not authorization.
