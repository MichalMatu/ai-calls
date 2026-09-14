# Device Capability Probe

## Purpose

Before implementing any real capture or injection backend, collect a repeatable capability report from the exact target device. The probe is diagnostic only. It must not start an AI session and should not persist call audio.

Target: Samsung Galaxy S22+ on stock firmware.

The first real target-device baseline was collected on 2026-09-14 and is stored in `S22_BASELINE_2026-09-14.md`.

## Why this exists

Android audio behavior is not uniform across OEMs. A capability that exists on Pixel, AOSP, another Samsung model, or a third-party project is not enough to mark the S22+ path as working.

The probe answers two questions before we spend time on implementation:

1. Can a shell/Shizuku-class process access the inputs needed for call downlink capture?
2. Does this Samsung expose any usable telephony output path for digital uplink injection?

## Evidence rule

Keep two levels distinct:

- **capability proof** — the exact S22+ exposes a device/permission/source and the operation can be initialized;
- **media-direction proof** — a physical two-phone call confirms the expected audio reaches/leaves the modem path.

A constructor or permission result can become `PROVEN_S22` for that narrow capability fact. It cannot by itself make cellular RX or TX `PROVEN_S22`.

## Report format

Every run should produce a small text/JSON report containing values and explicit pass/fail/error states. Do not report only booleans; preserve the failure reason when available.

Suggested top-level groups:

```text
device
runtime
privileges
call_state
audio_devices
audio_sources
telephony_output
samsung_observations
```

## Device metadata

Collect:
- manufacturer;
- model and product;
- Android SDK/version;
- One UI version if available through normal system properties;
- build fingerprint;
- security patch level;
- app version and git SHA.

The report must make it possible to reproduce a result after a firmware update.

### 2026-09-14 result

Recorded target tuple:

```text
manufacturer=samsung
model=SM-S906B
device=g0s
product=g0sxeea
android_release=16
sdk=36
one_ui=8.0 (system property 80000)
build_display=BP2A.250605.031.A3.S906BXXSOGZH3
build_fingerprint=samsung/g0sxeea/g0s:16/BP2A.250605.031.A3/S906BXXSOGZH3:user/release-keys
```

Status: baseline recorded.

## Runtime and privilege metadata

For both the normal app and privileged helper record:
- UID;
- process name;
- whether Shizuku is installed/running;
- whether the app is authorized to use Shizuku;
- whether the helper actually executes as shell/root/other UID;
- checks for relevant permissions/capabilities, including whether operations fail with `SecurityException`.

Do not assume that Shizuku implies every shell permission is usable by every API.

### 2026-09-14 result

Direct ADB shell / `app_process` probe executed as:

```text
uid=2000(shell)
SELinux context=u:r:shell:s0
```

Shell permission checks returned granted for:

```text
android.permission.CAPTURE_AUDIO_OUTPUT
android.permission.MODIFY_AUDIO_ROUTING
android.permission.MODIFY_PHONE_STATE
```

This proves the shell privilege facts on this build. It does not yet prove a Shizuku UserService integration, because the successful probe intentionally used direct ADB shell to isolate platform capability from Shizuku onboarding/configuration.

## Audio device inventory

During an active cellular call collect `AudioManager` input and output devices with:
- device ID;
- type;
- product name if available;
- address when non-sensitive/useful;
- source/sink role;
- active communication device if exposed.

The key result for generic injection is whether an output device with `AudioDeviceInfo.TYPE_TELEPHONY` exists.

Presence alone is not a pass. It only enables Phase 1B testing.

### 2026-09-14 off-call result

The exact S22+ exposes:

```text
TYPE_TELEPHONY sink   id=11
TYPE_TELEPHONY source id=17
```

Status: target-device capability fact proven.

## Audio source probes

From the privileged helper/shell, test initialization of candidate capture sources independently. At minimum:
- `VOICE_DOWNLINK`;
- `VOICE_UPLINK`;
- `VOICE_CALL`;
- `VOICE_COMMUNICATION` as a control/comparison source.

For each candidate report:
- create success/failure;
- start success/failure;
- actual sample rate/channel/encoding if available;
- bytes/frames observed over a short interval;
- thrown exception and message;
- whether an active cellular call was present.

