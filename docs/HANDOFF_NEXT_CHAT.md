# Handoff — local phone LLM + live-call integration

Date: 2026-09-19

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here

Read only what is relevant:

1. `AGENTS.md`
2. this file
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/SECURITY_PRIVACY.md` when touching model/network/credentials
6. `docs/PHASE2D_FREEZE_2026-09-18.md` before changing Samsung media internals.

Then verify `main` HEAD and `.agent/status/daemon.json`. In a new chat bootstrap the repository Local Agent and use that chat's fresh binding; never copy an old chat binding.

## Product direction

The current priority is a fully local telephone agent on the S22:

```text
telephony RX
  -> on-device STT
  -> local text LLM on the S22
  -> application-owned approval/commitment rules
  -> local TTS
  -> telephony TX
```

Runtime selection remains selectable. OpenAI Realtime stays preserved/frozen unless explicitly resumed.

Target local text providers are now:

```text
OPENAI_TEXT
LOCAL_PHONE_LLM
LOCAL_MAC_LLM
```

MCP belongs to the tools/context layer, not the LLM-provider selector.

## Current durable checkpoints

Runtime selectors:

```text
1eb81f1202a8a0b4aace6231c73656ef14920c3d
feat: add selectable speech and llm modes
```

Production local speech adapters:

```text
e953b78ea2b56c3bbc62fded3da295c23ccbf1bb
feat: add production local speech adapters
```

Provider-neutral local text pipeline:

```text
67a1bc75e7deb55ec0e4e515ef26195c4587edae
feat: add local text agent pipeline
```

Local Mac OpenAI-compatible backend:

```text
d9e705a2e38f73cc6653b43a6e990e590577e909
feat: add local Mac text backend
```

Full on-device phone-LLM speech pipeline probe:

```text
e0cfd0971103568e9a540ee75f6555d2bc8b66d0
test: add local phone llm speech pipeline probe
```

Phase 2D frozen Samsung media remains at:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
PROVEN_S22
```

## PROVEN_S22 local phone-LLM evidence

On exact S22+ serial `RFCT70L7E8J`, Android 16 / API 36:

- `llama.cpp` Android arm64 `llama-server` runs directly on the phone;
- Qwen2.5-0.5B-Instruct Q4_K_M GGUF is present on-device and loads successfully;
- `/health` responds on phone loopback;
- a direct Polish prompt returned `Lokalny model na telefonie działa.` in about 0.62 s for the small smoke request;
- short generation measured roughly 58 tokens/s in that smoke;
- the existing OpenAI-compatible text backend can call the phone-loopback server;
- model output passed the application-owned approval policy and local TTS produced non-empty PCM;
- the complete off-call pipeline is physically proven:

```text
local TTS test phrase
  -> on-device STT
  -> Qwen on the same S22
  -> application-owned approval
  -> local TTS response
```

The successful physical report included:

```text
stt_text=to jest test lokalnego modelu na telefonie
backend_complete_response=true
approved_text_nonblank=true
approved_output_pcm_nonempty=true
local_phone_llm_speech_pipeline_success=true
```

Call state remained idle before and after.

## Immediate next gate

Promote the proven local phone LLM from diagnostic plumbing to normal runtime provider `LOCAL_PHONE_LLM`.

Requirements:

1. provider selection behind the existing `TextCallAgentBackend` boundary;
2. production lifecycle/readiness checks for the phone-local inference endpoint/runtime;
3. cancellation/timeouts/fail-closed behavior;
4. no model output bypasses proposal parsing, confirmation policy, commitment authorization or output approval;
5. host tests + `bash scripts/verify_host.sh`;
6. physical off-call regression proof on the exact S22 after runtime wiring.

Do not destructively refactor the frozen Samsung RX/TX media implementation.

## Controlled automated live-call gate

After product runtime wiring is green, connect the proven local pipeline to frozen telephony RX/TX.

Automated dialing/hangup is now allowed by project policy for the dedicated test SIM, but only under the guardrails in `AGENTS.md`:

- destination must be explicitly operator-defined and allowlisted for the test;
- model/tool output cannot create or widen the allowlist;
- one active call at a time with bounded retries/cooldown;
- no emergency, premium-rate, arbitrary short-code, bulk or enumerated dialing;
- the runner may hang up a call it created as bounded cleanup;
- preserve exact direct-USB and media-route validation before AI media injection.

For the next test, use a specifically allowlisted Orange customer-service/infoline destination supplied or verified for the test. Validate:

```text
allowlisted dial
  -> active cellular call
  -> telephony RX
  -> local STT
  -> local phone LLM
  -> application approval
  -> local TTS
  -> telephony TX
  -> bounded cleanup / hangup
```

Collect transcript/response timing, TAKE OVER behavior, cleanup and call-state evidence. Do not make service commitments or account changes without the existing user-decision/commitment authorization flow.

## Frozen OpenAI Realtime option

The existing Realtime stack, host credential broker, Quick Tunnel lab and controlled live-call path stay intact as a selectable alternative. A standard OpenAI API key remains host/backend-only. Do not place it in source, APK, Intent, ADB arguments or Android storage.
