# Android AI Call Bridge

Experimental Android project for testing whether a single Android phone can bridge a normal cellular call to a realtime AI voice session without external hardware.

## Target outcome

```text
remote caller -> cellular downlink -> app PCM -> realtime AI
realtime AI -> app PCM -> cellular uplink -> remote caller
```

The initial hardware target is a Samsung Galaxy S22+ on stock Samsung firmware. The user must always be able to take over the call immediately.

## Current technical position

The project no longer treats cellular capture and injection as one unknown problem.

There are existing precedents for both halves:

- scrcpy exposes direct voice-call, uplink and downlink audio sources when run with sufficient shell/system capability;
- ShizuCallRecorder demonstrates a non-root Shizuku/shell path for recording carrier calls on modern Android versions;
- Basic Call Player demonstrates injection through `AudioTrack` routed to `TYPE_TELEPHONY`, but that output device is not implemented consistently across OEMs.

This means **downlink capture is the lower-risk half**. **Uplink injection on the target Samsung remains the critical project gate.** Samsung features such as Text Call are evidence that Samsung's own stack can perform software-to-call audio bridging, but they are not proof that a third-party application can access the same path.

## Development rule

Do not build the AI layer first.

The order is now:

1. probe the exact S22+ capabilities;
2. prove digital remote-call audio capture;
3. prove deterministic digital audio injection to the remote caller;
4. stabilize a local full-duplex bridge with fail-safe takeover;
5. only then connect a realtime model;
6. only after that build polished call UX.

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the go/no-go gates.

## Architecture

- `app/` — normal Android process, UI, orchestration and diagnostics.
- `audio-bridge/` — device-independent capture/injection contracts and PCM models.
- `privileged-helper/` — Shizuku/shell or other privileged experiments. Privileged Android/Samsung internals stay isolated here.
- `realtime-client/` — realtime model transport abstraction, deliberately independent of telephony access.
- `docs/` — evidence, roadmap, architecture and repeatable device tests.

The intended privileged media shape is:

```text
normal app <--- control via Binder/AIDL ---> privileged helper
normal app <====== PCM pipe/socket =======> privileged helper
```

Do not send every audio frame as a separate Binder transaction.

## Current milestone

**Phase 0.5 — Device Capability Probe**

Before implementing a production audio backend, collect a repeatable report from the S22+ covering:

- Android / One UI / build fingerprint;
- Shizuku and shell capability;
- relevant permissions;
- input/output audio devices;
- presence of `TYPE_TELEPHONY`;
- audio source initialization behavior during a live cellular call;
- routing state and call metadata needed for later tests.

See [`docs/DEVICE_CAPABILITY_PROBE.md`](docs/DEVICE_CAPABILITY_PROBE.md).

## Hard rules

- A Samsung feature or third-party project is evidence, not proof for our S22+.
- A backend becomes `PROVEN_S22` only after a physical two-phone test.
- No long-lived OpenAI API key in the APK.
- No recording by default.
- `Take over` must fail locally and immediately toward normal human call behavior.
- If the app or network dies, AI injection must not remain active.
- Do not replace the default dialer until the media bridge works.

## Documents

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phase order, gates and fallback order.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — component boundaries and fail-safe design.
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — implementation sequence.
- [`docs/DEVICE_CAPABILITY_PROBE.md`](docs/DEVICE_CAPABILITY_PROBE.md) — first S22+ diagnostic task.
- [`docs/POC_AUDIO_TEST_PLAN.md`](docs/POC_AUDIO_TEST_PLAN.md) — physical capture/injection validation.
- [`docs/RESEARCH_NOTES.md`](docs/RESEARCH_NOTES.md) — confirmed facts versus unresolved hypotheses.
- [`docs/SECURITY_PRIVACY.md`](docs/SECURITY_PRIVACY.md) — privilege, credential and takeover rules.

## License

No project-wide license has been selected yet. Do not copy GPL-licensed implementation code into this repository. External projects may be used as research references; any code reuse must be evaluated against its license first.