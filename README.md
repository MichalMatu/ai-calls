# AI Calls

Modułowe laboratorium Android do porównywania STT, modeli językowych, strategii dialogu, instrukcji i TTS. Docelowo wspólne, zweryfikowane komponenty obsłużą sekretarkę i konkretnie autoryzowane zadania telefoniczne.

## Aktualny stan (Sprint 6 CLOSED, UX-01–05 done — 2026-11-07)

- **`main@1b359146` — UX ITERATION CLOSED.** 325 testów hosta, Kotlin/JVM/AndroidTest/APK/friction **PASS**. Wdrożono bezpieczne kasowanie wszystkich zakończonych rozmów, WAV tap-to-play/stop z dymków, poprawione przewijanie i STOP w Laboratorium, czytelne Skills/Silniki/Kampanie.
- **Fizyczny S22 PASS:** instalacja aplikacji/test APK bez utraty danych, **28/28 native UI + 24/24 visual**, po poprawce Android regex dodatkowo **4/4 targeted**, **2 rzeczywiste tury lokalnej Gemmy + STOP PASS**, 46 prywatnych screenshotów. Bez prawdziwych połączeń, END_CALL i usuwania danych właściciela.
- **Nowy SOURCE_PASS `main@7df2c33`:** UX-01/02/03/04 wydzieliły prezentery Kampanii, kontroler Historii, runner Laboratorium i odczytowe presentery modeli/audio. Ostatni UX-04: **13 JVM, 332 Python, friction, AndroidTest/APK PASS** na Macu; **nowy APK nieinstalowany na S22**. [Checkpoint UX-04](docs/checkpoints/2026-11-07-ux04-model-and-audio-presentation-closeout.md).
- **UX-05 / F-103 VERIFIED:** PL/EN 553 wspólne klucze, 337 host Python + 10 focused i AndroidTest/APK PASS, **fizyczny S22 2/2 native oraz 4/4 prywatne screenshoty PL/EN PASS**, zachowane dane i przywrócone `pl`. [Checkpoint UX-05](docs/checkpoints/2026-11-07-ux05-pl-en-accessibility-closeout.md). Manualnego TalkBack nie przeprowadzano.
- **Niezależne otwarte:** F-080 (pełny ciągły recorder/player), osobne autoryzowane real-call i ocena semantyki; nie wznawiać zamkniętych iteracji UX.

[Końcowy checkpoint Sprint 6](docs/checkpoints/2026-11-07-ux-sprint6-final-handoff.md) · [Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md).

## Architektura

- Sześć modułów Gradle: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Istniejące czyste komponenty `IncomingIdleTimeout` i `ConversationSessionRegistry` są w JVM `conversation-core`.
- Jeden `AndroidConversationRuntime` obsługuje INCOMING/OUTGOING. Silniki STT/model/TTS i scenariusze są wymienne; snapshot konfiguracji nie zmienia się podczas rozmowy.
- Uprawnienia, konkretne zgody, własny Android Call, STOP, ujawnianie faktów i skutki kontroluje aplikacja, nie model. Tor Samsung/Shizuku PCM/PFD pozostaje oddzielną chronioną granicą.
- **UX-01–04 zaliczone źródłowo; UX-05 lokalizacja i automatyczna dostępność zweryfikowane na S22**, bez refaktoru Telecom. Następny niezależny zakres F-080. [Plan modularizacji](docs/SAFE_MODULARIZATION_PLAN.md).

## Wejście do pracy

[Aktualny handoff](docs/HANDOFF_NEXT_CHAT.md) · [Nowy czat](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Architektura](docs/ARCHITECTURE.md) · [Indeks dokumentacji](docs/README.md) · [Rejestr BLOCKERS](BLOCKERS.md) · [AGENTS](AGENTS.md).

Testowanie: `bash scripts/verify_host.sh`, `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`; przy zmianach Androida odpowiednie testy instrumentacyjne z realnym **IDLE** na S22. Tryb: GitHub Connector + Mac Local Agent, `main`, commity `[skip ci]`; bez Codex, Work, GitHub Actions, wymuszonego push ani publikacji prywatnych plików.
