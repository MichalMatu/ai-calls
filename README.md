# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**. Samsung cellular/media, local STT/TTS and Gate D authority remain **PROVEN_S22 / FROZEN**.

The dialogue-resilience architecture is **HOST_GREEN** and the direct Gemma 4 no-call path is **PROVEN_S22**:

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

Current target/provider:

```text
provider: LOCAL_GEMMA_4
model: Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

Direct in-process runtime:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

The old Edge Gallery HTTP assumption that another app exposes an OpenAI-compatible server on `127.0.0.1:8080` was physically disproven. The dead HTTP backend has been removed. A stored legacy provider value `EDGE_GALLERY` is migrated to `LOCAL_GEMMA_4`; it is not a runtime dependency. Qwen comparison/tuning is out of scope unless explicitly reopened.

Pinned/verified SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

## Application-owned model lifecycle — PROVEN_S22

The application owns an explicit model-import path:

```text
user-selected Android SAF document
 -> AndroidGemma4ModelImporter
 -> Gemma4ModelInstaller
 -> app-owned sibling staging file
 -> streaming pinned SHA-256
 -> flush + fsync
 -> atomic same-filesystem replacement
 -> <app external files>/models/gemma-4-E2B-it.litertlm
 -> direct LiteRT-LM runtime
```

The installer rejects unreadable, empty or wrong-hash data before activation; failed writes/activation remove staging data and preserve the previous active model. There is no silent non-atomic replacement fallback.

Physical S22 proof exercised the actual app UI and Android DocumentsUI/SAF importer, not a direct ADB destination copy. The selected 2,588,147,712-byte source was imported by the application process; the active destination changed inode and mtime, retained the exact pinned SHA-256, had app-owned external-files ownership/SELinux labeling, and left no `.importing` file.

Immediately after that fresh import, the physical no-call Gemma contract passed:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

No telephony, microphone, STT/TTS or call-media session was started for this proof.

Edge Gallery/ADB may still be used as development sources for already-downloaded bytes, but neither is a production runtime dependency. No arbitrary network model URL or downloader authority has been introduced.

## Model readiness — PROVEN_S22

The app exposes one cheap application-owned readiness result for the active Gemma file: `MISSING / INVALID / READY`. It reuses `Gemma4ModelCatalog` and expected size metadata; it does **not** hash the 2.6 GB model on every turn. Full SHA-256 verification stays at import/activation time.

For product local-text-call preparation, `LOCAL_GEMMA_4` readiness is checked before speech preflight and backend construction. Missing/invalid model state therefore fails deterministically before STT/TTS or LiteRT engine initialization. The main UI reports the same state.

Physical S22 proof showed:

```text
Gemma 4 model: READY (2588147712 bytes)
state=READY bytes=2588147712 reason=none
skill=ACKNOWLEDGE_NEUTRAL confidence=0.95 reason=Potwierdzenie odbioru telefonu
```

No telephony/media path was started. Host tests cover `MISSING`, `INVALID` and `READY`; the physical test exercised the existing valid app-owned model without destructively corrupting it.

The audit also found explicit developer/diagnostic constructors (including Gate C hybrid/live-probe tooling). They are not product readiness owners and backend construction itself does not initialize LiteRT. Product session preparation is the fail-closed owner.

## Reviewed acquisition and download lifecycle

The reviewed network source is pinned to immutable revision `6e5c4f1e395deb959c494953478fa5cec4b8008f` of `litert-community/gemma-4-E2B-it-litert-lm`. Transport is anonymous HTTPS, sends no Authorization header, restricts redirect completion to the reviewed Hugging Face/CDN host family, and streams bytes directly into `Gemma4ModelInstaller`.

`Gemma4ModelInstaller` remains the only activation authority: full expected-size/SHA-256 verification, app-owned staging, fsync and atomic replacement happen there. The product lifecycle is explicit and two-step: source/license/revision/size confirmation first, then a separate `Download 2.59 GB` action; SAF import and network download are serialized, progress is streamed, cancel closes active transport, partial staging fails closed, and retry restarts from byte 0.

### Full physical S22 acquisition proof

The complete 2,588,147,712-byte artifact was downloaded through the product UI on the S22. Observed staging checkpoints included `477934181`, `1027826896`, `1615416174` and `2178292139` bytes before atomic replacement. The active model changed from stat `2588147712:551596:1790194198` to `2588147712:561969:1790200763`. Final SHA-256 was exactly:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

The final file remained app-owned (`u0_a736`, `ext_data_rw`, `media_rw_data_file`), `.importing` was absent, and a clean MainActivity restart reported `Gemma 4 model: READY (2588147712 bytes)`. The post-download synthetic no-call contract passed with:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

The first full-transfer driver task (`v169`) was marked failed only because its final immediate UI-text assertion was too strict; the network transfer, atomic replacement, final size/hash and ownership had already succeeded. Follow-up `v170` independently revalidated the final file, UI readiness and no-call inference. Full application-owned network acquisition is therefore **PROVEN_S22**.

## Verification

Current Gemma/model-lifecycle/provider work has:

- RED -> GREEN model-installer evidence;
- targeted installer + Gemma + dialogue-skill + hybrid tests;
- `bash scripts/verify_host.sh`;
- Android debug + AndroidTest package gates;
- physical S22 SAF import + atomic activation proof;
- physical S22 post-import no-call inference proof;
- provider migration RED -> GREEN;
- physical S22 no-call regression under `LOCAL_GEMMA_4`;
- removal of the unused HTTP `EdgeGalleryTextBackend` path.

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
