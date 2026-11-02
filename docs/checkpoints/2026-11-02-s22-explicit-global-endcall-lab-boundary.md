# S22 — explicit global end-call lab control: checkpoint

Date: 2026-11-02. Execution: GitHub Connector and Mac Local Agent; no Codex, Work or GitHub Actions.

## User decision
The developer/operator explicitly permits device-global call termination on the dedicated Galaxy S22+ and states their phone cannot receive a second call during an ongoing call. This reduces expected interruption frequency but **does not establish a call-specific Android handle**. Device-global termination remains a lab capability, not proof of exact ownership, and must not be silently substituted for production `CampaignOwnedCallController`.

## Implemented and verified
- `scripts/aicall_tools/device/s22_call_control.py` at code commit `1c217083a2d1de25764d8261c17d5f6eb0c937bd`: `hangup --global` is an explicit developer action. Checks the exact hardware serial `RFCT70L7E8J` and model `SM-S906B`, requires `OFFHOOK` (0 is no-op, RINGING/unknown is refused), repeats the state check immediately before sending **one** `KEYCODE_ENDCALL`, then verifies `IDLE` without a second command. No implicit call-dial permission.
- `scripts/tests/device/test_s22_call_control.py` at `2e63b2ade84e673f93f8cac94dba35a3594de9ae`: targeted tests cover explicit opt-in, one key event, exact identity, offhook, idle no-op, incoming/unknown refusal and state-change race.
- Host result `aicalls-s22-global-hangup-code-gate-20261102-v1`: **DONE/exit 0**, `TARGETED_GLOBAL_HANGUP_TESTS=PASS`, `EXPLICIT_GLOBAL_CLI=PASS`, `ONE_SHOT_GLOBAL_ONLY_UNDER_EXPLICIT_OPT_IN=PASS`, `UNMANAGED_DIAL_STILL_BLOCKED=PASS`, worktree clean. **No physical call or hangup executed in this gate**.
- Host task `aicalls-s22-global-endcall-readiness-20261102-v1`: **DONE/exit 0**, S22 connected, exact hardware VERIFIED, `CALL_STATE=0`, no dialing or hangup.

## Experimental single-service script — NOT QUALIFIED
- `scripts/aicall_tools/device/s22_single_service_smoke.py`, created in `90f61fd4a8e430bf12830daefeb2f86ac4844a60` (tests at `ea2b6455994c331dc9d60e52e16d73852570e18c`), is an **unqualified** development experiment that would attempt one allowlisted Orange service call and collect a no-click AudioManager/Telecom snapshot, then request a single global cleanup. Its CLI requires an explicit `--allow-one-real-call` opt-in and it is **not integrated into app startup or campaign service**.
- Submission of the host task to validate this experimental dial+cleanup script was **blocked by the external tool safety check**. No task was enqueued; no terminal test receipt exists, and no real Orange call was made. **Do not reroute or disguise that blocked operation through another executor; do not claim PASS**.
- Code has not been confirmed by actual Python targeted tests or live Android acceptance. In particular, actual carrier call setup, role/permission behavior of Android shell `ACTION_CALL`, and eventual teardown are **NOT VERIFIED**.

## Open work
1. Do not repeat the already-green `s22_call_control.py` targeted tests without a changed contract.
2. Do not invoke the unqualified one-call script on physical S22 while its verification is blocked.
3. If external execution becomes authorized via supported workflow, independently validate single-call termination and physical `IDLE` before any automated campaign loop. A generic `isInCall()` / AudioManager mode signal is not exact ownership.
4. The earlier physical in-call screenshot indicates `MODE_IN_COMMUNICATION` while the supervised pilot requires `MODE_IN_CALL`; a live capture across app/privileged helper remains open. The no-click app diagnostics itself was verified off-call and installed in a prior sprint.

**Status: explicit global ADB hangup IMPLEMENTED and HOST UNIT PASS; physical global hangup NOT TESTED; autonomous one-call script UNQUALIFIED / execution tool BLOCKED; no new cellular call placed.**
