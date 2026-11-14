# AI Calls — szybki rejestr jakości kombinacji audio

Ten raport porównuje **wejście mowy × model dialogowy × silnik TTS**. Nie miesza testów syntetycznych z rozmowami telefonicznymi. Brak wyniku oznacza **nieprzetestowane**, nie „0 punktów” ani „PASS”. Kod raportujący nie steruje telefonem i nie zmienia profilu S22.

## Stan początkowy (14.11.2026; wcześniejsze potwierdzenia, nie nowy benchmark)

| Wejście | Dialog | Wyjście | Dowód | Ocena jakości |
| --- | --- | --- | --- | --- |
| Android STT | Gemma 4 E2B | Android TTS PL | Bazowa ścieżka używana na S22 | Brak pomiaru porównawczego |
| Gemma Audio Direct | Gemma 4 E2B | Android TTS PL | MVP: 3/3 syntetyczne próbki ASR→JSON oraz jedna rozmowa; wcześniejsza APK | Brak wspólnego pomiaru E2E/odsłuchu |
| Parakeet PL | Gemma 4 E2B | Android TTS PL | Ścieżka i testy w kodzie | Wymaga urządzenia |
| Android STT | Gemma 4 E2B | Piper PL (Android TTS engine) | Obsługiwany wybór silnika; instalacja Piper zgłoszona | Polski głos niepotwierdzony |
| Gemma Audio Direct | Gemma 4 E2B | Piper PL | Obsługiwany wybór silnika | Polski głos niepotwierdzony |
| Parakeet PL | Gemma 4 E2B | Piper PL | Obsługiwany wybór silnika | Polski głos niepotwierdzony |

Pozostałe silniki Android TTS i importowane modele LiteRT można badać osobno po identyfikacji wersji modelu, pakietu silnika i głosu. Kokoro i Pocket TTS nie są kwalifikowanymi polskimi wyjściami w obecnej aplikacji. Samo przejście testów JVM/Gradle **nie oznacza jakości głosu**.

## Macierz testów odbiorczych

To checklista, nie drugi plan. PASS dotyczy wskazanego SHA, środowiska i receipt. Brak próby na S22 = **PHYSICAL_OPEN**, nie FAIL. Nie mieszaj prób syntetycznych i rzeczywistych.

| ID | Przypadek / tor | Warunek zaliczenia | Ostatni stan |
| --- | --- | --- | --- |
| H1 | Preflight, 6 modułów, Python, Gemma lab, APK | Czysty Mac, exit 0 dla tego SHA | **PASS** `ce05a593`: 1311 JUnit, 438 Python, receipt `aicalls-host-final-all-tests-20261114-v4` |
| E1 | Głos, PL UI, STOP, szyfrowanie, Lab | Emulator, 0 błędów | **PASS 29/29** `50234e95`: `aicalls-emulator-safe-instrumentation-20261114-v4` |
| R1 | Android STT → Gemma E2B → Android TTS PL | S22: WER, ASR/model/TTS/E2E, odsłuch, echo | Bazowy tor znany; pomiar **PHYSICAL_OPEN** |
| R2 | Gemma Audio Direct → Gemma E2B → Android TTS PL | S22: długi RX, retry P1, WER, timeout, latencje | 3/3 syntetyczne + starsza rozmowa MVP; retest **PHYSICAL_OPEN** |
| R3 | Parakeet PL → Gemma E2B → Android TTS PL | S22: model natywny gotowy, WER, latencje | **PHYSICAL_OPEN**; brak modelu = `not_available` |
| R4–R6 | R1–R3 z polskim Piper | S22: potwierdzony głos PL, odsłuch, latencje | **PHYSICAL_OPEN**; brak głosu = `not_available` |
| C1 | 4–6 tur: długa mowa, szum, cisza, korekta | S22: rozpoznanie i poprawna kolejność tur | **PHYSICAL_OPEN** |
| C2 | Barge-in, STOP podczas ASR/model/TTS/TX | S22: brak starego TX, brak obcego hangup | **PHYSICAL_OPEN** |
| C3 | „Do widzenia” → zakończenie | S22: brak dalszego pytania, exact app-owned END_CALL | **PHYSICAL_OPEN** |
| G6.1 | BOOT/retry, kampania, takeover/resume | Osobny fizyczny odbiór po stabilizacji I/O | **PHYSICAL_OPEN** |

