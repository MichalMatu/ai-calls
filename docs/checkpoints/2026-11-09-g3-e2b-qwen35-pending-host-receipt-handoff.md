# AI Calls — G0–G3 pełny checkpoint i handoff (2026-11-09; G3 wynik odebrany)

**Stan referencyjny przed tym commitem dokumentacyjnym:** `main@29669674c694d722eb0fe47f482f0e06927f0848`. Dokumentacja opisuje tylko zaobserwowane dowody; jej publikacja nie zmienia kodu Android, modelu, profilu ani urządzenia. Po commicie należy odczytać świeży HEAD.

**Jedyny plan:** [AI_CALLS_MASTER_EXECUTION_PLAN](../AI_CALLS_MASTER_EXECUTION_PLAN.md). **Pierwsza otwarta bramka: G3 — analiza prywatnej semantyki i formalna decyzja.** Macierz 96 tur na S22 jest już ukończona; nie powtarzać inferencji ani nie przechodzić do G4 bez decyzji.

## 1. Macierz stanu — nigdy nie zamieniać braku raportu na PASS

| Bramka | Stan i dowód | Czego wynik nie dowodzi |
| --- | --- | --- |
| G0 — F-158 Skills kontekstowe | **S22_PASS techniczny**, `aicalls-g0-f158-s22-20261109-v1` DONE/0; 24/24 tur E2B/GPU, 12 rekordów + 4 podsumowania, bez Call | Human semantic review i dobór Skill w każdej turze nieprzetestowane |
| G1 — źródło i sprzęt Qwen3.5 | **SOURCE_PASS**, `aicalls-g1-qwen35-2b-metadata-hardware-20261109-v1` DONE/0; pin/licencja/S22/ABI/RAM/flash | Same metadane nie są uruchomieniem modelu |
| G2 — Qwen3.5 tekst na S22 | **MAC_PASS + S22_PASS techniczny**, `aicalls-g2-qwen35-lab-cpu-mac-qa-20261109-v2` DONE/0 i `aicalls-g2-qwen35-2b-cpu-native-s22-20261109-v1` DONE/0; 3/3 non-Call tury na CPU | Brak wykazanej wyższej jakości i brak produkcyjnej telefonii |
| G3 — E2B vs Qwen3.5 | **S22_TECHNICAL_PASS 96/96 tur**, 48 rekordów i 2 podsumowania, prywatny Mac JSONL 48/48 PASS; trzy komendy hosta PASS, końcowy clean check NOT_RUN przez task budget | Semantic review, końcowy clean Git i wybór modelu OPEN |
| G4–G7 | **PLANNED**, bez awansu z G3 | Nie uznawać dynamicznych Skills / pełnych rozmów / Call / UX za wdrożone |

## 2. Implementacja i dowody G1/G2

- Backend: LiteRT-LM Android `0.19.0`. Kandydat `QWEN_3_5_2B` dodany do `DialogueModelId` / `LanguageModelCatalog`, tekst-only, **Lab-only**. Bez drugiego engine runnera. `AudioEngineRegistry.incompatibility` odmawia profilu telefonicznego Qwen3.5; modelu nie można aktywować zwykłym przyciskiem „Użyj”. Fabryczny `GEMMA_4_E2B` nadal domyślny.
- Dokładny artefakt: `litert-community/Qwen3.5-2B`, rewizja `f24013ba6844bd39be2d1f68e8d2421a77eed1f2`, `Qwen3.5-2B_int8.litertlm`, **2 119 843 840 B**, SHA-256 `c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49`, źródłowa deklaracja Apache-2.0. Mac pobrał plik i sprawdził SHA, S22 zaimportował go istniejącą aplikacyjną ścieżką z weryfikacją SHA i czyszczeniem stagingu.
- Sprzęt: Samsung `SM-S906B`, Exynos `s5e9925`, Android ABI `arm64-v8a`; `MemTotal=7 445 972 KiB`, jednorazowe `MemAvailable=2 853 956 KiB` w G1. Na Exynos/Mali polityka kandydacka **CPU_ONLY**; nowa konwersacja dostaje niezależny Engine, by nie mieszać rekurencyjnego stanu Qwen3.5. GPU Qwen3.5 **NOT_RUN**.
- S22 G2 (`aicalls-g2-qwen35-2b-cpu-native-s22-20261109-v1`, DONE/0): APK/test APK bez wipe, Call IDLE przed i po, **test import 1/1 PASS**, test Qwen CPU 1/1 PASS: 3 tury/2 niezależne backendy, cold **23 983 ms**, warm **3 537 ms**, fresh **15 758 ms**, PSS procesu **2 897 MiB** i wolne RAM **2 837 MiB** w końcowym próbkowaniu, `low_memory=false`. To trzy obserwacje, nie benchmark statystyczny ani PSS peak.
- Host source G2 v1 miał 3 czerwone testy przy 818 JVM, ponieważ dotychczasowe kontrakty zakładały, że **każdy** katalogowy model jest gotowy do telefonu. Minimalna naprawa testów (zachowująca bezpieczny rozdział Lab/Call) `4d695eb` i dedykowany natywny test `0f60782`. G2 v2 **DONE/0**, celowane testy, pełny `bash scripts/verify_host.sh`, 371 Python, Kotlin/JVM, lint/APK/AndroidTest oraz `audit_product_friction.py` PASS. Nie powtarzać starych RED bez nowej przyczyny.

