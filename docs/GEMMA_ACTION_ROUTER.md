# Gemma-first dialogue action router

## Contract

Natural-language dialogue routing is Gemma-first; application policy and executors remain authoritative.

```text
finalized STT
 -> Gemma returns one structured action
 -> policy validates action / argument / confidence / state
 -> app-owned executor produces speech or a typed control
 -> output approval -> TTS/TX
 -> TAKE_OVER/failure falls back to the live supervisor
```

Gemma never receives identity plaintext, commitment authority or permission to declare external success.

## Action vocabulary

- `ASK_REPEAT`
- `ASK_CLARIFY`
- `ACKNOWLEDGE_NEUTRAL`
- `STATE_TASK_SUBJECT`
- `DISCLOSE_AUTHORIZED_FACT`
- `CONFIRM_AUTHORIZED_EFFECT`
- `TAKE_OVER`

Arguments are symbolic and allowlisted. For the current CLIR task the only fact argument is `PHONE`.

## Current proof

The real Gemma 4 model on the S22 passes the current 8-case off-call action-router probe, including task-subject, service-number, effect-confirmation, repeat and unsupported-sensitive-fact cases.

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
