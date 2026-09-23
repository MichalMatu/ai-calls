# Orange live acceptance — checkpoint and pause

Date: 2026-09-23

## Decision

Pause further Orange IVR automation work at the current checkpoint.

The physical call path is proven enough to support architecture discussion, but the current Orange-specific interaction layer is **experimental and brittle**. Do not keep extending it by adding more exact utterance variants or one-off follow-up actions.

Status:

```text
MEDIA / STT / TTS PATH: PROVEN_S22
REVIEWED SINGLE-TURN ORANGE RESPONSE: PROVEN_S22
ORANGE IVR AUTOMATION: EXPERIMENTAL / BRITTLE / PAUSED
CLIR ACTIVATION: NOT COMPLETED
NO FURTHER LIVE CALLS IN THIS CHECKPOINT
```

## What is physically proven

On Samsung S22+ (`SM-S906B`, Android 16):

- cellular call control reached `IDLE -> OFFHOOK -> IDLE` correctly;
- direct USB ADB, in-call audio routing and downlink capture worked;
- Shizuku privileged probe worked after restoring its runtime authorization;
- `RECORD_AUDIO` permission was required and restored after reinstall;
- STT captured Orange IVR speech;
- reviewed application-owned TTS was injected into the live call;
- Orange reacted to the injected speech;
- cleanup/hangup returned the phone to `IDLE`.

The strongest bounded live proof was the reviewed CLIR request:

```text
approved_text=Chcę włączyć usługę CLIR, czyli stałą blokadę prezentacji mojego numeru przy połączeniach wychodzących.
```

Orange then replied, approximately:

```text
Czy sprawa dotyczy numeru, z którego dzwonisz?
```

The call was intentionally stopped after observation. No CLIR account change was completed.

## Why the current Orange method is too brittle

The current Gate C Orange exploration relies on reviewed phrase/rule matching against concrete STT strings. This is useful as a diagnostic harness but not a robust production dialogue layer.

Observed failure modes during the live acceptance work:

1. **IVR wording and prerolls vary.**
   - examples included fragments such as `5g jakości orange` before the real root prompt;
   - exact wording changes cause `TAKE_OVER` even when the semantic intent is obvious.
2. **One-off action growth does not scale.**
   - adding another exact branch for every Orange follow-up creates a fragile operator script rather than a reusable call agent.
3. **Environment state matters.**
   - reinstalling the debug APK reset `RECORD_AUDIO`;
   - Shizuku kept a separate authorization state and also had to be restored;
   - missing grants looked like silent STT failures until explicitly diagnosed.
4. **Developer relay plumbing is useful but not a product boundary.**
   - transient Git relay refs/worktrees are acceptable for diagnostics, not for runtime conversation control;
   - stale local relay refs can block a session before dialing.
5. **Telephony diagnostics can be transient.**
   - repeated `dumpsys telephony.registry` checks were unreliable while a call was active;
   - the relay was hardened to distinguish registry transients from real ADB failures.

## What should remain frozen

Do not reopen or rewrite the physically proven Samsung media path merely because the Orange IVR script is brittle.

Do not add more exact Orange follow-up phrases as the default strategy.

Do not give an LLM direct authority to dial, widen the target, disclose secrets, approve proposals, commit, complete, or mutate account state.

Gate D ownership invariants remain unchanged.

## Recommended next architecture discussion

The next design step should be **operator-agnostic dialogue control**, not more Orange phrase mapping.

A better direction is a constrained dialogue-state layer with:

```text
observed utterance
 -> semantic interpretation into a small typed intent/event set
 -> app-owned dialogue state + expected-slot validation
 -> bounded candidate response
 -> authority/policy re-check
 -> reviewed or generated speech
 -> observe next turn
```

Key properties to discuss before implementing:

- semantic matching instead of exact transcript strings;
- explicit confidence / ambiguity handling;
- `TAKE_OVER` when the observed turn is outside the expected state;
- operator adapters only for stable facts such as phone number, known DTMF/USSD commands or documented capabilities;
- no operator-specific business logic in the generic dialogue reducer;
- preflight capability state for microphone/Shizuku/device readiness before dialing;
- replayable offline conversation fixtures built from redacted transcripts;
- deterministic tests for dialogue state transitions;
- model/shadow interpretation allowed only as a proposal source, never as authority.

## Candidate next-step options

Discuss these before writing more production code:

1. **Generic dialogue reducer** — typed states/events/slots, with Orange as only one fixture/adaptor.
2. **Semantic classifier layer** — map STT to bounded intents with confidence + deterministic fallback.
3. **Hybrid deterministic/model interpretation** — deterministic first, bounded model shadow/candidate second, existing app-owned authority unchanged.
4. **Stable non-speech channel where available** — documented USSD/DTMF/API for narrow tasks instead of conversational IVR scraping.
5. **Human takeover boundary** — preserve the live media path but hand control to the user when identification, secrets or unexpected dialogue appears.

## Repository checkpoint

At this pause point:

```text
main = 9658a55981c4aed5ebc0b2445e65dc52ab7e064d
remote branches = main, agent-control
```

The reviewed Orange CLIR root action remains in `main` as diagnostic evidence. No bounded own-line follow-up action was merged; the attempted v090 task was rejected before execution because its task JSON was invalid.

This checkpoint is intentionally suitable for architecture discussion before any further live-call automation.
