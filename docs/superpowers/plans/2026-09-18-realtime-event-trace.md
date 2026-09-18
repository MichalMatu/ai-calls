# Realtime event-trace implementation plan — 2026-09-18

## Goal

Prepare deterministic, privacy-safe evidence for the first genuine OpenAI S22 session and first cellular Realtime call without changing frozen Samsung media behavior.

The trace must answer:

- did the Realtime transport connect;
- what GA output/function lifecycle ordering actually occurred;
- which events belonged to the same response/output part/function call;
- when remote speech, first output audio, response completion and cancellation happened relative to session start.

It must not become a transcript/recording/logging feature.

## Security constraints

Never retain in the trace:

- PCM contents;
- transcript text;
- function arguments or function outputs;
- long-lived or short-lived credentials;
- raw Realtime `response_id`, `item_id`, or `call_id` values;
- raw exception messages.

Stable correlation identifiers are local aliases (`R1`, `I1`, `C1`). Only event type, elapsed time, sizes/counts, terminal status, function name and exception class may be retained.

Both the event ring and identity-alias tables must be bounded.

## Slice 1 — bounded trace + transport decorator

Add a transport-neutral `RealtimeEventTrace` and `TracingRealtimeTransport` in `realtime-client`.

Record:

- connect start/success/failure;
- response cancellation;
- function-output submission metadata;
- identified/unidentified output audio size only;
- transcript delta/final character count only;
- output-audio completion;
- response completion/status;
- remote speech start/stop;
- function call identity/function name only;
- error class;
- transport close.

Host tests must prove redaction, alias stability, bounded event count and bounded identity maps.

## Slice 2 — production runtime opt-in

`CallRealtimeAgentRuntime.create` accepts an optional caller-owned trace. When present, each fresh transport generation is wrapped before reaching the session controller. When absent, production behavior is unchanged.

The trace must not own session lifecycle and must never delay TAKE OVER or transport cleanup.

## Slice 3 — off-call smoke evidence

After Slice 1/2 host GREEN, pass a trace into `RealtimeNetworkOffCallSmokeProbe` and return only its compact redacted rendering with smoke evidence.

Extend the host smoke parser/tests to accept the optional trace line without changing PASS criteria:

`FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> FAILED`

The trace supplements, never replaces, the existing fail-closed state gate.

## Slice 4 — first live-call evidence

After genuine OpenAI off-call PASS, use the same trace in the controlled non-committing cellular Realtime validation. Correlate event order/latency without persisting speech content.

Do not claim GA ordering or latency as physically proven until captured on the target S22+.

## Verification

For each behavior commit:

- focused trace/decorator tests;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- all Python script tests;
- production secret-pattern scan;
- `git diff --check`;
- clean working tree.

No cellular call is needed for these host-only slices.
