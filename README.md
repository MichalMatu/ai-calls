# AI Calls

Lokalny agent telefoniczny na Androida: użytkownik autoryzuje konkretny cel, target, konto/SIM i dozwolone skutki, a aplikacja prowadzi własne połączenie przez **STT → Gemma 4 E2B → TTS/TX** z kontrolą grantu i selektywnym STOP. CLIR jest pierwszym testowanym zadaniem; rezerwacje i inne skutki nie są jeszcze ogólnie dostępne.

## Potwierdzony stan — 4 listopada 2026

- **Realny S22+ (`SM-S906B`):** Gemma Direct RAW prowadzi wieloturowy dialog. Zakończone, prywatnie zarejestrowane sesje o 06:30 i 06:35 (Europe/Warsaw): odpowiednio **8/8 i 7/7** rozpoznanych wypowiedzi biura, własnych odpowiedzi Gemmy i pełnych lokalnych TX; 0 `DIRECT_REJECTED`. To **techniczny sukces toru rozmowy**, nie dowód wykonania CLIR lub słyszalności każdej wypowiedzi u operatora.
- **Zamrożony punkt odniesienia RAW:** `0d44b7d5a7e01daa118da10c2e04c458086dced9`. Kod źródłowy z dynamicznym, autoryzowanym CLIR `TaskBrief`, czterema tekstowymi prompt-skills i prywatnym czatem STT/Gemma/TX ma ostatni potwierdzony APK S22 z `268be1f8f13502c7202822438b501706e0fcb0e1`. Mac host QA, instalacja exact S22 i 4 syntetyczne natywne tury PASS. Bieżący `main` HEAD należy zawsze odczytać ponownie (dokumentacja przesuwa historię).
- **Obserwowalność:** dolny panel rozmowy w aplikacji prezentuje biuro/STT, zatwierdzony tekst Gemmy, TTS/TX i godziny. Właściciel potwierdził, że UI i dialog działają dobrze. Wewnętrzny czat jest nietrwały i nie jest archiwum danych.
- **Ostatni odczyt po rozmowie:** S22 `IDLE`, brak oczekującego ponowienia. Transkrypt, WAV i chronologiczny raport ostatnich sesji pobrano prywatnie na Mac, nie do repozytorium.

## Co pozostało

1. **Nowy sprint: menu i wybór silników audio** — wejście Android STT / Parakeet PL / eksperymentalne Gemma Audio Direct; model Gemma E2B/E4B; wyjście Android TTS PL / planowany Piper PL / Kokoro i PocketTTS dla wspieranych języków. Wybory niegotowe nie mogą udawać działających. [Szczegółowy handoff implementacyjny](docs/checkpoints/2026-11-04-edge-gallery-audio-implementation-handoff.md).
2. **F-048 — bezpieczne fakty:** Gemma zna cel CLIR, ale nie ma autoryzowanego `RequestFact` do kontrolowanego dostarczania informacji z `IdentityVault`. Nie wklejać danych osobowych do promptu; każdą decyzję o ujawnieniu podejmuje aplikacja.
3. **F-047 / F-045:** odzyskiwanie transkryptu RX przerwanego przez koniec Call (diagnostyka już działa) oraz semantyczna kontrola swobodnego tekstu RAW. Nie potwierdzono zewnętrznego skutku CLIR.

**F-049 wycofane przez właściciela:** dwuczęściowe powitanie infolinii jest prawidłowym zachowaniem po jej stronie. Nie planować zmian VAD, ACK ani opóźnienia Gemmy z tego powodu.

**Pełny bieżący checkpoint:** [2026-11-04 — RAW live / fakty / historia IVR](docs/checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md). **Jedyny rejestr statusów:** [BLOCKERS.md](BLOCKERS.md). **Następny sprint:** [ROADMAP](docs/ROADMAP.md) i [HANDOFF](docs/HANDOFF_NEXT_CHAT.md).

## Granice

Pracuj w trybie GitHub Connector + Mac Local Agent; bez Codex, Work, GitHub Actions, nowych samowolnych połączeń i force-push. Dokładny scope grantu, Android Telecom, prywatność rozmówców, STOP i zamrożony tor Samsung/Shizuku nadal obowiązują. Dokumentacja i wyniki testów nie nadają uprawnień. Historyczne dowody są w `docs/checkpoints/`, źródła zasad w [AGENTS](AGENTS.md), [architekturze](docs/ARCHITECTURE.md) i [bezpieczeństwie](docs/SECURITY_PRIVACY.md).
