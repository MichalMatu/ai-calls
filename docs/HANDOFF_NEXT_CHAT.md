# Handoff — pinned Gemma acquisition/downloader HOST_GREEN; lifecycle UX next

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`. Durable branch after close-out: `main`. Fetch fresh `origin/main` and fresh Local Agent daemon/binding in every chat; never copy an old binding.

## Stable/proven foundation

- Gate D `BOOK_APPOINTMENT`: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.
- Samsung cellular/media, local STT/TTS, `privileged-helper/`, Gate D authority and IdentityVault: `PROVEN_S22 / FROZEN`.
- Direct Gemma 4 no-call inference: `HOST_GREEN / PROVEN_S22`.
- App-owned SAF import + pinned SHA-256 + atomic activation: `HOST_GREEN / PROVEN_S22`.
- Provider `LOCAL_GEMMA_4` and readiness `MISSING / INVALID / READY`: `HOST_GREEN / PROVEN_S22`.
- Reviewed network acquisition source + streaming downloader: `HOST_GREEN`.

No live-call authorization is carried by this handoff.

## Target model identity

```text
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
runtime=LiteRT-LM
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

## Reviewed acquisition source

```text
repository=litert-community/gemma-4-E2B-it-litert-lm
revision=6e5c4f1e395deb959c494953478fa5cec4b8008f
file=gemma-4-E2B-it.litertlm
license=apache-2.0
requires_auth=false
```

`Gemma4ModelAcquisitionCatalog` is source metadata only. Never switch it to mutable `main`. `Gemma4ModelDownloader` performs anonymous HTTPS streaming; it sends no Authorization header and restricts the final redirect host to the reviewed Hugging Face/CDN family. `Gemma4ModelInstaller` remains sole activation authority: full expected-size check, SHA-256, staging cleanup, fsync and atomic replacement.

## Evidence from this slice

RED source contract: `.agent/results/chatgpt-gemma4-acquisition-red-v149-20260923.json` (`GEMMA4_ACQUISITION_RED=true`).

Source GREEN: `.agent/results/chatgpt-gemma4-acquisition-green-v150-20260923.json` (`GEMMA4_ACQUISITION_HOST_GREEN=true`) with targeted tests, full `verify_host.sh` and both Android packages.

Downloader RED: `.agent/results/chatgpt-gemma4-downloader-red-v151-20260923.json` (`GEMMA4_DOWNLOADER_RED=true`).

Downloader GREEN: `.agent/results/chatgpt-gemma4-downloader-green-v152-20260923.json` (`GEMMA4_DOWNLOADER_HOST_GREEN=true`) with targeted tests, `verify_host.sh`, debug APK and AndroidTest APK.

Bounded remote source proof: `.agent/results/chatgpt-gemma4-acquisition-range-v153-20260923.json`:

```text
status=206
final_scheme=https
final_host=us.aws.cdn.hf.co
content_range=bytes 0-0/2588147712
content_length=1
bytes_read=1
GEMMA4_PINNED_SOURCE_RANGE_GREEN=true
```

No full model download was performed; only one remote byte was read. No telephony/media path was started.

## Exact next gate

Continue with **download lifecycle UX**, still without live calls:

1. fresh main/daemon/binding + baseline;
2. RED for explicit start/progress/cancel and no concurrent import/download;
3. decide retry vs resumable partial transfer. If resume is implemented, partial data is never active and final complete SHA-256 verification remains mandatory;
4. expose reviewed source/license and ~2.59 GB expected size before user starts;
5. do not auto-download merely because model readiness is missing;
6. make cancellation close network work and clean/non-activate staging;
7. only then wire an explicit UI action;
8. run targeted + `verify_host.sh` + Android package;
9. physical S22 full transfer only when the operator explicitly initiates that large download.

Every real call still requires fresh explicit authorization in the same chat for one concrete target/number and one concrete task.
