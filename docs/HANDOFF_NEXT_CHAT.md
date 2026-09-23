# Handoff — Gemma readiness PROVEN_S22; acquisition-source semantics next

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch after close-out: `main`. Fetch fresh `origin/main` and fresh Local Agent daemon/binding; never copy an old binding from this file. Expected durable remote branches after cleanup: `main`, `agent-control`.

## Stable foundation

- Gate D `BOOK_APPOINTMENT`: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.
- Samsung cellular/media, local STT/TTS, `privileged-helper/`, Gate D authority and IdentityVault: `PROVEN_S22 / FROZEN`.
- Gemma direct no-call inference: `HOST_GREEN / PROVEN_S22`.
- App-owned SAF import + pinned SHA-256 + atomic activation: `HOST_GREEN / PROVEN_S22`.
- Product provider cleanup to `LOCAL_GEMMA_4`: `HOST_GREEN / PROVEN_S22`.
- Model readiness semantics: `HOST_GREEN / PROVEN_S22`.

No live-call authorization is carried by this handoff.

## Dialogue architecture

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM deterministic owner path
 -> unresolved/ambiguous/cold Gemma 4 bounded skill classifier
 -> app-owned exact reviewed response
 -> failure / low confidence / TAKE_OVER -> injected-response / ChatRelay fallback
 -> output approval
 -> TTS
```

Gemma returns only `skill/confidence/reason` and has no dial/disclosure/confirmation/commitment/completion/speech-release authority.

## Gemma target

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
runtime=LiteRT-LM
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Legacy stored `EDGE_GALLERY` migrates to `LOCAL_GEMMA_4`; the dead HTTP `127.0.0.1:8080` backend is removed.

## Proven model lifecycle and readiness

Import remains:

```text
SAF source -> app-owned staging -> streaming pinned SHA-256 -> fsync -> atomic replacement -> active model
```

Ordinary readiness is deliberately cheap:

```text
Gemma4ModelCatalog + app-owned active file metadata -> MISSING / INVALID / READY
```

Full SHA-256 is import-time verification; it is not recomputed on every call/turn. `LOCAL_GEMMA_4` product call preparation checks readiness before STT/TTS and backend construction. Main UI uses the same state. Backend construction itself is lazy and does not create LiteRT engine until generation.

Physical S22 evidence from the readiness slice:

```text
UI: Gemma 4 model: READY (2588147712 bytes)
GemmaReadinessProof: state=READY bytes=2588147712 reason=none
GemmaSkillProof: skill=ACKNOWLEDGE_NEUTRAL confidence=0.95 reason=Potwierdzenie odbioru telefonu
```

Evidence: `.agent/results/chatgpt-gemma4-readiness-s22-v145-20260923.json`, marker `GEMMA4_READINESS_S22_GREEN=true`. No telephony/media was started. Host tests cover `MISSING`, `INVALID`, `READY` and preflight-before-speech/backend ordering.

Audit found developer/diagnostic constructors (`GateCHybridDialogueBackend`, live-probe tooling, direct dialogue factories). They are not product readiness owners; merely creating their backend does not initialize LiteRT. Keep this distinction explicit.

## Exact next gate

Continue with **reviewed model acquisition-source semantics** before implementing any downloader:

1. fetch fresh main/daemon/binding and run baseline tests;
2. research/audit authoritative distribution of the exact Gemma 4 E2B LiteRT artifact;
3. record source identity, license/terms, authentication/entitlement, redirect/version behavior and whether a stable machine-download contract exists;
4. decide whether product acquisition remains explicit SAF import or gets one reviewed source catalog/downloader;
5. never invent an arbitrary URL and never place credentials/tokens in Git/evidence;
6. any acquisition implementation only supplies bytes to the proven `Gemma4ModelInstaller`; pinned identity and atomic activation remain authoritative;
7. preserve all dialogue/output/call authority owners; no media changes.

## Live-call rule

Every real call requires fresh explicit authorization in the same chat for one concrete target/number and one concrete task. A connected phone, previous proof, handoff, allowlist or `.agent/results` never grants dialing permission.

Bootstrap for a fresh chat: `docs/NEXT_CHAT_PROMPT.md`.
