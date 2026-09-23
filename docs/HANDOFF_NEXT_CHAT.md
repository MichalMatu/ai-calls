# Handoff — Gemma 4 no-call PROVEN_S22; hybrid dialogue HOST_GREEN

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Always fetch fresh `origin/main` before acting. Do not rely on a handoff SHA as the current tip.

Expected durable remote branches remain:

```text
main
agent-control
```

A new chat must read fresh `.agent/status/daemon.json` and use only that chat's current immutable Local Chat Bridge binding. Never copy an old `agent_binding` from task history or documentation.

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

## Active dialogue architecture

```text
finalized STT turn
 -> PhraseMatrix
 -> response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: local Gemma 4 bounded skill classifier
 -> app-owned skill policy selects exact reviewed response
 -> local model error / low confidence / TAKE_OVER:
      injected-response / ChatRelay fallback
 -> application output approval
 -> TTS
```

`PhraseMatrix` has bounded bands:

```text
HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS
```

WARM may route deterministically only when one candidate wins the reviewed threshold/margin. Ambiguity remains fail-closed.

The model returns only bounded `skill + confidence + reason`. It does not gain dial, target widening, plaintext disclosure, confirmation, commitment, completion or direct speech-release authority.

## Model decision

For this stage use **Gemma 4 E2B IT only**.

```text
model: Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
```

Do not compare/tune Qwen unless the user explicitly reopens that scope.

The old `EdgeGalleryTextBackend` assumption that Edge Gallery exposes an OpenAI-compatible server on `127.0.0.1:8080` was physically disproven and must not be revived.

Current runtime direction is direct in-process LiteRT-LM through:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

with:

```text
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

## Gemma no-call gate — now PROVEN_S22

The model already downloaded by Edge Gallery was used only as a development source. Proven source path includes:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

The app-owned runtime target is:

```text
<pl.michalmatu.aicallbridge external files>/models/gemma-4-E2B-it.litertlm
```

Verified source/app-owned SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Important root cause discovered during provisioning:

- `adb shell cp` produced a destination that the app/LiteRT could not open (`PERMISSION_DENIED`);
- JNI/native LiteRT loading itself was working;
- writing the model through `run-as pl.michalmatu.aicallbridge` produced app-owned readable ownership/SELinux labeling;
- after that, `Engine.create()` and physical inference succeeded.

Therefore the failed intermediate run was a **model-file ownership/SELinux** problem, not GPU/CPU fallback, native runtime, model format, JSON `ResponseFormat`, parser, memory or timeout.

Physical no-call S22 evidence:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

The instrumentation test completed `OK (1 test)`. LiteRT-LM loaded the GPU delegate. No telephony, microphone, STT, TTS or call media session was started.

Relevant Local Agent evidence on `agent-control`:

```text
.agent/results/chatgpt-gemma4-s22-appowned-v121-20260923.json
.agent/results/chatgpt-gemma4-hybrid-proof-v122-20260923.json
```

## Synthetic hybrid gate — HOST_GREEN

The reviewed hybrid composition now has direct host contract coverage for telemetry and fallback source:

```text
ASK_REPEAT confidence=0.93 reason=fragmented_input -> source=LOCAL_SKILL
ASK_CLARIFY confidence=0.41 reason=ambiguous -> source=CHAT_RELAY
classifier error=engine_unavailable -> source=CHAT_RELAY
```

Existing PhraseMatrix tests cover WARM deterministic routing and ambiguous/unresolved fail-closed routing. `DialogueSkillTextBackend` still owns bounded parsing/policy behavior, while the application owns exact speech.

`GateCHybridDiagnostics` records the model decision, local-skill error and final response source. ChatRelay remains developer/injected-response fallback infrastructure, not product runtime transport authority.

## Code changes in this slice

- physical no-call Android test now emits terminal `skill/confidence/reason` evidence;
- Gate C hybrid composition has a small internal seam so the real failover/telemetry wiring is host-testable without changing runtime policy;
- `GateCHybridDialogueBackendTest` verifies local success, low-confidence fallback and classifier-error fallback.

No Samsung media, `privileged-helper/`, Gate D authority, IdentityVault or Orange exact-phrase aliases were changed.

## Verification completed

On fresh current code before merge:

1. targeted PhraseMatrix/dialogue/Gemma/hybrid host tests — GREEN;
2. `bash scripts/verify_host.sh` — GREEN;
3. Android debug + AndroidTest compile/package with LiteRT-LM — GREEN;
4. app-owned model hash/ownership verification — GREEN;
5. physical S22 no-call Gemma inference — `PROVEN_S22`;
6. terminal bounded decision proof — GREEN;
7. synthetic hybrid fallback + telemetry source proof — `HOST_GREEN`.

No live call was made.

## Next product engineering gap

Production must not depend on Edge Gallery storage or ADB provisioning.

The next generic product task is to design/implement an explicit **application-owned model lifecycle**:

- import or download `gemma-4-E2B-it.litertlm` into app-owned storage;
- verify integrity/version/expected identity before activation;
- keep Edge Gallery completely optional and development-only;
- rerun the physical no-call Gemma gate after provisioning changes.

Do not invent a production dependency on another app's sandbox.

A bounded live acceptance call is technically eligible for consideration only after the no-call/hybrid gates above, but it remains a separate authorization event.

## Frozen / safety boundaries

- Samsung media/`privileged-helper/` remains frozen.
- Gate D authority remains frozen unless a real regression is proven.
- Gemma/skills cannot directly dial, disclose secrets, approve proposals, issue/consume commitment authority, complete tasks or bypass output approval.
- Identity plaintext remains late-bound through `AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation -> optional user approval`.
- Do not add more exact Orange phrase variants as the main strategy.
- ChatRelay remains a developer/injected-response fallback boundary.

## Live-call authorization

This handoff contains **no authorization to dial**.

A future real call requires fresh explicit authorization in that same chat for one concrete target/number and one concrete task.

Ready-to-paste bootstrap is in `docs/NEXT_CHAT_PROMPT.md`.