## 3. Co dokładnie zawiera G3

- Kod na `main@bf31c485b776e34cf78fd3f1356d5424af9c1369`: `AndroidGemmaE2BQwen35DialogueABNativeTest`, AndroidTest na jednej fizycznej S22, **8 scenariuszy PL × 3 niezależne powtórzenia × 2 modele × 2 tury = 48 sesji, 96 tur**.
- Scenariusze: `intent_change`, `clarification`, `recall`, `correction`, `contradiction`, `privacy`, `message`, `goodbye`. Kolejność startującego modelu naprzemienna; wspólny system prompt, fabryczny snapshot Skills, schemat `reply/tool`, kontekst `2048`, bez wykonawczych efektów. E2B standardowo GPU, Qwen3.5 CPU — **nie** należy interpretować różnicy latencji jako porównania architektur na jednakowym akceleratorze.
- Kod dodatkowy `main@29669674c694d722eb0fe47f482f0e06927f0848`: plik `no_backup/g3-private/e2b-qwen35-ab-<timestamp>.jsonl` z **wyłącznie syntetycznymi** parami tur, zapis po każdej zakończonej parze. Zadanie hosta ma wyeksportować go prywatnym `adb exec-out run-as` do `~/Library/Application Support/ai-calls/qa/g3-private` (tryb 0600). Transkrypty/treści i pliki prywatne **nigdy do GitHub, task result ani logcat**. Kod prywatnego journala **nie ma jeszcze osobnego potwierdzenia build/QA**; kompilacja jest pierwszą fazą oczekującego zadania.
- Logcat planowo emituje tylko `AiCallModelG3AB`: `model,scene,repeat,cold_ms,warm_ms,backend,engine_ms,native_ok,json_ok,tools_ok,hint_applicable,content_hint,output_chars,context,summary,PSS,available RAM`. Heurystyka treści (np. trafienie w kod) **nie jest automatyczną oceną sensu**. Prywatne reporty pozwolą na późniejszy review `TOPIC, MEMORY, CORRECTIONS, FACTUALITY, NATURALNESS, QUESTIONS, PROMISES, ENDING`.
- G3 Mac QA `aicalls-g3-e2b-qwen35-ab-mac-qa-20261109-v1` na `bf31c48`: DONE/0 — AndroidTest build, pełny `verify_host.sh` i anti-friction PASS. Nowszy `2966967` prywatny journal **został skompilowany i uruchomiony** w G3 S22 task (build/AndroidTest PASS). Pełny `verify_host.sh` nie był ponawiany wyłącznie po dopisaniu journala.

## 4. Zakończony G3 — techniczne metryki i przyczyna czerwonego statusu

