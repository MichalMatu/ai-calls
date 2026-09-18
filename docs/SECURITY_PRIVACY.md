# Security and privacy

## Security objective

The agent may speak and act only inside authority explicitly granted by the user. Technical failure must disable AI injection or return control to the human caller; it must never broaden authority.

## Privilege boundary

Protected Samsung call-audio access stays inside `privileged-helper` / Shizuku UserService. The normal app does not directly own private Samsung audio primitives.

Continuous PCM crosses through transferred PFDs. Binder/AIDL is control only. The helper has no model networking and no business-policy authority.

## Selectable engine boundary

The app supports or plans three audio modes:

```text
LOCAL_STT_TTS
OPENAI_REALTIME_AUDIO
LOCAL_REALTIME_AUDIO
```

`LOCAL_STT_TTS` additionally selects a text-model provider:

```text
OPENAI_TEXT
LOCAL_MAC_LLM
```

Model/provider selection never changes user authority. `CallTask`, deterministic confirmation policy, commitment authorization, output approval and TAKE OVER remain application-owned.

MCP is a tools/context integration layer, not a model provider. MCP/local tool calls must pass the same application-owned authorization boundaries as provider-native function calls.

## Local speech privacy boundary

The preferred local speech path keeps speech recognition and synthesis on the S22 when using Android on-device `SpeechRecognizer` and non-network-required `TextToSpeech` voices.

Physically proven on the target S22+:

- `pl-PL` on-device STT model is installed;
- local Polish TTS voices are available;
- caller-supplied PCM16LE mono 16 kHz can be delivered to STT through a PFD pipe;
- a known Polish phrase round-trips locally through TTS -> PCM -> STT.

Production local speech code should:

- use only on-device recognition mode for `LOCAL_STT_TTS`;
- reject/fail closed if the required language model is unavailable rather than silently falling back to cloud recognition;
- choose TTS voices with `isNetworkConnectionRequired == false`;
- avoid retaining synthesized files/PCM after the active generation;
- close both ends of temporary PFD/pipe resources on completion, cancellation or TAKE OVER;
- never treat diagnostic probe files as durable user data.

## OpenAI credential boundary

A standard OpenAI API key must never be:

- embedded in source/resources/BuildConfig/APK;
- stored in Android app-private smoke configuration;
- passed through an Android Intent;
- passed in ADB argv/process arguments;
- logged by the app, helper or scripts.

For the preserved OpenAI Realtime mode, expected flow remains:

```text
host/backend OPENAI_API_KEY
  -> authenticated developer broker
  -> short-lived Realtime client secret
  -> Android
  -> trusted OpenAI Realtime WebSocket
```

`scripts/realtime_credential_broker.py` binds loopback by default, reads the long-lived key from host environment, requires a distinct Android bearer and returns only short-lived credential fields. Device smoke configuration is staged over ADB stdin and deleted on read.

The genuine OpenAI Realtime smoke uses a protected/authenticated HTTPS path to that loopback broker. `scripts/realtime_offcall_lab.py` provides the development path with a one-shot bearer and temporary Quick Tunnel. Quick Tunnels remain development/test infrastructure, not the production credential service.

`OPENAI_TEXT` must follow the same core rule: the long-lived API key stays off Android. A future text endpoint should expose only the narrow authenticated capability required by the app; do not solve text mode by embedding a standard API key in the APK.

## Local Mac LLM boundary

`LOCAL_MAC_LLM` is a model-provider choice, not a trust bypass.

Preferred deployment:

```text
S22 app
  -> trusted LAN / authenticated narrow endpoint
  -> local model server on user's Mac
```

Requirements:

- do not expose the local model server publicly by default;
- bind to loopback/LAN intentionally, not all interfaces without need;
- use authentication or a device-specific session secret for write/action-capable endpoints;
- keep the endpoint narrow: model inference and explicitly permitted tool calls only;
- do not let the local model directly mutate commitment/workflow state;
- sanitize/limit model/tool diagnostics just as with remote providers;
- if the Mac is unavailable, fail the selected generation cleanly rather than silently switching to another provider that may have different privacy/cost semantics.

## Authority and commitments

Keep these categories separate:

- hard constraints — may not be autonomously exceeded;
- preferences — desired choices that may require a user decision;
- authorized facts — facts explicitly available to the task;
- model/counterparty text — untrusted input, never new authority.

Strict proposal decoding must stay side-effect-free. `CallConfirmationPolicy` evaluates a proposal. An allowed or explicitly user-approved proposal gets an opaque one-shot permit tied to that exact proposal. Commitment consumes the permit once.

Replacement proposal, session restart, TAKE OVER, close, stale generation or failed submission invalidates authorization. User approval of one proposal never widens standing authority.

## Speech/output integrity

No engine may speak unapproved output into the cellular uplink.

For `LOCAL_STT_TTS`, the safe first production behavior is:

```text
final caller transcript
 -> complete model candidate
 -> application policy/approval
 -> local TTS
 -> telephony TX
```

Do not synthesize a model response before approval merely to reduce latency. Later sentence/chunk streaming is allowed only if it preserves equivalent approval and commitment semantics.

For OpenAI Realtime, identified model PCM continues to be buffered before telephony TX. Release requires completed output and application-owned approval; cancelled, failed, incomplete and unsafe responses are dropped.

Transcript inspection is defense in depth, not cryptographic proof of audio samples.

## Diagnostics

Diagnostics should retain bounded metadata only where possible: relative timing, states, byte/character counts, terminal status, sanitized labels and local correlation aliases.

Do not retain by default:

- raw PCM;
- call recordings;
- full transcripts;
- model/tool arguments containing user data;
- credentials;
- raw provider IDs or exception payloads.

Temporary diagnostic speech files used for a physical probe should be app-private and deleted after the probe/generation.

## Data minimization

Do not collect/store by default:

- call recordings;
- raw PCM after an active session;
- full transcripts unless a product feature explicitly requires and discloses them;
- unnecessary counterparty identifiers;
- long-lived credentials.

Prefer state, size, timing, local aliases and redacted reasons in diagnostics.

## TAKE OVER invariant

Required ordering is local-first for every engine:

```text
stop accepting/releasing AI audio
-> abort local telephony media generation
-> stop local STT/TTS/audio workers
-> invalidate active model/tool generation
-> best-effort cancel/close remote or local model session
```

The first steps cannot wait for network/model acknowledgement. App/helper death must likewise disable injection.

## Controlled live-call preflight

Before any live AI-engine smoke, use the exact S22+ over direct USB ADB and require the selected engine's documented safety preconditions. The existing Realtime runner additionally requires Bluetooth OFF, `CALL_STATE=2`, `MODE_IN_CALL`, earpiece and muted voice-call stream before broker config is staged.

No validation runner may dial or hang up the cellular call unless a future explicit test requirement says so and is separately authorized.

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`.

Physically proven now:

- frozen Samsung cellular bridge/fail-safe;
- local on-device `pl-PL` STT availability/model installation;
- local Polish TTS synthesis;
- local TTS -> PCM16LE mono 16 kHz -> PFD pipe -> on-device STT round-trip on S22;
- protected Realtime live-probe off-call refusal/preflight observability/voice-call mute command.

Not yet proven:

- production local speech adapters in a real cellular call;
- `OPENAI_TEXT` telephone-agent path;
- `LOCAL_MAC_LLM` telephone-agent path;
- local realtime-audio model path;
- genuine OpenAI Realtime S22 session/audio;
- a real autonomous external task.

Exact continuation: `docs/HANDOFF_NEXT_CHAT.md` and `docs/ROADMAP.md`.
