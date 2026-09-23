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

## Next product engineering gate

The model runtime and manual app-owned SAF lifecycle are now proven. The next generic gap is **product readiness/acquisition semantics**, not another runtime rewrite:

1. audit where `LOCAL_GEMMA_4` can be selected/started without a verified active model;
2. define one application-owned readiness result for missing/invalid/ready model state using the pinned catalog identity;
3. surface that readiness consistently in UI/session preparation instead of waiting for LiteRT engine initialization to fail;
4. keep acquisition source separate from activation authority — do not invent an arbitrary network URL;
5. if a reviewed downloader/source catalog is added later, feed its bytes into the existing verified `Gemma4ModelInstaller` boundary;
6. preserve all existing dialogue/authority/output-approval rules;
7. verify host/package first and run only the physical no-call gate required by any changed Android readiness/UI boundary.

A bounded live acceptance call remains technically eligible for consideration, but it is a separate authorization gate and is not an automatic roadmap step.

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
