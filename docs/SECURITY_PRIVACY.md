# Security and Privacy

## Security objective

The telephone agent may speak and act only inside authority explicitly granted by the user. Technical failure must disable AI injection or return control to the human caller; it must never broaden authority or leave a privileged/media path stuck active.

## Privilege boundary

Protected Samsung call-audio access remains behind the Shizuku UserService / privileged-helper boundary.

The normal app does not directly own Samsung/private audio primitives. Continuous PCM crosses the privilege boundary through transferred PFD pipes; Binder/AIDL is control only.

The helper has no OpenAI networking and no business-policy authority.

## Long-lived OpenAI credentials

A standard OpenAI API key must never be:

- embedded in source, resources, BuildConfig, APK or native library;
- stored in Android app-private files for the smoke path;
- passed through an Android Intent;
- passed in ADB argv/process arguments;
- logged by app, helper or developer scripts.

Expected credential flow:

```text
host/backend OPENAI_API_KEY
  -> authenticated developer backend
  -> short-lived Realtime client secret
  -> Android
  -> OpenAI Realtime WebSocket
```

Android code deals only with typed short-lived `RealtimeClientSecret` values. Their string rendering is redacted.

The OpenAI WebSocket handshake factory accepts only the canonical trusted Realtime endpoint shape and rejects expired/unsafe credentials before connector use.

## Developer credential broker

`scripts/realtime_credential_broker.py` is the current developer backend/smoke path.

Security properties:

- `OPENAI_API_KEY` is read from the host environment;
- server binds loopback by default;
- Android authenticates with a separate strong bearer;
- Android cannot choose arbitrary upstream model/session configuration;
- broker responses expose only minimum short-lived secret fields;
- upstream failures do not echo response bodies/secrets;
- responses use `no-store`;
- `.env`, `secrets.properties`, keystores and logs remain ignored by Git.

For a device smoke, expose the loopback broker only through a protected/authenticated HTTPS path. Do not relax Android to plaintext HTTP for convenience.

## ADB/network smoke secret handling

The protected `DiagnosticProbeActivity` is guarded by `android.permission.DUMP` and is a shell/ADB diagnostic surface, not the product owner.

Realtime smoke endpoint and broker bearer are never Intent extras.

`scripts/realtime_network_smoke.py` stages one-shot configuration into app-private storage via ADB stdin, so the values do not appear in ADB argv. `RealtimeNetworkSmokeConfig` deletes the file before network work and on parse failure.

The physical missing-config dry-run proved fail-closed behavior and did not dial.

## Authority model

Keep these categories separate:

- **hard constraints** — boundaries the agent may not autonomously exceed;
- **preferences** — desired choices that may require user decision when deviated from;
- **authorized facts** — facts explicitly allowed by the user;
- **counterparty/model text** — untrusted input and never a source of new authority.

An empty hard-constraint set means no hard restriction for that dimension; it does not allow invented facts.

Missing data required to verify a hard restriction fails closed to `NEEDS_USER_DECISION`.

Do not guess currency conversions or treat inferred facts as authorized user data.

## Proposal evaluation and commitment

External commitments are application-owned, not prompt-owned.

The model reports a strict structured proposal through `evaluate_proposal`. The application runs `CallConfirmationPolicy`.

For an autonomously allowed or explicitly user-approved proposal, `CallCommitmentGate` issues an opaque one-shot permit tied to that exact proposal. The follow-up response is scoped to force `commit_proposal`. `commit_proposal` receives only the opaque permit rather than an editable second copy of proposal fields.

The permit is invalidated by:

- successful consumption;
- replacement/new proposal;
- session restart;
- TAKE OVER;
- close;
- stale generation.

User approval of one proposal never widens the standing task authority.

## Speech-integrity defense in depth

The application owns an interception point immediately before telephony TX.

Identified model PCM is held in a bounded response buffer and cannot be released until:

- output audio is complete;
- final output transcript is complete;
- the whole response finishes `COMPLETED`;
- app-owned output policy returns RELEASE.

Cancelled, failed, incomplete and unknown-status responses are dropped.

