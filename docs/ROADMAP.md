# AI Calls — current roadmap

This is the **current** execution plan. The complete 2026-11-01 engineering/audit chronology stays in Git history and the named sprint/checkpoint documents; historical `latest` labels are not current acceptance evidence.

## Status

- `DONE`: implemented; `HOST_GREEN`: full exact-source host gate PASS; `PROVEN_S22`: actual device proof for stated scope; `FROZEN`: do not change without a concrete defect.
- **Supervised first-call MVP:** physical *off-call* gates `PROVEN_S22`; cellular real-call acceptance **OPEN**.
- **Autonomous owned calls / Orange CLIR:** **BLOCKED** by absence of a proven exact-call owner with targeted hangup; the safety refusal must stay enabled.

## 2026-11-02 offline diagnostics gate (newer than the original pre-call quality receipt)

The first on-call attempt reported `AUDIO_ROUTE_NOT_READY`. The current diagnostic module refactor (code `cce1fe99`) removes inconsistent double-sampling, uses synchronized diagnostics and atomic private receipts, and avoids model argument/local error content in evidence. **New diagnostic source HOST_GREEN**: Mac task `aicalls-diagnostics-modular-audit-host-20261102-v2` is terminal **DONE/0** on pinned production code `cce1fe99`; new focused tests, app assemble/lint, static safety and clean-worktree checks all **PASS**. The initial v1 compile failure is retained as a failed attempt, not relabeled. Current phone is disconnected, so **physical deployment, real-call RX/TX and acoustic STOP remain OPEN**. Install only after exact S22 identity + IDLE preflight. Do **not** rerun earlier PASS full suites. See [offline diagnostics audit](checkpoints/2026-11-02-offline-supervised-diagnostics-architecture-audit.md) and [handoff](HANDOFF_NEXT_CHAT.md).

## Immediate next step — one first supervised call

1. Read [pre-call checkpoint](checkpoints/2026-11-02-before-first-call-checkpoint.md) and [new-chat handoff](HANDOFF_NEXT_CHAT.md). Inspect fresh `main` HEAD, current Mac Local Agent task results, S22 USB identity, Shizuku and `IDLE`; do not repeat completed speech/model/off-call tests absent a code/configuration regression.
2. Obtain one **consenting test partner** and have the user **manually place** a regular call from Google Phone. This is *not* app-owned dialing. Only the user may decide the destination/consent; no target may be inferred from files or chat history.
3. User returns to **Pierwsze połączenie — test nadzorowany** and explicitly approves attaching AI audio in the correct Gemma 4 or script mode. No separate service effect or PHONE disclosure.
4. Observe up to three safe turns: cellular PCM RX → Polish STT → bounded Gemma skill or reviewed script → application approval → local TTS → PCM TX. Read **sanitized counters only** from `chat-relay-live-call-report.txt` (RX bytes, STT lengths, model/script count, TX bytes, failure reason) via Local Agent.
5. Check with the consenting partner that AI audio was actually heard, and test **STOP AI** while preserving the user's cellular call. Verify media stops, no further app PCM TX, phone remains under Google Phone control, and the user hangs up manually.
6. Record exact PASS/FAIL evidence and any minimal remediation without claiming success from counters alone. Return the handset to `IDLE` and update the checkpoint.

**Stop lines:** Never dial, hang up, change SIM/CLIR, switch dialer, bypass app confirmation, expose transcripts/identity, or use global `TelecomManager.endCall()` on Local Agent's initiative. If the call is not present, attach must fail closed.

## Proven, do not repeat

| Boundary | Device evidence |
|---|---|
| Polish Google STT and local TTS PCM | TTS → PCM → exact Polish transcription **PASS** (2026-11-02) |
| Complete-text deterministic backend + speech output | STT → approved text → TTS output **PASS** |
| Real local Gemma 4 skill + exact app-approved speech | Physical Android instrumentation **PASS** |
| Shizuku Binder, microphone permission, model READY | Physical read-only probe **PASS** |
| Off-call frozen cellular media backend | Correct rejection and teardown to `IDLE` **PASS** |
| Pilot UI under `IDLE` | Attach refuses; STOP inactive **PASS** |
| Real user call RX/STT/dialogue/TTS/TX and acoustic STOP | **OPEN** — requires consenting partner |
| Independent exact-call ownership and targeted hangup | **BLOCKED** — separate engineering track |

Sources: [STT recovery](checkpoints/2026-11-02-ai-calls-s22-polish-stt-recovery.md), [no-call pilot](checkpoints/2026-11-02-ai-calls-autonomous-nocall-supervised-acceptance.md).

## Later — autonomous generic phone tasks

Only after the separate exact-call owner with selective termination is built and physically verified may app-owned dialing be reconsidered. A real scoped, revocable, application-owned grant must match target/task/effect/account. Source/model text is not permission. A service statement, permit consumption or hangup is not external-success proof; factual evidence and independent state check are necessary.

Relevant enduring contracts: [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md), [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [ARCHITECTURE](ARCHITECTURE.md), [CORRECTION_CAMPAIGN](CORRECTION_CAMPAIGN_2026-11-01.md) and [G5_CLIR_ROUTE_DISCOVERY](G5_CLIR_ROUTE_DISCOVERY.md).