A successful constructor is not proof of useful call audio. The physical two-phone test in `POC_AUDIO_TEST_PLAN.md` remains the proof gate.

### 2026-09-14 result

Normal app:

```text
MIC                 initialized
VOICE_COMMUNICATION initialized
VOICE_RECOGNITION   initialized
VOICE_CALL          Cannot create AudioRecord
VOICE_DOWNLINK      Cannot create AudioRecord
VOICE_UPLINK        Cannot create AudioRecord
```

Shell UID 2000, with no active cellular call:

```text
VOICE_CALL     state=INITIALIZED
VOICE_DOWNLINK state=INITIALIZED
VOICE_UPLINK   state=INITIALIZED
```

Interpretation: the shell privilege class can construct the protected sources on this S22+. Actual in-call start/read/useful PCM is still not tested.

## Telephony output probe

If `TYPE_TELEPHONY` exists:
- create a short-lived `AudioTrack`;
- first test `AudioAttributes.USAGE_MEDIA`, because an external physically-qualified implementation (AgentCall) reports that route on another privileged Android device;
- retain `USAGE_VOICE_COMMUNICATION` as a comparison route based on BCP-style precedent;
- request the telephony device using `setPreferredDevice()`;
- record whether construction/routing succeeds;
- inspect the actual routed device if available;
- do not stream arbitrary remote/model audio.

The first audible injection test belongs to Phase 1B and must use a deterministic locally generated sample.

### 2026-09-14 off-call result

With **no active cellular call**:

```text
USAGE_MEDIA              -> UnsupportedOperationException: Cannot create AudioTrack
USAGE_VOICE_COMMUNICATION -> UnsupportedOperationException: Cannot create AudioTrack
```

Interpretation: unresolved. Do not classify generic injection as failed until the same experiment is repeated while a real carrier call is active.

## Call-state metadata

Record enough state to distinguish different carrier paths:
- active/inactive call;
- incoming/outgoing when available through permitted APIs;
- earpiece/speaker/Bluetooth route;
- Wi-Fi Calling state if it can be determined reliably;
- microphone mute state;
- screen on/off state for reproducibility.

Do not require becoming the default dialer for the initial capability probe unless a concrete observation cannot be made otherwise.

The 2026-09-14 baseline was intentionally collected off-call. Live-call metadata remains pending until the dedicated test SIM is available.

## Samsung observations

The probe may inventory relevant Samsung packages/services and package metadata that are visible through supported package APIs. It must not treat package names, permissions found in firmware dumps, or the presence of Text Call as an API contract.

Useful observations include:
- Samsung InCallUI package presence/version;
- other visible telephony-related Samsung packages;
- device features exposed by `PackageManager`;
- non-invasive system property values useful for identifying firmware.

Private Binder/service research belongs to Phase 1C, only if generic injection fails during an active call.

## Safety rules

- no continuous call recording;
- no upload of audio;
- no API keys;
- no automatic outbound calls;
- no hidden Samsung calls during the first probe;
- time-bound every audio-source experiment;
- close every `AudioRecord`, `AudioTrack`, pipe and helper session on failure;
- never store SIM/phone number/device identifiers in public evidence beyond non-sensitive build/model facts.

## Current Phase 0.5 summary

```text
exact target build recorded       PASS
shell UID 2000                     PASS
CAPTURE_AUDIO_OUTPUT               PASS (shell permission fact)
MODIFY_AUDIO_ROUTING               PASS (shell permission fact)
MODIFY_PHONE_STATE                 PASS (shell permission fact)
VOICE_DOWNLINK initializes         PASS (off-call capability)
VOICE_UPLINK initializes           PASS (off-call capability)
VOICE_CALL initializes             PASS (off-call capability)
TYPE_TELEPHONY source present      YES
TYPE_TELEPHONY sink present        YES
in-call VOICE_DOWNLINK data        NOT TESTED
in-call telephony AudioTrack       NOT TESTED
audible remote TX injection        NOT TESTED
Shizuku UserService parity         NOT YET PROVEN
```

Detailed evidence: `S22_BASELINE_2026-09-14.md`.

## Resume point

When the dedicated SIM is available, do not repeat the off-call inventory unless firmware changed. Resume from:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`

The next proof is bounded active-call `VOICE_DOWNLINK` capture, followed by deterministic in-call telephony TX injection.