# AI Calls

Android prototype for completing bounded real-world tasks over ordinary cellular calls on a stock Samsung Galaxy S22+ (`SM-S906B`).

## Product direction

The product is not an Orange/CLIR bot and not an appointment-only bot. The target is a **generic autonomous phone task engine**:

```text
user task
 -> exact target + constraints + authorized facts
 -> cellular call
 -> STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skills
 -> supervisor fallback when needed
 -> application-owned validation / output approval
 -> TTS
 -> exact external effect authorization
 -> factual success evidence
```

Examples of the same product mechanism:

- enable a carrier setting such as CLIR;
- call a clinic and book an appointment within time/price constraints;
- change or cancel a reservation;
- call a service provider and resolve a bounded request;
- obtain information without making any external commitment.

Service-specific knowledge may help dialogue, but it must not become the authority model.

## Stable proven foundation

The following are already physically proven on the S22 and should remain frozen unless a concrete root cause requires reopening them:

- Samsung cellular RX/TX and `CallMediaSessionCoordinator`;
- `privileged-helper/` / Shizuku media boundary;
- local Polish STT/TTS foundation;
- IdentityVault encryption/disclosure boundary;
- existing `BOOK_APPOINTMENT` Gate D proposal/confirmation/commitment/completion flow;
- Gemma 4 direct LiteRT-LM inference;
- application-owned Gemma import/download/readiness lifecycle.

Current local model:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
runtime=LiteRT-LM
```

The full reviewed network acquisition, atomic activation and post-download no-call inference are `PROVEN_S22`. Do not redownload the model merely to reprove it.

## Dialogue architecture

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: deterministic owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skill classifier
 -> app-owned reviewed response
 -> Gemma error / low confidence / TAKE_OVER: supervisor/ChatRelay fallback
 -> application output approval
 -> TTS/TX
```

Gemma produces bounded dialogue decisions, not business authority. The supervisor may help choose a conversational response, but cannot widen the task, target, disclosed facts or external effects.

## Next architecture step

`CallTask` is already generic, but the existing commitment layer is still coupled to appointment-shaped `CallProposal` data. The next implementation scope is to generalize the **commitment subject** into a typed application-owned external effect while preserving the proven appointment path.

Conceptually:

```text
CallTask
 -> candidate ExternalEffect
 -> validate against target/task/constraints/user authorization
 -> exact one-shot commitment permit
 -> effect-specific execution/speech
 -> effect-specific success evidence
 -> factual completion
```

CLIR is the first acceptance case for this generic mechanism. A clinic booking is the next broader case. Do not create a parallel `ClirCommitmentGate` or grow Orange exact-phrase mappings into product architecture.

See `docs/GENERIC_PHONE_TASK_AUTHORITY.md`.

## Latest physical live-call checkpoint

On 2026-09-24 the Orange call path was re-exercised:

- dialing and `OFFHOOK` were reconfirmed;
- real Orange downlink audio was reconfirmed;
- `scripts/chatgpt_relay_live_call.py` was hardened against transient ADB `dumpsys audio` / hangup failures;
- after reinstall, Android microphone permission had to be granted again;
- subsequent no-call probe confirmed both Shizuku and microphone permission and completed normally.

A full CLIR account change has **not** yet been completed. No live-call authorization is carried across chats.

## Source of truth

- `AGENTS.md` — repository workflow and invariants;
- `docs/ROADMAP.md` — execution order;
- `docs/ARCHITECTURE.md` — component/authority ownership;
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md` — target generic effect/commitment model;
- `docs/SECURITY_PRIVACY.md` — authority/privacy/live-call rules;
- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation checkpoint;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste next-chat instruction.

Every real call requires fresh authorization in the current chat for the concrete target/number and task. A handoff, connected phone, previous proof or old allowlist never authorizes dialing.