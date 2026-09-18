# Handoff — local speech production path

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here

Read only what is relevant:

1. `AGENTS.md`
2. this file
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/SECURITY_PRIVACY.md` when touching model/network/credentials
6. `docs/PHASE2D_FREEZE_2026-09-18.md` before changing Samsung media internals.

Then verify `main` HEAD and `.agent/status/daemon.json`. In a new chat bootstrap the repository Local Agent and use that chat's fresh binding; never copy an old chat binding.

## Product direction

The project is no longer Realtime-only. Runtime selection is now:

```text
Audio mode
├── LOCAL_STT_TTS
│   └── text LLM provider: OPENAI_TEXT | LOCAL_MAC_LLM
├── OPENAI_REALTIME_AUDIO   (preserved/frozen)
└── LOCAL_REALTIME_AUDIO    (future)
```

MCP belongs to the tools/context layer, not the LLM-provider selector.

The current priority is `LOCAL_STT_TTS`. Do not resume the OpenAI Realtime credential gate unless explicitly requested.

## Current durable checkpoints

Runtime selectors:

```text
1eb81f1202a8a0b4aace6231c73656ef14920c3d
feat: add selectable speech and llm modes
```

Local speech capability probe:

```text
6a1ba494ddc2319ef9fe8847f88f4e7816b2a7b0
feat: add local speech capability probe
```

Local speech PCM/PFD proof foundation:

```text
ef65f99420aaa00bdcfa3be3faeca210ba17c4ae
feat: prove local speech PFD loopback
```

Pipe-stream fix and successful physical proof:

```text
386031a1f9bf891970e4cf6af8a3ec148a65aa7a
fix: stream local STT input through PFD pipe
```

Phase 2D frozen Samsung media remains at:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
PROVEN_S22
```

## New PROVEN_S22 local speech evidence

On exact S22+ serial `RFCT70L7E8J`, Android 16 / API 36:

- on-device `SpeechRecognizer` is available;
- `pl-PL` model was downloaded and reports installed;
- local Polish TTS is available with multiple non-network-required voices;
- local TTS synthesis succeeds;
- TTS WAV output was decoded and resampled to PCM16LE mono 16 kHz;
- PCM16LE mono 16 kHz streamed through `ParcelFileDescriptor.createPipe()` into the on-device recognizer;
- recognized result matched the known test phrase: `to jest test lokalnego rozpoznawania mowy`;
- `loopback_success=true`;
- cellular call state remained idle before and after.

The successful task evidence is on `agent-control` in:

```text
.agent/results/local-speech-pfd-pipe-s22-20260918-3490.json
```

Do not repeat this physical proof without a regression reason; reuse it as the foundation for production adapters.

## Immediate next gate

Implement production-owned local speech adapters without live-call wiring:

1. streaming PCM16LE mono 16 kHz -> Android on-device STT via pipe PFD;
2. local TTS text -> PCM16LE mono 16 kHz;
3. explicit cancellation/generation ownership and bounded cleanup;
4. deterministic host tests around PCM and lifecycle where possible;
5. `bash scripts/verify_host.sh`.

Keep the diagnostic probe as evidence/test-only. Product code must not call the probe as its runtime speech engine.

## Gate after production speech adapters

Introduce a provider-neutral text-agent boundary above speech. Reuse application-owned task/workflow/confirmation/commitment rules rather than duplicating them per provider.

Target text providers:

```text
OPENAI_TEXT
LOCAL_MAC_LLM
```

The first integration can use a deterministic fake backend to prove lifecycle/output approval off-call before connecting a real model.

## Later controlled live-call gate

Only after local speech + selected text backend work off-call, connect them to the already-proven frozen telephony media generation during a user-established call. The test runner must not dial or hang up. Validate RX -> STT -> text agent -> TTS -> TX, TAKE OVER, latency and cleanup.

## Frozen OpenAI Realtime option

The existing Realtime stack, host credential broker, Quick Tunnel lab and controlled live-call smoke stay intact as a selectable alternative. A standard `OPENAI_API_KEY` remains host/backend-only. Do not place it in source, APK, Intent, ADB arguments or Android storage.

If the user explicitly resumes the Realtime path, continue from the existing off-call genuine-session gate; otherwise leave it frozen.
