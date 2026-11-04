# AI Calls

Autonomiczny agent telefoniczny na Androida: użytkownik określa legalne zadanie, numer i zakres uprawnień, a aplikacja prowadzi rozmowę lokalnie (STT → reguły/stan zadania → Gemma → zatwierdzona wypowiedź → TTS/TX). Testem pilotażowym jest CLIR; umówienie wizyty, mechanika i inne skutki wymagają jeszcze osobnych, jawnie autoryzowanych akcji. Pilot ręczny nie jest produktem docelowym.

## Aktualny stan — 2026-11-04

- **Kod główny:** gałąź `main`. Ostatni wdrożony na S22 kod aplikacji: `032b6f5138a1c10f8d7f646065a5aa539e2a766a` (F-043 + F-042 pacing). Odczytuj świeży HEAD; dokumentacyjne commity mogły przesunąć go bez nowej wersji APK.
- **F-043 VERIFIED:** jeden natywny czat Gemmy na połączenie, bez powtarzania instrukcji i tworzenia nowego `Conversation` po każdej udanej turze. Na S22+ (`SM-S906B`) zainstalowano źródło `bcbe0a2a`; test dwóch syntetycznych tur GPU potwierdził `conversation_reused=false → true`. Samo `generate` trwało 6233 ms → 3998 ms; nie są to czasy odpowiedzi w rozmowie.
- **F-042 CZĘŚCIOWO ZWERYFIKOWANE:** neutralne „Proszę chwilę zaczekać.” przygotowywane równolegle i odtwarzane wyłącznie w autoryzowanej kampanii, przez jeden tor TX bez nakładania głosów. Poprawka F-042 z 4 listopada usuwa podwójne oczekiwanie po blokującym zapisie PCM i mierzy EOS→zapowiedź oraz EOS→pełny TX. **Mac: RED/GREEN, 248 Python, Android build/unit/lint, audyt PASS; S22: aktualizacja APK PASS, grant i IDLE zachowane, bez połączenia. Słyszalność i realne opóźnienie podczas rozmowy nadal OPEN.**
- **Ostatni rzeczywisty pomiar przed F-042/F-043:** około 11,7–11,9 s od końca mowy do TX. Rzeczywista słyszalność u rozmówcy i efekt CLIR nie zostały niezależnie potwierdzone.
- **Po wdrożeniu F-042 na S22:** `IDLE`, kampania nieaktywna, rola dialera oraz grant i stan kontynuacji zachowane. To odczyt z zakończonego zadania wdrożeniowego, nie stały monitoring urządzenia.

## Następny etap

Na nowej, konkretnie autoryzowanej rozmowie zmierzyć EOS→pierwsze TX zapowiedzi, EOS→pełna odpowiedź, Gemma/TTS i słyszalność po drugiej stronie; zweryfikować brak nakładania się głosów, respektowanie STOP oraz rzeczywisty wynik zadania. Nie powtarzać zakończonego testu dwóch tur F-043 bez zmiany kodu lub urządzenia.

## Źródła prawdy

- [AGENTS.md](AGENTS.md) — reguły wykonania, autoryzacja, testy i bezpieczeństwo.
- [BLOCKERS.md](BLOCKERS.md) — jedyny rejestr F-xxx i ich statusów (historia pozostaje dla audytowalności).
- [Handoff](docs/HANDOFF_NEXT_CHAT.md) — bieżący stan, identyfikatory Mac Local Agenta, plan wejścia do kolejnego sprintu.
- [Roadmap](docs/ROADMAP.md) i [architektura](docs/ARCHITECTURE.md) — plan oraz granice warstw.
- Dowody: [F-042 pacing 04.11](docs/checkpoints/2026-11-04-f042-ack-pacing-measurement-sprint.md), [F-042 początkowe](docs/checkpoints/2026-11-03-f042-parallel-ack-gemma-latency.md), [F-043](docs/checkpoints/2026-11-04-f043-gemma-one-conversation-per-call.md), [F-041](docs/checkpoints/2026-11-03-f041-private-rx-tx-silence-f042-latency.md). Starsze checkpointy są historią, nie instrukcją bieżącą.

## Zasady

Stosuj GitHub Connector + Mac Local Agent; bez Codex, Work i GitHub Actions. Nie omijaj odmów narzędzia, autoryzacji zadania, uprawnień Androida, prywatności rozmówców ani selektywnego STOP. Nagrania i transkrypcje tylko prywatnie na urządzeniu/Macu — nie w GitHub. **Test hostowy lub syntetyczny nie dowodzi zrealizowania zadania ani słyszalności rozmowy.**
