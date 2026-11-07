# AI Calls

Modułowe laboratorium Android do porównywania STT, modeli językowych, strategii dialogu, instrukcji i TTS. Docelowo wspólne, zweryfikowane komponenty obsłużą sekretarkę i konkretnie autoryzowane zadania telefoniczne.

## Aktualny stan (2026-11-07)

- **Sprint 6, kod `main@0dface1`:** `SOURCE_GREEN` — 325 testów hosta PASS, kompilacja Kotlin/JVM/AndroidTest/APK i anti-friction PASS. Nowe funkcje: bezpieczne zbiorcze usuwanie zakończonych rozmów, audio WAV odtwarzane/zatrzymywane z dymków, poprawki Laboratorium (auto-follow, STOP, czytelniejsza ocena END_CALL) i statusów Kampanii.
- **Fizyczny S22 Sprint 6: QA_PENDING_ADB.** Mac nie zobaczył `SM-S906B / RFCT70L7E8J` w `adb devices -l`; nie zainstalowano tej wersji ani nie wykonano najnowszej matrycy 28 UX / 24 visual. Dwuturowy test realnej Gemmy Sprintu 6 nie uruchomił się przez brak wymaganych screenshotów. **Nie jest to FAIL modelu**.
- **Ostatnia potwierdzona fizyczna baza — Sprint 5:** Samsung S22 27/27 UX, 23/23 wizualne, 43 prywatne screenshoty, rzeczywista lokalna Gemma 2 tury + STOP PASS. To wyniki starszego kodu `0bd17da`, nie Sprintu 6.
- **Otwarte:** F-103 pełne PL/EN, F-080 ciągły recorder/player całego połączenia, odrębne testy realnych rozmów i semantyki Gemmy. Dymki historii obsługują tylko istniejące prywatne segmenty WAV.

[Pełny checkpoint Sprintu 6](docs/checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md) · [Ostatni fizyczny baseline — Sprint 5](docs/checkpoints/2026-11-07-ux-sprint5-live-lab-s22-acceptance.md)

## Architektura

- Sześć modułów Gradle: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Istniejące czyste komponenty `IncomingIdleTimeout` i `ConversationSessionRegistry` są w JVM `conversation-core`.
- Jeden `AndroidConversationRuntime` obsługuje INCOMING/OUTGOING. Silniki STT/model/TTS i scenariusze są wymienne; snapshot konfiguracji nie zmienia się podczas rozmowy.
- Uprawnienia, konkretne zgody, własny Android Call, STOP, ujawnianie faktów i skutki kontroluje aplikacja, nie model. Tor Samsung/Shizuku PCM/PFD pozostaje oddzielną chronioną granicą.
- **Proponowany następny sprint:** odsprzęgnięcie UX od logiki przez małe testowalne kroki: read-only prezenter statusu Kampanii → kontroler Historii → runner Laboratorium, nie ogólny refaktor Telecom. [Plan modularizacji](docs/SAFE_MODULARIZATION_PLAN.md).

## Wejście do pracy

[Aktualny handoff](docs/HANDOFF_NEXT_CHAT.md) · [Nowy czat](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Architektura](docs/ARCHITECTURE.md) · [Indeks dokumentacji](docs/README.md) · [Rejestr BLOCKERS](BLOCKERS.md) · [AGENTS](AGENTS.md).

Testowanie: `bash scripts/verify_host.sh`, `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`; przy zmianach Androida odpowiednie testy instrumentacyjne z realnym **IDLE** na S22. Tryb: GitHub Connector + Mac Local Agent, `main`, commity `[skip ci]`; bez Codex, Work, GitHub Actions, wymuszonego push ani publikacji prywatnych plików.
