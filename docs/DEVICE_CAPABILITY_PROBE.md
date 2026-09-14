# Device Capability Probe

## Purpose

Before implementing any real capture or injection backend, collect a repeatable capability report from the exact target device. The probe is diagnostic only. It must not start an AI session and should not persist call audio.

Target: Samsung Galaxy S22+ on stock firmware.

## Why this exists

Android audio behavior is not uniform across OEMs. A capability that exists on Pixel, AOSP, another Samsung model, or a third-party project is not enough to mark the S22+ path as working.

The probe answers two questions before we spend time on implementation:

1. Can a shell/Shizuku process access the inputs needed for call downlink capture?
2. Does this Samsung expose any usable telephony output path for digital uplink injection?

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

## Runtime and privilege metadata

For both the normal app and privileged helper record:
- UID;
- process name;
- whether Shizuku is installed/running;
- whether the app is authorized to use Shizuku;
- whether the helper actually executes as shell/root/other UID;
- checks for relevant permissions/capabilities, including whether operations fail with `SecurityException`.

Do not assume that Shizuku implies every shell permission is usable by every API.

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

## Audio source probes

From the privileged helper, test initialization of candidate capture sources independently. At minimum:
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

## Telephony output probe

If `TYPE_TELEPHONY` exists:
- create a short-lived `AudioTrack` with voice-communication usage;
- request the telephony device using `setPreferredDevice()`;
- record whether routing request succeeds;
- inspect the actual routed device if available;
- do not yet stream arbitrary remote/model audio.

The first audible injection test belongs to Phase 1B and must use a deterministic locally generated sample.

## Call-state metadata

Record enough state to distinguish different carrier paths:
- active/inactive call;
- incoming/outgoing when available through permitted APIs;
- earpiece/speaker/Bluetooth route;
- Wi-Fi Calling state if it can be determined reliably;
- microphone mute state;
- screen on/off state for reproducibility.

Do not require becoming the default dialer for the initial capability probe unless a concrete observation cannot be made otherwise.

## Samsung observations

The probe may inventory relevant Samsung packages/services and package metadata that are visible through supported package APIs. It must not treat package names, permissions found in firmware dumps, or the presence of Text Call as an API contract.

Useful observations include:
- Samsung InCallUI package presence/version;
- other visible telephony-related Samsung packages;
- device features exposed by `PackageManager`;
- non-invasive system property values useful for identifying firmware.

Private Binder/service research belongs to Phase 1C, only if generic injection fails.

## Safety rules

- no continuous call recording;
- no upload of audio;
- no API keys;
- no automatic outbound calls;
- no hidden Samsung calls during the first probe;
- time-bound every audio-source experiment;
- close every `AudioRecord`, `AudioTrack`, pipe and helper session on failure.

## Exit artifact

A successful Phase 0.5 run produces:

1. capability report;
2. logcat excerpt for failed privileged operations;
3. exact device/build metadata;
4. a short summary table:

```text
Shizuku helper as shell       PASS / FAIL
VOICE_DOWNLINK initializes    PASS / FAIL
VOICE_DOWNLINK produces data  PASS / FAIL / NOT PROVEN AUDIO
TYPE_TELEPHONY present        YES / NO
AudioTrack route request      PASS / FAIL / NOT TESTED
```

Only after this report is saved do we implement the Phase 1A capture backend and Phase 1B injection backend.