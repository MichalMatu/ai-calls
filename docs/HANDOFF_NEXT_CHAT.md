# Handoff — Gemma lifecycle PROVEN_S22; readiness semantics next

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

Current target/provider only:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
runtime=LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Do not compare/tune Qwen unless explicitly reopened.

The obsolete Edge Gallery HTTP `127.0.0.1:8080` path is removed. `EDGE_GALLERY` remains only as a legacy stored preference string that migrates to `LOCAL_GEMMA_4`; it is not runtime authority or a dependency.

## Application-owned lifecycle — PROVEN_S22

Code owners:

```text
Gemma4ModelInstaller.kt
AndroidGemma4ModelImporter.kt
Gemma4LiteRtTextBackend.kt
MainActivity.kt
```

Flow:

```text
Android SAF source URI
 -> app process source stream
 -> app-owned .importing sibling
 -> streaming pinned SHA-256
 -> flush + fsync
 -> atomic same-filesystem replacement
 -> <app external files>/models/gemma-4-E2B-it.litertlm
 -> direct LiteRT runtime
```

Fail-closed properties:

- wrong/empty/unreadable source cannot activate;
- failed write/hash/activation removes staging data;
- previous active model survives failed verification/activation;
- no silent non-atomic replacement fallback exists;
- acquisition source does not become runtime/model authority.

## Physical evidence from this session

The actual app UI `Import Gemma 4 model` launched Android DocumentsUI. A 2,588,147,712-byte development source in Downloads was selected through SAF.

Application result:

```text
Gemma 4 model ready: Gemma 4 E2B IT; 2588147712 bytes; sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

The active destination changed inode/mtime across import, proving replacement of the previous active file. After success:

- exact pinned SHA-256 matched;
- `.importing` staging file was absent;
- destination had app-owned external-files ownership/SELinux labeling;
- required atomic replacement succeeded on the S22 external-files filesystem.

Post-import physical no-call inference:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

Evidence:

```text
.agent/results/chatgpt-gemma4-lifecycle-s22-inference-v131-20260923.json
GEMMA4_LIFECYCLE_S22_PROVEN=true
```

No telephony, microphone, STT/TTS or call-media session was started.

## Provider cleanup — HOST_GREEN / PROVEN_S22

The product provider is now `LOCAL_GEMMA_4`. The dead `EdgeGalleryTextBackend` / loopback HTTP implementation and its test were removed after an audit proved there were no production callers.

RED migration proof:

```text
.agent/results/chatgpt-gemma4-provider-cleanup-red-v135-20260923.json
GEMMA4_PROVIDER_CLEANUP_RED=true
```

Host/package gate:

```text
.agent/results/chatgpt-gemma4-provider-cleanup-green-v137-20260923.json
GEMMA4_PROVIDER_CLEANUP_HOST_GREEN=true
```

Physical no-call regression on `AndroidGemma4DialogueSkillContractTest`:

```text
.agent/results/chatgpt-gemma4-provider-cleanup-s22-v138-20260923.json
GEMMA4_PROVIDER_CLEANUP_S22_GREEN=true
```

It returned the same bounded `ACKNOWLEDGE_NEUTRAL / 0.95` result.

## Exact next gate

The manual app-owned lifecycle is complete. Continue with **model readiness semantics** before inventing any downloader.

1. Fetch fresh `origin/main`, fresh daemon and current Local Chat Bridge binding.
2. Audit all places that can select/start `LOCAL_GEMMA_4` (`MainActivity`, preferences, text-call preparation/readiness, dialogue backend factories, diagnostic probes).
3. Define one application-owned readiness result backed by `Gemma4ModelCatalog`: at minimum missing / invalid identity / ready.
4. Surface readiness before LiteRT engine creation so a missing/corrupt model fails deterministically at preparation/UI level rather than as a native runtime surprise.
5. Do not hash a 2.6 GB model on every turn. Separate durable/import-time verified identity from cheap ordinary readiness; if stronger revalidation is needed, define when it runs and why.
6. Preserve legacy preference migration from `EDGE_GALLERY` to `LOCAL_GEMMA_4`.
7. Do not invent or hard-code an arbitrary model download URL. A future reviewed downloader/source catalog must feed the already-proven installer boundary.
8. Use RED -> minimal GREEN -> targeted tests -> `verify_host.sh` -> Android package. Run a physical no-call S22 gate only if the changed Android readiness/UI boundary requires it.

## Frozen/safety boundaries

- Samsung media/`privileged-helper/`: frozen.
- Gate D authority: frozen unless a real regression is proven.
- Gemma/model import/readiness cannot dial, disclose secrets, confirm proposals, commit, complete tasks or bypass output approval.
- Identity plaintext remains late-bound through existing authorization/disclosure owners.
- Do not add Orange exact phrase aliases as the main strategy.
- No live call is authorized by this handoff.

## Live-call authorization

Every future real call requires fresh explicit authorization in the same chat for one concrete target/number and one concrete task. A connected phone, prior proof, handoff, old allowlist or `.agent/results` never grants dialing permission.

Ready-to-paste bootstrap: `docs/NEXT_CHAT_PROMPT.md`.
