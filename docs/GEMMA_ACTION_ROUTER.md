# Gemma-First Dialogue Action Router

## Decision

The live-call dialogue path is Gemma-first. Natural-language understanding is no longer implemented as a growing collection of Orange-specific phrase matchers or scripted first/second turns.

The runtime is split into three boundaries:

1. **Classifier (Gemma)** — interprets the other party and returns one structured action.
2. **Policy + executor (application-owned)** — validates confidence, allowed actions/arguments, disclosure/commitment readiness, and produces the exact app-owned response or control token.
3. **Control plane** — owns call lifecycle, identity facts, external-effect permits, success evidence, independent verification, evidence retention, and cleanup.

Gemma never receives authority to read arbitrary secrets, invent fact values, consume external-effect permits, or declare task success.

## Core action vocabulary

The initial generic vocabulary is:

- `ASK_REPEAT`
- `ASK_CLARIFY`
- `ACKNOWLEDGE_NEUTRAL`
- `STATE_TASK_SUBJECT`
- `DISCLOSE_AUTHORIZED_FACT`
- `CONFIRM_AUTHORIZED_EFFECT`
- `TAKE_OVER`

`DISCLOSE_AUTHORIZED_FACT` carries a symbolic argument such as `PHONE`; the model never receives or returns the value itself.

## CLIR mapping

For the current Orange CLIR campaign:

- purpose-of-call prompt -> `STATE_TASK_SUBJECT` -> app-owned CLIR subject phrase,
- request for service number -> `DISCLOSE_AUTHORIZED_FACT(argument=PHONE)` -> host action control token -> runtime-only number executor,
- request to confirm activation -> `CONFIRM_AUTHORIZED_EFFECT` -> app-owned commitment control, only if the route is independently verified,
- unsupported or low-confidence input -> `TAKE_OVER` / error -> interactive supervisor relay.

The host no longer decides these intents by matching Polish natural-language phrases. Host-side deterministic logic consumes only exact control tokens emitted after model classification.

## Invariants

- STT transcript may be shown to Gemma; identity plaintext is not.
- Model output is structured JSON with a closed action enum.
- Model output is untrusted until policy validation succeeds.
- Action arguments are allowlisted per task.
- External effects still require `CallCommitmentGate`.
- Success remains factual external evidence plus independent verification; a model statement is never enough.
- Low confidence, malformed output, unsupported action/argument, or state mismatch fails closed to takeover/fallback.
- Host scripts remain responsible for build/test/device lifecycle/evidence, not natural-language dialogue interpretation.

## Migration rule

New conversational behavior should be added as a reusable action/skill and executor capability. Do not add provider-specific natural-language `if`/regex branches for IVR wording unless they are evidence/safety guards rather than dialogue routing.
