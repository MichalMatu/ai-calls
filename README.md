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
- Basic Call Player demonstrates injection through `AudioTrack` routed to `TYPE_TELEPHONY`, but that output device is not implemented consistently across OEMs;
- AgentCall demonstrates independently qualified digital telephony RX/TX on another privileged/rooted Android device, including a `USAGE_MEDIA -> TYPE_TELEPHONY` TX route. It is research evidence only, not S22+ proof.

The physical `SM-S906B` running Android 16 / API 36 / One UI 8.0 has now crossed the first real live-call boundary:

- stock firmware exposes both `TYPE_TELEPHONY` sink and source devices;
- ADB shell executes as UID 2000 and has `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE`;
- shell can create initialized `AudioRecord` instances for `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK`;
- during a real carrier call, bounded `VOICE_DOWNLINK` capture returned 32,000 PCM16 samples over 2 seconds with strong non-silent signal (`RMS≈2339`, `peak=15370`) and zero read errors;
- the equivalent off-call control is near silence (`RMS≈1.85`, `peak=10`);
- during that same active call, both tested generic `AudioTrack -> TYPE_TELEPHONY` constructions still failed with `UnsupportedOperationException`;
- the normal application process cannot create the protected call sources, as expected.

See [`docs/S22_BASELINE_2026-09-14.md`](docs/S22_BASELINE_2026-09-14.md) for the capability baseline and [`docs/S22_LIVE_CALL_2026-09-15.md`](docs/S22_LIVE_CALL_2026-09-15.md) for the first live-call evidence.

This means **digital in-call downlink signal is now physically demonstrated on the S22+**, but the repository's full media-direction `PROVEN_S22` gate still requires source-direction/intelligibility confirmation from a retained bounded diagnostic sample. **The current generic telephony TX candidates failed even in-call**, so Samsung-specific Phase 1C injection research is now justified while the failure evidence is preserved.

## Development rule

Do not build the AI layer first.

The order is:

1. preserve/revalidate the exact S22+ capability baseline;
2. prove digital remote-call audio capture during a live cellular call;
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

**Phase 1 live-call validation is active. The dedicated SIM and Wireless ADB path are working on the target S22+.**

Already reproduced on the exact S22+ build:

- target firmware/build identity;
- shell UID 2000 privilege class;
- relevant shell permission grants;
- protected call-source initialization;
- `TYPE_TELEPHONY` RX/TX device presence;
- real in-call `VOICE_DOWNLINK` start/read with strong non-silent PCM;
- active-call failure of the two current generic `AudioTrack -> TYPE_TELEPHONY` construction candidates;
- repeatable bounded capture tooling and ADB-over-Wi-Fi call-control test tooling.

Still pending:

- confirm the live `VOICE_DOWNLINK` sample contains the remote IVR specifically and classify whether it is remote-only or mixed;
- physically validate DTMF navigation through the test helper during an active call;
- investigate Samsung-specific TX after the generic in-call construction failure;
- Shizuku UserService integration after the raw shell media path is settled.

The executable Phase 1 plan remains [`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`](docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md).

## Development methodology

The repository adopts the [`obra/superpowers`](https://github.com/obra/superpowers) agentic development methodology.

- project-specific agent instructions: [`AGENTS.md`](AGENTS.md)
- workflow adaptation: [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md)
- implementation plans: `docs/superpowers/plans/`

Superpowers is a development workflow/plugin, not an Android runtime dependency and is not shipped in the APK.

## Hard rules

- A Samsung feature or third-party project is evidence, not proof for our S22+.
- A media direction becomes `PROVEN_S22` only after the physical two-phone test.
- A successful constructor/permission/device enumeration is not equivalent to working call media.
- No long-lived OpenAI API key in the APK.
- No recording by default.
- `Take over` must fail locally and immediately toward normal human call behavior.
- If the app or network dies, AI injection must not remain active.
- Do not replace the default dialer until the media bridge works.
- Do not merge `.agent` control/result traffic into `main`.

## Documents

- [`docs/S22_BASELINE_2026-09-14.md`](docs/S22_BASELINE_2026-09-14.md) — exact physical-device capability baseline.
- [`docs/S22_LIVE_CALL_2026-09-15.md`](docs/S22_LIVE_CALL_2026-09-15.md) — first active-call RX/TX evidence and repeatable-test handoff.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phase order, gates and fallback order.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — component boundaries and fail-safe design.
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — implementation sequence.
- [`docs/DEVICE_CAPABILITY_PROBE.md`](docs/DEVICE_CAPABILITY_PROBE.md) — S22+ diagnostic contract and current baseline status.
- [`docs/POC_AUDIO_TEST_PLAN.md`](docs/POC_AUDIO_TEST_PLAN.md) — physical capture/injection validation.
- [`docs/RESEARCH_NOTES.md`](docs/RESEARCH_NOTES.md) — confirmed facts versus unresolved hypotheses.
- [`docs/SECURITY_PRIVACY.md`](docs/SECURITY_PRIVACY.md) — privilege, credential and takeover rules.
- [`docs/DEVELOPMENT_WORKFLOW.md`](docs/DEVELOPMENT_WORKFLOW.md) — Superpowers-based engineering workflow.

## License

No project-wide license has been selected yet. Do not copy GPL/AGPL-licensed implementation code into this repository by default. External projects may be used as research references; any code reuse must be evaluated against its license first.
