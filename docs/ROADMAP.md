# Roadmap

Capabilities advance only when their required evidence gate is actually proven.

Evidence levels:

- `HOST_GREEN` — deterministic tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Phase 0 — repository and verification discipline

Status: `DONE`

The project is modularized into app, audio contracts, privileged helper and Realtime client. `scripts/verify_host.sh` is the canonical local/CI quality gate. Durable work is main-first; Local Agent metadata remains isolated on `agent-control`.

## Phase 1 — cellular media capability

Status: `DONE / PROVEN_S22`

Production directions:

```text
RX: VOICE_DOWNLINK -> privileged helper -> PFD -> app
TX: app -> PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Generic media/voice-communication TX experiments are not the production route on this S22.

## Phase 2 — stable local bridge

Status: `DONE / PROVEN_S22 / FROZEN`

Physical evidence covers simultaneous bidirectional media, endpoint loss, app/helper death cleanup, repeated start/abort, natural call end, 600 seconds total live bidirectional media and separate resource telemetry.

Frozen checkpoint commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not repeat the full physical matrix without concrete regression evidence. Details: `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Phase 3 — selectable telephone-agent engines

Status: `LOCAL SPEECH + LOCAL PHONE LLM OFF-CALL PROVEN_S22 / LIVE CELLULAR INTEGRATION NEXT / OPENAI REALTIME AUDIO FROZEN`

The product direction is selectable rather than Realtime-only.

### Runtime choices

```text
Audio mode
├── LOCAL_STT_TTS
│   └── text LLM provider: OPENAI_TEXT | LOCAL_PHONE_LLM | LOCAL_MAC_LLM
├── OPENAI_REALTIME_AUDIO   (preserved/frozen)
└── LOCAL_REALTIME_AUDIO    (future local speech-to-speech/server engine)
```

Tool integration is independent from model selection. MCP is a tools/context transport and must not be treated as an LLM provider. A future model may use local tools, MCP tools, or no tools while the application-owned authority/commitment gate remains unchanged.

Implemented and host-verified shared safety/application stack includes:

- production app-side media coordinator/backend/runtime;
- deterministic task/constraints/preferences/authorized-facts workflow model;
- `CallConfirmationPolicy` and `NEEDS_USER_DECISION`;
- strict side-effect-free proposal parsing;
- one-shot commitment authorization;
- output approval before cellular TX;
- local TAKE OVER and frozen Samsung media fail-safe invariants;
- selectable runtime preferences for `LOCAL_STT_TTS`, `OPENAI_REALTIME_AUDIO`, `LOCAL_REALTIME_AUDIO` and text LLM providers;
- provider-neutral `TextCallAgentBackend` + `TextCallTurnController`;
- production local STT/TTS adapters;
- OpenAI-compatible local text backend proven against Mac and phone-loopback servers.

### Local speech evidence on S22

Status: `PROVEN_S22`

Physically proven on the exact Samsung S22+:

- Android on-device speech recognition is available;
- `pl-PL` on-device recognition model is installed;
- local Polish TTS voices are available and synthesize successfully without network-required voices;
- TTS output can be decoded/resampled to the internal telephony format PCM16LE mono 16 kHz;
- caller-supplied PCM16LE mono 16 kHz can be streamed through a `ParcelFileDescriptor` pipe into on-device STT;
- production local adapters complete a physical local TTS -> STT roundtrip;
- the speech proofs are off-call and leave `CALL_STATE=0` before and after.

Relevant checkpoints:

```text
6a1ba494ddc2319ef9fe8847f88f4e7816b2a7b0
feat: add local speech capability probe

386031a1f9bf891970e4cf6af8a3ec148a65aa7a
fix: stream local STT input through PFD pipe

e953b78ea2b56c3bbc62fded3da295c23ccbf1bb
feat: add production local speech adapters
```

### Gate 3L-A — production local speech adapters

Status: `DONE / PROVEN_S22`

Production-owned local STT/TTS components now provide:

- streaming PCM16LE mono 16 kHz into on-device STT;
- local TTS synthesis returning PCM16LE mono 16 kHz;
- cancellation/generation ownership and bounded cleanup.

### Gate 3L-B — off-call local conversation engine

