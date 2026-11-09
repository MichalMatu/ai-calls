# AI Calls — G5.3: prywatny korpus rozmów, przegląd jakości i latencja — 2026-11-09

## Decyzja
**G5.3 PRIVATE_QUALITY_EVIDENCE_S22_PASS / HUMAN_SEMANTIC_REVIEW_OPEN.**
Na fizycznym Samsungu S22 `SM-S906B` trzy niezależne sesje po trzy syntetyczne tury przeszły `9/9`; zapisano lokalnie dziewięć par tekstowych STT/odpowiedzi i **18 WAV** do prywatnego katalogu aplikacji i Mac. Offline HTML do odsłuchu i formularz 8 ocen jest gotowy. Nie wolno zamknąć całego G5.3 bez *rzeczywistej oceny człowieka* pełnych wypowiedzi; wszystkie `TOPIC, MEMORY, CORRECTIONS, FACTUALITY, NATURALNESS, QUESTIONS, PROMISES, ENDING` pozostają `UNASSESSED`. G6 realna telefonia osobno NOT_RUN. Gemma 4 E2B pozostaje główna.

Źródło przed checkpointem: `main@b0409ce1a6bee3c2c91328cf18e1eb16260b617d`. Wzorcowy techniczny STOP G5.3 na STT/E2B/TTS pozostał PASS z [poprzedniego checkpointu](2026-11-09-g5p3-active-native-stop-stt-e2b-tts-technical-pass.md), **nie był ponawiany**. Nowy zakres to wyłącznie opt-in zbieranie syntetycznych danych jakościowych i renderowanie prywatnego review; normalna aplikacja bez parametru testowego nie zapisuje tego korpusu.

## Kod i prywatność
- `AndroidG52MultiturnSpeechSkillsE2ETest` reutilizuje istniejący `LocalSpeechTextPipeline` i `AndroidSkillLabSessionFactory`; opcjonalne `g53_private_evidence=true` zatrzymuje referencję syntetycznej mowy, prawdziwy transcript STT, zatwierdzoną pełną odpowiedź Gemmy, PCM RX/TX, 3 zegary i decyzje Skills. Bez flagi test nadal niczego nie zapisuje.
- `G53PrivateAudioEvidence` zapisuje `manifest.json` dopiero po przygotowaniu plików `turn-NN-rx.wav` i `turn-NN-tx.wav`, 16 kHz/mono/PCM16, wyłącznie w `context.noBackupFilesDir/g53-synthetic-audio`. Połączenie telefoniczne, mikrofon, odsłuch głośnikiem i efekty zewnętrzne nie są uruchamiane.
- Prywatny odczyt Mac to `adb exec-out run-as` do `~/Library/Application Support/ai-calls/qa/g53-private/` z katalogami 0700 i plikami 0600. Zdalne JSON tasków, GitHub i logcat otrzymują wyłącznie liczniki, bez surowych transkryptów i WAV.
- `scripts/aicall_tools/diagnostics/g53_private_audio_review.py` weryfikuje manifest, izoluje ścieżki i format WAV, HTML-escapuje tekst oraz generuje lokalne `review.html` z odtwarzaniem i ośmioma wymiarami. Eksport ludzkiej recenzji wymaga ręcznej tury + notatki dla PASS/FAIL; bez recenzenta status to `UNASSESSED`. Lokalny indeks trzech sesji znajduje się w **`~/Library/Application Support/ai-calls/qa/g53-private/index.html`**.

## Dokładne receipts
| Bramka | Task | Wynik |
| --- | --- | --- |
| Pierwsza kompilacja prywatnego opt-in testu | `aicalls-g53-private-corpus-source-qa-20261109-v1` | **FAILED** wyłącznie `Unresolved reference arguments` w AndroidTest; F-162, nie przeprowadzono S22 |
| Poprawione źródło i regresja | `aicalls-g53-private-corpus-source-qa-20261109-v2` | DONE/0, AndroidTest/Kotlin compile, `verify_host.sh`, 371 Python, lint/audio, anti-friction, clean |
| S22 i eksport pierwszej sesji | `aicalls-g53-private-3turn-audio-corpus-s22-20261109-v1` | DONE/0, kontrakt writer 1/1, E2B/STT/TTS 3 tury 1/1, prywatne 6 WAV/3 transkrypcje, 885256 B / 7 plików na Mac, IDLE i clean |
| Renderer/bezpieczeństwo | `aicalls-g53-private-review-tool-mac-qa-20261109-v1` | DONE/0, 4/4 nowe testy Pythona, pełny host `375 Python PASS`, clean |
| Weryfikacja lokalnej recenzji S1 | `aicalls-g53-private-human-review-render-mac-20261109-v1` | DONE/0, 3 tury, 6 WAV, 8 wymiarów bez oceny, tryb prywatny 0700/0600 |
| S22 dwie kolejne niezależne sesje | `aicalls-g53-private-voice-two-more-sessions-s22-20261109-v1` | DONE/0, 2×3 tury, 12 WAV, bez ponownej instalacji, brak Call, IDLE i clean |
| Mac prywatny indeks i metryki N=3 | `aicalls-g53-private-9turn-report-mac-20261109-v1` | DONE/0, 3 strony recenzji, indeks, WAV 18, 9 turn, 8 × `UNASSESSED`, WER/CER |
| Realny rozkład LiteRT bez danych osobowych | `aicalls-g53-native-timing-readonly-s22-20261109-v1` | DONE/0, 9/9 rekordów GPU 4096: engine/Conversation/generate, niezależne od wyników human review |

