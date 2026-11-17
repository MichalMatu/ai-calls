# AI Calls — szybki rejestr jakości kombinacji audio

**Archiwum pomiarów, nie aktywny backlog.** Aktualny plan: [PROJECT](../../docs/PROJECT.md).
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

To historyczny rejestr wyników, nie lista do wykonania. PASS dotyczy wskazanego SHA, środowiska i receipt. Brak próby na S22 = **PHYSICAL_OPEN**, nie FAIL. Nie mieszaj prób syntetycznych i rzeczywistych.

| ID | Przypadek / tor | Warunek zaliczenia | Ostatni stan |
| --- | --- | --- | --- |
| H1 | Preflight, 6 modułów, Python, Gemma lab, APK | Czysty Mac, exit 0 dla tego SHA | **PASS** `ce05a593`: 1311 JUnit, 438 Python, receipt `aicalls-host-final-all-tests-20261114-v4` |
| E1 | Głos, PL UI, STOP, szyfrowanie, Lab | Emulator, 0 błędów | **PASS 29/29** `50234e95`: `aicalls-emulator-safe-instrumentation-20261114-v4` |
| R1 | Android STT → Gemma E2B → Android TTS PL | S22: WER, ASR/model/TTS/E2E, odsłuch, echo | **S3/S4 syntetycznie PASS**, odsłuch i rzeczywisty Call PHYSICAL_OPEN |
| R2 | Gemma Audio Direct → Gemma E2B → Android TTS PL | S22: długi RX, retry P1, WER, timeout, latencje | **S3/S6 syntetycznie PASS**, długi RX i rzeczywisty Call PHYSICAL_OPEN |
| R3 | Parakeet PL → Gemma E2B → Android TTS PL | S22: model natywny gotowy, WER, latencje | **S3/S4 syntetycznie PASS**, rzeczywisty Call PHYSICAL_OPEN |
| R4–R6 | R1–R3 z polskim Piper | S22: potwierdzony głos PL, odsłuch, latencje | **S3/S4/S6 syntetycznie PASS**, odsłuch i rzeczywisty Call PHYSICAL_OPEN |
| S1 | **Piper Gosia → PCM16 → Parakeet PL (off-call)** | Dokładna Gosia, natywny Parakeet, brak rozmowy; WER i czasy | **PASS** na S22, SHA `0c79cf4`: TTS 2478 ms, prewarm 2466 ms, infer 437 ms, 0/6 błędnych słów; receipt `aicalls-s22-sole-piper-audio-retest-20261114-v1` |
| S2 | **Stały zestaw pl-audio-v1: Piper Gosia → Parakeet PL (5 × 2, off-call)** | 10 prób, ten sam hash tekstów, wszystkie próby pozytywne, łączny WER ≤25% | **PASS** S22 na SHA `7c08d554`: 10/10, 6/104 błędnych słów (WER 5,77%), E2E p50 4473,5 ms / p95 7763 ms, TTS p50 2682 ms, Parakeet infer p50 659 ms; termin/godzina 3/9 ×2, pozostałe 0; korpus SHA256 `fe64ca3f291656a7c067fb7adac35d956b66686485078f52725d50927e68e701`; receipt `aicalls-s22-fixed-audio-suite-baseline-20261114-v1` |
| C1 | 4–6 tur: długa mowa, szum, cisza, korekta | S22: rozpoznanie i poprawna kolejność tur | **PHYSICAL_OPEN** |
| C2 | Barge-in, STOP podczas ASR/model/TTS/TX | S22: brak starego TX, brak obcego hangup | **PHYSICAL_OPEN** |
| C3 | „Do widzenia” → zakończenie | S22: brak dalszego pytania, exact app-owned END_CALL | **PHYSICAL_OPEN** |
| G6.1 | BOOT/retry, kampania, takeover/resume | Osobny fizyczny odbiór po stabilizacji I/O | **PHYSICAL_OPEN** |

**Kolejność:** sprawdź SHA/artefakty i dostępność silników off-call bez zmian S22. Dopiero po jawnej zgodzie, sprawdzeniu IDLE i urządzenia, przeprowadź jeden uzgodniony Call z C1–C3. Zbieraj agregaty i identyfikatory receipts, bez PII/WAV/transkryptów w Git. Nie uruchamiaj masowego dial.

## Wykonanie testu syntetycznego na urządzeniu (po zatwierdzonym APK)

