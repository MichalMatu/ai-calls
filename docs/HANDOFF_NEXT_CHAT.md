# Handoff — Gate D done; dialogue resilience + Gemma 4 fallback is the active work

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Always fetch fresh `origin/main` before acting.

Current implementation checkpoint before this handoff documentation update:

```text
e35152f78446e69df8b98f4f403943751eb1a130
Use direct Gemma 4 backend for Android text calls
```

Expected durable remote branches remain:

```text
main
agent-control
```

Local Agent is currently idle. A new chat must use its own fresh bridge-provided binding; never copy the old binding from task history.

## Stable foundation — do not redo

Gate D `BOOK_APPOINTMENT` remains `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

The Samsung S22+ cellular/media path remains `PROVEN_S22 / FROZEN`:

- call control;
- downlink capture;
- local STT;
- reviewed TTS/uplink;
- Shizuku privileged media path;
- Gate D proposal/user-confirmation/commitment/completion authority.

Do not reopen `privileged-helper/`, `CallMediaSessionCoordinator`, Gate D authority owners, IdentityVault, commitment ordering or completion ordering unless a new concrete root cause points there.

## Why the Orange exact-phrase approach was changed

Real Orange acceptance proved that the physical call/STT/TTS path works, but exact IVR wording is too variable for a script that stops on every small transcript or timing variation.

Observed live example:

```text
AI: Chcę włączyć usługę CLIR, czyli stałą blokadę prezentacji mojego numeru przy połączeniach wychodzących.
Orange: Czy sprawa dotyczy numeru, z którego dzwonisz?
```

No CLIR setting was changed. The old strategy of adding another reviewed exact phrase/action per Orange follow-up is no longer the main direction.

## Active architecture: two-track dialogue resilience

The requested design is now explicitly two-track:

```text
finalized STT turn
 -> deterministic PhraseMatrix
 -> response temperature / semantic tolerance
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: local Gemma 4 skill classifier
 -> app-owned skill policy + exact reviewed response
 -> if local model fails / confidence too low / TAKE_OVER:
      existing injected-response / ChatRelay fallback
 -> normal output approval
 -> TTS only after approval
```

The model remains a proposal/classification layer. It does not gain dial, target widening, plaintext disclosure, confirmation, commitment, completion or direct speech-release authority.

## Response temperature already implemented

`PhraseMatrix` now has a bounded `PhraseResponseTemperature` classifier with bands:

```text
HOT
WARM
UNCERTAIN
COLD
AMBIGUOUS
```

Important semantics:

- HOT = existing exact/alias/fuzzy deterministic match;
- WARM = one semantically/lexically dominant rule above threshold and margin;
- UNCERTAIN = one possible rule but not strong enough for deterministic ownership;
- AMBIGUOUS = competing near-equal rules; fail closed;
- COLD = no useful deterministic interpretation.

`WARM` can route through the existing deterministic CallPlan owner. Ambiguous/cold input must not be guessed deterministically.

## Hybrid fallback / Skills already implemented

The repository now contains a bounded dialogue skill layer:

- `DialogueSkillTextBackend`;
- `DialogueSkillPolicy` / typed skill IDs;
- model returns only structured `skill + confidence + reason`;
- the application owns the exact approved response text for each skill;
- local model errors/low confidence can fall through to the existing injected-response/ChatRelay path;
- output approval remains mandatory before speech release.

The session/router work also has an explicit opt-in unresolved-turn mode that can return `Generate` for the reviewed hybrid path while default/Gate D behavior remains fail-closed and unchanged.

Do not turn Skills into a second authority store.

## Model decision: Gemma 4 only for this stage

User decision: **do not compare Qwen vs Gemma in the current task.**

Target local model:

```text
Gemma 4 E2B IT
model file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
```

Qwen infrastructure may remain in the repository as historical/fallback infrastructure, but do not spend the next session benchmarking or tuning it unless the user explicitly reopens that scope.

## Critical Gemma root-cause findings

The previous `EdgeGalleryTextBackend` assumed an OpenAI-compatible HTTP server at:

```text
127.0.0.1:8080
```

Physical S22 diagnostics proved this assumption is wrong for the installed AI Edge Gallery app:

- `com.google.ai.edge.gallery` is installed;
- launching `MainActivity` starts the application process;
- no port `8080` is exposed before or after launch;
- therefore the old `Edge Gallery = HTTP server` path must not be revived.

The S22 does contain the actual Gemma 4 LiteRT-LM model file in Edge Gallery external storage. Proven locations include:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm

/sdcard/Android/data/com.google.aiedge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

The first package is not debuggable; the second installed variant is debuggable. Do not build a production dependency on another app's private sandbox.

## Direct Gemma 4 implementation already on `main`

Current `main` already contains the new direct in-process LiteRT-LM path:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

and Gradle dependency:

```text
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

