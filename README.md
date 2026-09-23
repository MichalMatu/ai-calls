# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

PR #5 was squash-merged as:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

The Samsung cellular/media path and Gate D authority chain remain stable/frozen.

The active work is now **dialogue resilience + direct Gemma 4 local fallback**, prompted by real Orange IVR acceptance where exact phrase matching proved too brittle.

## Active dialogue architecture

```text
finalized STT
 -> PhraseMatrix
 -> response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skill classifier
 -> app-owned reviewed skill response
 -> local model failure / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> application output approval
 -> TTS
```

`PhraseMatrix` now exposes bounded response-temperature bands:

```text
HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS
```

WARM tolerates natural wording drift only when one rule wins by threshold and margin. Ambiguity remains fail-closed.

Dialogue Skills are structured model output (`skill/confidence/reason`); the application owns the allowed skill set and exact reviewed speech. The model never gains dial, disclosure, confirmation, commitment, completion or speech-release authority.

## Gemma 4 direction

For the current stage the target local model is:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
```

Qwen comparison/tuning is intentionally out of scope unless explicitly reopened.

The old assumption that Google AI Edge Gallery exposes an OpenAI-compatible HTTP server on `127.0.0.1:8080` was physically disproven on the S22. The product direction is direct in-process LiteRT-LM through:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

with dependency:

```text
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

The app-owned model target is:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

A copy of the model already exists on the phone in Edge Gallery external storage, so the next engineering gate is clean model provisioning into the app-owned path followed by a physical **no-call** Gemma skill proof.

## Stable Gate D authority

The reviewed BOOK_APPOINTMENT owner chain remains:

```text
finalized text
 -> deterministic/bounded interpretation
 -> TaskGraphApplyBridge
 -> CallWorkflow proposal owner
 -> explicit app-owned user decision
 -> exact one-shot CallCommitmentGate permit
 -> permit-consumption evidence
 -> deferred structured COMPLETE
 -> exact SUCCESS evidence validation
 -> CallWorkflow.complete(outcome)
 -> commit TaskGraph COMPLETE only after owner success
```

Hard invariant:

```text
permit issued != permit consumed != business success confirmed
```

No generic effect/completion executor exists.

## Verification state

Gate D and Samsung media have physical S22 evidence.

The new direct Gemma 4 path is **not yet PROVEN_S22**. The immediate next gate is:

1. current-head host/canonical regression;
2. Android compile/package with LiteRT-LM;
3. app-owned Gemma model provisioning;
4. physical S22 no-call skill inference using synthetic text;
5. synthetic hybrid fallback proof;
6. only then consider another bounded live-call acceptance test.

## Orange checkpoint

Real Orange acceptance proved call control, in-call audio, STT and reviewed TTS injection. A reviewed CLIR request was understood by Orange, which then asked whether the matter concerned the number being used for the call.

No CLIR account change was completed.

Orange exact-phrase mappings remain diagnostic fixtures. Do not grow them into the main conversation engine.

## Frozen foundation

Cellular RX/TX, `CallMediaSessionCoordinator`, Samsung `privileged-helper/`, local speech and proven Gate D authority remain frozen unless a concrete root cause requires reopening them.

Identity plaintext remains late-bound through:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> resolve plaintext only after ALLOW
```

## Sources of truth

- `docs/HANDOFF_NEXT_CHAT.md` — exact current continuation checkpoint;
- `docs/ROADMAP.md` — authoritative execution order;
- `docs/ARCHITECTURE.md` — component and authority ownership;
- `docs/SECURITY_PRIVACY.md` — privacy/live-call rules;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste continuation prompt;
- `docs/HANDOFF_PROTOCOL.md` — close-out/transfer rules.

Canonical local gate:

```bash
bash scripts/verify_host.sh
```

Every future live call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.
