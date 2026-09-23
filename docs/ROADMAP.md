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

## Active gate — robust hybrid dialogue with Gemma 4

The current task is not another Gate D slice and not more Orange exact-phrase mapping.

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

### Implemented pieces

1. `PhraseResponseTemperature` with `HOT/WARM/UNCERTAIN/COLD/AMBIGUOUS`.
2. deterministic WARM routing only when one candidate wins by threshold/margin.
3. typed bounded dialogue skills with `skill/confidence/reason`.
4. app-owned exact response text; model does not own arbitrary speech.
5. explicit unresolved-turn routing mode for reviewed hybrid sessions; default/Gate D behavior remains unchanged.
6. failover backend path for local model -> injected-response fallback.
7. direct Gemma 4 LiteRT-LM backend on Android.
8. `litertlm-android:0.17.1` dependency.

Implementation checkpoint before latest handoff docs:

```text
e35152f78446e69df8b98f4f403943751eb1a130
Use direct Gemma 4 backend for Android text calls
```

Always use fresh `origin/main` rather than hard-coding this SHA.

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

The S22 already contains the Gemma 4 model downloaded by Edge Gallery in external storage, including:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

A second installed Edge Gallery variant also has the same model in its external files.

Do not make production depend on another application's private sandbox. For development proof, Local Agent/ADB may copy an already-downloaded model from accessible external storage into the app-owned model location.

Current app-owned target:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

## Immediate execution order

1. Fetch fresh `origin/main` and fresh Local Agent daemon/binding evidence.
2. Run targeted host tests for response-temperature, skill backend, hybrid routing and direct Gemma backend.
3. Run `bash scripts/verify_host.sh` on current HEAD.
4. Verify Android compile/package with LiteRT-LM dependency.
5. Provision `gemma-4-E2B-it.litertlm` into the app-owned model directory.
6. Run physical S22 **no-call** Gemma dialogue-skill test using synthetic text only.
7. Require parsed bounded `skill/confidence/reason` and no telephony/media side effect.
8. If the physical test fails, classify the exact layer before changing code:
   - model path/provisioning;
   - LiteRT Engine initialization;
   - GPU backend / CPU fallback;
   - native runtime/dependency;
   - memory/timeout;
   - JSON `ResponseFormat`;
   - skill parser/policy.
9. After Gemma no-call proof, run synthetic hybrid-flow regressions:
   - HOT/WARM deterministic;
   - unresolved -> Gemma;
   - low confidence/error -> injection fallback;
   - telemetry identifies Gemma decision and final response source.
10. Only then consider another bounded live acceptance call.

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

Do not continue growing exact Orange phrase aliases as the main strategy. Resume Orange only after the generic hybrid dialogue path is green or when it tests a specific generic capability.

## Live-call stop line

A connected phone, existing allowlist, old chat or previous successful call never authorizes a new call.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.
