# AI Calls — F-156 i porównanie Gemma E2B/E4B, Agent Skills Gallery (2026-11-09)

## Preflight i warunki

- Repo `MichalMatu/ai-calls`, `main`, fizyczny Samsung S22 `SM-S906B` (USB ADB), bez jakiegokolwiek realnego Call, kampanii ani zgody na efekty telefoniczne. `Call IDLE` przed/po, bez wipe, oryginalny aktywny profil modelu i zaszyfrowane presety nietknięte.
- `aicalls-f156-f157-preflight-e4b-and-promptfix-host-20261109-v1` **DONE/0**: urządzenie PASS, E2B model `2 588 147 712` bajtów i E4B `3 659 530 240` bajtów **zgodne z pinned size katalogu**, dostępne lokalnie — nie pobierano. Zapas RAM `MemAvailable≈2.7 GiB` przed testem; brak rewalidacji SHA wielogigabajtowych plików w tym przebiegu. Host `scripts/verify_host.sh`, Gradle/AndroidTest/JVM/lint/Python/audio i friction **PASS**, checkout czysty.
- Wszystkie porównania wykonano z **kontekstem 2048** na identycznych, fikcyjnych wypowiedziach, w oddzielnych sekwencyjnych sesjach natywnych modeli. W obu przypadkach **GPU**, ten sam JSON schema `reply/tool`. Żadne proponowane narzędzie nie zostało wykonane. Nie zawarto promptów ani odpowiedzi w publicznych logach; tylko metryki, bool i długości odpowiedzi.

## F-156 — poprawka aktywnych Skills

**RED:** `aicalls-f156-skills-ui-native-ab-s22-20261109-v1`: GUI kopiowanie/edycja/aktywacja/wyłączenie/przywrócenie własnego Skill **PASS 1/1**, poprzednia macierz **PASS 2/2**, ale E2B w pierwszym A/B nie wygenerowała wymaganego znacznika. `aicalls-f156-skills-real-ab-s22-triage-20261109-v2`: potwierdzono RED dwukrotnie (`active_marker=false`, `inactive_marker=false`).

**Diagnoza:** `aicalls-f156-salience-prompt-s22-probe-20261109-v2` **DONE/0**, ten sam E2B 4096: pozycja instrukcji tuż przed formatem odpowiedzi **marker true**, minimalny prompt **marker true**. Stara kompozycja rozmywała ważność instrukcji; pierwsza wersja sondy nie wykonała modelu przez wadliwe dopasowanie nagłówka — poprawiona w v2.

**GREEN:** `ConversationInstructionBuilder.kt` przesunął **wersjonowane Skills** po stałych zasadach bezpieczeństwa, bezpośrednio przed sekcją odpowiedzi (commit `63e5b9c6483ebe4bd071e16148b588351e02de65`). Dodano JVM test kolejności i izolacji nieaktywnych Skills (`f55fea5816dfeca21d411cf828ffee22bc2bd0e2`). Fizyczny `aicalls-f156-prompt-fix-native-s22-reaccept-20261109-v1` **DONE/0**: E2B `active_marker=true`, `inactive_marker=false`, natywne A/B **1/1 PASS**, UI Skills edit/revision **1/1 PASS**. `F-156 VERIFIED_SCOPED`; bezpieczeństwo/telefonia/rozdzielenie grantu niezmienione.

## Dwa modele — czasy i podstawowa jakość

`aicalls-f157-e2b-e4b-native-differential-s22-20261109-v1` **DONE/0**: pełna Mac bramka **PASS**, S22 **1/1 AndroidTest, 4/4 sesje, 8/8 tur natywnych PASS**, żadnego telefonu, wybór E2B w profilu zachowany.

| Model | Skill | Zimna pierwsza tura | Kolejna tura | Marker oczekiwany | Pamięć kodu | JSON / brak narzędzia |
| --- | --- | ---: | ---: | --- | --- | --- |
| E2B | włączony | 14 382 ms | 2 162 ms | obecny | PASS | PASS |
| E2B | wyłączony | 12 300 ms | 2 305 ms | nieobecny | PASS | PASS |
| E4B | włączony | 42 513 ms | 10 513 ms | obecny | PASS | PASS |
| E4B | wyłączony | 40 192 ms | 6 142 ms | nieobecny | PASS | PASS |

