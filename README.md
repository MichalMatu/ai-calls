# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to selectable AI engines without external audio hardware.

Target: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Proven foundation

- cellular RX/TX + fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned output approval: `DONE / PROVEN_S22`;
- product-owned local runtime/readiness/prepared-call boundary: `DONE`;
- deterministic `CallPlan + PhraseMatrix` final-STT path: `HOST_GREEN`;
- controlled Gate C cellular RX -> STT -> deterministic routing -> approved TTS -> cellular TX: `PROVEN_S22`;
- live endpointing signals: physically proven, production endpoint state machine still separate/open.

The frozen Samsung implementation is documented in `docs/PHASE2D_FREEZE_2026-09-18.md`. Do not change it during ordinary Gate C work.

## Current product direction — Gate C / Orange Service Pack Explorer

Gate C is no longer waiting for its first live proof. The current active slice is building the first evidence-backed deterministic service pack for the Orange support line at the exact allowlisted target `510100100`.

The Orange front door is voice-first: Max asks what the call is about. It is not a fixed numeric DTMF menu at the root.

### Physical live checkpoint

`v115` physically proved the full deterministic live chain on the S22:

```text
Orange cellular RX
 -> local STT
 -> LocalTextCallSession
 -> PhraseMatrix
 -> CallPlan rule validation
 -> application-owned output approval
 -> local TTS
 -> cellular TX
```

The reviewed action `list_capabilities` sent exactly `Jakie sprawy możesz załatwić?`, produced nonzero cellular TX, kept `backend_generate_calls=0`, then ran an `OBSERVE_ONLY` turn. Max replied with a reprompt asking the caller to state the subject of the call.

Evidence:

```text
.agent/results/chatgpt-orange-explorer-live-v115-20260921.json
```

`v123` physically proved the same path for the reviewed action `invoice_status`, which sent exactly `Chcę sprawdzić fakturę.` with `backend_generate_calls=0` and nonzero cellular TX. Max again returned the same root reprompt instead of entering a verified invoice route. Therefore the invoice service seed is `DISCOVERED`, not `VERIFIED`.

Evidence:

```text
.agent/results/chatgpt-orange-invoice-live-v123-20260921.json
```

### Durable Orange service tree

Current evidence-backed data lives in:

```text
service-packs/orange/service_tree.v1.json
```

Current verified nodes:

- `orange.root` — Max voice intent router;
- `orange.root.reprompt` — Max asks again what the call concerns.

Current verified observed edges:

- `orange.root.list_capabilities -> orange.root.reprompt`;
- `orange.root.invoice_status -> orange.root.reprompt`.

The second edge verifies the observed result of the exact reviewed utterance, not a successful invoice route.

Service-state discipline:

```text
SEED -> DISCOVERED -> VERIFIED
```

Only physical call evidence may promote a route to `VERIFIED`. Public Orange documentation may seed a backlog but is not route proof.

### Explorer authority boundary

Orange discovery is deterministic and fail closed:

- exact target only: `510100100`;
- speech-producing actions are named reviewed constants, never arbitrary CLI/model text;
- `OBSERVE_ONLY` has zero `SAY` rules and cannot release speech;
- unknown/changed root input falls back to `TAKE_OVER`;
- `GateCFastPathSentinelBackend` must remain at `backend_generate_calls=0`;
- application-owned output approval remains mandatory before TTS/TX.

Known physically observed root-acquisition fragments that may be ignored only in the bounded root-acquisition phase are exact values:

```text
orange
jakości orange
5g jakości orange
```

There is also a bounded retry for Android `SpeechRecognizer` `ERROR_NO_MATCH(7)` only when the strict root-acquisition report conditions prove real speech, trailing-silence endpointing, bound Gate C and zero backend generation. `OBSERVE_ONLY` never uses these retries.

Latest host checkpoint:

```text
.agent/results/chatgpt-orange-service-tree-final-green-v125-20260921.json
```

It passed the Orange tree/runner tests and the full canonical `bash scripts/verify_host.sh` gate on product/data checkpoint `5b0f599b98aefcef2279490cb14824f70e63ea17`.

## Authority ownership

Authority remains in the existing product owners:

- `CallTask` — immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` — concrete target only, no allowlist expansion;
- `CallWorkflow` — progress, proposals, user decisions and terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — exact one-shot commitment permit;
- application-owned output approval — required before TTS/TX.

Models, helpers, matchers, discovery tools and Agent Skills remain proposal/classification-only.

## Matcher engine decision

Keep the small native Kotlin `PhraseMatrix` as the production matcher direction. RiveScript and ChatScript remain reference material only; KStateMachine remains deferred. Do not broaden fuzzy matching merely to make a live Orange prompt pass.

## Next engineering slice

Continue the Orange service pack as an evidence-driven exploration loop:

1. choose one unverified Orange capability seed;
2. define one exact reviewed non-committing utterance and RED tests;
3. minimal GREEN through the existing `CallPlan + PhraseMatrix` authority path;
4. run targeted regressions and `bash scripts/verify_host.sh`;
5. with explicit current-session authorization, execute one bounded allowlisted Orange call and an `OBSERVE_ONLY` follow-up;
6. persist the observed node/edge/result into `service_tree.v1.json`;
7. if the branch reaches authentication, customer data, payment or a state-changing confirmation, record that barrier and move to another branch instead of finalizing it.

The final product target is:

```text
natural user intent
 -> validated Orange service_id
 -> verified deterministic route
 -> bounded call execution
 -> auth/confirmation only where the verified route actually requires it
```

Exact continuation instructions live in `docs/HANDOFF_NEXT_CHAT.md`.

## Frozen / deferred experiments

- general-purpose phone-local llama.cpp model sweep: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only;
- `OPENAI_TEXT` / `OPENAI_REALTIME_AUDIO`: preserved but deferred;
- `LOCAL_MAC_LLM`: preserved provider option;
- future local realtime audio: later gate.

Live Orange calls require explicit current-session operator authorization and the exact allowlisted destination; authorization from a previous chat must not be assumed.

## Repository workflow

Authoritative current docs:

- `AGENTS.md` — work rules and active engineering constraints;
- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation point;
- `docs/ROADMAP.md` — gate matrix;
- `docs/ARCHITECTURE.md` — ownership boundaries;
- `docs/SECURITY_PRIVACY.md` — authority/privacy rules;
- `docs/PHASE2D_FREEZE_2026-09-18.md` — frozen Samsung media invariants.

Durable product code/docs live on `main`. Local Agent tasks/results stay on `agent-control`.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

`HOST_GREEN` is not `PROVEN_S22`; hardware/OEM claims require physical S22 evidence.
