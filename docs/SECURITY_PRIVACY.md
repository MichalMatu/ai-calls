# Security and privacy

## Security objective

The agent may speak and act only inside authority explicitly granted by the user. Technical failure must disable AI injection or return control to the human caller; it must never broaden authority.

## Privilege boundary

Protected Samsung call-audio access stays inside `privileged-helper` / Shizuku UserService. The normal app does not directly own private Samsung audio primitives.

Continuous PCM crosses through transferred PFDs. Binder/AIDL is control only. The helper has no OpenAI networking and no business-policy authority.

## OpenAI credential boundary

A standard OpenAI API key must never be:

- embedded in source/resources/BuildConfig/APK;
- stored in Android app-private smoke configuration;
- passed through an Android Intent;
- passed in ADB argv/process arguments;
- logged by the app, helper or scripts.

Expected flow:

```text
host/backend OPENAI_API_KEY
  -> authenticated developer broker
  -> short-lived Realtime client secret
  -> Android
  -> trusted OpenAI Realtime WebSocket
```

`scripts/realtime_credential_broker.py` binds loopback by default, reads the long-lived key from host environment, requires a distinct Android bearer and returns only short-lived credential fields. Device smoke configuration is staged over ADB stdin and deleted on read.

The genuine OpenAI smoke requires a protected/authenticated HTTPS path to that loopback broker. Do not weaken Android network security to plaintext HTTP for convenience.

## Authority and commitments

Keep these categories separate:

- hard constraints — may not be autonomously exceeded;
- preferences — desired choices that may require a user decision;
- authorized facts — facts explicitly available to the task;
- model/counterparty text — untrusted input, never new authority.

`CallRealtimeProposalParser` strictly decodes one proposal without mutating state. `CallConfirmationPolicy` evaluates it. An allowed or explicitly user-approved proposal gets an opaque one-shot permit tied to that exact proposal. `commit_proposal` consumes the permit once.

Replacement proposal, session restart, TAKE OVER, close, stale generation or failed submission invalidates authorization. User approval of one proposal never widens standing authority.

## Speech integrity

Identified model PCM is buffered before telephony TX. Release requires:

- output audio completion;
- final transcript completion;
- successful `response.done(COMPLETED)`;
- application-owned output policy `RELEASE`.

Cancelled, failed, incomplete and unknown responses are dropped. Ordinary speech is released only in safe active negotiation without a pending commitment permit. Speech is dropped while commitment is pending, in `NEEDS_USER_DECISION`, or outside safe negotiation.

Transcript inspection is defense in depth, not cryptographic proof of the audio samples.

## Realtime diagnostics

`RealtimeEventTrace` stores bounded metadata only. Allowed examples: relative timing, event type, PCM byte count, transcript character count, terminal status, sanitized function/error label and local aliases such as `R1/I1/C1`.

It must not retain PCM content, transcript text, function arguments/output, credentials, raw provider IDs or raw exception messages. Diagnostic labels are bounded ASCII; unsafe labels render as `REDACTED`.

## Data minimization

Do not collect/store by default:

- call recordings;
- raw PCM after an active session;
- full transcripts unless a product feature explicitly requires and discloses them;
- unnecessary counterparty identifiers;
- long-lived credentials.

Prefer state, size, timing, local aliases and redacted reasons in diagnostics.

## TAKE OVER invariant

Required ordering is local-first:

```text
stop accepting/releasing AI audio
-> abort local telephony media generation
-> stop local PCM workers
-> best-effort cancel/close Realtime session
```

The first steps cannot wait for the network/model. App/helper death must likewise disable injection.

## Controlled live-call preflight

Before the live Realtime smoke can stage broker configuration it requires:

- exact target S22+ over direct USB ADB;
- Bluetooth OFF;
- `CALL_STATE=2`;
- `MODE_IN_CALL`;
- earpiece route;
- voice-call stream muted.

The runner fails closed if any condition is absent. It never dials or hangs up and does not silently change Bluetooth, route or mute state.

On the target S22+, `adb shell cmd audio adj-mute 0` was physically verified to be idempotent and reversible; restore uses `adj-unmute 0`.

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. Physically proven: frozen Samsung cellular bridge/fail-safe, protected live-probe off-call refusal, preflight observability and voice-call mute command. Not yet proven: genuine OpenAI S22 session, cellular Realtime audio or a real autonomous external task.

Exact current gate: `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.