`bash scripts/bin/aicalls audio-route-check` uruchamia przez chroniony USB Debug test **tylko poza rozmową**: zapisany Piper/głos PL syntetyzuje stałe polskie zdanie, natywny Parakeet je rozpoznaje. Zwraca `passed`, `tts_ms`, `native_prewarm_ms`, `native_infer_ms`, `word_errors` i `reference_words` — bez transkryptu, numeru, WAV czy zmian ustawień. Wymaga faktycznie zainstalowanej wersji APK z obsługą `audio_route_check`; samo przejście testów hosta nie jest odbiorem S22. Bez instalacji, AndroidTest i połączeń bez odrębnej zgody.

## Stały korpus S2: Piper → Parakeet (off-call)

Uruchom na autoryzowanym S22: bash scripts/bin/aicalls audio-suite.
Korpus pl-audio-v1: 5 ustalonych polskich przypadków (powitanie, termin,
korekta, długa wypowiedź, pożegnanie), w tej samej kolejności × 2 powtórzenia.
Wynik zawiera hash pełnego korpusu, ID przypadku, WER, czasy TTS/inicjalizacji/ASR
oraz mediany i p95. PASS: 10/10 przypadków i łączny WER nie większy niż 25%.
Niezgodny hash przerywa test. Nie przesyłamy transkryptów, numerów ani PCM.
Przy każdej próbie TTS **ponownie generuje** audio ze stałego tekstu — same
próbki nie są gwarantowane bitowo identyczne, więc porównania dotyczą treści
i metryk, nie hashy WAV. To nie jest test jakości Gemmy, barge-in ani rozmowy.

## Macierz S3: sześć porównywalnych torów (S22, off-call)

Stały korpus pl-audio-v1: 5 polskich wypowiedzi × 2 rundy = 10
przypadków dla każdego z 6 torów; łącznie 60 prób.
Identyfikatory: matrix-android-google, matrix-android-piper,
matrix-parakeet-google, matrix-parakeet-piper,
matrix-gemma-google, matrix-gemma-piper.
Wykonywane w test APK na S22 przez ograniczone adb probe: bez Telecom,
SIM i zapisu profilu. TTS jawnie wskazuje Google lub Piper/Gosię.
Oddzielnie raportuj WER, TTS, inicjalizację oraz czas ASR.
Gemma Audio Direct mierzy rozpoznawanie audio, nie całą odpowiedź
modelu dialogowego. STOP, barge-in i hangup nadal wymagają osobnego odbioru.

## Wyniki S3 na S22 — 14.11.2026

Kod testu: SHA 5990b3b1; Android Test APK SHA-256:
2a5ac0ad2a2ccbadc9bc9b2b207646442af867256be321037679ff82aa2402ef.
Ten sam korpus pl-audio-v1, 5 tekstów × 2 powtórzenia × 6 torów.
Żaden test nie użył połączenia, mikrofonu, prywatnej rozmowy ani nie
zmienił zapisanego profilu/grantów. Raporty lokalne w prywatnym katalogu
audio-matrix na Macu. Testy 10/10 nie oznaczają WER=0 ani pełnego dialogu.

| Wejście | TTS | PASS | WER | TTS p50 | ASR infer p50 / p95 | Inicj. p50 |
|---|---|---:|---:|---:|---:|---:|
| ANDROID_STT | Google | 10/10 | 3,85% | 1669,5 ms | 624 / 1289 ms | 41,5 ms |
| ANDROID_STT | Piper Gosia | 10/10 | 7,69% | 3765,5 ms | 602 / 1413 ms | 36 ms |
| PARAKEET_PL | Google | 10/10 | 5,77% | 1453 ms | 1192 / 2203 ms | 1390 ms |
| PARAKEET_PL | Piper Gosia | 10/10 | 5,77% | 2428 ms | 1160,5 / 2677 ms | 1197,5 ms |
| GEMMA_AUDIO_DIRECT | Google | 10/10 | 7,69% | 1542 ms | 10894,5 / 45235 ms | nieujęte |
| GEMMA_AUDIO_DIRECT | Piper Gosia | 10/10 | 9,62% | 3438 ms | 8681,5 / 12324 ms | nieujęte |

Pomiary Gemma Audio Direct korzystają z wcześniej rozgrzanego modelu;
raportowane zero inicjalizacji nie oznacza zerowego cold start.
Każdy tor ma 104 słowa odniesienia. Weryfikacja dat/godzin wymaga
odrębnego testu semantycznego: błędy powtarzały się szczególnie
w przypadku appointment. Wstępnie najszybszy i najdokładniejszy:
ANDROID_STT + Google TTS, ale nie jest to jeszcze kwalifikacja
pełnego dialogu, jakości odsłuchowej ani Telecom.
Dowody: aicalls-s22-matrix-parakeet-piper-google-20261114-v2,
aicalls-s22-matrix-android-google-piper-20261114-v1,
aicalls-s22-matrix-gemma-google-piper-20261114-v1.

