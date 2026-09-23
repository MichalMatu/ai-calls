# Roadmap

## Status vocabulary

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — targeted/canonical host evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `PENDING_PHYSICAL` — host/package work is complete but the changed Android/device boundary still needs physical proof.
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
- physical no-call Gemma 4 skill inference on the previously provisioned model: **PROVEN_S22**;
- application-owned model import lifecycle: **HOST_GREEN / PENDING_PHYSICAL**;
- Samsung media path: **PROVEN_S22 / FROZEN** and was not reopened.

Implemented dialogue pieces include `HOT/WARM/UNCERTAIN/COLD/AMBIGUOUS`, typed `skill/confidence/reason`, app-owned exact speech, reviewed unresolved-turn generation, local-model -> injected-response failover, direct LiteRT-LM, and diagnostics for model decision/error/final response source.

## Model decision

For this stage use **Gemma 4 E2B IT only**.

```text
model: Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
sha256: 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Do not compare or tune Qwen unless the user explicitly reopens that scope.

The old `EdgeGalleryTextBackend` HTTP assumption (`127.0.0.1:8080`) is not the product direction. The runtime is direct in-process LiteRT-LM with an app-owned model path.

## Physical no-call Gemma evidence

The previous development proof provisioned model bytes into:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

A plain `adb shell cp` produced a file the app/LiteRT could not open (`PERMISSION_DENIED`). Writing through the application UID fixed ownership/SELinux and allowed direct LiteRT inference.

Observed physical S22 decision:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

LiteRT JNI/native runtime and GPU delegate were proven. That proof predates the new application-owned import implementation and therefore does not prove the new importer.

## Application-owned model lifecycle

The generic provisioning gap is now implemented on the host side.

New reviewed boundary:

```text
user-selected SAF document
 -> app process opens source stream
 -> app-owned sibling staging file
 -> streaming SHA-256 against pinned Gemma 4 identity
 -> flush + fsync
 -> same-filesystem atomic replacement request
 -> active app-owned model path
 -> existing direct LiteRT-LM runtime
```

Failure behavior is fail-closed:

- unreadable source fails;
- empty source fails;
- wrong SHA-256 fails before activation;
- import/write failure removes staging data;
- atomic activation failure preserves the previous active model;
- no non-atomic activation fallback is silently used.

The UI exposes `Import Gemma 4 model` through Android Storage Access Framework. The product does not hard-code Edge Gallery storage or an arbitrary model download URL. The installer is source-agnostic so a future reviewed downloader can feed the same verified activation boundary without changing runtime authority.

RED -> GREEN evidence exists for the installer. On exact implementation HEAD `68812a3c577c4488b4152b9f3fe55c8feb9162b8`, targeted installer/Gemma/dialogue/hybrid tests, `bash scripts/verify_host.sh`, debug APK and AndroidTest APK all passed.

## Immediate next gate — physical S22, no call

The next step requires the physical phone. Do not do any live call.

1. Install the current APK/AndroidTest APK on the S22.
2. Provide a readable source copy of the exact Gemma model for development proof if necessary; ADB/Edge Gallery may be used only to stage source bytes, never as production runtime authority.
3. Exercise the **application UI/SAF importer**, not the old direct ADB destination copy.
4. Require successful exact SHA-256 verification and activation into the app-owned model path.
5. Verify the Android external-files filesystem supports the required atomic replacement. If `ATOMIC_MOVE` fails, classify that exact filesystem/activation root cause; do not silently weaken to a non-atomic replacement.
6. Re-run the physical no-call Gemma dialogue-skill contract and require terminal `skill/confidence/reason`.
7. Only after this gate may model lifecycle be promoted from `PENDING_PHYSICAL` to `PROVEN_S22`.

No telephony/media proof needs repeating for this slice.

## Synthetic hybrid gate

Host contract evidence remains:

```text
bounded skill -> reviewed app response -> source=LOCAL_SKILL
low confidence -> injected fallback -> source=CHAT_RELAY
classifier error -> injected fallback -> source=CHAT_RELAY
```

ChatRelay remains developer/injected-response fallback infrastructure, not product runtime authority.

## Authority invariant

Gemma, model storage/import, matchers, Skills, injected-response tooling, ServicePacks and TaskGraph helpers are proposal/data layers only. They cannot independently dial or widen a target, disclose plaintext identity, release speech without output approval, confirm a user decision, issue/consume commitment authority, infer factual external success or complete the workflow/task.

## Orange status

Orange remains a ServicePack/acceptance fixture, not the active architecture owner. Existing live evidence already proved call control, downlink, STT and reviewed TTS injection. No CLIR account change was completed. Do not grow exact Orange phrase aliases as the main strategy.

## Live-call stop line

A connected phone, existing allowlist, old chat, previous successful call, documentation or no-call proof never authorizes a new call.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.