**Dokładny task:** `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1` na `MichalMatu/local-agent:agent-control`, host `macbook-michal/cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; publikacja tasku `503f47a76c555e4cb9ce0865e3e5b9a2d5d3a471` z 08:58:23 UTC. Pin źródła `29669674c694d722eb0fe47f482f0e06927f0848`.

- Start `2026-11-09T08:58:29.465487Z`, koniec `09:34:08.718481Z`. Etap 1 Git pin/clean `exit=0`; etap 2 `assembleDebug`/`assembleDebugAndroidTest` `exit=0`; etap 3 natywny S22, `exit=0`, 2 120,349 s; etap 4 końcowe `git status --porcelain` **nie został uruchomiony**, `exit=125`, `task_budget_exhausted` (wymagane 6460 s przy pozostałych 5460,812 s). Formalny result ma `status=failed` **wyłącznie z powodu pominiętego ostatniego kroku QA**, nie porażki inferencji ani importu.
- S22 fizyczny `SM-S906B`, połączenia IDLE przed/po, brak kampanii, instalacja debug APK i test APK bez wipe. `am instrument` **1/1 PASS**, `G3_TECHNICAL_MATRIX_FINISHED_96_TURNS`, **48/48 sesji, 96/96 tur**, 48 metryk oraz 2 poprawne podsumowania.
- Prywatny plik `no_backup/g3-private/e2b-qwen35-ab-<timestamp>.jsonl` został wyeksportowany na Mac (katalog `~/Library/Application Support/ai-calls/qa/g3-private`, chmod `0600`), **48/48 par zapisanych**. Nie publikować tekstu, nie kopiować raportu do GitHub, tool result ani chat.
- Dostarczony wcześniej log Local Agent kończy się 09:27:24 UTC, **w środku poprawnie biegnącego testu G3**. Brak nazwy G3 w tym logu i widoczne timeouty Git/lease `ExecutionLeaseBusy` **nie były dowodem zablokowania G3**; dokładny receipt rozstrzyga.

| 24 sesje / 48 tur per model; wspólne prompt, Skills, JSON, 2048 | Gemma E2B GPU | Qwen3.5 2B CPU |
| --- | ---: | ---: |
| `native_ok` / `json_ok` / `tools_ok` | 24/24 każde | 24/24 każde |
| Heurystyki treści `content_hints_ok/total` | 15/15 | 12/15 |
| Mediana cold | 24 200 ms | 49 191 ms |
| p95 cold | 28 892 ms | 54 498 ms |
| Mediana warm | 4 725 ms | 7 340 ms |
| p95 warm | 7 289 ms | 11 213 ms |
| PSS MiB podczas modelowego summary (nie peak) | 3380 | 3087 |
| Wolna RAM MiB podczas summary | 2772 | 2851 |
| `low_memory` | false | false |

**Ograniczenia:** porównanie używa różnych akceleratorów GPU vs CPU, więc surowe czasy nie są benchmarkiem tego samego backendu. `content_hints` to tylko leksykalne sygnały kodów/dni, nie pełna semantyka. Nie przeprowadzono ludzkiej oceny naturalności i prawdziwości. Per-scenario agregator hosta pokazuje `model:unknown`, ponieważ nie usuwa prefiksu logcat przed parsowaniem; wiersze `summary=GEMMA_4_E2B` i `summary=QWEN_3_5_2B` mają prawidłowe oznaczenia. Weryfikacja szczegółowa powinna korzystać z prywatnego JSONL na Macu (tylko liczebności w odpowiedzi). Nie ustawiać `PROMOTE_QWEN` na podstawie tych metryk. **E2B nadal domyślny; formalna decyzja G3 OPEN.**

## 5. Pierwszy krok następnego czatu — krótki postflight, nie ponowna macierz

1. Pobierz aktualny `main`, ten checkpoint, master plan i dokładny completed task-result. Zachowaj **G3 S22 technical PASS**, ale nie maskuj formalnego `failed/task_budget_exhausted`.
2. Zleć co najwyżej **krótki, odczytowy** Mac postflight: zweryfikuj czysty checkout i najnowszy HEAD/remote (bez nadpisywania prac konkurencyjnych), istnienie prywatnego pliku i 48 unikalnych par, zlicz `scene×model` i `json/tool/hint` bez wynoszenia tekstu. Nie wymaga ADB ani ponowienia 96 tur. Dla krótkiego tasku ustaw budżety tak, by **każda komenda miała krótszy timeout niż pozostały budżet**.
3. Dopiero później oceń semantycznie prywatne syntetyczne odpowiedzi: pamięć, korekty, fakty, naturalność, koniec, obietnice i prywatność. Ocenę `UNASSESSED` zmieniaj tylko na podstawie rzeczywistych dowodów, nie heurystyk.
4. Na bazie wyników odnotuj jedną decyzję `KEEP_E2B` / `PROMOTE_QWEN` / `INSUFFICIENT_EVIDENCE`. Do tego czasu bez zmiany domyślnego E2B. Po formalnym G3 dopiero G4 Skills per turn, G5 dialog, G6 autoryzowane realne Call, G7 UX.
5. Nie uruchamiaj ponownie G0/G1/G2/G3, nie pobieraj ponownie modelu ani nie ruszaj innych projektów. Żadnych prywatnych tekstów w GitHub, żadnych call bez aplikacyjnego upoważnienia.

## 6. Granice bezpieczeństwa i QA

- Tryb: **GitHub Connector + Mac Local Agent + izolowany ChatGPT Sandbox**, nie Codex, Work, GitHub Actions (również nie odczytywać statusu), nowe branch, force-push i transmisja prywatnego tekstu.
- S22: przed ADB weryfikuj `SM-S906B`, `IDLE`, brak własnej kampanii i brak konkurencyjnego tasku. Nie wykonuj rzeczywistych Call bez odrębnego, dokładnego upoważnienia w aplikacji. Własność Call, STOP, dane, IdentityVault i zamrożony Samsung RX/TX pozostają nienaruszone.
- **To aktualizacja checkpointu po otrzymaniu raportu:** wcześniej wykonana inferencja i import danych są już potwierdzone; publikacja dokumentacji nie uruchomiła kolejnych tur. Ostatni clean Git i human semantic review pozostają OPEN.