Zimny start to cały czas: uruchomienie silnika, stworzenie konwersacji i generacja. Porównanie **nie** używa identycznej liczby generowanych tokenów (backend nie dostarczył liczników); E4B dawała zwykle dłuższą odpowiedź. RAM `Debug.MemoryInfo` odczytany po turnach jest punktem pomiarowym, **nie peak GPU/CPU RSS**.

## Trudniejszy korpus semantyczny

`aicalls-f157-e2b-e4b-semantic-s22-20261109-v1` **DONE/0**, `AndroidGemmaE2BE4BSemanticDifferentialNativeTest` **1/1 AndroidTest PASS**, 2 modele × [3-tura korekta faktu + 2-tura zakończenie] = **10/10 technicznych tur**. Czysty build test APK, bez zmian profilu, Call IDLE.

| Model i korpus | Zimna tura | Średnia ciepłych tur | Kryterium semantyczne |
| --- | ---: | ---: | --- |
| E2B — sprostowanie kodu | 13 584 ms | 2 130 ms | nowy kod PASS, brak starego PASS, tool=none PASS |
| E2B — cytat i zakończenie | 11 175 ms | 2 737 ms | cytat → continue PASS, jawny koniec → propozycja end_call PASS |
| E4B — sprostowanie kodu | 38 776 ms | 5 891 ms | nowy kod PASS, brak starego PASS, tool=none PASS |
| E4B — cytat i zakończenie | 35 369 ms | 6 419 ms | cytat → continue PASS, jawny koniec → propozycja end_call PASS |

**Łącznie 18/18 tur** i oba modele w **tym konkretnym** syntetycznym korpusie przeszły wszystkie punktowe kryteria jakości. E4B nie dała dodatkowej poprawności, lecz kosztem było zwykle **~3× dłuższe cold i warm**. Nie wyciągać z tak małej próby wniosku o ogólnej wyższości E2B: brak N≥3 niezależnych replikacji dla każdego korpusu, pytań długich, oceny naturalności człowieka, audio STT/TTS, pomiarów p95, rzeczywistych telefonii i tokenów/s.

## Gallery Agent Skills — analiza i następny eksperyment

Obecny `ConversationSkillLibrary` jest **wersjonowaną biblioteką prompt Skills**, nie runtime `Agent Skills` Gallery. Oficjalny Google [Agent Skills / SKILL.md](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md) udostępnia `name`, `description` i treść instrukcji ładowaną **na żądanie** oraz opcjonalne skrypty JS/natywne narzędzia. Selekcja nadal korzysta z promptu modelu; narzędzia wykonuje aplikacja. Zalecany kierunek to **selektywne Skills na życzenie + jawne kontrolery narzędzi** z istniejącymi granicami autoryzacji, bez niekontrolowanego wykonywania URL/shell i bez przepisywania całego repo.

[Ocena i projekt warstwy modułowej](../AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md). Następny fizyczny benchmark powinien zestawić *obecny prompt po F-156* vs *selektor + tylko adekwatne instrukcje* przy tym samym modelu, tych samych promptach i koszcie selekcji wliczonym w latency.

## Decyzja robocza

**Pozostawić E2B domyślną Gemmą na S22** dla responsywnych rozmów: w dwóch fizycznych zestawach testów (18 tur) E4B nie wykazała wyższej poprawności wg punktowych kryteriów, za to była istotnie wolniejsza. E4B pozostaje legalnym lokalnym wariantem testowym do cięższych zadań; rekomendacja nie modyfikuje profilu telefonu. Osobno rozważyć MTP dopiero po sprawdzeniu wersji LiteRT-LM i pomiarach stabilności, wydajności i jakości na S22.

**Status:** F-156 scoped VERIFIED, E2B/E4B fizyczny benchmark **PASS**, architektura Gallery **DESIGN_REVIEW** (nie wdrożone), długie dialogi/F-148 i N≥3 **OPEN**, rzeczywiste Call interventions **NOT_RUN**.
