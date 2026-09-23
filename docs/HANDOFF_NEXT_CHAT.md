# Handoff — full Gemma network acquisition PROVEN_S22

Date: 2026-09-24

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`. Durable work lives on `main`; `agent-control` is Local Agent task/result traffic only. Every new chat must fetch fresh `origin/main` and fresh Local Agent daemon/binding.

## Stable foundation

- Gate D `BOOK_APPOINTMENT`: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.
- Samsung cellular/media, local STT/TTS, `privileged-helper/`, Gate D authority and IdentityVault: `PROVEN_S22 / FROZEN`.
- Gemma direct no-call runtime, SAF import/activation, provider/readiness: `HOST_GREEN / PROVEN_S22`.
- immutable network source, streaming downloader, progress/cancel/serialization lifecycle, explicit download UI and **full 2.59 GB network acquisition**: `HOST_GREEN / PROVEN_S22`.

No live-call authorization is carried by this handoff.

## Model identity

```text
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
provider=LOCAL_GEMMA_4
runtime=LiteRT-LM
```

Reviewed source:

```text
repository=litert-community/gemma-4-E2B-it-litert-lm
revision=6e5c4f1e395deb959c494953478fa5cec4b8008f
license=apache-2.0
auth=none
```

## Full S22 acquisition evidence

Preflight `chatgpt-gemma4-full-download-preflight-v168-20260923` proved S22, Wi-Fi default route, correct existing model and ~99 GB free space.

`chatgpt-gemma4-full-download-s22-v169-20260923` then entered the normal product UI, opened reviewed confirmation and tapped the explicit positive `Download 2.59 GB` action. Staging checkpoints included:

```text
477934181
1027826896
1615416174
2178292139
```

Atomic replacement changed the active stat from:

```text
2588147712:551596:1790194198
```

to:

```text
2588147712:561969:1790200763
```

Final SHA-256 exactly matched the pinned model. Final ownership/label remained `u0_a736`, `ext_data_rw`, `media_rw_data_file`; staging was absent.

`v169` is recorded as failed only because the driver required an immediate `Gemma 4 model ready` text in the same UI dump after the already-successful transfer. This was a test-driver assertion, not a download/installer failure.

`chatgpt-gemma4-full-download-postcheck-v170-20260923` is GREEN and independently confirmed final stat/hash/ownership, no staging, restarted MainActivity `Gemma 4 model: READY (2588147712 bytes)`, and the post-download no-call inference:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

## Current next gate

There is no remaining model acquisition proof. Do not redownload merely to reprove it. Select the next product task explicitly. Frozen Samsung media/Gate D boundaries stay closed absent a new root cause.

A real cellular call still requires fresh explicit authorization in the current chat for one concrete target/number and one concrete task. This handoff does not authorize any call.