## S4: trzy syntetyczne tury z Gemmą E2B — fizyczny S22

Cztery konfiguracje zdały po 3/3 testów funkcjonalnych (wiadomość →
korekta terminu → pamięć poprawionego dnia), prawdziwa natywna
odpowiedź Gemmy E2B i końcowe PCM16, ale **bez połączenia telefonicznego**.
Pierwsza para: SHA c9d0edade58b5ebbcf6508863df50110f89beb63,
receipt aicalls-s22-fullturn-two-route-20261114-v1.
Druga para: SHA 6c2f74c38371178d652e51b96ae5d0b85bde86c9,
receipt aicalls-s22-fullturn-androidpiper-parakeetgoogle-20261114-v1.

| Tor STT → Gemma E2B → TTS | Tury | PCM p50 / p95 [ms] | Odpowiedź p50 [ms] | STT p50 [ms] |
|---|---:|---:|---:|---:|
| Android → Google | 3/3 | 16539 / 26181 | 14873 | 5195* |
| Android → Piper Gosia | 3/3 | 17551 / 27257 | 13802 | 4876 |
| Parakeet → Google | 3/3 | 20542 / 25800 | 18039 | 7288 |
| Parakeet → Piper Gosia | 3/3 | 26513 / 33441 | 20792 | brak* |

* Dla Android/Google STT odzyskano z istniejącego logu (4628, 5998,
5195 ms); dla Parakeet/Piper brak pełnego pola w starym raporcie.
Późniejsze dwa tory miały kompletny parser; testy zakończono PASS.
STT w teście 3-tur obejmuje odtwarzanie PCM w czasie rzeczywistym,
więc nie jest porównywalne z samym czasem inferencji ASR w tabeli S3.
Gemma Audio Direct jako ASR wymaga kwalifikacji współdzielenia
modelu natywnego z dialogowym Gemma E2B — S3 nie jest pełnym flow.
Telecom, zakończenie pożegnalne i barge-in nadal PHYSICAL_OPEN;
STOP wymaga osobnego testu poza rozmową i odbioru telefonicznego.

## S5 — STOP poza rozmową, fizyczny S22

Pięć niezależnych testów fizycznych PASS na SHA eb60b7e (14.11.2026):
cancel/restart Android STT, cancel/restart TTS, anulowanie natywnej
generacji Gemmy z odzyskaniem kontekstu, odrzucanie spóźnionych callbacków
oraz idempotentny przycisk STOP bez dzwonienia. Receipt
aicalls-s22-native-stop-matrix-20261114-v1, test APK SHA-256
f8dd403ca3e6071592222330380a630d3324aadf888e89aabecca19a48cb01dd.
Wszystkie 5/5, telefon IDLE, profil/granty bez zmian.
Nie jest to test STOP podczas rzeczywistej rozmowy.

## S6 — eksperymentalny pełny tor Gemma Audio Direct

Obie dodatkowe konfiguracje 3-tur zaliczone **3/3** na fizycznym
S22, SHA bed4397cad0a3a4082744eaaa8e861fce0fe67a0,
receipt aicalls-s22-gemma-audio-direct-fullturn-20261114-v1,
APK testów SHA256 36bf0ade846d5ba01ffeabb7712f0ac5f1ddb7843b14854825b297bb67cb6969.
Tak samo: wiadomość → korekta dnia → pamięć poprawionej daty;
bez osobnego ASR, staged audio→transcript→reply w jednym Engine.
Korzysta z produkcyjnego DialogueEngineProvider, nie z tekstowego
adaptera Test Lab. Bez Telecom, instalacji produkcyjnego APK,
zmian profilu, grantów, mikrofonu lub prawdziwego rozmówcy.

| Tor Gemma Audio Direct → E2B → TTS | Tury | Gotowe PCM p50 / p95 | Audio→transcript p50 |
|---|---:|---:|---:|
| Google TTS | 3/3 | 19523 / 27590 ms | 17999 ms |
| Piper Gosia | 3/3 | 22155 / 26713 ms | 18068 ms |

