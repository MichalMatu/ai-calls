# Next-chat prompt — Gemma download lifecycle UX

Kontynuuj `MichalMatu/android-ai-call-bridge` z aktualnego `main`. Pobierz świeży `origin/main`, świeży daemon i wyłącznie current Local Chat Bridge binding.

Przeczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/HANDOFF_PROTOCOL.md`; `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media.

Gate D i Samsung media/STT/TTS pozostają proven/frozen. Gemma runtime, SAF import/atomic activation, provider/readiness są PROVEN_S22. Pinned acquisition source oraz streaming downloader są HOST_GREEN.

Reviewed source:

```text
repo=litert-community/gemma-4-E2B-it-litert-lm
revision=6e5c4f1e395deb959c494953478fa5cec4b8008f
file=gemma-4-E2B-it.litertlm
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
license=apache-2.0
auth=none
```

`Gemma4ModelDownloader` jest transportem, nie authority. Installer nadal jako jedyny zatwierdza size/SHA-256 i atomic activation. Range proof przeczytał tylko 1 bajt i dostał `206`, `Content-Range: bytes 0-0/2588147712` z `us.aws.cdn.hf.co`. Pełnego 2.59 GB downloadu nie wykonano.

## Pierwszy task

**Nie wykonuj live calla. Nie rozpoczynaj automatycznie pełnego model downloadu.**

1. Fresh HEAD/daemon/binding + baseline.
2. RED: kontrakt download lifecycle: jawny user start, progress, cancel, brak concurrent SAF import/download, active model zachowany do verified activation.
3. Zdefiniuj retry/resume. Jeżeli resume: partial state nieaktywny, a finalny pełny SHA-256 nadal obowiązkowy.
4. Pokaż w UI reviewed source/license i expected ~2.59 GB przed startem.
5. Cancellation ma zamknąć request/stream i nie aktywować partiala.
6. Nie używaj auth tokenu; source jest publiczny. Nie używaj mutable `main`.
7. Po GREEN: targeted, `verify_host.sh`, debug + AndroidTest package.
8. Physical full network transfer na S22 dopiero po jawnym uruchomieniu dużego downloadu przez operatora w gotowym UI; sama obecność telefonu nie jest zgodą na transfer ani call.

Każdy realny call wymaga osobnej świeżej autoryzacji konkretnego numeru/targetu i zadania w tym samym oknie.
