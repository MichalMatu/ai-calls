# Controlled Realtime live-call probe plan — 2026-09-18

## Goal

Prepare the smallest protected harness for the first controlled cellular Realtime validation after the genuine OpenAI off-call smoke is proven.

This slice is host-only. It must not place a call, answer a call, hang up, or be physically executed before the off-call OpenAI gate passes.

## Safety contract

The probe:

- is reachable only through the existing `android.permission.DUMP` diagnostic activity;
- refuses unless a cellular call is already active (`CALL_STATE=2` / `MODE_IN_CALL`);
- consumes the existing one-shot app-private broker configuration and deletes it before network work;
- uses production `CallMediaSessionRuntime` and `CallRealtimeSessionOrchestrator` without changing frozen Samsung media code;
- exposes no Realtime function tools and has no commitment handler;
- keeps the full identified-output response buffer enabled with a diagnostic RELEASE policy, so audio still requires completed audio/transcript/response lifecycle evidence before TX;
- records only the existing bounded/redacted `RealtimeEventTrace`;
- runs for a short bounded duration, then invokes local `takeOverNow()`;
- treats `ACTIVE -> STOPPING -> TAKEN_OVER` after an explicit local stop request as PASS;
- treats startup failure, unexpected takeover, timeout or Realtime/media failure as FAIL;
- leaves the cellular call itself active for the human after TAKE OVER.

## Host runner

Add `scripts/realtime_live_call_smoke.py` using the existing `SmokeEnvironment` and stdin-only private-config staging.

The runner must:

1. verify `CALL_STATE=2` before staging any secret;
2. launch only the protected diagnostic activity with a boolean trigger and bounded duration;
3. never invoke dial, answer or hangup controls;
4. require a terminal probe result;
5. verify the call is still `CALL_STATE=2` afterward;
6. verify the one-shot config is deleted and no call-media helper remains alive.

## TDD

RED first:

- state tracker accepts PASS only after ACTIVE and an explicit controlled-stop request;
- early TAKEN_OVER / FAILED / timeout fail closed;
- host runner refuses non-active call state before secret staging;
- launch arguments contain no broker secret and no telephony control command.

GREEN:

- implement tracker/result;
- implement lower-level no-tools live probe;
- wire protected diagnostic activity;
- implement host runner.

## Verification

Before declaring HOST_GREEN:

- focused live tracker tests;
- focused Python live-runner tests;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- all Python script tests;
- production secret-pattern scan;
- `git diff --check`;
- clean working tree.

No physical call or device execution belongs to this implementation slice.
