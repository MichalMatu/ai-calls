# Handoff — Gemma model lifecycle HOST_GREEN; physical S22 import gate next

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Always fetch fresh `origin/main` before acting. Never treat a handoff SHA or old Local Agent binding as current.

Expected durable remote branches after close-out:

```text
main
agent-control
```

A new chat must read fresh `.agent/status/daemon.json` and use only that chat's current immutable Local Chat Bridge binding.

## Stable foundation — do not redo

Gate D `BOOK_APPOINTMENT` remains `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

Samsung S22+ cellular/media, local STT/TTS and Gate D authority remain `PROVEN_S22 / FROZEN`. Do not reopen `privileged-helper/`, `CallMediaSessionCoordinator`, IdentityVault, commitment/completion ordering or Orange exact-phrase scripting without a concrete root cause.

## Dialogue architecture

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: local Gemma 4 bounded skill classifier
 -> app-owned exact reviewed response
 -> model error / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> application output approval
 -> TTS
```

PhraseMatrix bands remain `HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS`; ambiguity fails closed. Gemma returns only `skill/confidence/reason` and gains no dial, disclosure, confirmation, commitment, completion or speech-release authority.

ChatRelay remains developer/injected-response fallback infrastructure, not product runtime transport.

## Gemma 4 decision

Current target only:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Do not compare/tune Qwen unless explicitly reopened.

The old Edge Gallery HTTP `127.0.0.1:8080` path was physically disproven and must not be revived. Runtime is direct in-process LiteRT-LM through `Gemma4LiteRtTextBackend`.

## What is already physically proven

The earlier app-readable development provision is **PROVEN_S22** for no-call direct Gemma inference. Physical output:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

LiteRT JNI/native runtime and GPU delegate worked. No telephony/media session was started.

A previous provisioning failure was isolated to destination ownership/SELinux: plain `adb shell cp` caused `PERMISSION_DENIED`; writing destination through the application UID made the model readable. That finding motivated the app-process-owned importer.

## New application-owned lifecycle — HOST_GREEN / PENDING_PHYSICAL

New code:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4ModelInstaller.kt
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/AndroidGemma4ModelImporter.kt
app/src/main/kotlin/pl/michalmatu/aicallbridge/MainActivity.kt
app/src/test/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4ModelInstallerTest.kt
```

Flow:

```text
Android SAF source URI
 -> app process opens stream
 -> app-owned .importing sibling
 -> streaming SHA-256 against pinned Gemma identity
 -> flush + fsync
 -> atomic same-filesystem replacement request
 -> <app external files>/models/gemma-4-E2B-it.litertlm
 -> existing direct LiteRT runtime
```

Properties:

- source is user-selected/readable; product has no dependency on Edge Gallery storage;
- exact expected model identity/hash is pinned in `Gemma4ModelCatalog`;
- wrong/empty/unreadable input fails before activation;
- failed write/hash/activation removes staging bytes;
- previous active model survives failed verification/activation;
- no silent non-atomic fallback exists;
- existing UI exposes `Import Gemma 4 model` via Android SAF;
- installer is source-agnostic so a future reviewed downloader can reuse the same activation boundary without changing runtime authority.

No model downloader URL was invented or hard-coded in this slice.

## RED -> GREEN evidence

RED was recorded at the host/build layer before implementation:

```text
.agent/results/chatgpt-gemma4-model-lifecycle-red-v126-20260923.json
GEMMA4_MODEL_LIFECYCLE_RED=true
```

Installer GREEN:

```text
.agent/results/chatgpt-gemma4-model-installer-green-v127-20260923.json
GEMMA4_MODEL_INSTALLER_GREEN=true
```

Exact current-code behavior gate before documentation-only commits:

```text
code HEAD: 68812a3c577c4488b4152b9f3fe55c8feb9162b8
.agent/results/chatgpt-gemma4-model-lifecycle-host-v129-20260923.json
GEMMA4_MODEL_LIFECYCLE_CURRENT_HEAD_GREEN=true
```

Final verified checkpoint including authoritative documentation at that point:

```text
checkpoint: 5a6b49b9aab1cbaf12dc9be624ec4659e7af82f5
.agent/results/chatgpt-gemma4-model-lifecycle-final-v130-20260923.json
GEMMA4_MODEL_LIFECYCLE_FINAL_HOST_GREEN=true
```

Verification included installer/Gemma/dialogue/hybrid tests, full `bash scripts/verify_host.sh`, debug APK, AndroidTest APK and clean diff/worktree checks. Commits after that checkpoint are documentation-only handoff metadata.

The implementation is therefore **HOST_GREEN**, not yet `PROVEN_S22`.

## Exact next gate — phone required, no call

Further meaningful validation now requires the physical S22.

1. Fetch fresh `origin/main`, fresh daemon and current Local Chat Bridge binding.
2. Build/install current debug + AndroidTest APK if needed.
3. Make the exact Gemma model available through a readable source document for development proof. ADB/Edge Gallery may stage source bytes, but destination activation must go through the new app UI/SAF importer.
4. Exercise `Import Gemma 4 model` in the app.
5. Require exact SHA-256 success and active app-owned destination.
6. Prove whether Android external-files supports the required `ATOMIC_MOVE` replacement. If it fails, isolate the filesystem/activation root cause; do **not** silently weaken activation to a non-atomic overwrite.
7. Re-run the existing physical **no-call** Gemma dialogue-skill test and require terminal `skill/confidence/reason`.
8. Only after that promote lifecycle to `PROVEN_S22`.

No telephony, microphone, STT/TTS or media path needs to be started for this gate.

## Frozen/safety boundaries

- Samsung media/`privileged-helper/`: frozen.
- Gate D authority: frozen unless a real regression is proven.
- Gemma/model import cannot dial, disclose secrets, confirm proposals, commit, complete tasks or bypass output approval.
- Identity plaintext remains late-bound through existing authorization/disclosure owners.
- Do not add Orange exact phrase aliases as the main strategy.
- No live call is authorized by this handoff.

## Live-call authorization

Every future real call requires fresh explicit authorization in the same chat for one concrete target/number and one concrete task. A connected phone, prior proof, handoff, old allowlist or `.agent/results` never grants dialing permission.

Ready-to-paste bootstrap: `docs/NEXT_CHAT_PROMPT.md`.
