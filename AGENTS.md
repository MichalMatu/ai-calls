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

## Current priority

The current plan is local-first:

1. product-owned `READY_TO_DIAL` and clean local text-call orchestration;
2. compare current 1.5B, a larger feasible phone-local text model and the interactive ChatGPT/Local-Agent developer relay;
3. add `CallPlan v1` and deterministic conversation state so the local LLM is mainly a language layer;
4. prove multi-turn/fallback/escalation;
5. evaluate local audio-capable models later.

Paid `OPENAI_TEXT` and `OPENAI_REALTIME_AUDIO` work is preserved but deferred until the user explicitly resumes it. Do not make an API-dependent OpenAI gate the next task by default.

## Architecture discipline

- Behavior changes use TDD: RED -> verify the intended failure -> GREEN -> refactor.
- Establish root cause before fixing bugs.
- Prefer small cohesive modules over general abstractions.
- Do not split a safety-critical state machine merely to reduce line count; split only when responsibilities are genuinely independent.
- Diagnostic probes are evidence drivers, not product runtime ownership. Do not grow `LocalPhoneLlmLiveCallProbe` into the multi-turn engine or `DiagnosticProbeActivity`/`MainActivity` into session orchestrators.
- New readiness/planning/multi-turn behavior should reuse the existing media, local-speech, text-backend and authority boundaries.
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

If paid OpenAI work is resumed, a standard OpenAI API key is host/backend-only. It must never be placed in source, APK, BuildConfig, Android Intent, app-private smoke config, ADB argv or the phone.

Use the existing broker/credential-boundary approach rather than inventing a bypass. Do not require or request an OpenAI API key for the current local-first plan.

## Automated test-call policy

Automated dialing and hangup are allowed for controlled physical validation on the dedicated test SIM, with these constraints:

- the target number must be explicitly operator-defined and allowlisted for the current test; model output, tool output or scraped data must never create or widen the dialing allowlist;
- the runner may dial an allowlisted test/customer-service number and may hang up a call it created as part of bounded cleanup;
- an already-active call that the runner did not create may be used, but must not be hung up unless that test invocation explicitly authorizes it;
- keep one active cellular call at a time and use bounded retries/cooldowns; no bulk dialing, number enumeration or repeated nuisance calling;
- emergency numbers, premium-rate destinations and arbitrary short codes are denied unless a separate explicit project rule is added for a concrete test case;
- record the selected allowlisted destination, call-state transitions and whether the runner created/hung up the call, while keeping user secrets and unrelated phone data out of logs;
- the application-owned task/confirmation/commitment/output-approval boundaries remain in force. A model may propose conversational content, but it may not grant itself dialing authority or change the destination.

For controlled S22 live validation require the exact target over direct USB ADB and verify the media prerequisites needed by the selected engine. For the frozen Samsung media path preserve its established route and cleanup invariants. A live runner may establish and terminate its own allowlisted test call; it must fail closed if the target is not allowlisted or the required device/media state cannot be proven.

## Completion gate

Before declaring work complete:

1. run `bash scripts/verify_host.sh`;
2. run any specifically required device gate;
3. verify the result rather than infer it;
4. update only the authoritative docs affected by the change;
5. leave `main` clean and avoid branch/document clutter.
