# Milestone D remaining robustness gates — 2026-09-17

## Goal

Finish all code/tooling preparation that can be validated without the physical S22+, then leave only target-device execution and evidence collection for Milestone D.

Do not change the proven Samsung media backend semantics while preparing these diagnostics.

Preserve:

- direct-shell RX construction ordering;
- proven Shizuku attributed-context path;
- RX system attribution and TX `com.android.shell` attribution;
- `USAGE_CALL_ASSISTANT` / telephony TX route;
- internal mono PCM16LE with stereo duplication only at the Samsung TX boundary;
- one shared RX+TX fail-safe generation;
- PFD media transport and no per-frame Binder;
- local silent-phone guard for every live test.

## Baseline before this plan

Milestone C is physically proven and frozen. M1-M4 are fixed. Milestone D already has GREEN evidence for:

- explicit abort/takeover;
- helper/UserService process death;
- heartbeat timeout;
- 30-second bidirectional endurance.

The normal-app-death gate had one inconclusive run because host ADB entered `waiting for device`. A new phone-side observer harness in `scripts/s22_app_death_gate.py` now measures cleanup using device uptime and publishes an atomic result file; its host tests are GREEN, but the physical gate still needs the S22+.

## Task 1 — selective transferred-PFD close diagnostic

Purpose: prove the documented invariant that loss of either transferred RX or TX endpoint terminates the whole controller generation and its sibling.

Changes:

- extend `ShizukuBidirectionalProbeMedia` with deterministic one-side endpoint close operations while retaining one-owner final cleanup;
- add host tests proving one-side close affects only that stream immediately and final `close()` remains idempotent;
- add a protected diagnostic probe for RX-close and TX-close cases;
- add `DiagnosticProbeActivity` triggers only; do not expose these through the exported launcher.

Device exit gate for each direction:

- active session before endpoint close;
- baseline heartbeat true;
- selected endpoint intentionally closed;
- controller becomes inactive within a bounded interval;
- prepared/active/heartbeat all false afterwards;
- sibling is not left active;
- local probe workers close cleanly;
- phone remains physically silent.

## Task 2 — configurable long endurance diagnostic

Purpose: reuse the already-proven endurance path for the final 10-minute gate instead of creating a second media implementation.

Changes:

- keep 30 seconds as the default diagnostic duration;
- permit a protected, bounded duration override for the diagnostic activity;
- bound accepted duration to a narrow safe range ending at 10 minutes;
- unit-test duration validation;
- keep the media loop, heartbeat cadence and cleanup semantics unchanged.

Device final gate:

- 600000 ms requested;
- active throughout;
- heartbeat healthy throughout;
- RX/TX byte counts increase;
- final explicit abort clean;
- no orphan media/helper session.

## Task 3 — repeated start/abort cycle diagnostic

Purpose: exercise repeated prepare/start/PFD-transfer/abort on one bound UserService so resource accumulation can be observed instead of hiding leaks behind helper-process recreation.

Changes:

- add a protected `ShizukuCycleProbe`;
- bind UserService once;
- run a bounded 1-20 cycle count;
- in every cycle: prepare, start, take PFDs, run short bidirectional media, heartbeat, explicit abort, assert empty controller state, deterministically close app-side media;
- report completed cycles, cumulative RX/TX bytes, maximum abort RPC latency and first failed cycle;
- unit-test cycle-count validation.

Device gate:

- 20 cycles complete;
- every cycle has active media before abort and empty state after abort;
- host samples helper/app FD count, thread count and RSS while the same UserService stays bound;
- no upward unbounded trend and no orphan process/session at the end.

## Task 4 — call-end observation

Purpose: prove a real cellular call ending underneath an active bridge cannot leave CALL_ASSISTANT injection alive.

Prefer test tooling over product changes.

Approach:

- run an active protected live diagnostic that keeps heartbeats flowing;
- trigger cellular hangup externally after CALL_ASSISTANT is confirmed active;
- observe call state -> IDLE, media session -> inactive and CALL_ASSISTANT -> stopped;
- use phone-side timing where host ADB transport could distort latency;
- require final helper state to be empty and local app workers terminated.

Do not make call-state control part of the privileged media production API solely for this test.

## Task 5 — external resource telemetry helper

Prepare a host-side script that samples existing `/proc/<pid>` and `dumpsys` evidence during cycle/endurance runs without expanding the production Binder API.

Metrics:

- app/helper PID;
- `VmRSS`;
- FD count;
- thread count;
- CALL_ASSISTANT active/stopped state;
- call state;
- sample monotonic timestamp.

Output should be line-oriented/CSV or JSONL so start/end/peak/delta can be computed deterministically.

## Host verification after every implementation slice

```text
PYTHONPATH=scripts python3 -m unittest discover -s scripts -p 'test_*.py'
$HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle \
  :privileged-helper:testDebugUnitTest \
  :app:testDebugUnitTest \
  :audio-bridge:testDebugUnitTest \
  :realtime-client:testDebugUnitTest \
  :app:assembleDebug \
  --no-daemon --warning-mode all
git diff --check
```

Keep the worktree clean.

## Physical execution order when the S22+ returns

1. normal-app-death gate with the new phone-side observer;
2. RX transferred-PFD close gate;
3. TX transferred-PFD close gate;
4. call-end gate;
5. 20-cycle resource gate;
6. final 10-minute endurance with external telemetry;
7. full regression/audit;
8. freeze Milestone D;
9. only then begin Realtime AI integration.
