# Roadmap

## Status vocabulary

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — targeted/canonical host evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a separate root-cause scope.

## Stable completed foundation

Gate D `BOOK_APPOINTMENT` is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

PR #5 was squash-merged as:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

The following stay frozen unless a new concrete root cause requires reopening:

- Samsung cellular RX/TX and `CallMediaSessionCoordinator`;
- `privileged-helper/` media boundary;
- local Polish STT/TTS foundation;
- Gate D proposal/user-confirmation/commitment/completion authority;
- Android IdentityVault encryption/disclosure boundary.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Robust hybrid dialogue with Gemma 4

Target flow:

```text
finalized STT
 -> PhraseMatrix
 -> response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: Gemma 4 bounded dialogue skill classifier
 -> app-owned reviewed skill response
 -> Gemma failure / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> output approval
 -> TTS
```

Current state:

- response-temperature routing: **HOST_GREEN**;
- typed bounded dialogue skills: **HOST_GREEN**;
- synthetic hybrid failover + diagnostics: **HOST_GREEN**;
- direct Gemma 4 LiteRT-LM Android package/runtime: **HOST_GREEN**;
- physical no-call Gemma 4 skill inference: **PROVEN_S22**;
- Samsung media path: still **PROVEN_S22 / FROZEN** and was not reopened.

### Implemented pieces

1. `PhraseResponseTemperature` with `HOT/WARM/UNCERTAIN/COLD/AMBIGUOUS`.
2. deterministic WARM routing only when one candidate wins by threshold/margin.
3. typed bounded dialogue skills with `skill/confidence/reason`.
4. app-owned exact response text; model does not own arbitrary speech.
5. explicit unresolved-turn routing mode for reviewed hybrid sessions; default/Gate D behavior remains unchanged.
6. failover backend path for local model -> injected-response fallback.
7. direct Gemma 4 LiteRT-LM backend on Android.
8. `litertlm-android:0.17.1` dependency.
9. hybrid diagnostics that record bounded model decisions, local-skill errors and final response source.
10. physical no-call terminal proof of the direct Gemma path.

## Model decision

For this stage use **Gemma 4 E2B IT only**.

```text
model: Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
```

Do not spend roadmap work comparing Qwen and Gemma unless the user explicitly reopens that scope.

The old `EdgeGalleryTextBackend` HTTP assumption (`127.0.0.1:8080`) is not the product direction. Physical diagnostics showed that the installed Edge Gallery app does not expose that server, even after launching its activity.

The product direction is direct in-process LiteRT-LM with an app-owned model path.

## Physical model evidence

The S22 contains the Gemma 4 model downloaded by Edge Gallery in external storage, including:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

The development proof provisioned the same bytes into:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

Verified SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Important provisioning finding: a plain `adb shell cp` created a file that LiteRT could not open from the app process (`PERMISSION_DENIED`). Writing the destination through the application UID with `run-as pl.michalmatu.aicallbridge` produced app-readable ownership/SELinux labeling and made the direct LiteRT engine path work.

Edge Gallery is only a development source for the already-downloaded bytes. Production must explicitly import/download and own its model.

## Proven no-call Gemma gate

Fresh-head verification completed successfully with:

1. targeted host response-temperature/dialogue/Gemma/hybrid tests;
2. `bash scripts/verify_host.sh`;
3. Android debug + AndroidTest compile/package with LiteRT-LM;
4. app-owned model hash verification;
5. physical S22 no-call instrumentation using synthetic text;
6. terminal `skill/confidence/reason` proof;
7. no telephony/media side effect.

Observed physical S22 decision:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

LiteRT loaded its JNI/native runtime and initialized the GPU delegate. The earlier failed run was isolated to model-file ownership/SELinux, not engine format, JSON `ResponseFormat`, parser, memory or timeout.

## Proven synthetic hybrid gate

Host contract evidence now covers:

```text
bounded skill -> reviewed app response -> source=LOCAL_SKILL
low confidence -> injected fallback -> source=CHAT_RELAY
classifier error -> injected fallback -> source=CHAT_RELAY
```

The diagnostics retain the parsed decision (`skill/confidence/reason`) when a decision exists and separately record the source of the final response. Existing PhraseMatrix tests keep HOT/WARM on the deterministic owner path and ambiguity fail-closed.

ChatRelay remains developer/injected-response fallback infrastructure, not product runtime authority.

## Next product engineering gate

The remaining generic product gap is model lifecycle/provisioning:

1. define an explicit app-owned Gemma import/download path;
2. verify integrity/version/expected model identity before activation;
3. keep runtime independent of Edge Gallery storage;
4. preserve the existing bounded skill policy and output approval;
5. rerun targeted/canonical tests and the no-call S22 model gate after provisioning changes.

A bounded live acceptance call is now technically eligible for consideration, but it is a separate authorization gate, not an automatic roadmap step.

## Authority invariant

Gemma, matchers, Skills, injected-response tooling, ServicePacks and TaskGraph helpers are proposal/classification layers only.

They cannot independently:

- dial or widen a target;
- disclose plaintext identity;
- release speech without output approval;
- confirm a user decision;
- issue/consume commitment authority;
- infer factual external success;
- complete the workflow/task.

The existing application owners remain authoritative.

## Orange status

Orange remains a useful ServicePack/acceptance fixture, not the active architecture owner.

Live evidence already proved:

- cellular control and cleanup;
- in-call audio/downlink;
- STT;
- reviewed TTS injection;
- Orange response to reviewed CLIR request.

No CLIR account change was completed.

Do not continue growing exact Orange phrase aliases as the main strategy.

## Live-call stop line

A connected phone, existing allowlist, old chat, previous successful call, documentation or no-call proof never authorizes a new call.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.
