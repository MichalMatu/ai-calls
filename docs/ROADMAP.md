# Roadmap

## Status vocabulary

- `DONE` — implementation complete for stated scope.
- `HOST_GREEN` — targeted/canonical host evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `PENDING_PHYSICAL` — host/package work is complete but the changed Android/device boundary still needs physical proof.
- `FROZEN` — do not modify without a separate root-cause scope.

## Stable completed foundation

Gate D `BOOK_APPOINTMENT` is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

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
- direct Gemma 4 LiteRT-LM Android runtime: **HOST_GREEN / PROVEN_S22**;
- application-owned model import lifecycle: **HOST_GREEN / PROVEN_S22**;
- provider cleanup/migration to `LOCAL_GEMMA_4`: **HOST_GREEN / PROVEN_S22**;
- Samsung media path: **PROVEN_S22 / FROZEN** and was not reopened.

## Model decision

For this stage use **Gemma 4 E2B IT only**.

```text
provider: LOCAL_GEMMA_4
model: Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
sha256: 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Do not compare/tune Qwen unless the user explicitly reopens that scope.

The obsolete Edge Gallery HTTP `127.0.0.1:8080` backend has been removed. A legacy stored provider value `EDGE_GALLERY` migrates to `LOCAL_GEMMA_4`; Edge Gallery is not runtime authority or a production dependency.

## Proven application-owned model lifecycle

The reviewed path is:

```text
user-selected Android SAF document
 -> app process opens source stream
 -> app-owned sibling staging file
 -> streaming SHA-256 against pinned Gemma identity
 -> flush + fsync
 -> same-filesystem atomic replacement
 -> active app-owned model path
 -> direct LiteRT-LM runtime
