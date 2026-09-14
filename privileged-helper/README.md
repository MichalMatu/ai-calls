# Privileged Helper

This directory is the isolation boundary for operations that a normal third-party Android process cannot perform.

For the current roadmap, the **primary no-root research path is Shizuku UserService / ADB shell capability**. This is no longer treated only as a late fallback because call-specific capture requires privileges unavailable to an ordinary APK.

The first direct-shell capability probe on the target Galaxy S22+ is documented in `docs/S22_BASELINE_2026-09-14.md`.

## Current target evidence

On the exact `SM-S906B` / Android 16 / API 36 / One UI 8.0 build tested on 2026-09-14:

- direct ADB shell executes as UID 2000;
- shell has `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE`;
- shell can create initialized `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK` `AudioRecord` instances off-call;
- firmware exposes both `TYPE_TELEPHONY` sink and source devices;
- off-call `AudioTrack` creation targeting telephony failed for both `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION`.

Do not interpret the off-call TX result as an in-call failure. The next proof requires a real cellular call.

Shizuku UserService parity is not yet proven; direct ADB shell was intentionally used first to isolate platform capability from Shizuku onboarding/configuration.

## Responsibilities

The helper may eventually own:
- device capability probes that require shell context;
- direct cellular downlink capture;
- generic telephony-output injection experiments;
- Samsung-specific injection research if generic routing fails during an active call;
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

1. Preserve/revalidate the existing target baseline after firmware changes.
2. Active-call downlink-only capture experiment (`VOICE_DOWNLINK` / scrcpy-style direct source).
3. Generic active-call uplink injection experiment:
   - first `AudioTrack` with `USAGE_MEDIA` -> `TYPE_TELEPHONY`;
   - then `USAGE_VOICE_COMMUNICATION` only as a separately recorded comparison if needed.
4. Local hard abort/cleanup semantics.
5. Shizuku UserService integration after the raw shell path is understood.
6. Samsung-specific backend only if generic injection fails reproducibly while in-call.

Do not implement OpenAI networking in this module.

The executable next-step plan is:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`

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

A successful API call is not enough. Media backends become `PROVEN_S22` only after the physical tests in `docs/POC_AUDIO_TEST_PLAN.md`.

## Safety requirements

Injection code must be fail-safe:
- controller death or heartbeat timeout stops injection;
- queued PCM is discarded during `abortNow`/takeover;
- resources are released even when initialization fails halfway;
- helper must never continue injecting indefinitely after the normal app dies;
- no arbitrary shell-command API is exposed to the realtime model or ordinary UI;
- test playback is bounded and deterministic;
- no automatic call placement is part of the audio helper.

## Licensing

External projects are research references, not code donors by default.

- scrcpy is Apache-2.0 and may be reused only with the required notices/attribution.
- ShizuCallRecorder is GPL-3.0-or-later with additional terms; do not copy its implementation into this project unless that licensing decision is made deliberately.
- AgentCall is AGPL-3.0; its qualified `USAGE_MEDIA -> TYPE_TELEPHONY` path is research evidence only and must not be copied into this repository by default.
- Basic Call Player is a reference for telephony output behavior; check its license before any implementation reuse.
- Superpowers is MIT and is used as a development methodology/plugin reference, not runtime code.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/DEVICE_CAPABILITY_PROBE.md`, and `docs/S22_BASELINE_2026-09-14.md` before adding code here.