**Kolejność:** sprawdź SHA/artefakty i dostępność silników off-call bez zmian S22. Dopiero po jawnej zgodzie, sprawdzeniu IDLE i urządzenia, przeprowadź jeden uzgodniony Call z C1–C3. Zbieraj agregaty i identyfikatory receipts, bez PII/WAV/transkryptów w Git. Nie uruchamiaj masowego dial.

## Wykonanie testu syntetycznego na urządzeniu (po zatwierdzonym APK)

`bash scripts/bin/aicalls audio-route-check` uruchamia przez chroniony USB Debug test **tylko poza rozmową**: zapisany Piper/głos PL syntetyzuje stałe polskie zdanie, natywny Parakeet je rozpoznaje. Zwraca `passed`, `tts_ms`, `native_prewarm_ms`, `native_infer_ms`, `word_errors` i `reference_words` — bez transkryptu, numeru, WAV czy zmian ustawień. Wymaga faktycznie zainstalowanej wersji APK z obsługą `audio_route_check`; samo przejście testów hosta nie jest odbiorem S22. Bez instalacji, AndroidTest i połączeń bez odrębnej zgody.

## Zapis nowego pomiaru

```sh
python3 scripts/aicall_tools/diagnostics/audio_route_quality_report.py report
python3 scripts/aicall_tools/diagnostics/audio_route_quality_report.py record --input /PRIVATE/aggregate-run.json
```

Plik `/PRIVATE/aggregate-run.json` musi zawierać wyłącznie zagregowane dane testowe, np.:

```json
{
  "run_id": "example-only-001",
  "source_sha": "9c0d987001b6e02586e3f944853a180c87ecd08d",
  "input": "GEMMA_AUDIO_DIRECT",
  "model": "GEMMA_4_E2B",
  "tts_engine": "ANDROID_TTS",
  "mode": "synthetic_offcall",
  "cases": 3,
  "passed": 2,
  "wer_errors": 2,
  "reference_words": 17,
  "asr_ms": 3200,
  "model_ms": 6800,
  "tts_ms": 850,
  "end_to_end_ms": 10850,
  "voice_quality_1_to_5": 3.5,
  "stop_ok": true,
  "barge_in_ok": null,
  "end_call_ok": null,
  "evidence_id": "test-receipt-001"
}
```

**To wyłącznie przykład struktury, nie rzeczywisty wynik.** Pole `mode` to `synthetic_offcall` albo `real_call`. Lokalny rejestr jest w `benchmarks/audio_route_quality_runs.jsonl`; zapis jest jawny, wyniki się dopisują, identyfikator uruchomienia musi być unikatowy. Nie ma automatycznego publikowania do Git.

## Jak porównywać

- **ASR:** liczba błędnych słów / liczba słów odniesienia (WER), dla identycznego zestawu polskich próbek.
- **Szybkość:** osobno opóźnienia ASR, modelu, TTS oraz end-to-end (p50 i p95, w ms).
- **Dialog:** odsetek poprawnie wykonanych prób; pamięć i poprawki informacji należy testować w tym samym scenariuszu.
- **Jakość głosu:** ocena odsłuchowa 1–5, niezależna od WER i szybkości.
- **Sterowanie rozmową:** osobno STOP, przerwanie wypowiedzi i fizyczne zakończenie połączenia.

Należy zapisywać commit APK, wariant wejścia, model, silnik/głos TTS, rodzaj testu i identyfikator dowodu. Nie wolno zapisywać w publicznym Git numerów telefonów, WAV ani transkryptów rozmów. Testy syntetyczne i rzeczywiste nie mogą być uznawane za równoważne. Do decyzji o preferowanej kombinacji potrzeba powtarzalnych testów na S22 — sam raport nie generuje oceny.
