# Handoff — next chat: Orange CLIR gate

Date: 2026-09-24

## Repository

`MichalMatu/ai-calls`

Durable product/docs branch: `main`. Local Agent transport branch: `agent-control` only. Normal steady state is exactly those two branches. Always use the fresh bridge-provided Local Agent binding; never copy an old binding from history or docs.

## Product goal

AI Calls is a generic autonomous phone-task engine, not an Orange-specific bot. Models/supervisor help dialogue only; application-owned deterministic policy owns target/task/effect authority, disclosure, commitment and factual completion.

## Frozen foundation

Keep closed unless a concrete root cause appears:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- Shizuku / privileged media boundary — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS, IdentityVault disclosure, Gemma 4 LiteRT-LM lifecycle — proven;
- `BOOK_APPOINTMENT` Gate D — proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate` — host/no-call proven;
- `SET_SERVICE(CLIR=true)` exact validation + one-shot permit + separate external-success evidence — host/no-call proven;
- full synthetic/no-call product chain on S22 — proven.

Do not return to completed G1–G4/G6, redownload Gemma, rewrite frozen media or start broad cleanup without a concrete root cause.

## G5 completed checkpoints

- PR #14: live-call readiness + legacy CLIR authority hardening.
- PR #15: host-only G5 CLIR route-discovery contract.
- 2026-09-24 G5b: exactly one fresh-authorized physical read-only Orange route-discovery call completed.

G5b pre-dial state was `IDLE` and app readiness passed `RECORD_AUDIO` + Shizuku binder/runtime support + app-specific Shizuku permission. The call used only reviewed `caller_id_restriction_info` (`Jak działa zastrzeganie numeru?`) followed by `OBSERVE_ONLY`.

Redacted physical observation:

```text
Przepraszam, że przedłużyć Dale. Chcę mieć pewność, w jakiej sprawie dzwonisz do nas. Powiedz proszę, czego dotyczy twoja sprawa.
```

Evidence task: `chatgpt-g5b-live-readonly-route-discovery-v275-20260924`; bounded observation 15000 ms. The call cleaned back to `IDLE`. No CLIR/account state changed, no commitment permit was issued/consumed, and discovery is not CLIR success evidence.

## Current gate

The physical response was a generic Orange clarification/reprompt. Therefore the CLIR service route remains **unverified**:

```text
service_route_verified=false
success_evidence=false
```

The one-call G5b authorization from that chat is consumed. Do not dial again from old authorization. Any further read-only discovery call needs fresh explicit authorization for that concrete target/task.

## G5c — prepared, but BLOCKED

Do not execute G5c while `service_route_verified=false`, and do not infer route verification from synthetic/no-call data or the generic reprompt.

After the route is physically verified, G5c still requires fresh authorization covering the concrete account-changing `SET_SERVICE(CLIR=true)` effect. Then use only the existing generic lifecycle:

```text
mandatory live-call readiness
 -> require IDLE + exact allowlisted target + verified route
 -> exact CallTask + service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> exactly one CallCommitmentGate permit immediately before commitment
 -> reviewed effect execution/speech
 -> exact one-shot permit consumption evidence
 -> separate factual external-success evidence
 -> cleanup owned call to IDLE
 -> factual effect/workflow completion
```

Do not add `ClirCommitmentGate`. Permit consumption is not external success. Missing/ambiguous route, changed target, failed readiness, absent fresh authorization, invalid permit or absent factual success evidence must fail closed.

## Relevant files

- `scripts/live_call_readiness.py`
- `scripts/local_phone_llm_live_call.py`
- `scripts/g5_clir_route_discovery_plan.py`
- `docs/G5_CLIR_ROUTE_DISCOVERY.md`
- `docs/ORANGE_MAPPING_RUNBOOK.md`
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
- `docs/SECURITY_PRIVACY.md`
- `service-packs/orange/service_tree.v1.json`

## Local Agent rules

- use only the fresh current-chat binding;
- inspect daemon/active-task evidence before queueing work;
- direct GitHub edits for small reviewable repository changes;
- Local Agent for Gradle/tests/ADB/device commands;
- `.agent/*` stays on `agent-control`, never merge it into `main`;
- durable changes go to `main`.

## Authorization stop line

This handoff authorizes nothing. A connected S22, allowlist, old call evidence or prior-chat permission does not authorize a new dial. G5c has not been authorized or executed.

## Suggested next-chat instruction

```text
Kontynuuj wyłącznie MichalMatu/ai-calls z aktualnego main i docs/HANDOFF_NEXT_CHAT.md. Nie wracaj do G1–G4/G6 ani cleanupu. G5b z 2026-09-24 zakończył się bezpiecznym read-only REPROMPT i service_route_verified=false. Traktuj G5c jako przygotowany, ale zablokowany. Nie wykonuj żadnego połączenia ani zmiany CLIR bez świeżej autoryzacji w tym czacie; discovery nie jest success evidence.
```
