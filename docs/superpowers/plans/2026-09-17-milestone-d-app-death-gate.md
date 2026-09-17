# Milestone D app-death gate plan — 2026-09-17

## Goal

Make the destructive normal-app-death test reliable even if host ADB temporarily disconnects while the S22+ is in a live call.

This is test tooling only. Do not change the proven Samsung RX/TX path, Shizuku UserService media implementation, controller watchdog, PFD ownership, or app behavior for this task.

## Why the previous gate was inconclusive

The first Milestone D app-death run confirmed that the normal app and UserService processes were gone after `am force-stop`, but host-side observation printed `- waiting for device -` and the host loop then reported about 105 seconds. That duration is not a valid media-cleanup latency measurement.

Follow-up harness diagnosis proved:

- the target S22+ does not provide `toybox nohup` (`exit 125`);
- a background `sh ... &` process launched through ADB returns immediately and survives the ADB shell session;
- therefore the timing-critical observer can run entirely on the phone and persist its result for later collection.

## Approach

Add `scripts/s22_app_death_gate.py` with two separate responsibilities:

1. **arm** — generate/push a small `/system/bin/sh` observer and launch it in the background on the phone;
2. **collect** — read the observer's completed result file, parse it, apply the gate, and print a deterministic verdict.

The observer itself performs the destructive action and uses `/proc/uptime`, so host ADB continuity is not part of the measured interval.

### Termination modes

Support explicit modes rather than silently falling back:

- `kill-pid` — preferred app-process-death experiment; sends `kill -9` to the exact normal-app PID;
- `force-stop` — broader ActivityManager fallback/diagnostic mode.

Record the selected mode and command return code in the result. If `kill-pid` is not permitted on the physical build, that fact must be visible rather than hidden by an automatic fallback.

## Observer result

Write to a temporary file first and atomically rename it only when complete.

Record at least:

```text
termination_method
termination_rc
boot_before
boot_after
boot_same
start_uptime
app_process_gone
app_cleanup_ms
userservice_process_gone
helper_cleanup_ms
call_assistant_stop_seen
media_stop_observed_ms
call_state_after
shizuku_server_alive
done
```

The observer may additionally preserve diagnostic snapshots if needed, but the first implementation should stay small.

## Initial strict gate

The collector should fail unless all of these are true:

- `done=1`;
- termination command succeeded;
- normal app disappeared;
- UserService disappeared;
- UserService cleanup was observed within 3000 ms;
- `USAGE_CALL_ASSISTANT` stopped event was observed within 3000 ms;
- the cellular call remained active (`mCallState=2`);
- Shizuku server remained alive;
- boot id did not change.

If the physical run shows that Samsung/AudioPolicy does not emit a deterministic stopped log on process death, do not weaken the gate speculatively. Capture direct post-state evidence first and revise the criterion from evidence.

## TDD

Files:

```text
scripts/test_s22_app_death_gate.py
scripts/s22_app_death_gate.py
```

RED first:

- parsing a complete observer result;
- rejecting missing/invalid integer/boolean fields;
- rejecting cleanup above the deadline;
- rejecting missing media-stop evidence;
- rejecting call termination, Shizuku loss, or boot change;
- generated device script contains no `nohup`;
- generated device script uses atomic temp-file -> final-file publication;
- termination mode is explicit.

GREEN:

- implement only the parser, validator, observer generation and `arm`/`collect` CLI needed by the tests;
- reuse the existing `Adb` helper from `scripts/s22_call_control.py` rather than duplicating target-selection logic.

Host verification:

```text
PYTHONPATH=scripts python3 -m unittest discover -s scripts -p 'test_*.py'
$HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle \
  :privileged-helper:testDebugUnitTest \
  :app:testDebugUnitTest \
  :audio-bridge:testDebugUnitTest \
  :realtime-client:testDebugUnitTest \
  :app:assembleDebug --no-daemon --warning-mode all
git diff --check
```

## Physical gate when the phone returns

1. direct USB-C connection; verify exact S22+ serial/build and Shizuku shell server;
2. install APK built from the exact branch HEAD;
3. assert silent-phone guard before dialing and again after ACTIVE;
4. start the 30-second Shizuku endurance probe and confirm the exact normal-app and `:call_media` PIDs plus active `USAGE_CALL_ASSISTANT`;
5. arm the phone-side observer in `kill-pid` mode;
6. allow the host ADB link to recover naturally if it drops;
7. collect the atomically published result without rerunning the call;
8. only if `kill-pid` is rejected by the platform, run a separately labelled `force-stop` experiment;
9. keep the call locally silent throughout and hang up only after evidence is collected.

Do not start the remaining call-end/PFD-close/repetition/10-minute gates until the app-death result is classified from this reliable measurement.
