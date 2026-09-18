# Phase 2D freeze — 2026-09-18

## Verdict

Phase 2D is `DONE / PROVEN_S22` on Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

Frozen commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

This commit is preserved in `main` history; a permanent milestone branch is not required.

## Physical evidence

Validated on the target device:

- normal-app death while media active -> app/helper cleanup while cellular call remained available;
- helper/UserService death -> injection path stopped;
- RX or TX transferred-PFD loss -> whole generation cleanup;
- 20/20 start/abort cycles with clean final state;
- natural cellular call end -> helper-side `CallModeWatchdog` cleanup;
- 10 x 60-second live bidirectional sessions = 600 seconds total RX+TX media;
- separate 50.1-second resource telemetry with stable PIDs/FD counts and no thread-growth signal.

Important evidence identifiers remain in Git/Local Agent history, including:

```text
phase2-milestone-d-app-death-physical-v5-20260917-2301
phase2-milestone-d-endpoint-close-physical-v2-20260917-2330
phase2-natural-call-end-watchdog-physical-20260918-0108
phase2-milestone-d-10min-segmented-live-soak-v2-20260918-0125
```

The resource run complements the 600-second media soak; it is not a claim of 600 seconds of RSS/FD/thread telemetry.

## Frozen invariants

Do not casually change:

- direct-shell RX prepare-before-explicit-context ordering;
- separately proven Shizuku attributed-context ordering;
- RX system attribution;
- TX `com.android.shell` attribution;
- `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT`;
- TELEPHONY_TX route;
- internal mono PCM16LE;
- stereo duplication only at the Samsung TX boundary;
- PFD AutoClose ownership;
- one shared RX+TX fail-safe generation;
- endpoint loss -> sibling/whole-generation cleanup;
- no per-frame Binder PCM transport;
- local immediate TAKE OVER;
- heartbeat and `CallModeWatchdog`.

Narrow lint annotations around this path document static-analysis limits; they are not permission/security bypasses. Changing the target-specific hidden/private audio path requires targeted physical regression evidence.

## Regression rule

Do not rerun the full Phase 2D physical matrix during ordinary Phase 3 work. Rerun only the smallest relevant physical gate when a change could plausibly affect one of the frozen invariants.
