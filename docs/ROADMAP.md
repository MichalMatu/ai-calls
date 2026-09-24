# Roadmap

## Status

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a concrete root cause.

## Stable foundation

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`.
- Shizuku / privileged media boundary — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- IdentityVault disclosure boundary — proven.
- Gemma 4 LiteRT-LM runtime + model lifecycle — proven.
- `BOOK_APPOINTMENT` Gate D — `DONE / HOST_GREEN / PROVEN_S22`.
- generic typed `CallExternalEffect` commitment subject — `DONE / HOST_GREEN`.
- `SET_SERVICE(CLIR=true)` validator + one-shot permit + separate external-success evidence — `DONE / HOST_GREEN`.
- full synthetic/no-call acceptance chain on S22 — `PROVEN_S22`.
- negotiated clinic booking proof on the same generic commitment store — `HOST_GREEN`.

## Current Orange gate

### G5 prerequisite — DONE

PR #14 hardened the live boundary:

- app-level no-call readiness for `RECORD_AUDIO` + Shizuku;
- fail-closed host reader;
- legacy commit-capable `caller_id_restriction_enable` diagnostic path blocked;
- generic `CallExternalEffect` path remains the only valid CLIR commitment route.

### G5a — DONE host-only

PR #15 added the read-only route-discovery contract:

```text
exact allowlisted Orange target
 -> live-call readiness
 -> reviewed caller_id_restriction_info turn
 -> OBSERVE_ONLY next turn
 -> nonblank observation evidence
```

No external effect, permit or account change occurs in G5a.

### G5b — NEXT: physical read-only discovery

Requires fresh explicit live-call authorization in the new chat for the exact Orange target and **read-only CLIR route discovery**.

Before dial:

- read fresh `origin/main` and handoff;
- verify exact Local Agent binding if used;
- run `scripts/live_call_readiness.py` / equivalent app readiness evidence;
- require phone `IDLE` and exact target.

During the call:

- use only the reviewed caller-ID information turn;
- follow with existing `OBSERVE_ONLY` capture;
- do not issue/consume a commitment permit;
- do not change CLIR or any account state;
- stop on authentication, customer-data or commitment barriers;
- finish with owned-call cleanup back to `IDLE`.

Output: redacted route/response evidence sufficient to decide the exact G5c execution path.

### G5c — after route verification: generic CLIR execution

Requires explicit authorization that covers the account-changing effect.

Use only the existing generic lifecycle:

```text
exact CallTask + exact target + service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption evidence
 -> separate external success evidence
 -> factual effect completion
 -> workflow completion
```

Do not add `ClirCommitmentGate`, do not treat route discovery as success evidence, and do not declare CLIR complete without factual counterparty evidence.

## After CLIR acceptance

Return to generic product development. The next representative cases are multi-turn negotiated tasks such as appointment availability/booking, modification and cancellation. Extend shared TaskGraph/effect adapters rather than building service-specific bots.

## Live-call stop line

Every real call requires fresh explicit authorization in the current chat for the concrete target and task. Handoff text, old calls, connected hardware, ServicePack evidence or allowlists do not carry authorization forward.