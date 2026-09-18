# Phase 2 Milestone D freeze — 2026-09-18

## Verdict

Milestone D is `DONE / PROVEN_S22` on the target Samsung Galaxy S22+ `SM-S906B` running Android 16 / API 36 / One UI 8 with Orange PL, Google Phone and Shizuku shell UID 2000.

This freeze preserves the proven Samsung media path. It does not refactor the low-level RX/TX primitives.

## Physical fail-safe evidence

GREEN on the target device:

- normal app death while media is active:
  - task `phase2-milestone-d-app-death-physical-v5-20260917-2301`;
  - app disappeared about 180 ms after force-stop;
  - helper disappeared about 340 ms;
  - media stopped about 530 ms;
  - cellular call and Shizuku server remained alive;
- transferred endpoint close:
  - task `phase2-milestone-d-endpoint-close-physical-v2-20260917-2330`;
  - RX PFD close -> whole generation inactive about 39 ms;
  - TX PFD close -> whole generation inactive about 13 ms;
- repeated lifecycle: 20/20 start/abort cycles with stable UserService PID and clean final prepared/active/heartbeat state;
- natural cellular call end:
  - the first gate exposed a real product defect: heartbeats could keep media active after the call ended;
  - fixed by helper-side `CallModeWatchdog`, watching loss of `AudioManager.MODE_IN_CALL`;
  - physical validation task `phase2-natural-call-end-watchdog-physical-20260918-0108` is GREEN;
- bidirectional endurance:
  - task `phase2-milestone-d-10min-segmented-live-soak-v2-20260918-0125`;
  - 10 independent 60-second live sessions;
  - 600 seconds total real bidirectional RX+TX;
  - all heartbeats/session-state checks GREEN and every segment cleaned up;
- resource trend:
  - one separate short live run preserved 24 external samples over 50.1 seconds while the call remained active;
  - app PID stable: RSS 123128 -> 124196 KiB, FD 41 -> 41, threads 27 -> 25;
  - helper PID stable: RSS 152932 -> 153948 KiB, FD 41 -> 41, threads 19 -> 19;
  - no resource-growth signal was observed.

The resource run complements the 600-second media soak. It is not a claim that RSS/FD/thread telemetry was preserved for the full 600 seconds.

## Final host regression

At the pre-freeze product HEAD:

- Python: 48/48 tests PASS;
- Gradle unit tests for `privileged-helper`, `app`, `audio-bridge` and `realtime-client`: GREEN;
- `:app:assembleDebug`: GREEN;
- Gradle result: `BUILD SUCCESSFUL`;
- `git diff --check`: GREEN;
- clean product tree: GREEN.

Security-shape checks:

- `.agent` absent from the product branch;
- `DiagnosticProbeActivity` protected by `android.permission.DUMP`;
- exported `MainActivity` does not expose privileged live-probe automation extras;
- no long-lived OpenAI API key pattern found in product modules;
- realtime contract uses a short-lived credential;
- no per-frame Binder PCM transport;
- local `abortNow()` / TAKE OVER and helper fail-safe remain independent of model/network availability;
- call recording remains off by default.

## Frozen invariants

Do not casually change:

- direct-shell RX prepare-before-explicit-context ordering;
- Shizuku attributed-context ordering;
- RX system attribution;
- TX `com.android.shell` attribution;
- `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT`;
- `AUDIO_DEVICE_OUT_TELEPHONY_TX`;
- internal mono PCM16LE;
- stereo duplication only at the Samsung TX boundary;
- PFD AutoClose ownership;
- one shared RX+TX fail-safe generation;
- endpoint loss -> whole-generation cleanup;
- no per-frame Binder transport;
- local immediate TAKE OVER;
- `CallModeWatchdog` unless concrete regression evidence requires a change.

## Next phase

Do not start another Phase 2 robustness expansion. The next product work is Telephone Agent v1, beginning with a production app-side `CallMediaSessionCoordinator`, then the user-level call task/workflow model, and only then OpenAI Realtime transport integration.
