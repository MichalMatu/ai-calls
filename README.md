# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

PR #5 was squash-merged as:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

The Samsung cellular/media path and Gate D authority chain remain stable/frozen.

The active dialogue architecture is now **HOST_GREEN**, and the direct Gemma 4 no-call inference boundary is **PROVEN_S22**.

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

`PhraseMatrix` exposes bounded response-temperature bands:

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

The development proof copied the already-downloaded Edge Gallery model into that app-owned path using the app UID. Source and target SHA-256 matched:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

A plain `adb shell cp` is not sufficient on this Samsung build: the resulting file could not be opened by LiteRT from the app process. Writing the file through `run-as pl.michalmatu.aicallbridge` produced app-readable ownership/SELinux labeling and fixed the model-path failure.

Production must not depend on Edge Gallery storage; the application still needs an explicit owned import/download lifecycle for the model.

## Verification state

The direct Gemma 4 path is now **PROVEN_S22 for no-call synthetic dialogue-skill inference**:

- app-owned model hash verified;
- LiteRT-LM JNI/native runtime loaded;
- GPU delegate initialized on S22;
- physical instrumentation completed successfully;
- terminal bounded output contained `skill/confidence/reason`;
- no telephony/media path was started.

Observed physical proof:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

The synthetic hybrid contract is **HOST_GREEN**:

- HOT/WARM remain on deterministic owner paths;
- bounded local skill completion records `LOCAL_SKILL` as the final source;
- low confidence falls through once to injected-response/ChatRelay and records `CHAT_RELAY`;
- local classifier error also falls through once and records `CHAT_RELAY`;
- diagnostics retain the bounded model decision and final response source.

No live call was made for these proofs.

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

## Next gate

The next product engineering gap is explicit application-owned Gemma model acquisition/import rather than relying on an ADB development copy from Edge Gallery.

A future bounded live acceptance call may be considered only after fresh explicit authorization in that same chat for one concrete target/number and one concrete task. Current proof/handoff state never authorizes dialing.

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
