# Phase 2 Local Cellular Bridge Plan

**Goal:** Convert the physically proven direct-shell cellular capture and injection mechanisms into a fail-safe local bidirectional PCM bridge, without AI networking.

## Proven inputs

- S22+ `VOICE_DOWNLINK` remote cellular PCM: `PROVEN_S22`.
- Generic `USAGE_MEDIA` / `USAGE_VOICE_COMMUNICATION` telephony TX: `FAILED_S22` for tested live-call constructors.
- Samsung `USAGE_CALL_ASSISTANT -> incall_music_uplink -> TELEPHONY_TX`: `PROVEN_S22`.
- Direct shell UID 2000 privilege boundary: proven.
- Shizuku UserService parity: not yet proven.

## Invariants

1. Hidden/system Samsung audio behavior stays inside `privileged-helper`.
2. Binder/AIDL is control-plane only.
3. Continuous PCM uses `ParcelFileDescriptor` pipe first.
4. No per-frame Binder transactions.
5. Internal PCM is signed PCM16 little-endian, mono, with explicit sample rate.
6. Samsung TX duplicates mono to stereo only at the backend boundary.
7. `abortNow()` discards queued output immediately and never waits on network activity.
8. Controller death/watchdog failure stops capture/injection and releases resources.
9. The helper never places calls and contains no OpenAI networking.
10. Existing bounded CLI probes remain physical-regression tools.

## Milestone A — reusable Samsung TX primitive

Extract the proven CALL_ASSISTANT track lifecycle into a dedicated helper/backend class.

Acceptance:
- accepts 16 kHz and 48 kHz PCM16 input;
- route is confirmed as `TYPE_TELEPHONY` before caller PCM is written;
- mono PCM16LE is converted to Samsung's stereo INCALL_MUSIC profile;
- repeated writes avoid avoidable per-frame object churn where practical;
- graceful stop releases resources;
- `abortNow()` flushes/discards queued audio and releases immediately;
- existing tone probe remains a regression harness rather than a second implementation.

## Milestone B — privileged media pipes

Expose control methods that hand out pipe endpoints:
- downlink: helper writes mono PCM16, app reads;
- uplink: app writes mono PCM16, helper reads and injects.

Use bounded worker threads and deterministic EOF/close semantics. EOF on the TX input must stop injection.

## Milestone C — Shizuku UserService parity

Run the proven guards under UserService:
- effective UID and attribution context;
- `CAPTURE_AUDIO_OUTPUT`;
- `MODIFY_AUDIO_ROUTING`;
- `MODIFY_PHONE_STATE`;
- `VOICE_DOWNLINK` start/read;
- CALL_ASSISTANT track construction;
- actual `TYPE_TELEPHONY` route.

Compile/build success is not evidence of privilege parity.

## Milestone D — local bridge endurance

Before any realtime model:
- start both media directions;
- move deterministic PCM through the same pipe boundaries intended for production;
- exercise repeated stop/start;
- exercise `abortNow()` while TX data is queued;
- kill/disconnect the controller and verify helper cleanup;
- measure underrun/overrun, memory and latency behavior.

Exit gate: 10-minute stable local bridge with bounded resources and immediate human takeover semantics.
