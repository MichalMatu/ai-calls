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

Status: `LOCAL SPEECH FOUNDATION PROVEN_S22 / OPENAI REALTIME AUDIO FROZEN`

The product direction is now selectable rather than Realtime-only.

### Runtime choices

```text
Audio mode
├── LOCAL_STT_TTS
│   └── text LLM provider: OPENAI_TEXT | LOCAL_MAC_LLM
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
- selectable runtime preferences for `LOCAL_STT_TTS`, `OPENAI_REALTIME_AUDIO`, `LOCAL_REALTIME_AUDIO` and text LLM provider `OPENAI_TEXT` / `LOCAL_MAC_LLM`.

### Local speech evidence on S22

Status: `PROVEN_S22`

Physically proven on the exact Samsung S22+:

- Android on-device speech recognition is available;
- `pl-PL` on-device recognition model is installed;
- local Polish TTS voices are available and synthesize successfully without network-required voices;
- TTS output can be decoded/resampled to the internal telephony format PCM16LE mono 16 kHz;
- caller-supplied PCM16LE mono 16 kHz can be streamed through a `ParcelFileDescriptor` pipe into on-device STT;
- the known Polish phrase `To jest test lokalnego rozpoznawania mowy.` round-trips through local TTS -> PCM16/16 kHz -> PFD pipe -> on-device STT with a matching transcript;
- the proof is off-call and leaves `CALL_STATE=0` before and after.

Relevant checkpoints:

```text
6a1ba494ddc2319ef9fe8847f88f4e7816b2a7b0
feat: add local speech capability probe

ef65f99420aaa00bdcfa3be3faeca210ba17c4ae
feat: prove local speech PFD loopback

386031a1f9bf891970e4cf6af8a3ec148a65aa7a
fix: stream local STT input through PFD pipe
```

### Gate 3L-A — production local speech adapters

Status: `NEXT`

Extract the proven probe mechanics into small production-owned components for:

- streaming PCM16LE mono 16 kHz into on-device STT;
- local TTS synthesis returning PCM16LE mono 16 kHz;
- cancellation/generation ownership and bounded cleanup;
- no telephony/live-call wiring yet.

Require TDD + `scripts/verify_host.sh`. Preserve the frozen Samsung media implementation unchanged.

### Gate 3L-B — off-call local conversation engine

Status: `PENDING 3L-A`

Connect the production local STT/TTS adapters to a provider-neutral text-agent boundary. First provider may be a deterministic/fake host-test backend to prove lifecycle and output approval before adding network/local LLM inference.

### Gate 3L-C — text LLM providers

Status: `PENDING 3L-B`

Implement provider selection behind one application-owned text-agent interface:

- `OPENAI_TEXT` — remote text model using a safe host/backend credential boundary;
- `LOCAL_MAC_LLM` — LAN/local server on the user's Mac, preferably through a narrow authenticated/OpenAI-compatible or equivalent endpoint.

Model output must not bypass proposal parsing, confirmation policy, one-shot commitment authorization or output approval.

### Gate 3L-D — first controlled local-speech cellular call

Status: `PENDING 3L-C`

Use the already-proven frozen telephony RX/TX bridge during a user-established cellular call. The runner must not dial or hang up. Validate real call RX -> local STT -> text agent -> local TTS -> TX, latency, interruption/TAKE OVER and cleanup.

### OpenAI Realtime Audio branch

Status: `FROZEN / PRESERVED`

The existing OpenAI Realtime implementation, credential broker, off-call smoke and controlled live-call runner remain in the repository as a selectable alternative. Do not delete or destructively refactor them while developing the local path.

Its previous gates remain available if explicitly resumed:

- genuine OpenAI off-call S22 smoke;
- controlled non-committing Realtime cellular call;
- real user-authorized Realtime task.

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

Develop `LOCAL_STT_TTS` first. The S22 local speech primitives and exact PCM/PFD bridge are already physically proven. The immediate continuation is production local speech adapters without live-call wiring, followed by a provider-neutral text-agent boundary and selectable `OPENAI_TEXT` / `LOCAL_MAC_LLM` backends. Preserve `OPENAI_REALTIME_AUDIO` as a frozen selectable alternative and keep `LOCAL_REALTIME_AUDIO` as the third future engine.
