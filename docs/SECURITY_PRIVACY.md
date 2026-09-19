# Security and privacy

## Objective

The agent may speak, dial or commit only inside authority explicitly granted by the user. Technical failure must disable AI injection or return control to the human caller; it must never broaden authority.

## Privilege boundary

Protected Samsung call-audio access stays inside the privileged helper / Shizuku UserService.

Continuous PCM crosses through transferred PFDs. Binder/AIDL is control only. The privileged helper has no model networking and no business-policy authority.

The frozen media path is documented in `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Engine boundary

Supported/preserved audio modes:

```text
LOCAL_STT_TTS
OPENAI_REALTIME_AUDIO
LOCAL_REALTIME_AUDIO
```

Text providers for `LOCAL_STT_TTS`:

```text
LOCAL_PHONE_LLM
LOCAL_MAC_LLM
OPENAI_TEXT
```

Model/provider selection never changes `CallTask`, confirmation, commitment, output approval or TAKE OVER semantics.

Paid OpenAI paths are currently deferred, not deleted.

## Local speech privacy

The proven `LOCAL_STT_TTS` path uses on-device Polish speech recognition and local non-network-required TTS voices on the target S22+.

Production local speech must:

- require on-device recognition rather than silently falling back to cloud STT;
- use TTS voices that do not require a network connection;
- bound and clean temporary PCM/files;
- close temporary PFDs on completion, cancellation and TAKE OVER;
- avoid retaining call recordings or full transcripts by default.

## Local model boundary

`LOCAL_PHONE_LLM` uses a phone-loopback server and product-owned privileged runtime.

Readiness must verify exact expected model identity, not only endpoint health. A stale or unexpected process fails closed or is recovered through the owned runtime path.

The model never receives authority merely because it runs locally. Local model output is still untrusted candidate text until application approval.

## CallPlan and authorized facts

Future `CallPlan v1` must distinguish:

- hard constraints;
- preferences;
- authorized facts;
- preset answers;
- allowed actions;
- decisions requiring user confirmation;
- model/counterparty text.

Model/counterparty text cannot create new authorized facts.

Pre-call research may suggest a destination or fact, but research output does not silently widen automated dialing authority. The actual live destination must be explicitly operator/user authorized under `AGENTS.md`.

Never invent sensitive user data to satisfy a counterparty question. Missing required data must cause a bounded clarification/escalation.

## Commitment and output integrity

The application owns commitment authorization.

```text
evaluate proposal
 -> deterministic policy
 -> optional explicit user decision
 -> exact one-shot permit
 -> commit exact proposal once
```

Replacement proposal, stale generation, TAKE OVER, close or failed submission invalidates authorization.

For local text mode:

```text
final caller transcript
 -> complete candidate response
 -> application policy/approval
 -> local TTS
 -> telephony TX
```

Do not synthesize/release unsafe model text merely to reduce latency.

## READY_TO_DIAL security gate

A future local call must fail closed before dialing unless:

- the task and target are valid;
- the live target is explicitly authorized;
- required plan data is present;
- STT/TTS readiness is proven;
- the selected local model is loaded and identity-verified;
- warm-up succeeds.

Readiness must not silently switch to a provider with different privacy, cost or network semantics.

## Controlled live-call policy

Automated live validation follows `AGENTS.md`:

- only explicit operator-defined allowlisted destinations;
- one active cellular call at a time;
- bounded duration/retries;
- no model/tool expansion of the dialing allowlist;
- no emergency/premium/arbitrary short-code targets;
- a runner may hang up the bounded call it created;
- an unrelated pre-existing call must not be terminated without explicit authorization.

Keep selected destination, call-state transitions and cleanup outcome observable without logging unrelated phone data.

## Developer ChatGPT relay benchmark

A planned quality benchmark may send the local STT transcript to the current interactive ChatGPT conversation through Local Agent/ADB and return response text to local S22 TTS.

This is developer tooling, not a production autonomous backend.

Privacy rules:

- prefer transcript and response text; do not export raw call audio when not required;
- run only intentionally during a controlled benchmark;
- keep payloads bounded to the current test task;
- do not write transcripts into durable repo logs by default;
- never treat the chat relay as a hidden background listener.

## Remote/OpenAI credentials

If paid OpenAI work is resumed, a standard API key must never be:

- embedded in source/resources/BuildConfig/APK;
- stored in Android app-private configuration;
- passed through an Android Intent;
- passed in ADB argv/process arguments;
- logged by app/helper/scripts.

Existing broker-based OpenAI code is preserved for future use. Long-lived credentials remain host/backend-only.

`OPENAI_TEXT` should transmit text/context only unless a different mode is explicitly selected. OpenAI Realtime Audio is a separate preserved/frozen audio path.

## Local Mac provider

`LOCAL_MAC_LLM` remains a provider option.

Preferred boundary:

```text
S22 -> trusted LAN/authenticated narrow endpoint -> local model server
```

Do not expose a local model server publicly by default. Failure must not silently switch to another provider with different privacy/cost semantics.

## Diagnostics and data minimization

Do not retain by default:

- raw PCM;
- call recordings;
- full transcripts;
- model/tool arguments containing user data;
- credentials;
- unrelated counterparty identifiers.

Prefer:

- state;
- sizes;
- timings;
- sanitized failure reasons;
- local correlation IDs;
- bounded text only when needed for an explicit development proof.

## TAKE OVER

Required local-first ordering:

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate model/tool generation
 -> best-effort cancel remote/local model work
```

The first steps cannot wait for network/model acknowledgement. App/helper death must likewise disable injection.

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`.

Physically proven now includes:

- frozen bidirectional Samsung cellular media and fail-safe cleanup;
- on-device `pl-PL` STT and local TTS;
- provider-neutral local speech/text pipeline;
- product-owned Qwen2.5-1.5B runtime lifecycle and identity verification;
- one bounded local-LLM Orange cellular turn;
- trailing-silence endpointing during that live turn.

Not yet proven:

- larger phone-local text model;
- GPT-5.6 Sol interactive relay benchmark;
- `READY_TO_DIAL` product gate;
- `CallPlan v1` constrained multi-turn task execution;
- local audio-model path;
- paid OpenAI text or genuine OpenAI Realtime end-to-end path.