```

Failure behavior is fail-closed: unreadable/empty/wrong-hash data cannot activate, failed staging is removed, activation failure preserves the previous model, and there is no silent non-atomic fallback.

Physical S22 proof exercised the actual `Import Gemma 4 model` UI and DocumentsUI/SAF flow. The 2,588,147,712-byte source activated successfully; the destination inode/mtime changed, the active SHA-256 exactly matched the pinned value, the model had app-owned external-files SELinux/ownership, and `.importing` was absent after completion.

Post-import physical no-call inference returned:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

A second physical regression after provider cleanup passed through `AndroidGemma4DialogueSkillContractTest` under `LOCAL_GEMMA_4` with the same bounded result. No telephony/media path was started.

## Synthetic hybrid gate

Host contract evidence remains:

```text
bounded skill -> reviewed app response -> source=LOCAL_SKILL
low confidence -> injected fallback -> source=CHAT_RELAY
classifier error -> injected fallback -> source=CHAT_RELAY
```

ChatRelay remains developer/injected-response fallback infrastructure, not product runtime authority.

## Model readiness gate — PROVEN_S22

Application-owned Gemma readiness is complete for the current product boundary:

1. `Gemma4ModelReadinessProbe` reports `MISSING / INVALID / READY` from the pinned catalog identity and cheap file metadata;
2. import-time SHA-256 remains the strong identity verification, avoiding a 2.6 GB hash on each call/turn;
3. `AndroidTextCallReadiness` fails `LOCAL_GEMMA_4` preparation before STT/TTS/backend construction when readiness is not `READY`;
4. `MainActivity` exposes the same semantic state;
5. host tests cover all three states and ordering;
6. physical S22 UI/readiness contract returned `READY` for exactly 2,588,147,712 bytes;
7. the subsequent no-call Gemma skill inference remained green.

Evidence: `.agent/results/chatgpt-gemma4-readiness-s22-v145-20260923.json` with `GEMMA4_READINESS_S22_GREEN=true`.

Audit classification: product call preparation owns fail-closed readiness. Developer/diagnostic constructors such as Gate C hybrid/live-probe tooling may construct a backend directly, but construction itself does not initialize LiteRT and those paths are not product readiness authority.

## Reviewed acquisition source and downloader — HOST_GREEN

The source/transport boundary is now implemented without changing model activation authority:

1. `Gemma4ModelAcquisitionCatalog` pins `litert-community/gemma-4-E2B-it-litert-lm` at immutable revision `6e5c4f1e395deb959c494953478fa5cec4b8008f`;
2. filename, expected bytes and SHA-256 are derived from the existing production `Gemma4ModelCatalog`;
3. source metadata records Apache-2.0 and no authentication requirement;
4. `Gemma4ModelDownloader` streams HTTP body bytes directly to `Gemma4ModelInstaller`;
5. non-2xx, transport failure and a known wrong `Content-Length` fail closed before activation;
6. the production request is anonymous HTTPS GET with no Authorization header;
7. redirect completion must remain HTTPS on the reviewed Hugging Face/CDN host family;
8. full expected-size/SHA-256 verification and atomic activation remain exclusively installer-owned.

Host evidence: RED `chatgpt-gemma4-downloader-red-v151-20260923`; GREEN `chatgpt-gemma4-downloader-green-v152-20260923` with targeted tests, `verify_host.sh` and Android debug/AndroidTest package gates.

Bounded remote proof `chatgpt-gemma4-acquisition-range-v153-20260923` read exactly one byte and returned HTTP 206 from `us.aws.cdn.hf.co` with `Content-Range: bytes 0-0/2588147712`. No full model transfer occurred.

## Full Gemma network acquisition — HOST_GREEN / PROVEN_S22

The complete explicit acquisition lifecycle is now physically proven on the target S22:

1. reviewed immutable source and anonymous HTTPS transport;
2. explicit two-step user confirmation;
3. serialized `IDLE / IMPORTING / DOWNLOADING` model operation gate;
4. real streamed progress and cancellable transport;
5. installer-owned staging, expected-size and SHA-256 verification;
6. fsync + same-filesystem atomic replacement;
7. old active model preserved until verified replacement;
8. retry semantics remain restart-from-byte-0;
9. final app-owned file and semantic readiness `READY`;
10. post-download no-call Gemma inference remains green.

Full-transfer evidence: `chatgpt-gemma4-full-download-s22-v169-20260923`. The task intentionally entered through the UI positive action and recorded staging growth at `477934181`, `1027826896`, `1615416174` and `2178292139` bytes. Atomic replacement produced final stat `2588147712:561969:1790200763`, exact pinned SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, correct `u0_a736/ext_data_rw` ownership and no remaining staging file.

`v169` exited nonzero only on a post-success immediate UI text assertion. Follow-up `chatgpt-gemma4-full-download-postcheck-v170-20260923` is fully GREEN: final stat/hash/ownership persisted, restarted UI reported `READY`, and synthetic no-call inference returned `ACKNOWLEDGE_NEUTRAL / 0.95 / Potwierdzenie odbioru telefonu`.

## Next product engineering gate

The model acquisition lifecycle has no remaining physical gate. Select the next roadmap task explicitly from product priorities rather than reopening downloader/storage/media code without a concrete root cause.

A bounded live acceptance call remains a separate authorization gate. It is **not** authorized by this acquisition proof, a connected phone, documentation, or continuation command.

## Authority invariant

Gemma, model storage/import/readiness, matchers, Skills, injected-response tooling, ServicePacks and TaskGraph helpers are proposal/data layers only. They cannot independently:

- dial or widen a target;
- disclose plaintext identity;
- release speech without output approval;
- confirm a user decision;
- issue/consume commitment authority;
- infer factual external success;
- complete the workflow/task.

## Orange status

Orange remains a ServicePack/acceptance fixture, not the active architecture owner. Existing live evidence already proved call control, downlink, STT and reviewed TTS injection. No CLIR account change was completed. Do not grow exact Orange phrase aliases as the main strategy.

## Live-call stop line

A connected phone, existing allowlist, old chat, previous successful call, documentation or no-call proof never authorizes a new call.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.
