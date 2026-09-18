# Handoff — Milestone D frozen / Telephone Agent v1 approaching first Realtime device smoke

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Local Agent binding:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
control_branch: agent-control
```

## Start rule

Milestone D is closed. Do not rerun the Phase 2 physical matrix or modify frozen Samsung/Shizuku media code without concrete regression evidence.

Read `AGENTS.md`, this handoff, `docs/PHASE2D_FREEZE_2026-09-18.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, and `docs/superpowers/plans/2026-09-18-telephone-agent-v1.md`.

## Frozen baselines

```text
Phase 2B:
milestone/phase2b-proven-s22-20260916
c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C:
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4

Phase 2D:
milestone/phase2d-failsafe-proven-s22-20260918
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Milestone D physical evidence remains: normal-app death, helper death, PFD-close whole-generation cleanup, 20/20 start/abort cycles, natural call-end cleanup, and 600 seconds total real bidirectional media as 10 x 60-second sessions. Separate active resource telemetry covered 50.1 seconds only; do not describe it as 600-second resource telemetry.

Preserve direct-shell RX ordering, RX `android` attribution, TX `com.android.shell` attribution, CALL_ASSISTANT/TELEPHONY_TX, internal mono PCM16LE, stereo duplication only at the Samsung TX boundary, PFD AutoClose ownership, one shared RX+TX fail-safe generation, endpoint-loss whole-generation cleanup, local TAKE OVER, heartbeat and `CallModeWatchdog`.

## Telephone Agent v1 — current completed state

Production/app layers now include:

- `CallMediaSessionCoordinator` + `ShizukuCallMediaSessionBackend` with generation ownership and fail-closed local TAKE OVER;
- immutable `CallTask` / `CallWorkflow` / deterministic confirmation policy with hard constraints, soft preferences, authorized facts, structured outcomes and `NEEDS_USER_DECISION`;
- Realtime transport-neutral boundary plus concrete GA WebSocket transport;
- mono PCM16LE 16 kHz <-> 24 kHz adapter isolated from the frozen Samsung path;
- typed GA function tools/calls/function outputs and generation-bound one-shot responders;
- `evaluate_proposal` strict JSON bridge into `CallConfirmationPolicy`;
- app-owned `CallCommitmentGate`: a positive proposal evaluation yields an opaque one-shot permit tied to that exact proposal;
- separate `commit_proposal` tool that consumes the permit once; replacement proposal, restart, TAKE OVER or close invalidates the old permit;
- response-level followup sequencing: positive evaluation/user approval forces `commit_proposal`; successful commit continues with `tool_choice:none`;
- prompt/instructions updated so positive `evaluate_proposal` is not described as permission to verbally confirm before `commit_proposal` succeeds;
- `CallRealtimeAgentSessionSpec` that binds task-derived instructions, both tools and their handlers;
- `CallRealtimeAgentSessionController` and `CallRealtimeAgentRuntime` production ownership boundary; runtime cleanup order is controller -> bootstrap executor -> media runtime;
- `BackendRealtimeCredentialProvider` plus secure Android developer-backend request factory; client-side direct minting at `api.openai.com` and standard `sk-...` keys are rejected;
- host development credential broker `scripts/realtime_credential_broker.py`: loopback-only, developer bearer required, long-lived `OPENAI_API_KEY` from host env only, server-controlled model, minimal `value + expires_at` response, no credential/body logging;
- one-shot app-private Realtime smoke config, deleted before network activity;
- ADB-only Realtime off-call smoke through `DiagnosticProbeActivity`, still protected by `android.permission.DUMP` and carrying only a boolean Intent trigger;
- secure host runner `scripts/realtime_network_smoke.py`; endpoint/bearer come from env and are staged to app-private storage through ADB stdin, never argv or Intent.

## Latest verified gates

Host Realtime network-smoke composition gate at product HEAD `71e3e22f5d39e513bb41c64df2d3fe7087a4ca0d` is GREEN:

- focused credential-provider/request-factory tests;
- complete `realtime-client` and `app` unit tests;
- `:app:assembleDebug`;
- security-shape checks including DUMP protection and absence of endpoint/token Intent extras;
- long-lived-key scan;
- clean tree.

The secure host smoke runner at `7d9e0995bf5db124b0d21052f64b8f4827e7313e` is GREEN:

- 5 focused runner tests;
- 59/59 Python tests total;
- `py_compile` for broker + runner;
- secret-pattern scan;
- `git diff --check` and clean tree.

Physical S22+ dry-run of the new probe is GREEN **without OpenAI and without dialing**:

```text
CALL_STATE_BEFORE=0
realtime_network_off_call_smoke=FAIL
reason=config_error
states=none
config_file_remains=false
CALL_STATE_AFTER=0
HELPER_PID_AFTER=none
```

This proves protected activity wiring and fail-closed missing-config handling on the physical S22+, not Realtime connectivity.

Earlier production media off-call regression also remains GREEN: `BINDING -> PREPARING -> STOPPING -> FAILED -> IDLE` with expected `cellular call is not active`, call state idle, Bluetooth unchanged and helper cleaned up.

## Current OpenAI Realtime direction

Use GA Realtime shapes only. Standard OpenAI API key stays on the developer backend/host. Android receives only a short-lived Realtime client credential.

Current project direction intentionally keeps WebSocket as the first measured transport because the app owns explicit telephony PCM. `RealtimeTransport` remains neutral so WebRTC can be benchmarked/replaced later.

## Remaining gates before the first real telephone-agent call

### 1. Real OpenAI network/session smoke on the S22+

External prerequisites currently missing from the Local Agent environment:

- host `OPENAI_API_KEY`;
- an HTTPS route to the loopback credential broker (for example a tunnel).

Do not put the long-lived OpenAI key in the APK, Intent, task JSON, GitHub, logs or ADB arguments.

When prerequisites are available:

1. start `scripts/realtime_credential_broker.py` on host loopback;
2. expose only that loopback service through HTTPS;
3. set `AI_CALL_BRIDGE_BROKER_HTTPS_URL` + a distinct strong `AI_CALL_BRIDGE_BROKER_TOKEN` in host env;
4. run `scripts/realtime_network_smoke.py <S22 adb serial>` while `CALL_STATE=0`;
5. PASS requires the app to reach Realtime successfully and then hit the expected off-call media rejection; no cellular call is made.

### 2. Speech-integrity gate before autonomous real-world commitment

App-side replay/substitution/authorization is now hard-gated, and post-evaluation sequencing forces `commit_proposal`. This still does **not** prove the model can never verbally imply commitment before invoking `evaluate_proposal`.

Do not enable fully autonomous booking/purchase/commitment until this remaining speech-integrity risk is addressed. A conservative technical option is buffering a complete model output response before telephony TX and validating its transcript, but Realtime audio/transcript deltas are not byte-synchronized, so chunk-by-chunk transcript filtering is not sufficient.

A first live call may therefore be run in a non-committing mode even before autonomous booking is enabled.

### 3. First real cellular Realtime call

After network/session smoke is physically GREEN:

- keep direct USB-C where possible;
- Bluetooth off for the live test;
- voice-call stream muted before dial and rechecked after ACTIVE;
- speakerphone off;
- local TAKE OVER must remain immediately available;
- first live call should prove bidirectional Realtime conversation without authorizing an external commitment;
- only after that should a controlled booking scenario be attempted.

Safe test number `510100100` remains authorized only when a genuine physical regression test needs it. Do not dial it merely to repeat already frozen Phase 2 evidence.

## Do not claim yet

- no real OpenAI Realtime network/session has yet been proven on the S22+;
- no end-to-end OpenAI Realtime audio through a real cellular call has yet been proven;
- no autonomous real-world booking is yet proven safe;
- the protected dry-run is not a Realtime connectivity proof.