Status: `DONE / PROVEN_S22`

The provider-neutral text-agent pipeline is physically proven with complete-response approval before TTS. Application-owned workflow/commitment/output approval remains outside the model backend.

Checkpoint:

```text
67a1bc75e7deb55ec0e4e515ef26195c4587edae
feat: add local text agent pipeline
```

### Gate 3L-C — text LLM providers

Status: `LOCAL_MAC_LLM PROVEN_S22 / LOCAL_PHONE_LLM OFF-CALL PROVEN_S22 / PRODUCT RUNTIME WIRING NEXT`

Implemented/proven pieces:

- `LOCAL_MAC_LLM` via an OpenAI-compatible local endpoint;
- Qwen2.5-0.5B-Instruct Q4_K_M running directly on the S22 through Android arm64 `llama-server`;
- healthy phone-loopback `/health` and `/v1/chat/completions`;
- direct short Polish response around 0.62 s in the initial smoke, with roughly 58 tokens/s for that small generation;
- complete physical off-call flow:

```text
local TTS test phrase
  -> on-device STT
  -> Qwen on S22
  -> application-owned approval
  -> local TTS response
```

Successful proof checkpoint:

```text
e0cfd0971103568e9a540ee75f6555d2bc8b66d0
test: add local phone llm speech pipeline probe
```

Next: promote `LOCAL_PHONE_LLM` from diagnostic configuration into normal runtime provider selection with readiness/lifecycle/failure handling and rerun the focused S22 regression gate.

### Gate 3L-D — controlled automated local-speech cellular call

Status: `NEXT`

Connect the already-proven frozen telephony RX/TX bridge to the proven local pipeline:

```text
telephony RX
  -> local STT
  -> selected text backend (prefer LOCAL_PHONE_LLM)
  -> application approval
  -> local TTS
  -> telephony TX
```

Project policy now permits the test runner to establish and terminate its own cellular call on the dedicated test SIM when the destination is explicitly operator-defined and allowlisted.

Guardrails:

- exact direct-USB target;
- one active call at a time;
- destination selected from operator-owned allowlist, never from model/tool output;
- bounded retries/cooldown and bounded call duration;
- no bulk dialing, number enumeration, emergency destinations, premium-rate destinations or arbitrary short codes;
- runner may hang up the allowlisted call it created for bounded cleanup;
- model cannot change the dial target or grant itself dialing authority;
- preserve frozen Samsung route, TAKE OVER, endpoint-loss and cleanup invariants;
- no service commitment/account change without existing user-decision + commitment authorization.

Initial live target: an explicitly allowlisted Orange customer-service/infoline number used only for controlled validation. Measure RX transcript, model response, end-to-end latency, TX audibility, interruption/TAKE OVER and cleanup.

### OpenAI Realtime Audio branch

Status: `FROZEN / PRESERVED`

The existing OpenAI Realtime implementation, credential broker, off-call smoke and live-call path remain in the repository as a selectable alternative. Do not delete or destructively refactor them while developing the local path.

A standard OpenAI API key remains host/backend-only and is never placed on Android.

### Local Realtime Audio branch

Status: `LATER`

Future third audio engine where telephony PCM is handled by a local audio-capable model/server rather than Android STT/TTS + text LLM. It must reuse the same app-owned authority, output and TAKE OVER boundaries.

## Phase 4 — product UX

Status: `INCREMENTAL`

Runtime selectors already exist. Add only UX required by proven engines: provider readiness, concise diagnostics, prominent TAKE OVER, user-decision surface and structured outcomes. Do not replace the default dialer without a concrete requirement.

## Phase 5 — robustness matrix

Status: `LATER`

After a complete selected engine works in a real cellular call, validate longer calls, screen/background behavior, endpoint/model/network failure, route/Bluetooth changes, Wi-Fi Calling, incoming/outgoing variants and repeated sessions. Exit condition: failures have defined fail-safe behavior and never leave AI injection active.

## Current decision

Finish `LOCAL_PHONE_LLM` as a normal runtime provider, then run the first controlled automated cellular validation against an explicitly allowlisted Orange customer-service destination on the dedicated test SIM. Preserve frozen Samsung media behavior and the OpenAI Realtime alternative.
