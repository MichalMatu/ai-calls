# Handoff — next chat: Orange physical acceptance

Date: 2026-09-24

## Repository

`MichalMatu/ai-calls`

Durable product/docs branch: `main`.
Local Agent transport branch: `agent-control` only.

At handoff close, remote branches are intentionally only:

```text
main
agent-control
```

The next chat must use its **fresh bridge-provided Local Agent binding**. Never copy an old `agent_binding` from history/docs.

## Product goal

AI Calls is a generic autonomous phone-task engine, not an Orange-specific bot. The same architecture must eventually handle carrier settings, appointment availability/booking, changes/cancellations and other bounded phone tasks.

Dialogue/runtime target:

```text
cellular RX -> STT -> deterministic state/PhraseMatrix
 -> bounded Gemma 4 skill -> supervisor fallback if unresolved
 -> application output approval -> TTS/TX
 -> generic external-effect authority when state changes are needed
 -> factual external-success evidence -> workflow completion
```

Models/supervisor are dialogue helpers only; they never own business authority.

## Proven/frozen foundation

Keep closed unless a concrete root cause appears:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- Shizuku / privileged media boundary — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS — proven;
- IdentityVault disclosure boundary — proven;
- Gemma 4 LiteRT-LM runtime/model lifecycle — proven;
- `BOOK_APPOINTMENT` Gate D — proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate` — host/no-call proven;
- `SET_SERVICE(CLIR=true)` exact validation + one-shot permit + separate external-success evidence — host/no-call proven;
- full synthetic/no-call product chain on S22 — proven.

Do not redownload Gemma or rewrite frozen media merely to reprove them.

## Important completed checkpoints

- PR #14 / `a3cd0dd99e977b3e5aca8fd4b7b19c43a8625563`: live-call readiness + legacy CLIR authority hardening.
- PR #15 / `5037c21aadea06e3204f5d9db60476f1c8dbaba1`: host-only G5 CLIR route-discovery contract.

PR #14 added fail-closed app readiness for `RECORD_AUDIO` + Shizuku and blocked the legacy commit-capable `caller_id_restriction_enable` diagnostic path.

PR #15 established the next physical discovery contract:

```text
live_call_readiness_required=true
primary_action=caller_id_restriction_info
observe_next=true
external_effect_execution=false
commitment_permit_use=false
fresh_live_call_authorization_required=true
```

## Current gate — G5b

Next step is **one bounded, read-only Orange call** to discover/confirm the current CLIR route/response using the code in practice.

Before dialing:

1. fetch fresh `origin/main`;
2. read `AGENTS.md`, `docs/ROADMAP.md`, `docs/G5_CLIR_ROUTE_DISCOVERY.md`, `docs/SECURITY_PRIVACY.md`;
3. verify fresh Local Agent binding/daemon state if Local Agent is used;
4. require fresh explicit authorization in that new chat for the exact Orange target and read-only discovery task;
5. run the app-level live-call readiness check (`RECORD_AUDIO` + Shizuku); fail closed if not ready;
6. require phone call state `IDLE`.

Physical G5b shape:

```text
exact allowlisted Orange target
 -> reviewed caller_id_restriction_info utterance
 -> existing OBSERVE_ONLY next turn
 -> capture redacted nonblank observation evidence
 -> hang up/cleanup to IDLE
```

G5b must NOT:

- change CLIR/account state;
- issue or consume a commitment permit;
- treat route discovery as CLIR success evidence;
- broaden the target/task;
- pass authentication/customer-data/payment/commitment barriers.

The current ServicePack still has `service_route_verified=false` for the service route. Persist only what is physically observed.

## After G5b — G5c

Only after route evidence is understood, an account-changing CLIR step may be attempted under authorization covering that concrete effect.

Use the existing generic path only:

```text
exact CallTask + exact target + service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption evidence
 -> separate factual external-success evidence
 -> effect completion
 -> workflow completion
```

Do not add `ClirCommitmentGate`. Do not claim CLIR completed without accepted factual external-success evidence.

## Latest physical evidence

Prior authorized Orange experiments already established:

- real dialing and `OFFHOOK` work;
- real Orange downlink works;
- transient ADB audio/hangup handling was hardened;
- microphone permission can need re-grant after reinstall;
- later no-call diagnostics confirmed Shizuku + `RECORD_AUDIO` readiness.

No CLIR account change has been completed yet.

## Relevant files

- `scripts/live_call_readiness.py`
- `scripts/local_phone_llm_live_call.py`
- `scripts/g5_clir_route_discovery_plan.py`
- `docs/G5_CLIR_ROUTE_DISCOVERY.md`
- `docs/ORANGE_MAPPING_RUNBOOK.md`
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
- `service-packs/orange/service_tree.v1.json`

## Local Agent rules

- use only the fresh binding supplied by the new chat;
- inspect daemon/active-task evidence before queueing work;
- direct GitHub edits for small reviewable repository changes;
- Local Agent for Gradle/tests/ADB/device commands;
- every Local Agent task JSON must contain exactly the fresh binding from that new chat;
- never launch local Codex from Local Agent;
- `.agent/*` remains on `agent-control`, never merge it into `main`.

## Authorization stop line

This handoff itself authorizes nothing. A connected S22, old call evidence, allowlists or previous chat permission do not authorize a new dial.

The new chat must receive fresh explicit authorization for G5b. A later account-changing G5c also needs authorization covering `SET_SERVICE(CLIR=true)` unless the new-chat instruction explicitly and unambiguously authorizes both discovery and that effect.

## Start prompt for the new chat

Copy/paste this into the new chat after the fresh Local Agent binding envelope appears:

```text
Kontynuuj rozwój wyłącznie repozytorium MichalMatu/ai-calls zgodnie z aktualnym main i docs/HANDOFF_NEXT_CHAT.md. Najpierw przeczytaj AGENTS.md, docs/ROADMAP.md, docs/G5_CLIR_ROUTE_DISCOVERY.md i docs/SECURITY_PRIVACY.md oraz sprawdź świeży stan Local Agenta. Nie wracaj do zakończonych G1–G4/G6 ani do szerokiego cleanupu.

Następne zadanie to G5b: użyć istniejącego kodu w praktyce w jednym ograniczonym, read-only połączeniu z Orange, aby sprawdzić trasę/odpowiedź dotyczącą zastrzegania numeru. Przed dialem obowiązkowo uruchom live-call readiness i wymagaj IDLE. W rozmowie użyj tylko reviewed caller_id_restriction_info, potem OBSERVE_ONLY, zbierz redacted evidence i zakończ połączenie do IDLE. Nie zmieniaj CLIR, nie wydawaj/zużywaj commitment permitu i nie traktuj discovery jako success evidence.

Autoryzuję jedno read-only połączenie do aktualnie allowlistowanego celu Orange wyłącznie w celu G5b route discovery, bez zmiany ustawień konta/CLIR. Po G5b pokaż wynik i na jego podstawie przygotuj G5c. Nie wykonuj G5c ani żadnej zmiany konta bez osobnej świeżej autoryzacji, chyba że udzielę jej później w tym samym czacie.
```
