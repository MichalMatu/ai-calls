# Gemma-first dialogue action router

## Contract

Natural-language dialogue routing is Gemma-first; application policy and executors remain authoritative.

```text
finalized STT
 -> Gemma returns one structured action
 -> policy validates action / argument / confidence / state
 -> app-owned executor produces speech or a typed control
 -> output approval -> TTS/TX
 -> TAKE_OVER/failure: app-owned call uses safe local recovery; only explicit developer paths may use a live supervisor
```

Gemma never receives identity plaintext, commitment authority or permission to declare external success.

## Current runtime vs planned skills

The current app-owned campaign has **Gemma-first structured action selection** plus deterministic, reviewed response text. It is **not** a free-running speech model. `GateCHybridDialogueBackendFactory` currently contains CLIR-specific `actionContext`, `actionPolicy` and `actionExecutor`; this is a migration target, not an independent authorization system.

**Planned**: versioned task-scoped skill declarations choose what the model is allowed to request (id, intent, parameters, facts/effects, preconditions, fallback, expected evidence). The application remains the sole authority for phone calls, disclosure, commitments and success verification. This plan is described in [organizational freeze](checkpoints/2026-11-03-organizational-freeze-f035-pre-voice-skills.md) and is **not yet implemented**.

Gemma is already prepared before Telecom dialing via `CampaignGemmaPreflight` (F-034). In-call latencies still need profiling after STT/model/TTS breakdown.

## Action vocabulary

- `ASK_REPEAT`
- `ASK_CLARIFY`
- `ACKNOWLEDGE_NEUTRAL`
- `STATE_TASK_SUBJECT`
- `DISCLOSE_AUTHORIZED_FACT`
- `CONFIRM_AUTHORIZED_EFFECT`
- `EXPLAIN_SERVICE_SCOPE`
- `ASK_NEXT_SERVICE_STEP`
- `REQUEST_SERVICE_TRANSFER`
- `TAKE_OVER`

Arguments are symbolic and allowlisted. For the current CLIR task the only fact argument is `PHONE`.

## Current proof

Previous model probes include an 8-case action-router acceptance and a later **7/7 contextual off-call Gemma** suite on S22. The latter was run before the F-035 install; it is not evidence of a complete external CLIR call. Current status is in the checkpoint.

For the Orange service-number prompt:

```text
Gemma -> DISCLOSE_AUTHORIZED_FACT(PHONE)
 -> app-owned local fact backend
 -> IdentityVault + AuthorizedFactSnapshot + FactDisclosurePolicy
 -> local plaintext resolution only after ALLOW
 -> approved speech
```

The supervisor/Git relay is not an identity-value transport. Unsupported arguments are normalized/fail-closed to takeover.

## Invariants

- model output is closed structured JSON and untrusted until policy validation;
- argumentless actions discard stray model arguments;
- unsupported fact identifiers fail closed;
- `CONFIRM_AUTHORIZED_EFFECT` is available only when application policy exposes that capability;
- external effects still require the shared `CallCommitmentGate`;
- factual success requires external evidence and independent verification when available;
- do not add provider-specific natural-language regex routing when a reusable action/skill can represent the behavior.
