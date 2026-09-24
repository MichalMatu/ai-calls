# G5 CLIR route discovery

G5 is split into discovery and execution so unknown Orange routing can never be treated as commitment authority.

## G5a — read-only route discovery

The host-only contract lives in `scripts/g5_clir_route_discovery_plan.py`.

It fixes the discovery shape to:

```text
exact allowlisted Orange target
 -> mandatory live-call readiness check before dialing
 -> reviewed caller_id_restriction_info turn
 -> OBSERVE_ONLY next turn
 -> nonblank observation evidence
```

The plan explicitly records:

```text
live_call_readiness_required=true
external_effect_execution=false
commitment_permit_use=false
fresh_live_call_authorization_required=true
```

The current Gate C `--observe-next` path already provides the two-turn transport behavior in one call. G5a therefore does not add a second audio/runtime stack or a second authority store.

A real discovery call still requires fresh explicit authorization for the concrete Orange target and read-only discovery task. Before any dial, the app-level readiness probe from `scripts/live_call_readiness.py` must confirm `RECORD_AUDIO`, Shizuku binder/runtime support and the app-specific Shizuku permission. The repository contract alone is not authorization.

## G5b — effect execution

Do not execute CLIR merely because G5a found a route. Execution remains a separate generic authority lifecycle:

```text
CallTask + exact target + explicit service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> one-shot CallCommitmentGate permit
 -> reviewed effect speech/execution
 -> exact permit-consumption evidence
 -> external success evidence
 -> factual effect completion
 -> workflow completion
```

No `ClirCommitmentGate` may be introduced.

Unknown or changed routing, blank/ambiguous observation, changed target, missing fresh authorization, failed live-call readiness, missing exact permit, or missing external success evidence must fail closed.

## Current evidence boundary

The existing Orange ServicePack proves only the read-only caller-ID restriction information edge and records `service_route_verified=false` for service routing. It does not prove a CLIR activation edge and must not be interpreted as success evidence for `SET_SERVICE(CLIR=true)`.
