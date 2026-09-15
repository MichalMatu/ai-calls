# Phase 1C Samsung Cellular Uplink Plan

**Goal:** Determine whether the exact Galaxy S22+ build can open Samsung's dedicated in-call uplink path and inject bounded PCM digitally into `AUDIO_DEVICE_OUT_TELEPHONY_TX` after the generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` constructors failed during a real carrier call.

**Evidence driving this plan:**

- Live `VOICE_DOWNLINK` digital capture is already proven on the target S22+.
- Generic live-call `AudioTrack` construction fails with `UnsupportedOperationException: Cannot create AudioTrack`.
- Samsung audio policy exposes `incall_music_uplink` with `AUDIO_OUTPUT_FLAG_INCALL_MUSIC` and routes it exclusively to `Telephony Tx`.
- `incall_music_uplink` accepts PCM16 stereo at 8 kHz, 16 kHz, or 48 kHz.
- Android/Samsung report `USAGE_CALL_ASSISTANT` as a supported system usage and route the corresponding strategy to `AUDIO_DEVICE_OUT_TELEPHONY_TX`.
- AOSP defines `USAGE_CALL_ASSISTANT = 17`; `AudioAttributes.Builder.setSystemUsage(17)` requires `MODIFY_PHONE_STATE` and `MODIFY_AUDIO_ROUTING`, both already granted to shell UID 2000 on this device.

## Constraints

- Keep generic failure evidence intact; do not silently replace `inject-tone` behavior.
- Add a separate explicit probe mode for the Samsung/system-usage candidate.
- No speaker fallback. If routing is not telephony TX, do not write PCM.
- Use deterministic bounded tone fixtures only; no TTS/AI yet.
- Keep calls short and always force hangup from a fail-safe path.
- Do not commit call audio or sensitive telephony identifiers.

## Task 1 — Add explicit CALL_ASSISTANT probe contract

- Add mode `inject-call-assistant-tone <durationMs> <frequencyHz> <amplitude>`.
- Write parser RED test first.
- Reuse `ToneGenerator`; do not duplicate PCM generation.
- Preserve existing `inject-tone` as the generic `USAGE_MEDIA` result.

## Task 2 — Build hidden/System API attributes safely

- Construct `AudioAttributes.Builder` normally.
- Reflect `setSystemUsage(int)` and invoke with `17` (`USAGE_CALL_ASSISTANT`).
- Report separately whether reflection lookup, invocation, and resulting attributes succeed.
- If hidden API access is rejected, stop and record the exact exception; do not disguise it as an AudioTrack failure.

## Task 3 — Match Samsung's uplink profile

Candidate order:

1. PCM16 / 16 kHz / stereo / `USAGE_CALL_ASSISTANT`.
2. Only if construction fails for a format-related reason, PCM16 / 48 kHz / stereo.

For each candidate report:

- sample rate / channels / encoding;
- `AudioTrack` construction and state;
- preferred telephony device request;
- actual routed device before writing;
- write count;
- playback-head movement;
- exact exception/failure stage.

Never write tone unless actual route resolves to `TYPE_TELEPHONY`.

## Task 4 — Off-call construction control

Run the new mode while IDLE first. This is only a control; success or failure off-call is not the final media verdict.

## Task 5 — Live-call construction/routing test

If the off-call probe is safe and the build is green:

- autonomously dial the existing Orange test IVR;
- wait for `mCallState=2`;
- run one 400 ms / 1 kHz / 5% CALL_ASSISTANT probe;
- always hang up;
- classify construction/routing separately from remote audibility.

If `AudioTrack` initializes and routes to Telephony Tx, do not yet claim remote injection is proven. Physical remote-party confirmation remains the final Phase 1B/1C gate.

## Task 6 — If CALL_ASSISTANT still cannot open

Do not repeat equivalent Java constructors. Investigate the lower-level `AUDIO_OUTPUT_FLAG_INCALL_MUSIC` selection path and whether shell/System API can request the `AUDIO_STREAM_CALL_ASSISTANT` / AudioSystem output directly. Keep any native/vendor-specific experiment isolated from generic app code.

## Exit criteria

One of:

- `CALL_ASSISTANT_CONSTRUCTION_ROUTING_PROVEN`: track initializes and actual route is Telephony Tx; proceed to remote audibility proof.
- `CALL_ASSISTANT_API_BLOCKED`: hidden/System API cannot be invoked at shell boundary; move to lower-level AudioSystem/vendor path.
- `CALL_ASSISTANT_TRACK_FAILED`: attributes are accepted but AudioTrack still cannot open; capture logs/policy decision and move to lower-level `INCALL_MUSIC` investigation.
