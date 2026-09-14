# Privileged Helper

This directory is the isolation boundary for operations that a normal third-party Android process cannot perform.

For the current roadmap, the **primary no-root research path is Shizuku UserService / ADB shell capability**. This is no longer treated only as a late fallback because call-specific capture requires privileges unavailable to an ordinary APK.

## Responsibilities

The helper may eventually own:
- device capability probes that require shell context;
- direct cellular downlink capture;
- generic telephony-output injection experiments;
- Samsung-specific injection research if generic routing fails;
- PCM pipe/file-descriptor creation;
- immediate local injection abort;
- helper watchdog and controller-death cleanup.

The normal application remains responsible for UI, orchestration and realtime networking.

## Control plane versus media plane

Use Binder/AIDL for:
- start/stop/probe commands;
- status/error reporting;
- passing `ParcelFileDescriptor` handles;
- small capability structures.

Do **not** send every PCM frame as an AIDL/Binder call.

Continuous media should use:
- a kernel pipe through `ParcelFileDescriptor` for the first implementation;
- or a local socket/shared-buffer design later if measurements justify it.

## Initial implementation order

1. `DeviceCapabilityProbe` in shell context.
2. Downlink-only capture experiment (`VOICE_DOWNLINK` / scrcpy-style direct source).
3. Generic uplink injection experiment (`AudioTrack` -> `TYPE_TELEPHONY`).
4. Local hard abort/cleanup semantics.
5. Samsung-specific backend only if generic injection fails.

Do not implement OpenAI networking in this module.

## Required documentation for every backend

Record:
- exact Android/Samsung build;
- effective UID;
- privilege mechanism: Shizuku/ADB shell, root, system app, etc.;
- permissions/capabilities actually used;
- source/output device identifiers;
- setup and teardown behavior;
- physical two-phone result;
- route-specific limitations;
- process-death behavior;
- whether an OTA is likely to break the mechanism.

A successful API call is not enough. Audio backends become `PROVEN_S22` only after the physical tests in `docs/POC_AUDIO_TEST_PLAN.md`.

## Safety requirements

Injection code must be fail-safe:
- controller death or heartbeat timeout stops injection;
- queued PCM is discarded during `abortNow`/takeover;
- resources are released even when initialization fails halfway;
- helper must never continue injecting indefinitely after the normal app dies;
- no arbitrary shell-command API is exposed to the realtime model or ordinary UI.

## Licensing

External projects are research references, not code donors by default.

- scrcpy is Apache-2.0 and may be reused only with the required notices/attribution.
- ShizuCallRecorder is GPL-3.0-or-later with additional terms; do not copy its implementation into this project unless that licensing decision is made deliberately.
- Basic Call Player is a reference for telephony output behavior; check its license before any implementation reuse.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/DEVICE_CAPABILITY_PROBE.md` before adding code here.