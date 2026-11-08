# LAB-DIALOG-01 — plan wykonawczy: prawdziwe dialogi testowe

**LAB-SEMANTIC-01 2026-11-08:** natywny realny `GEMMA_4_E2B` S22: 3 niezależne sesje × 7 tur, **pamięć/zmiana tematu/korekta/brak zgadywania 3/3 PASS**, Mac gate PASS, Call IDLE; nie jest to uniwersalna gwarancja factuality ani ręczny review ośmiu wymiarów. [Checkpoint](checkpoints/2026-11-08-lab-semantic-01-gemma-e2b-three-sessions.md).

**Aktualizacja 2026-11-08:** kod `main@992280b`: manual + editor + rubric + reguły adaptive + A/B, dodatkowo test prawdziwej adaptacji Gemmy i UI z osobnym scrollem, widocznym composerem i STOP. Pełny Mac gate na tym kodzie PASS. Na S22 3/3 funkcjonalnych wcześniejszych przypadków PASS; *nowy* native adaptive/layout **PASS na S22**: `aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1` DONE/0, 2/2 isolated UI/store, 1/1 real Gemma adaptive, dwie APK instalowane bez wipe, Call IDLE; dawne BLOCKED superseded. Semantyka, pełny klikalny UI, fizyczne A/B i audio F2 nadal OPEN. [Checkpoint](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

**Zatwierdzenie właściciela:** 2026-11-08. **Status:** A–D zaimplementowane i przetestowane źródłowo; E: fizyczny manualny tekst, STOP/restart i izolowany storage/UI PASS 3/3 na S22. Semantyka, fizyczny adaptive PASS 1/1, a semantyka, pełny klikalny UI i audio F2 pozostają OPEN. **Baza:** `main@d1bcf2a8d2ec3cf234cc1b355aef8a036ff604de`. Usunięte cztery stare sztywne historie pozostają usunięte. Ostatnia pełna bramka Mac `aicalls-lab-four-stories-removal-regression-20261108-v2`: DONE/0, 365/365 Python, Android+JVM build, audio audit, clean checkout. Nie przekładać dowodów starego Lab na nową funkcję.

## Zasada

Laboratorium ma **badać prawdziwe zachowanie lokalnego modelu**, a nie odtwarzać przypisane mu kwestie i przyznawać punkty tylko za `END_CALL`. Reagować na rzeczywiste odpowiedzi modelu, sprawdzać ciągłość kontekstu, fakty, poprawki, naturalność i decyzje. **Żadnych gotowych sztywnych historii** w aplikacji. Konfiguracja testu to oddzielny, lokalny dokument, nie produkcyjny scenariusz telefonu, nie grant i nie dane właściciela.

## Kolejność sprintów — jedna pionowa ścieżka na sprint

### A. Manualny dialog tekstowy (najpierw)

- W aktualnym ekranie Laboratorium wybór profilu/modelu i wersji Skills, inicjalizacja **jednej, ciągłej sesji modelu**; każde kolejne wpisane przez użytkownika zdanie trafia do tej samej sesji, z zachowaniem kontekstu, a nie do niezależnych promptów.
- Czytelne dwie strony czatu, odpowiedź, propozycja narzędzia/END_CALL jako obserwacja, znaczniki czasu, przewijanie, aktywny STOP i restart nowej sesji.
- Można wprowadzić nowy temat, wrócić do starego, sprostować fakt, poprosić o podsumowanie. AI nie otrzymuje gotowej listy kolejnych wypowiedzi.
- Wspólny `SkillLabRunner`/wspólne porty modelu: dodać obsługę ręcznych tur i snapshotów bez skopiowania runnera, osobnego silnika inferencji albo obchodzenia granic autoryzacji.
- **PASS:** na S22 co najmniej jedna faktyczna, wieloturowa rozmowa lokalna z zachowaniem kontekstu, jawny STOP podczas generacji, ponowny START bez starego callbacku, brak Telecom/efektów.

### B. Edytor własnych testów

- Utwórz / edytuj / skopiuj / usuń / ponownie otwórz; nazwa, cel, rola rozmówcy, stan początkowy, ważne fakty, reguły następnych wypowiedzi, oczekiwane zachowania i warunki końca. Kontrola wersji oraz lokalny trwały zapis.
- Najpierw ręczne uruchamianie wybranego przypadku z edytora. **Nie** zakodowywać ponownie 4 domyślnych historii; użytkownik tworzy dowolne przypadki samodzielnie.
- Testy są osobną strukturą od `ScenarioLibrary` i `IdentityVault`; nie aktywują automatycznie profilu połączeń, nie odczytują sekretów.
- **PASS:** test utworzony przez UI → zapis → zamknięcie aplikacji → odczyt → edycja/revision → odtworzenie w Laboratorium. Negatywny walidacyjny i bezpieczne usunięcie tylko testowej fixture.

### C. Rzetelny raport oceny dialogu

- Wynik *na turach* i w końcowym raporcie: zgodność z głównym tematem, pamięć wcześniej podanych szczegółów, naniesienie poprawek, brak sprzeczności/halucynacji, naturalność, adekwatne dopytanie, niesugerowane obietnice, właściwy koniec, latency, stop.
- Kryteria mogą mieć jawne oczekiwane fakty i reguły; automatyczna ocena tylko tam, gdzie daje się wykazać przesłankę. Pozostałe aspekty są **do ręcznej oceny**, z cytatem/referencją do konkretnej tury, bez sztucznego `12/12` za sam koniec.
- Odseparować od modelu badanego: jego własna odpowiedź nie jest wystarczającym dowodem poprawności ani samodzielną punktacją.
- **PASS:** raport wskazuje konkretną poprawną i konkretną błędną turę; odróżnia brak oceny od PASS i zachowuje wynik po odtworzeniu.

### D. Rozmówca adaptacyjny — reagujący na AI

- Symulator sterowany stanem dialogu i testem: reaguje na dopytanie, odmowę, zmianę tematu, pomyłkę, prośbę o powtórzenie i przedwczesne zakończenie; wybiera dalszą wypowiedź *po* przeanalizowaniu faktycznej odpowiedzi AI.
- Na początku **reguły/stany** z jawnym śladem decyzji. Ewentualny drugi model grający rozmówcę jest osobnym opcjonalnym rozszerzeniem, nigdy jedynym arbitrem własnego wyniku.
- Możliwość przejęcia następnej kwestii przez człowieka, cofnięcia do nowej sesji i powtórzenia tego samego przypadku z inną wersją Skills.
- **PASS:** zmiana zachowania badanego AI zmienia gałąź rozmówcy; zapisane przejścia i sprawdzalne oczekiwania; STOP/timeout/restart bez efektów zewnętrznych.

### E. Porównania i audio (dopiero po A–D)

- Te same własne przypadki na modelach i wersjach Skills: porównanie A/B z rozdzieleniem opóźnień inicjalizacji/generacji; liczba tur wynika z przebiegu, nie z limitu aplikacji.
- Sterowana ścieżka STT → model → TTS na S22; osobno test polskiego nagrania dla eksperymentalnego bezpośredniego wejścia PCM → Gemma (dotąd potwierdzona tylko **syntetyczna cisza**). Ocena WER/CER dla STT i jakości odpowiedzi osobno.
- Realne połączenia, narzędzia i ujawnianie danych pozostają poza Lab bez dokładnego użytkowego zlecenia i grantu.
- **PASS:** prywatny zapis dowodów na S22/Macu, porównywalny raport, brak nieautoryzowanych działań, STOP i postcheck telefonu.

## Praktyka wykonania i jakości

- Praca tylko `main`, GitHub Connector + Mac Local Agent, bez Codex/Work/GitHub Actions, bez dodatkowych gałęzi.
- Jedna funkcja i zestaw testów na commit: kontrakt JVM/Android → implementacja → targeted QA → Mac host gate → fizyczne S22 (tylko gdy wolny foreground i Call IDLE) → dokumentacja i checkpoint.
- Nie odtwarzać starego 4×12; neutralne testowe stuby worker/STOP są tylko fixture automatyczne.
- Brak arbitralnych limitów tur, timeoutów czy dodatkowych grantów, nie ruszać istniejących rzeczywistych zabezpieczeń Android/STOP. Zrzuty dozwolone, wrażliwe dane nie wychodzą do GitHub/logów.
- Przed A: w nowym sprincie sprawdzić aktualny HEAD/checkout i istniejące testy. Ostatnia bramka `d1bcf2a` była PASS, nie powtarzać jej przed zmianą bez nowej przesłanki.

## Wynik kodowania sprintu 2026-11-08

- **A SOURCE IMPLEMENTED:** `SkillLabRunner.startManual/submitManual`, jedna instancja natywnego backendu na wszystkie tury; immutable UI snapshots, STOP, reset nowej sesji i ochrona przed spóźnionym callbackiem. `ConversationSkillLabActivity` ma tekstowy composer.
- **B SOURCE IMPLEMENTED:** `LabCaseLibrary`, szyfrowana trwałość poprzez `AndroidConversationPreparationStore`, kreator w Lab, copy/edit/delete/revisions, brak fabrycznych historii. Wybór presetu Skills, modelu i roli.
- **C SOURCE IMPLEMENTED, SEMANTIC REVIEW REQUIRED:** `LabDialogueReport` zapisuje prywatne wypowiedzi, latencje, END_CALL jako obserwację, referencyjne fakty i 8 kategorii oceny. Domyślnie `UNASSESSED`; człowiek wskazuje dowód z numerem tury. A/B porównuje konfiguracje i jawnie sygnalizuje różny przypadek/rewizję. NIE MA automatycznego dowodu braku halucynacji.
- **D SOURCE IMPLEMENTED (rule-driven):** `LabAdaptiveInteractor`: pytanie, odmowa, propozycja końca, zawartość tekstu, brak wymaganego ciągu, fallback; jednorazowe gałęzie i ślad wyborów. To nie drugi LLM ani semantyczny tester.
- **E QA PASS (zakres tekstowy):** `SkillLabManualDialogueTest`, `LabCaseLibraryTest`, `LabAdaptiveInteractorTest`, `LabDialogueReportTest` w pełnym Mac gate `aicalls-labdialog01-final-source-gate-20261108-v3` DONE/0 (365/365 Python + JVM/lint/APK/AndroidTest/audio). Na S22: `aicalls-labdialog01-s22-device-acceptance-20261108-v1` DONE/0, 2/2 izolowany Android Keystore/UI i 1/1 realny manualny LiteRT 2 tury, STOP i restart; Call IDLE. Nie wykonywano Call.
- **Ograniczenia:** transfer ludzkiej kontroli nad trwającą automatyczną gałęzią nie ma osobnego przycisku; STOP i nowa ręczna sesja są dostępne. Audio STT/TTS i polskie PCM to osobna niezakończona bramka F2, a nie dowód etapu tekstowego.

[Checkpoint](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

**Zamknięcie etapu:** SOURCE/HOST/DEVICE TEXT PASS dla implementacji A–D; szczegółowe otwarte przypadki i ograniczenia zapisano w [końcowym checkpoincie](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md). Audio nie zostało odebrane w tym sprincie.