**F-162 VERIFIED_SCOPED.** Błąd był tylko w opt-in AndroidTest: `instrumentation.arguments` → `InstrumentationRegistry.getArguments()` na `main@ec1931b`; Mac v1 FAILED zachowany, v2 PASS, fizyczny writer/test/eksport PASS. Nie zmieniono produkcyjnej telefonii.

## Wyniki ilościowe na S22 (zegar każdej tury od startu syntetycznego wejścia do gotowego PCM)
| Sesja | Tura 1 | Tura 2 | Tura 3 |
| --- | ---: | ---: | ---: |
| 1 | 47389 ms | 25952 ms | 12972 ms |
| 2 | 42569 ms | 26791 ms | 11851 ms |
| 3 | 39114 ms | 41031 ms | 16671 ms |
| **Mediana pozycji, n=3** | **42569 ms** | **26791 ms** | **12972 ms** |

Mediany czasu STT po pozycji: **4395 / 5898 / 5121 ms**. Od STT do zatwierdzonej odpowiedzi (model + routing + approval): **33746 / 17635 / 4626 ms**. TTS od zatwierdzenia do PCM: **4428 / 3365 / 3225 ms**. **STT WER 0/75 słów = 0.000; CER = 0.000** na **sztucznej polskiej mowie**; nie uogólniać na ludzką mowę, akcent, hałas i naturalność.

**Natywne 9/9 kompletne `AiCallBridgePerf`:** w każdej sesji `conversation_reused=false,false,true`, `conversation_turn=1,1,2`. Silnik tworzy się w turze 1 (**9.0–15.6 s**), Conversation w turach 1 i 2 (**3.0–4.1 s**), a dopiero trzecia używa istniejącej Conversation bez kosztu ponownego tworzenia. Natywne `generate_ms` w poszczególnych sesjach: **[18382,13175,4617]**, **[17249,13581,3630]**, **[19861,24996,5764]**. Tura 2 odświeża Skills i Conversation, ale samo generowanie też bywa wolne: konieczny oddzielny eksperyment optymalizacyjny bez wyłączania dynamicznych Skills na ślepo. Żadnego rzetelnego p95 z n=3.

## Końcowa walidacja dokumentacji — historia QA

`aicalls-g53-private-voice-final-docsync-mac-20261109-v1` miał `DONE/0` tylko w kroku synchronizacji; celowany dokumentacyjny krok 2 zakończył się `FAILED` przez wyłącznie zbyt dokładny matcher interpunkcji statusu `F-162` (F-163). Żaden dodatkowy test S22/produktu nie był uruchamiany. Nowy odrębny Mac receipt `aicalls-g53-private-voice-final-docsync-mac-20261109-v2` **DONE/0** (każda komenda exit=0), weryfikacja checkpointu, handoff i `audit_product_friction.py` PASS, Git clean. F-163 VERIFIED_SCOPED; oryginalne v1 FAILED zachowane.

## Pozostały zakres i pojedynczy następny krok
**Human quality = OPEN/UNASSESSED.** Na lokalnym Macu otworzyć `index.html`, odsłuchać trzy sesje i w każdej zaznaczyć osiem ocen z dowodem konkretnej tury. Nie wolno automatycznie uznać poprawności znaczenia/naturalności na podstawie WER, JSON, asercji `wtor` ani własnych odpowiedzi badanego modelu. Następnie zapisać prywatny JSON recenzji i dopiero rozstrzygnąć G5.3. Odrębna praca może skontrolować opóźnienie cold/refresh/warm; nie zaczynać G6 bez osobnego rzeczywistego grantu i odbioru. Nie kopiować korpusu z Mac/S22 do Git.

Tryb: GitHub Connector + Mac Local Agent + ChatGPT Linux Sandbox; bez Codex/Work/GitHub Actions, nowych branchy/force-push; commity `[skip ci]`.
