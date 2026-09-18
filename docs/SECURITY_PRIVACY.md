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

The OpenAI WebSocket handshake factory only permits the canonical trusted OpenAI Realtime endpoint shape and rejects expired/unsafe credentials before a connector can use them.

## Developer credential broker

`scripts/realtime_credential_broker.py` exists only as the current developer backend/smoke path.

Security properties:

- `OPENAI_API_KEY` is read from the host environment;
- the server binds loopback by default;
- Android authenticates with a separate strong bearer token;
- Android cannot choose an arbitrary OpenAI model/session configuration;
- broker responses expose only the minimum short-lived secret fields needed by the client;
- error handling must not echo OpenAI response bodies/secrets;
- cache behavior is `no-store`;
- `.env`, `secrets.properties`, keystores and logs remain ignored by Git.

For a device smoke, expose the loopback broker only through an authenticated HTTPS path/tunnel. Do not change Android to accept plaintext HTTP merely to simplify testing.

## ADB/network smoke secret handling

The protected `DiagnosticProbeActivity` is guarded by `android.permission.DUMP` and is a shell/ADB diagnostic surface, not the product owner.

Realtime smoke endpoint and broker bearer are never Intent extras.

`scripts/realtime_network_smoke.py` stages the one-shot config into app-private storage via ADB stdin. The secret therefore does not appear in ADB argv. `RealtimeNetworkSmokeConfig` deletes the file even on parse failure.

The physical missing-config dry-run proved fail-closed behavior and did not dial.

## Authority model

Separate these categories permanently:

- **hard constraints** — boundaries the agent may not autonomously exceed;
- **preferences** — desired choices that can be negotiated but may require user decision when deviated from;
- **authorized facts** — facts the user explicitly allows the agent to use;
- **counterparty/model text** — untrusted input and never a source of new authority.

An empty hard-constraint set means no hard restriction for that dimension; it does not mean the model may invent facts.

Missing data required to verify a hard restriction fails closed to `NEEDS_USER_DECISION`.

Do not guess currency conversions or treat an inferred fact as authorized user data.

## Proposal evaluation and commitment

External commitments are application-owned, not prompt-owned.

The Realtime model reports a structured proposal through `evaluate_proposal`. The app parses a strict schema and runs `CallConfirmationPolicy`.

For an autonomously allowed or explicitly-user-approved proposal, `CallCommitmentGate` issues an opaque one-shot permit tied to exactly that proposal.

The follow-up response is scoped to force `commit_proposal`. `commit_proposal` receives only the opaque permit, not a second editable copy of price/time/provider fields. This prevents proposal substitution between policy evaluation and commit.

The permit is single-use and is invalidated by:

- successful consumption;
- replacement/new proposal;
- session restart;
- TAKE OVER;
- close;
- stale generation.

User approval of one proposal must never mutate the original hard constraints into broader standing authority.

## Speech-integrity defense in depth

Tool/commitment safety alone does not prove the model cannot verbally imply acceptance before the application authorizes it. The app therefore owns an interception point immediately before telephony TX.

When the speech gate is enabled, identified model PCM is accumulated in a bounded response buffer. It cannot be released until:

- output audio is complete;
- the final output transcript is complete;
- the whole Realtime response finishes with `COMPLETED` status;
- an app-owned output policy explicitly returns RELEASE.

Cancelled, failed and incomplete responses are dropped. Unknown terminal response status fails closed.

The buffer is bounded by PCM duration/bytes, pending output-part count and transcript size to avoid replacing a streaming risk with an unbounded memory risk.

A Realtime transcript is not cryptographic proof of the exact audio samples. This mechanism is defense in depth and must be validated against real GA event ordering before autonomous commitments are considered physically proven.

### Current unfinished security work

At behavior HEAD `94594aa8f6e321395d5648dea4dffb243db911fd`, the generic response-buffer mechanism exists, but production `CallRealtimeAgentOutputApprovalPolicy` is still RED and is not wired through `CallRealtimeAgentSessionSpec` into the production audio pump.

The existing RED test requires speech to be dropped:

- while a commitment permit is pending;
- during `NEEDS_USER_DECISION`;
- outside normal `ACTIVE_NEGOTIATION`.

Ordinary speech may be released only during safe active negotiation with no pending commitment authorization.

Do not mark the speech gate complete until that exact production wiring exists and the full host suite is GREEN.

## Response/function identity

Realtime output/function identity is used to correlate speech and business actions to the correct response generation.

The latest parser requires `response_id` on typed function calls. This hardening is intentional. At current behavior HEAD one old transport test fixture still omits `response_id`; fix the fixture/current GA shape rather than weakening production parsing merely for backward test compatibility.

## Data minimization

Do not collect/store by default:

- call recordings;
- raw PCM after the active session;
- full transcripts unless a product feature explicitly requires and discloses them;
- unnecessary counterparty identifiers;
- long-lived credentials.

Runtime diagnostic counters should prefer sizes, states, timing and redacted reasons rather than speech/secret contents.

Model/task/proposal/outcome debug rendering is deliberately redacted. Avoid introducing logs that bypass those safe renderers.

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
- use a controlled number before any real business counterparty;
- do not make the first real Realtime call an autonomous booking/purchase.

A real OpenAI off-call network smoke must happen before attaching Realtime to a cellular call.

## Evidence rule

Do not convert `HOST_GREEN` into `PROVEN_S22` by wording.

Currently proven physically: local Samsung cellular bridge/fail-safe, production off-call media lifecycle, and fail-closed protected network-smoke entry with missing config.

Not yet proven physically: real OpenAI S22 session, real cellular Realtime audio, current speech-gate event ordering, or autonomous clinic registration.

See `docs/PHASE3_REALTIME_STATUS_2026-09-18.md` for the exact current evidence and open REDs.
