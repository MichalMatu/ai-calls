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

## Host preparation status

All planned Milestone D host-side diagnostics/tooling are now implemented and host-validated on `work/phase1-live-call-probes`:

- selective transferred-PFD close probe for RX and TX;
- bounded endurance duration override, default 30 s and maximum 10 min;
- repeated start/abort probe, default 20 cycles, with one UserService bind for the whole run;
- external JSONL resource telemetry for app/helper RSS, FD count, thread count, call state and CALL_ASSISTANT state;
- app-side call-end probe that keeps heartbeats flowing while waiting for the real media path to terminate;
- phone-side call-end observer using `/proc/uptime`, telephony registry and CALL_ASSISTANT log evidence;
- resilient phone-side normal-app-death observer from the earlier slice.

Latest host regression after call-end tooling:

```text
focused call-end Java/Python tests: GREEN
all Python tests: 44 PASS
full Gradle host test/build + assembleDebug: GREEN
security-shape checks: GREEN
git diff --check + clean worktree: GREEN
```

No call-end preparation changed `privileged-helper`; the proven Samsung media backend remains untouched.

All remaining gates below are therefore **physical execution/evidence gates**, not missing host implementation.

## Task 1 — selective transferred-PFD close diagnostic

Purpose: prove the documented invariant that loss of either transferred RX or TX endpoint terminates the whole controller generation and its sibling.

Host preparation: `DONE / HOST GREEN`.

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

Host preparation: `DONE / HOST GREEN`.

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

Host preparation: `DONE / HOST GREEN`.

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

Host preparation: `DONE / HOST GREEN`.

Implemented approach:

- protected `ShizukuCallEndProbe` starts real bidirectional media and continues heartbeats while waiting for one media path to terminate;
- it then requires the controller to become empty and app-side media workers to stop;
- `scripts/s22_call_end_gate.py` observes `OFFHOOK -> IDLE`, CALL_ASSISTANT `started -> stopped`, Shizuku survival and boot continuity using phone-side monotonic timing;
- the observer does not trigger hangup itself (`KEYCODE_ENDCALL` / `input keyevent` are intentionally absent);
- no production call-state API and no Samsung backend change were added solely for this gate.

Physical gate still pending.

## Task 5 — external resource telemetry helper

Host preparation: `DONE / HOST GREEN`.

`scripts/s22_resource_telemetry.py` samples existing ADB evidence without expanding the production Binder API.

Metrics:

- app/helper PID;
- `VmRSS`;
- FD count;
- thread count;
- CALL_ASSISTANT active/stopped state;
- call state;
- sample monotonic timestamp.

Output is JSONL and summary mode computes deterministic start/end/peak/delta while preserving missing-process samples as missing rather than fabricating zeroes.

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

1. normal-app-death gate with the phone-side observer;
2. RX transferred-PFD close gate;
3. TX transferred-PFD close gate;
4. call-end gate with both app-side probe and phone-side observer;
5. 20-cycle resource gate with external telemetry;
6. final 10-minute endurance with external telemetry;
7. full regression/security/evidence audit;
8. freeze Milestone D;
9. only then begin Realtime AI integration.
