# Next-chat prompt — Gemma acquisition-source semantics

Kontynuuj repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego `main`. Pobierz świeży `origin/main` i świeży/current Local Chat Bridge binding; nie kopiuj starego `agent_binding`.

Przeczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/HANDOFF_PROTOCOL.md`; `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media.

Gate D oraz Samsung media/STT/TTS są zakończone/proven/frozen. Gemma 4 direct no-call runtime, app-owned SAF import/atomic activation, provider `LOCAL_GEMMA_4` i model readiness `MISSING / INVALID / READY` są `HOST_GREEN / PROVEN_S22`.

Target:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
bytes=2588147712
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Readiness product path fail-closed działa przed STT/TTS/backend construction; pełny hash pozostaje import-time, ordinary readiness używa cheap pinned metadata. Nie wracaj do Edge Gallery HTTP, nie porównuj Qwena, nie ruszaj frozen media.

## Pierwszy task

**Nie wykonuj live calla.**

1. Sprawdź świeży HEAD/daemon/binding i baseline.
2. Zrób preimplementation research/audit oficjalnego lub autorytatywnego źródła dokładnego artefaktu Gemma 4 E2B LiteRT: source identity, license/terms, auth/entitlement, stable URL/API, version/redirect semantics i expected hash/size.
3. Nie implementuj downloadu na podstawie przypadkowego linku. Najpierw zapisz decyzję: pozostajemy przy SAF albo akceptujemy jeden reviewed source catalog/downloader.
4. Jeżeli downloader jest uzasadniony i możliwy bez ukrytych credentials, RED -> minimal GREEN. Downloader ma wyłącznie dostarczać bytes do istniejącego `Gemma4ModelInstaller`; nie może zmieniać pinned identity/atomic activation/authority.
5. Nigdy nie commituj tokenów/credentials. Jeśli oficjalne pobranie wymaga interaktywnego zaakceptowania licencji lub sekretu użytkownika, zatrzymaj implementację na jasno opisanym boundary zamiast obchodzić wymaganie.
6. Po zmianie: targeted tests, `verify_host.sh`, Android package; physical no-call tylko jeśli faktycznie zmieni się Android acquisition UI/network boundary.

Każdy realny call wymaga świeżej jawnej autoryzacji konkretnego targetu/numeru i zadania w tym samym oknie.
