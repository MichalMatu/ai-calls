# G5 CLIR route discovery

G5 is split so unknown Orange routing can never be treated as commitment authority or CLIR success evidence.

## G5a — DONE host-only: discovery contract

The pure contract lives in `scripts/g5_clir_route_discovery_plan.py`.

```text
exact allowlisted Orange target
 -> mandatory live-call readiness before dialing
 -> reviewed caller_id_restriction_info turn
 -> OBSERVE_ONLY next turn
 -> nonblank observation evidence
```

It records `live_call_readiness_required=true`, `external_effect_execution=false`, `commitment_permit_use=false` and `fresh_live_call_authorization_required=true`.

## G5b — PHYSICAL ATTEMPT COMPLETE, ROUTE UNVERIFIED

On 2026-09-24 one explicitly authorized read-only Orange call executed the reviewed G5b contract. Immediately before dial, app live-call readiness passed for `RECORD_AUDIO` and Shizuku and phone state was `IDLE`.

The call used only:

```text
caller_id_restriction_info: "Jak działa zastrzeganie numeru?"
 -> OBSERVE_ONLY
```

Redacted observation evidence was a generic Orange clarification/reprompt rather than a CLIR-specific route:

```text
Przepraszam, że przedłużyć Dale. Chcę mieć pewność, w jakiej sprawie dzwonisz do nas. Powiedz proszę, czego dotyczy twoja sprawa.
```

Evidence task: `chatgpt-g5b-live-readonly-route-discovery-v275-20260924`. Observation capture was bounded to 15000 ms. The owned call was cleaned back to `IDLE`.

Therefore:

- `service_route_verified=false`;
- no CLIR/account state was changed;
- no commitment permit was issued or consumed;
- this discovery is **not** success evidence for `SET_SERVICE(CLIR=true)`;
- the single live-call authorization used for this G5b attempt is consumed and does not authorize another call.

## G5c — PREPARED, BLOCKED

The execution contract is prepared, but it must fail closed while the physical CLIR service route is unverified. Do not execute it from the G5b reprompt evidence.

Only after the route is physically verified and the current chat contains fresh authorization covering the concrete account-changing effect may G5c use the existing generic lifecycle:

```text
mandatory live-call readiness
 -> require phone IDLE + exact allowlisted target
 -> exact CallTask + service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation against the verified route
 -> issue exactly one CallCommitmentGate permit immediately before commitment
 -> reviewed effect speech/execution
 -> exact one-shot permit consumption evidence
 -> separate factual external-success evidence
 -> cleanup owned call to IDLE
 -> effect/workflow completion only from factual success evidence
```

No `ClirCommitmentGate` may be introduced. Unknown or changed routing, ambiguous evidence, changed target, missing fresh authorization, failed readiness, missing/invalid permit, or missing factual external-success evidence must fail closed. Permit consumption is not external success, and route discovery is not mutation success evidence.

## Current stop line

G5c is **not authorized and not executable yet** because `service_route_verified=false`. Any further Orange call, including another read-only route-discovery attempt, needs separate fresh live-call authorization in the current chat.