**S3+S4+S6:** 60/60 prób ASR/TTS oraz 18/18 pełnych
syntetycznych tur. **S5:** 5/5 testów STOP poza rozmową.
Wstępny wybór wydajnościowy z 6 torów:
Android STT → Gemma E2B → Google TTS (PCM p50 16539 ms).
Nie jest to jeszcze rekomendacja jakości głosu ani kwalifikacja
telefoniczna. C1–C3 (hałas/długie rozmowy, barge-in, END_CALL
i hangup z Telecom) pozostają **PHYSICAL_OPEN**. Dodatkowo
Test Lab z wybranym GEMMA_AUDIO_DIRECT wymaga osobnej kwalifikacji
adaptera: obecny wynik dotyczy bezpośredniego produkcyjnego backendu.

## S7 — cold vs hot Gemma E2B (wyizolowany test natywny)

gemma-cold-warm: świeży natywny Engine bez prewarm (3 identyczne
wypowiedzi), potem osobny Engine załadowany przez prewarm, z
potwierdzeniem natywnej gotowości (3 identyczne wypowiedzi).
Stały system prompt, limit 4096 tokenów i format JSON; bez STT, TTS,
telefonu lub zmian profilu. Osobno WALL_MS, ENGINE_MS, CONV_MS,
GEN_MS, REUSED, CHARS, REPAIRS oraz PREWARM_WALL_MS/ENGINE_MS/CONV_MS.
Pierwsza wygenerowana odpowiedź może mieć REUSED=0 nawet po
udanym prewarm, np. gdy silnik naprawia odpowiedź i tworzy konwersację
od nowa. Kryterium przygotowania to brak ponownej inicjalizacji Engine.
Cold oznacza świeży Engine, nie restart telefonu ani wyczyszczony
systemowy cache modelu. Tury 2/3 mają rosnący kontekst, co może
wpływać na generację; porównanie inferencji uwzględnia długości
odpowiedzi. **S7 — potwierdzenie na S22:** dwa przebiegi po 6 identycznych prób
native Gemma E2B (69 znaków odpowiedzi w każdej próbie).
Pomiary samego modelu; brak STT, TTS i połączenia.

| Metryka | Przebieg 1: SHA 1c09366 | Przebieg 2: SHA e0baaf1 |
|---|---:|---:|
| Inicjalizacja nowego Engine | 6327 ms | 5533 ms |
| Utworzenie Conversation | 1567 ms | 1491 ms |
| Pierwsza odpowiedź cold | 16178 ms | 15302 ms |
| Pełny prewarm przed pierwszą odpowiedzią | 6126 ms | 5795 ms |
| Prewarm Engine / Conversation | 4607 / 1515 ms | 4160 / 1632 ms |
| Pierwsza odpowiedź po prewarm | 7784 ms | 8455 ms |
| Hot, cold-branch, tura 2 | 1439 ms | 1437 ms |
| Hot, cold-branch, tura 3 | 1506 ms | 1471 ms |
| Hot, prewarm-branch, tura 2 | 1491 ms | 1633 ms |
| Hot, prewarm-branch, tura 3 | 1508 ms | 1718 ms |

Średnio: cold-first **15740 ms**, prewarmed-first **8119,5 ms**,
oszczędność **7620,5 ms = 48,4%**. Prewarm wymagał średnio
5960,5 ms. Mediana 8 kolejnych hot-turns to **1498,5 ms**
(zakres 1437–1718 ms). Wszystkie odpowiedzi: 69 znaków.
W pierwszej turze obu wariantów i obu przebiegów zanotowano
REPAIRS=1; w kolejnych REPAIRS=0. W prewarm-first
ENGINE_MS=0 i CONV_MS=0, ale REUSED=0: jest to odtworzenie
pierwszej konwersacji przy naprawie JSON, nie cold Engine.
To korelacja, a nie dowód ile milisekund zajmuje sama naprawa.
Cold oznacza nowy natywny Engine, nie restart S22 ani pusty
OS file cache.

Przebieg 1: 6 odpowiedzi zostało wygenerowanych, lecz końcowy
asercyjny check błędnie oczekiwał REUSED=1 przy pierwszej
odpowiedzi prewarm. Przebieg 2: **PASS 6/6**, bez zgadywania
stanu silnika. Telefon IDLE, preferencje bez zmian.
Receipts: aicalls-s22-gemma-cold-warm-real-native-20261115-v1
oraz aicalls-s22-gemma-cold-hot-confirmation-20261115-v2.
Prywatne agregaty zachowano na Macu. Samych 1,5 s nie
traktować jako czasu end-of-speech do TX na prawdziwym Call.

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
