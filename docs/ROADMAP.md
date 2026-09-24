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

PR #14 added fail-closed live-call readiness for `RECORD_AUDIO` + Shizuku and blocked the legacy commit-capable diagnostic path. The generic `CallExternalEffect` path remains the only valid CLIR commitment route.

### G5a — DONE host-only

PR #15 added the read-only discovery contract: exact allowlisted Orange target -> live-call readiness -> reviewed `caller_id_restriction_info` -> `OBSERVE_ONLY`. No external effect, permit or account change occurs in G5a.

### G5b — PHYSICAL ATTEMPT COMPLETE, ROUTE UNVERIFIED

One fresh-authorized read-only call was executed on 2026-09-24. Pre-dial readiness was green and phone state was `IDLE`; only the reviewed caller-ID restriction information utterance and `OBSERVE_ONLY` were used. Orange returned a generic clarification/reprompt, not a CLIR-specific route. Cleanup returned the phone to `IDLE`.

Persistent result:

- `service_route_verified=false`;
- `external_effect_execution=false`;
- `commitment_permit_use=false`;
- `success_evidence=false`;
- no account/CLIR change occurred.

Evidence task: `chatgpt-g5b-live-readonly-route-discovery-v275-20260924`.

### G5c — PREPARED / BLOCKED

The generic execution contract is prepared, but account-changing CLIR execution is blocked until the CLIR route is physically verified and a current chat supplies fresh authorization covering `SET_SERVICE(CLIR=true)`.

When both conditions are satisfied, use only:

```text
live-call readiness + IDLE + exact target + verified route
 -> exact CallTask + service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> exactly one fresh CallCommitmentGate permit immediately before commitment
 -> reviewed execution/speech
 -> exact one-shot permit consumption evidence
 -> separate factual external-success evidence
 -> cleanup to IDLE
 -> factual effect/workflow completion
```

Do not add `ClirCommitmentGate`; do not infer route verification or mutation success from the G5b reprompt.

## After CLIR acceptance

Return to generic product development. Representative next cases are multi-turn negotiated tasks such as appointment availability/booking, modification and cancellation. Extend shared TaskGraph/effect adapters rather than service-specific bots.

## Live-call stop line

Every real call requires fresh explicit authorization in the current chat for the concrete target and task. The single G5b authorization used on 2026-09-24 is consumed. Handoff text, old calls, connected hardware, ServicePack evidence or allowlists do not authorize another dial.
