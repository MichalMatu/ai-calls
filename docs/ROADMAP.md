# AI Calls — roadmapa i kolejność prac (aktualizacja 2026-11-07)

**Bieżący stan:** Sprint 6 `SOURCE_GREEN`, **S22_QA_PENDING_ADB**. Baza funkcjonalna `main@0dface1`. Każdy nowy sprint rozpoczyna od świeżego HEAD; status potwierdza [Sprint 6](checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md). Nie myl zatwierdzonego Sprintu 5 z jeszcze nieodebranym Sprintem 6.

## P0 — domknąć fizyczny odbiór Sprintu 6

| Bramka | Stan | Warunek zamknięcia |
|---|---|---|
| Host source/QA (325 testów) | **DONE / PASS** | Nie powtarzać bez zmiany źródeł |
| Exact S22 ADB | **BLOCKED / EXTERNAL** | `adb devices -l` widzi `RFCT70L7E8J` jako `device` |
| Native UX + screenshots | **QA_PENDING** | 28/28 testów UX, 24/24 wizualne, ≥42 prywatne JPG, instalacja `-r`, dane zachowane, IDLE, zero live calls |
| Real Skills Lab + STOP | **QA_PENDING** | 2 tury lokalnego modelu, ostatnia tura rzeczywiście widoczna, STOP, 4 JPG więcej |
| Odbiór wizualny właściciela | **QA_PENDING** | Właściciel przesyła ZIP, naprawia się tylko potwierdzone defekty |

Bez ADB zatrzymaj się na bezpiecznym preflight, nie odpalaj prawdziwych połączeń ani kolejnych hipotetycznych mikroaudytów.

## P1 — odsprzęgnąć UX od efektów (osobne, małe zmiany)

1. **UX-01: prezentacja statusu Kampanii.** `CampaignExecutionStatusPresenter` mapuje record + locale na typowany, jednoznaczny opis „zażądano”/„potwierdzono”/„brak danych”; **read-only**, bez service, grantu, STOP ani zapisu. `CampaignToolActivity` tylko podaje snapshot i renderuje. Testy PL/EN, raw diagnostics bez zmian; szybkie JVM + host. Najmniejsze ryzyko, najbardziej mierzalny pierwszy krok.
2. **UX-02: Historia, lista i kasowanie.** `ConversationHistoryController` z modelem `HistoryScreenState`; operacje `list/load/deleteCompleted/deleteAllCompleted` pozostają pod jednym, serializowanym wykonawcą IO, niezależnie od Activity. Oddzielona autoryzacja kasowania, wyniki typowane, ochrona aktywnej sesji, odrzucanie przestarzałych callbacków przy Back/destroy. `ArchivedConversationPlayer` zostaje Android off-call. Testy symlink/traversal, cancel dialog i dymek → play/stop. Żadnej migracji plików recorder.
3. **UX-03: Skills Lab runner i stan.** `SkillLabRunner` zarządza generacją i anulowaniem, ma jeden strumień zdarzeń `Pending/TurnCompleted/Stopped/Failed/Done`; `SkillLabUiState` stan renderowania. Activity nie zarządza wątkiem/model callbackami. `END_CALL` w Lab pozostaje tylko obserwacją. Testy STOP, race, late callback, 120s timeout, off-call i osobno dwuturowa Gemma.
4. **UX-04: pozostałe ustawienia.** Stopniowo przenieść odczyt/zapis/probe z `AudioEngineSettingsActivity` i wybory Modeli do dedykowanych istniejących serwisów. Nie tworzyć drugiego magazynu danych ani kolejnego download service.
5. **UX-05: F-103 pełne PL/EN.** Przejść po aktualnych ekranach, usunąć widoczne hardcoded PL w Kotlinie, test parytetu kluczy i Android locale screenshots. Po rozdzieleniu model/widok jest to prostsze.

**Nie robić wszystkiego w jednym sprincie.** Uzasadnienie i architektura: [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) oraz [ARCHITECTURE](ARCHITECTURE.md).

## P2 — funkcjonalność produktu i niezależne bramki

- **F-080:** rzeczywisty *ciągły* recorder i pełny player sesji z osią czasu. Obecne dymki odtwarzają tylko istniejące WAV RX/TX z tur, nie nagranie całego Call.
- **F-091 i rozmowy:** osobna realna kontrola end_call / naturalnego zakończenia / przejęcia / powrotu AI / STOP / timeoutu, **wyłącznie** z ważną autoryzacją i kontrolą S22.
- **Jakość Gemmy:** oddzielna walidacja semantyki, poprawności pytań, kompletności wiadomości i naturalności; licznik `END_CALL 2/2` nie jest jakością odpowiedzi.
- **F-103:** kompletna lokalizacja i dostępność na fizycznym telefonie (w zakresie UI).
- Pozostałe nieskończone obszary: [jedyny rejestr BLOCKERS](../BLOCKERS.md), [plan produktu](PRODUCT_ASSEMBLY_PLAN.md); nie wznawiać zamkniętych historycznych testów bez regresji.

## Zamknięte — nie rozpoczynać od nowa

| Obszar | Wynik referencyjny |
|---|---|
| Modularizacja etapy 0–1 | PASS: zależności, IncomingIdleTimeout → JVM `conversation-core` |
| Etap 2 | NO_CHANGE / PASS: typowane wyniki mają uzasadnioną własność w app |
| Etap 3 krok 1 | PASS: `ConversationSessionRegistry` + testy w `conversation-core` |
| Sprint 5 | S22 27/27 native, 23/23 wizualne, 43 JPG, realna Gemma 2 tury + STOP PASS |
| Sprint 6 | Źródła PASS: 325 host + AndroidTest/JVM/APK/friction; S22 odłożony, nie PASS |

Historyczne szczegóły są zachowane w [checkpointach](checkpoints/), a nie rozwijane jako kolejne instrukcje w tej roadmapie.

## Bramka definicji ukończenia

Dla każdego nowego kroku: świeży `main`, clean worktree, zadanie Local Agenta bez kolizji, **jedna odpowiedzialność**, test regresyjny RED→GREEN, `scripts/verify_host.sh`, friction audit i odpowiedni AndroidTest compile. Fizyczny S22 tylko gdy zmiana dotyczy zachowania mobilnego i urządzenie jest dostępne. Wpis PASS i checkpoint dopiero z dokładnym receipt. Nie zmieniać zasad grant/STOP/Call ownership/PCM/TX w UX refaktorze.