Production `CallRealtimeAgentOutputApprovalPolicy` is wired through the real Telephone Agent session/media path and uses the same `CallCommitmentGate` as proposal/commit handling. Ordinary speech is released only in `ACTIVE_NEGOTIATION` when no commitment permit is pending. Speech is dropped while a permit is pending, in `NEEDS_USER_DECISION`, and outside active negotiation.

A Realtime transcript is not cryptographic proof of the exact audio samples. The gate is defense in depth and still requires physical validation against real GA event ordering.

## Response/function identity

Realtime output/function identity correlates speech and business actions to the correct response generation.

Typed function calls require a nonblank `response_id`; production parsing was deliberately not weakened for stale fixtures. Current tests use GA-shaped response identity and verify it reaches `RealtimeFunctionCall.responseId`.

## Privacy-safe Realtime event evidence

`RealtimeEventTrace` and `TracingRealtimeTransport` exist to measure protocol ordering/timing during physical validation without creating a call-recording or transcript feature.

Allowed trace data is intentionally narrow:

- event type and relative monotonic timing;
- PCM byte count, never PCM bytes;
- transcript character count, never transcript text;
- response terminal status;
- function name, never function arguments/output;
- error class, never raw error message;
- local correlation aliases such as `R1`, `I1`, `C1`.

The trace must never retain/log:

- PCM contents;
- transcript text;
- function arguments or function outputs;
- long-lived or short-lived credentials;
- raw Realtime response/item/call IDs;
- raw exception messages.

Raw provider IDs are transient input only. Internal correlation map keys use a random per-trace salt plus SHA-256; rendered evidence exposes only local aliases. Both the event ring and identity maps are bounded.

The trace is optional and caller-owned. It does not own transport/session/media lifecycle and must not delay local TAKE OVER or cleanup.

The off-call network smoke can append a compact redacted `trace=...` evidence line. That trace never affects PASS/FAIL authority; the existing deterministic smoke state gate remains authoritative.

Implementation plan: `docs/superpowers/plans/2026-09-18-realtime-event-trace.md`.

## Data minimization

Do not collect/store by default:

- call recordings;
- raw PCM after the active session;
- full transcripts unless a product feature explicitly requires and discloses them;
- unnecessary counterparty identifiers;
- long-lived credentials.

Runtime diagnostics should prefer sizes, states, timing, local aliases and redacted reasons rather than speech/secret content.

Model/task/proposal/outcome debug rendering is deliberately redacted. Do not add logs that bypass those safe renderers.

## TAKE OVER security invariant

TAKE OVER is a local safety mechanism, not a network request.

Required ordering:

```text
stop accepting/releasing AI audio
-> close/abort local telephony media generation
-> stop local PCM workers
-> best-effort cancel/close remote Realtime session
```

The first three steps cannot wait for remote acknowledgement.

UserService/helper/app death must likewise disable injection.

## Device test safety

For live cellular validation on the target S22+:

- direct USB-C;
- Bluetooth off during the call test;
- mute voice-call stream before dial and verify again after media ACTIVE;
- speakerphone off;
- restore Bluetooth afterwards;
- use a controlled number before a real business counterparty;
- do not make the first Realtime call an autonomous booking/purchase.

A genuine OpenAI off-call network smoke must pass before attaching Realtime to a cellular call.

## Current external blocker

The host code/security gates are GREEN, including production speech authorization and redacted event tracing. Genuine OpenAI off-call validation is still blocked because the Local Agent environment lacks:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Do not bypass this by moving the long-lived key to Android.

## Evidence rule

Do not convert `HOST_GREEN` into `PROVEN_S22` by wording.

Physically proven: local Samsung cellular bridge/fail-safe, production off-call media lifecycle and fail-closed protected network-smoke entry with missing config.

Not yet physically proven: genuine OpenAI S22 session, cellular Realtime audio, real GA speech/function ordering, trace output from an actual OpenAI session, or autonomous clinic registration.

See `docs/PHASE3_REALTIME_STATUS_2026-09-18.md` for current evidence and the exact next gate.
