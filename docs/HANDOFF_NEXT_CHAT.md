# Handoff — Gemma download lifecycle UI PROVEN_S22; full transfer not started

Date: 2026-09-23

## Repository state

Repository: `MichalMatu/android-ai-call-bridge`. Durable branch after close-out: `main`. Every new chat must fetch fresh `origin/main` and fresh Local Agent daemon/binding; never reuse a historical binding.

## Stable foundation

- Gate D `BOOK_APPOINTMENT`: `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.
- Samsung cellular/media, local STT/TTS, `privileged-helper/`, Gate D authority and IdentityVault: `PROVEN_S22 / FROZEN`.
- direct Gemma no-call runtime, SAF import/atomic activation, provider `LOCAL_GEMMA_4`, model readiness: `HOST_GREEN / PROVEN_S22`.
- immutable acquisition source + streaming downloader + progress/cancel/serialization lifecycle: `HOST_GREEN`.
- explicit download confirmation/cancel UI boundary: `PROVEN_S22`.
- full 2,588,147,712-byte network download/activation: **NOT STARTED / NOT PHYSICALLY PROVEN**.

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

Never use mutable `main` as source identity. Downloader is transport only; `Gemma4ModelInstaller` owns expected size/SHA-256, staging cleanup, fsync and atomic activation.

## Download lifecycle now implemented

```text
IDLE
 -> user presses Download reviewed Gemma 4 model (~2.59 GB)
 -> confirmation shows source/license/revision/exact size/SHA-256 policy/retry policy
 -> only separate Download 2.59 GB action may start transfer
 -> DOWNLOADING with streamed progress
 -> Cancel closes network response/call
 -> partial staging fails closed and is removed
 -> retry starts byte 0
 -> only installer-verified complete bytes may activate
```

`Gemma4ModelOperationGate` also prevents concurrent SAF import/download. App/activity destruction cancels network work; this slice does not claim background/resumable transfer.

## Evidence

- acquisition/downloader host proof: v149-v153.
- lifecycle audit: `.agent/results/chatgpt-gemma4-download-lifecycle-audit-v156-20260923.json`.
- lifecycle RED: `.agent/results/chatgpt-gemma4-download-lifecycle-red-v157-20260923.json`.
- core GREEN: `.agent/results/chatgpt-gemma4-download-lifecycle-core-green-v158-20260923.json`, marker `GEMMA4_DOWNLOAD_LIFECYCLE_CORE_GREEN=true`.
- UI RED: `.agent/results/chatgpt-gemma4-download-ui-red-v159-20260923.json`.
- corrected UI GREEN: `.agent/results/chatgpt-gemma4-download-ui-green-v161-20260923.json`, marker `GEMMA4_DOWNLOAD_UI_HOST_GREEN=true`.
- v162 physical driver failed before tapping because Android uppercased Button text; no transfer began.
- diagnostic v163 confirmed actual uppercase node labels.
- corrected physical proof: `.agent/results/chatgpt-gemma4-download-ui-s22-v164-20260923.json`, marker `GEMMA4_DOWNLOAD_UI_S22_GREEN=true`. It opened the confirmation and cancelled via Back without tapping the positive download action. Active model stat stayed exactly `2588147712:551596:1790194198` and staging remained absent.
- post-UI no-call regression: `.agent/results/chatgpt-gemma4-download-ui-inference-v165-20260923.json`, marker `GEMMA4_DOWNLOAD_UI_NO_CALL_GREEN=true`; result remained `ACKNOWLEDGE_NEUTRAL / 0.95 / Potwierdzenie odbioru telefonu`.

## Exact next gate

Do **not** auto-start a large transfer. The remaining end-to-end model acquisition proof is the full pinned ~2.59 GB download on S22. It may be run only when the operator explicitly chooses/authorizes that large transfer. When authorized:

1. record active model stat/hash before start;
2. start only through the reviewed confirmation UI;
3. capture progress and ensure no concurrent SAF import;
4. let installer verify full expected bytes + SHA-256 and atomically activate;
5. verify staging absent, final hash exact and app-owned path/SELinux ownership;
6. rerun no-call Gemma skill contract;
7. do not place a cellular call.

If the operator does not authorize the full transfer, do not substitute an automatic/background download.

Every real call separately requires fresh explicit authorization for one concrete target/number and one concrete task.