`Gemma4LiteRtTextBackend` / `LiteRtGemma4Runtime` currently:

- owns a direct LiteRT-LM `Engine`;
- uses deterministic sampling for skill classification;
- disables thinking/tool calling;
- supports JSON `ResponseFormat` for typed dialogue skills;
- invalidates stale generations and supports cancellation;
- tries GPU and falls back to CPU;
- keeps Gemma as inference only; app policy remains authoritative.

The current app-owned model target is:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

via `Gemma4LiteRtTextBackendFactory.modelFile(context)`.

The Android text backend/provider path has already been changed to use direct Gemma 4 rather than the old HTTP Edge Gallery backend.

## What is NOT yet proven

Do not overclaim the new Gemma path.

Before the direct LiteRT-LM implementation, the physical S22 `AndroidEdgeGalleryDialogueSkillContractTest` failed because no `:8080` runtime existed. That failure is evidence against the old HTTP assumption, not against Gemma 4 itself.

The new direct LiteRT-LM implementation on current `main` still needs:

1. host/canonical regression on the current HEAD;
2. explicit model provisioning into the app-owned model path;
3. physical **no-call** S22 execution of the Gemma dialogue-skill contract;
4. telemetry proving parsed `skill/confidence/reason` and the final response source;
5. only after that, an optional bounded live dialogue acceptance test.

No fresh live-call authorization is carried into the next chat.

## First concrete continuation slice

Start with **no-call Gemma provisioning + proof**, not Orange.

Recommended order:

1. Fetch fresh `origin/main` and inspect current Gemma commits around `e35152f7...`.
2. Run targeted host tests plus `bash scripts/verify_host.sh` on fresh HEAD.
3. Establish a clean app-owned model provisioning rule.
   - For development, it is acceptable to copy the already-downloaded `gemma-4-E2B-it.litertlm` from accessible shared/external Edge Gallery storage into the app's external-files `models/` directory using Local Agent/ADB.
   - Do not hard-code another app's private data directory as the production runtime path.
   - Longer term, the app should own/import/download its model explicitly.
4. Run the physical no-call S22 Gemma skill contract using synthetic text only.
5. If it fails, classify the failure precisely: model path, LiteRT engine init, GPU/CPU backend, JSON response format, timeout, memory, or parser.
6. Once no-call Gemma is green, verify the hybrid path offline/synthetic:
   - HOT/WARM deterministic routing;
   - unresolved -> Gemma skill;
   - low confidence/error -> injected-response fallback;
   - telemetry of model decision and final response source.
7. Only after those gates are green should another live IVR call be considered.

## Important tests/files to inspect first

- `app/src/main/kotlin/pl/michalmatu/aicallbridge/localcall/PhraseMatrix.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/DialogueSkillTextBackend.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/DialogueSkillBackendFactory.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/localcall/CallPlanFinalTurnRouteMapper.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/localcall/LocalTextCallSession.kt`
- `app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/textagent/AndroidEdgeGalleryDialogueSkillContractTest.kt`
- `docs/ORANGE_LIVE_ACCEPTANCE_2026-09-23.md` for historical live evidence only.

The Android test name still contains `EdgeGallery`; it now exercises the `EDGE_GALLERY` provider identity which is being redirected to direct Gemma 4. Renaming the test/provider label for clarity is optional cleanup after the direct path is proven; do not let naming cleanup block the proof.

## Frozen / safety boundaries

- Samsung media/privileged-helper remains frozen.
- Gate D authority remains frozen unless a real regression is proven.
- Gemma/skills cannot directly dial, disclose secrets, approve a proposal, issue/consume commitment authority, complete a task or bypass output approval.
- Identity plaintext remains late-bound through `AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation -> optional user approval`.
- Do not add more exact Orange phrase variants as the main strategy.
- Do not use transient Git relay branches as a product runtime transport; ChatRelay remains a developer/injected-response fallback boundary.

## Local Agent / Local Chat Bridge

For a new chat:

- use only the fresh binding supplied by that chat;
- work only in `MichalMatu/android-ai-call-bridge` unless explicitly rebound;
- inspect fresh `.agent/status/daemon.json` before queueing device/local work;
- Local Agent is for Gradle/ADB/device/local commands; direct GitHub edits are preferred for exact reviewable diffs;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- never launch local Codex through Local Agent.

## Live-call authorization

This handoff contains **no authorization to dial**.

A future real call requires fresh explicit authorization in the new chat for one concrete target/number and one concrete task.

Ready-to-paste bootstrap is in `docs/NEXT_CHAT_PROMPT.md`.
