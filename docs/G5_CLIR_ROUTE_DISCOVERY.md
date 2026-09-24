# G5 CLIR route discovery

G5 is split so unknown Orange routing can never be treated as commitment authority or success evidence.

## G5a — DONE host-only: discovery contract

The pure contract lives in `scripts/g5_clir_route_discovery_plan.py`.

```text
exact allowlisted Orange target
 -> mandatory live-call readiness before dialing
 -> reviewed caller_id_restriction_info turn
 -> OBSERVE_ONLY next turn
 -> nonblank observation evidence
```

It records:

```text
live_call_readiness_required=true
external_effect_execution=false
commitment_permit_use=false
fresh_live_call_authorization_required=true
```

The existing Gate C `--observe-next` transport already provides the reviewed speech turn plus observation turn; no second audio/runtime stack or authority store is needed.

## G5b — NEXT: physical read-only discovery

A real G5b call requires fresh explicit authorization in the current chat for the exact Orange target and **read-only CLIR route-discovery task**.

Before dial:

- phone call state must be `IDLE`;
- `scripts/live_call_readiness.py` / app readiness must confirm `RECORD_AUDIO`, Shizuku binder/runtime support and app-specific Shizuku permission;
- target/action must match the reviewed discovery plan.

During the call:

```text
reviewed caller_id_restriction_info speech
 -> OBSERVE_ONLY
 -> redacted nonblank observation evidence
 -> cleanup to IDLE
```

G5b must not change account state, issue/consume a commitment permit or cross authentication/customer-data/payment/commitment barriers.

## G5c — after route verification: effect execution

Do not execute CLIR merely because G5b found a route. Execution is a separate generic authority lifecycle:

```text
CallTask + exact target + explicit service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> one-shot CallCommitmentGate permit
 -> reviewed effect speech/execution
 -> exact permit-consumption evidence
 -> separate external-success evidence
 -> factual effect completion
 -> workflow completion
```

No `ClirCommitmentGate` may be introduced.

Unknown/changed routing, blank or ambiguous discovery evidence, changed target, missing fresh authorization, failed readiness, missing exact permit or missing factual external-success evidence must fail closed.

## Current evidence boundary

The Orange ServicePack currently proves only read-only caller-ID restriction information and records `service_route_verified=false` for the service route. It does not prove a CLIR activation edge and must not be interpreted as success evidence for `SET_SERVICE(CLIR=true)`.