# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` exists only for Local Agent task/result traffic.

## Before changing code

Read only what is relevant:

1. `README.md` for current product state;
2. `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point;
3. `docs/ROADMAP.md` for evidence gates;
4. `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` when the change touches those boundaries;
5. `docs/PHASE2D_FREEZE_2026-09-18.md` before changing frozen Samsung media behavior.

Do not create a new planning/status document for every task. Put durable decisions into an existing authoritative document and leave historical detail in Git history or Local Agent results.

## Development discipline

- Behavior changes use TDD: RED -> verify the intended failure -> GREEN -> refactor.
- Establish root cause before fixing bugs.
- Prefer small cohesive modules over general abstractions.
- Do not split a safety-critical state machine merely to reduce line count; split only when responsibilities are genuinely independent.
- Run `bash scripts/verify_host.sh` before product changes are considered complete.
- Hardware/OEM behavior must be measured on the target device; host tests cannot create `PROVEN_S22` evidence.
- Do not rerun destructive or expensive physical matrices without a concrete regression reason.

## Evidence levels

- `HOST_GREEN` means deterministic host tests/build/lint pass.
- `PROVEN_S22` means the behavior was physically reproduced on the target S22+.
- Never rewrite a host result as a physical claim.

For the frozen media path, preserve the invariants documented in `docs/PHASE2D_FREEZE_2026-09-18.md` unless new physical evidence proves a change is required.

## Local Agent

`MichalMatu/local-agent` is an execution worker, not the source of truth.

- `.agent/tasks` and `.agent/results` stay on `agent-control`; never merge them into `main`.
- Use the current chat's immutable Local Agent binding in every task.
- Check `.agent/status/daemon.json` before creating another task that writes the same product tree.
- A queued/ACK task is not success; inspect its terminal result.
- Use Local Agent for Gradle, lint, host tests, ADB and physical-device work.
- Direct GitHub edits are appropriate when the exact code/docs diff can be reviewed without local/device execution.
- Never launch local Codex from a Local Agent task.

## Branch policy

Work directly on `main` unless there is a specific reason for temporary isolation. If a temporary branch is used, merge/fast-forward it after verification and delete it. Do not keep milestone branches merely as bookmarks; commits, freeze documents and Local Agent evidence are sufficient.

## Credential rule

A standard OpenAI API key is host/backend-only. It must never be placed in source, APK, BuildConfig, Android Intent, app-private smoke config, ADB argv or the phone.

The next physical Realtime gate requires the three host variables listed in `docs/HANDOFF_NEXT_CHAT.md`. Do not invent a bypass if they are absent.

## Live-call safety

For controlled S22 Realtime validation require, before staging any broker secret:

- exact target over direct USB ADB;
- Bluetooth OFF;
- active cellular call (`CALL_STATE=2`);
- `MODE_IN_CALL`;
- earpiece as active communication device;
- voice-call stream muted.

The live Realtime smoke runner observes these conditions and refuses when they are not met. It must not dial or hang up the cellular call.

## Completion gate

Before declaring work complete:

1. run `bash scripts/verify_host.sh`;
2. run any specifically required device gate;
3. verify the result rather than infer it;
4. update only the authoritative docs affected by the change;
5. leave `main` clean and avoid branch/document clutter.
