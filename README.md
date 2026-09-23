# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**. Samsung cellular/media, local STT/TTS and Gate D authority remain **PROVEN_S22 / FROZEN**.

The dialogue-resilience architecture is **HOST_GREEN** and direct Gemma 4 no-call inference is **PROVEN_S22**:

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: deterministic existing owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skill classifier
 -> app-owned reviewed skill response
 -> model failure / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> application output approval
 -> TTS
```

`PhraseMatrix` uses `HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS`. WARM tolerates bounded wording drift only when one rule wins by threshold and margin; ambiguity remains fail-closed.

Gemma returns only typed `skill/confidence/reason`. The application owns allowed skills and exact reviewed speech. The model does not gain dial, disclosure, confirmation, commitment, completion or speech-release authority.

## Gemma 4

Current target:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

Direct in-process runtime:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

The old `EdgeGalleryTextBackend` assumption that another app exposes an OpenAI-compatible server on `127.0.0.1:8080` was physically disproven and is not the product direction. Qwen comparison/tuning is out of scope unless explicitly reopened.

The no-call S22 proof succeeded with an app-readable copy at:

```text
<app external files>/models/gemma-4-E2B-it.litertlm
```

Pinned/verified SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Observed physical bounded result:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

## Application-owned model lifecycle

The application now has an explicit model-import path, currently **HOST_GREEN / PENDING_PHYSICAL**.

`Gemma4ModelInstaller` and `AndroidGemma4ModelImporter`:

- accept a user-selected document through Android SAF rather than depending on Edge Gallery storage;
- pin the expected Gemma 4 E2B identity and exact SHA-256;
- stream into an app-owned sibling staging file while hashing;
- reject empty or wrong-hash data before activation;
- flush + `fsync` staged bytes;
- request same-filesystem atomic replacement only after verification;
- preserve the previous active model if verification or activation fails;
- remove failed staging bytes;
- expose a minimal `Import Gemma 4 model` action in the existing app UI.

The importer is source-agnostic. Edge Gallery/ADB may still provide development source bytes, but neither is a production runtime dependency. No arbitrary network model URL or downloader authority was introduced.

Important previous provisioning finding: a plain `adb shell cp` produced a file LiteRT could not open (`PERMISSION_DENIED`) on the S22; writing via the app UID fixed ownership/SELinux. The new import path writes destination bytes from the application process itself.

## Verification

Current model-lifecycle implementation passed on its exact HEAD:

- RED proof before the installer existed;
- targeted installer + Gemma + dialogue-skill + hybrid tests;
- `bash scripts/verify_host.sh`;
- Android debug APK;
- AndroidTest APK;
- clean worktree/diff checks.

This proves **HOST_GREEN**, not physical activation. The next required gate is an S22 no-call test of the new import path, including whether the target external-files filesystem honors the required atomic move, followed by the existing terminal `skill/confidence/reason` inference proof.

No live call is required or authorized for that gate.

## Stable authority

The BOOK_APPOINTMENT invariant remains:

```text
permit issued != permit consumed != business success confirmed
```

Cellular RX/TX, `CallMediaSessionCoordinator`, Samsung `privileged-helper/`, local speech, Gate D authority and IdentityVault remain frozen unless a concrete root cause requires reopening them.

Identity plaintext remains late-bound through:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> resolve plaintext only after ALLOW
```

ChatRelay is developer/injected-response fallback infrastructure, not product runtime transport authority. Orange exact-phrase mappings remain diagnostic fixtures, not the main dialogue engine.

## Sources of truth

- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation checkpoint;
- `docs/ROADMAP.md` — authoritative execution order;
- `docs/ARCHITECTURE.md` — component and authority ownership;
- `docs/SECURITY_PRIVACY.md` — privacy/live-call rules;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste continuation prompt;
- `docs/HANDOFF_PROTOCOL.md` — close-out/transfer rules.

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task. A connected phone, previous proof, handoff or old call never grants dialing permission.
