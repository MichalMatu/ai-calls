# Privileged Helper

`privileged-helper/` is the isolation boundary for Android/Samsung operations that a normal third-party process cannot perform.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8. The production privileged path uses Shizuku UserService / shell UID 2000.

## Responsibilities

The module owns only the privileged media boundary:

- `VOICE_DOWNLINK` cellular RX;
- Samsung `CALL_ASSISTANT` / TELEPHONY_TX cellular TX;
- PCM `ParcelFileDescriptor` pipes;
- one shared RX+TX generation;
- heartbeat and `CallModeWatchdog` lifetime;
- endpoint-loss and immediate abort/TAKE OVER cleanup.

It does **not** own OpenAI networking, business policy or model authority. Continuous PCM never travels as per-frame Binder calls.

## Frozen physical baseline

Phase 2D is `PROVEN_S22` at commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

The commit is retained in `main` history; no permanent milestone branch is required.

Physically proven on the target device:

- real cellular downlink PCM through `VOICE_DOWNLINK`;
- cellular uplink through `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT` routed to TELEPHONY_TX;
- simultaneous RX+TX through one shared controller lifetime;
- real app -> Shizuku UserService -> helper parity;
- transferred PFD streaming;
- local abort/TAKE OVER;
- heartbeat timeout and natural-call-end watchdog cleanup;
- helper/app death fail-safe behavior;
- endpoint loss -> whole-generation cleanup;
- repeated lifecycle and 600 seconds total segmented bidirectional endurance.

Detailed freeze evidence and result identifiers live in `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Frozen invariants

Preserve unless a targeted physical regression proves a change is required:

- direct-shell RX prepare-before-explicit-context ordering;
- separately proven Shizuku attribution/context ordering;
- RX system attribution;
- TX `com.android.shell` attribution;
- `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT` and TELEPHONY_TX;
- internal mono PCM16LE;
- stereo duplication only at the Samsung TX boundary;
- PFD AutoClose ownership;
- one shared fail-safe generation;
- endpoint loss -> sibling cleanup;
- local immediate TAKE OVER;
- heartbeat and `CallModeWatchdog`;
- no per-frame Binder PCM transport.

RX and TX intentionally remain asymmetric because the target firmware requires different ordering and attribution rules.

## Static-analysis exceptions

A small number of lint suppressions document target-specific privileged behavior that Android lint cannot model, such as shell identity/permissions and the physically proven hidden/private API fallback. They are narrow annotations, not permission bypasses.

Do not broaden a suppression to hide an unrelated warning. `bash scripts/verify_host.sh` must remain GREEN.

## Regression policy

Do not rerun the full Phase 2D matrix during ordinary Realtime/agent work. If a change can affect a frozen invariant, run the smallest relevant physical gate and update the freeze evidence only if the observed behavior changes.

Current development status and next physical gate are documented in:

- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`
- `docs/ARCHITECTURE.md`
- `docs/SECURITY_PRIVACY.md`

## Licensing

External projects are research references, not code donors by default. Check the source license before reusing implementation code.
