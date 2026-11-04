# Zamrożenie: pierwsza działająca rozmowa Gemma Direct RAW

**Baseline kodu:** `0d44b7d5a7e01daa118da10c2e04c458086dced9` (`main`); późniejsze commity przed tym checkpointem dotyczyły tylko dokumentów. **Nie przestawiać starego `SCRIPT_GEMMA`, nie przywracać JSON parsera, nie zmieniać Samsung/Shizuku RX/TX.** Zmiany w dalszych sprintach mają być dodatkowym kontekstem zadania i obserwowalnością, z zachowaniem defaultu i testów RAW.

## Akceptacja wersji v1

- `aicalls-20261104-f046-gemma-raw-host-qa-v1` PASS: Gradle, lint, testy JVM/Python, bez urządzenia i rozmów.
- `aicalls-20261104-f046-raw-s22-instrument-diagnosis-v2` PASS, cztery syntetyczne tury w tej samej natywnej rozmowie; `aicalls-20261104-f046-raw-s22-final-readonly-v1` PASS: GPU, 4 + 4 naturalne odpowiedzi, reuse, S22 `IDLE`.
- `aicalls-20261104-f046-raw-postcall-evidence-v1` PASS: rzeczywista sesja `2026-11-04 03:46:12 Europe/Warsaw`, 2 × `COUNTERPART_STT`, 2 × `SPEAK/GEMMA_DIRECT_RAW`, 2 × `AGENT_APPROVED_TEXT`, 2 × `WRITE_COMPLETE`, 7/7 WAV poprawne, 2 × TTS, bez `DIRECT_REJECTED`.
- Rzeczywista słyszalność po stronie operatora i wykonanie CLIR nie zostały potwierdzone. Nie nadawać wersji v1 statusu pełnej produkcyjnej akceptacji.

## Diagnoza przerwanego trzeciego RX

Prywatny materiał `private-exports.zip` użytkownika: ostatnie `timeline.jsonl` (sesja powyżej) ma `RX_PCM turn=3 elapsed_ms=43722`, `END elapsed_ms=60841 reason_code=owned_call_ended`, brak `COUNTERPART_STT turn=3`, brak `AGENT_DECISION/APPROVED_TEXT/TX turn=3`. Trzecie RX WAV ma **17 080 ms** sygnału, bez błędu zapisu.

Odtworzenie na rzeczywistym audio tylko lokalnie w sandboxie, bez ujawniania nagrania i danych: RMS liczony po 20 ms, próg mowy 600, minimum mowy 200 ms. Pierwsze dwie tury mają końcową ciszę **1 500 ms** i zostały rozpoznane. W trzeciej turze ~8 360 ms ramek powyżej progu, najdłuższa cisza po rozpoczęciu mowy **1 000 ms**, końcowa **1 000 ms**. `ChatRelayLiveCallProbe.beginCaptureInputTurn` ustawia `PcmEndOfUtteranceDetector(trailingSilenceMs=1500,maxCaptureMs=60000)`. Nie zaszedł ani koniec po 1500 ms ciszy, ani timeout 60 s; odległy Call skończył się przed endpointem.

W obecnej ścieżce `pipeline.finishInput()` jest wywoływane tylko w `onFinished` detektora, natomiast `finish(owned_call_ended)` zamyka pipeline i anuluje `OnDeviceSpeechInput`. **Wniosek: stracona transkrypcja wskutek rozłączenia w środku trwającego okna nasłuchu, nie błąd pliku WAV, nie parser Gemmy ani dowód awarii silnika ASR**.

Ten przypadek wymaga osobnej diagnostyki `RX_INTERRUPTED` z czasem, sygnałem i powodem; ewentualne post-call flush musi pozostać wyłącznie do prywatnej transkrypcji i nigdy nie wysyłać Gemma/TTS/TX po zerwaniu własnego połączenia. Nie obniżać globalnie progu VAD bez porównania akustycznego, nie dotykać zamrożonej granicy Samsung media.

## Kolejność właściciela

(1) zamrożenie v1 i analiza przerwanego RX, (2) typowany dynamiczny `TaskBrief` składany z dokładnie autoryzowanego zadania i katalogu **prompt-skills** (rola/IVR/dopytanie/procedura), z menu w aplikacji, (3) dodatkowa semantyczna walidacja wypowiedzi (F-045) jako ostatni sprint. **Prompt-skills to instrukcje modelu, nie wykonywalne narzędzia i nie nowe uprawnienia.** `automaticToolCalling=false` w LiteRT RAW.

Żadnych prywatnych numerów, kont, dosłownych transkryptów ani WAV w repo. Starszy flow nadal domyślny. Każdy retest live tylko w istniejącym, ważnym zakresie grantu i z możliwością STOP.
