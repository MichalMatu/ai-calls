# AI Calls — handoff operacyjny (2026-11-04)

## Stan źródeł i urządzenia

- Repozytorium: `MichalMatu/ai-calls`, gałąź docelowa `main`. Ostatnie zatwierdzone źródło aplikacji: `bcbe0a2a84781bf79033ccbef7293ce5f29dcc47`. Dokumentacyjny reaudyt rozpoczęty po `a834d217`; **przed kolejnym zadaniem zawsze odczytaj świeży HEAD**, nie używaj tu zapisanego SHA jako aktualnego.
- **F-043 VERIFIED** na fizycznym Samsung S22+ `SM-S906B`: instalacja aplikacji i AndroidTest z podanego SHA, GPU, natywny `Conversation` użyty dwukrotnie (`false → true`); `generate_ms=6233 → 3998`, `engine_ready_ms=6709 → 0`, `conversation_create_ms=3191 → 0`. To test syntetyczny bez połączenia. [Pełny dowód](checkpoints/2026-11-04-f043-gemma-one-conversation-per-call.md).
- **F-042:** równoległa neutralna zapowiedź + diagnostyka GPU/CPU zainstalowane w tym samym APK. Mac F-042 v4 i F-043 full QA PASS (248 Python, Android build/unit/lint, anti-friction). **OPEN:** odczuwalny czas i słyszalność zapowiedzi oraz pełnego TX podczas rzeczywistej rozmowy. [Dowód](checkpoints/2026-11-03-f042-parallel-ack-gemma-latency.md).
- F-041 eksport prywatnych nagrań przez USB/ADB VERIFIED; wcześniejsze 11,7–11,9 s EOS→TX pochodzi z prawdziwej rozmowy **sprzed** F-042/F-043. Audio pozostaje na Macu w prywatnym katalogu, nie na GitHub. [Dowód](checkpoints/2026-11-03-f041-private-rx-tx-silence-f042-latency.md).
- Po wdrożeniu użytkownik nacisnął STOP AI: ostatni odczyt potwierdził `IDLE`, brak kontynuacji, zachowaną rolę dialera i grant. To **historyczny wynik** testu, nie dowód aktualnego stanu telefonu.
- **Niezamknięte niezależnie od F-043:** słyszalność TX u rozmówcy, realny wynik CLIR, żywe wznowienie po STT 6/7/pustym finale, generyczne umiejętności wizyty/mechanika. Statusy F-xxx wyłącznie w [BLOCKERS.md](../BLOCKERS.md).

## Następna mierzalna praca

1. Preflight: świeży `main` HEAD, brak nieodebranej pracy w Mac Local Agent, dokładny model S22+, `IDLE`, brak aktywnej kampanii, role/grant/STOP. Nie instaluj nowego APK podczas połączenia lub aktywnego retry.
2. W ramach konkretnego, nadal ważnego grantu i dozwolonego toru działania uzyskaj jeden test rzeczywistej rozmowy F-042: EOS→zapowiedź TX, EOS→pełny TX, czasy STT/Gemma/TTS, brak nakładania głosów, niezależna słyszalność odbiorcy, STOP i stan końcowy. Nie obchodź odmowy zewnętrznego narzędzia połączenia.
3. Zapisz tylko liczby i anonimowe statusy w rejestrze/checkpoincie; surowe RX/TX/numery/transkrypcje tylko na prywatnym Macu. Dopiero po dowodach dobierz optymalizację TTS/modelu i test regresji.
4. Nie powtarzaj zakończonego syntetycznego testu F-043 ani pełnego hostowego QA na niezmienionym źródle.

## Trasa pracy / bezpieczeństwo

- GitHub Connector + **Mac Local Agent**. Bez Codex, Work i GitHub Actions. Host `macbook-michal`; instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Aktualny protokół i stan czytaj w `MichalMatu/local-agent`, na gałęzi `agent-control`, a nie z historycznego statusu w repo AI Calls.
- Prywatne zadania Mac Host Ops: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<task-id>.json`, wyniki w analogicznym `task-results/<task-id>.json`, każdorazowo z dokładnym target ID i jednoznacznym terminalnym wynikiem. Nie wpisuj zadań hostowych ani ich surowych wyników do publicznego repo AI Calls.
- Repo `ai-calls:agent-control` służy osobnej starszej skrzynce projektu, **nie** jest kolejką Mac Host Ops. Nie traktuj commita ani historycznego heartbeat jako wyniku urządzenia.
- Grant zadania, prawa uczestników, Android, prywatność, selektywne rozłączanie wyłącznie własnego `Call`, STOP i odmowy narzędzia są nienaruszalne. Rozwój autonomiczny ≠ prawo wykonywania nieautoryzowanych skutków.

## Gałęzie — kontrola porządkowa

- `main`: jedyna gałąź produktu. `agent-control`: zachować, bo to odrębna skrzynka projektu; brak wspólnego przodka z `main`.
- `checkpoint/2026-11-03-f035-s22-baseline`: 0 commitów przed `main`, 109 commitów za nim. **Kandydat do usunięcia** po potwierdzeniu braku odniesień operacyjnych.
- `fix/stt-startup-errors-20261102`: 4 własne commity, 400 commitów za `main`, różnice w `OnDeviceSpeechInput.kt` i teście Android. **Nie usuwać bez porównania znaczenia zmian**; przydatny kod musi zostać przeniesiony albo świadomie odrzucony.
- Żadna gałąź nie została oznaczona jako skasowana bez potwierdzonego wyniku operacji usunięcia. Docelowy stan zgodny z `AGENTS.md`: `main` + `agent-control`.

## Nawigacja

[README](../README.md) — krótki opis produktu i bieżący status; [ROADMAP](ROADMAP.md) — etapy; [ARCHITECTURE](ARCHITECTURE.md) — granice modułów; [AGENTS](../AGENTS.md) — reguły; [BLOCKERS](../BLOCKERS.md) — jedyne statusy; checkpointy — historyczne dowody, nie bieżące instrukcje.
