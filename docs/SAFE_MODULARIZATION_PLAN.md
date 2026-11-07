# AI Calls — bezpieczna modularizacja

**Cel:** poprawić granice zależności bez zmiany działania połączeń. Liczby plików z checkpointów modularizacji 2026-11-06 są pomiarami historycznymi, nie celem bieżącej pracy. Nie optymalizować pod sztuczny limit plików.

## Zrealizowane

- **Etap 0 — DONE:** mapa odwołań, regresje timeoutu/end_call/takeover/STOP, zielona bramka hosta, bez zmian produkcyjnych. [Checkpoint](checkpoints/2026-11-06-mod-stage0-baseline.md).
- **Etap 1 — DONE:** pojedyncza klasa IncomingIdleTimeout przeniesiona do conversation-core, ciało algorytmu zgodne z poprzednią wersją. Czyste testy trafiły do core, integracyjne playback pozostały w app; pełny host i native test compile PASS. [Checkpoint](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md).

## Niezmienniki podczas dalszych etapów

- Nie zmieniać Samsung/Shizuku, PCM/PFD, audio pump, Handler/TTS, własności Call, SIM/grantu, STOP, przejęcia, autoryzacji skutków ani kolejności **zatwierdzony tekst → pełny TX → end_call**.
- Zachować 30 s aktywnego nasłuchu → ostrzeżenie → 15 s aktywnego nasłuchu → pożegnanie; odpowiedź głosem resetuje stan, bezmowny retry STT go nie resetuje. Model i synteza nie zużywają tego czasu.
- Jeden runtime, jeden mechanizm narzędzi, jeden tor polityk i jedna baza dowodów. Nie kopiować runnerów, magazynów ani wrapperów kompatybilnościowych bez potrzeby.
- Nie modyfikować wielu odpowiedzialności naraz. Jeden mały zakres i jeden commit na main. Nie zmieniać zachowania podczas refaktoryzacji struktur.

## Etap 2 — DONE / NO_CHANGE: typowane wyniki

Zidentyfikować użycia ConversationOutcome/ConversationOutcomeBoundary w pięciu plikach produkcyjnych app, ConversationRunResult w trzech i ConversationTermination w istniejącym core. Wydzielić najwyżej jeden kontrakt czystej logiki do conversation-core **tylko jeśli** zmniejsza sprzężenie. Zostawić Android runtime, recorder i callbacki platformowe w app. Zmienić wszystkie bezpośrednie importy oraz dopasować testy bez dodatkowych aliasów.

**Akceptacja:** zachowanie COMPLETED/FAILED/STOP/CALL_ENDED/HUMAN_TAKEOVER/POLICY_REVOKED/OWNERSHIP_CHANGED, brak spóźnionego TX i podwójnego finish; testy JVM i pełny scripts/verify_host.sh, audit_product_friction, kompilacja AndroidTest w razie zmiany importów. Jeżeli bilans zależności jest niekorzystny, zakończyć etap dokumentowanym NO_CHANGE zamiast sztucznego refaktoru.

**Wynik 2026-11-06:** NO_CHANGE po dokładnej mapie 5/5/3. Żaden z typów Outcome/Boundary/RunResult nie ma konsumenta core, a RunResult jest związany z raportem/evidence/chat token. ConversationTermination już jest w core. Celowane JVM, AndroidTest compile, pełny host i friction PASS (266 Python); bez zmian kodu i bez S22. [Checkpoint](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

## Etap 3 — AndroidConversationRuntime

815 linii, aktywny wspólny wykonawca INCOMING/OUTGOING. Wyjmować pojedyncze neutralne reguły stanu tury lub kodów zdarzeń; nie przenosić audio, Telecom, lifecycle ani obsługi jedynego aktywnego Call. Każdy krok osobno chroniony testem stale callback/generation/STOP/timeout/playback i pełną bramką.

**Krok 1 (2026-11-06) DONE/PASS:** neutralny `ConversationSessionRegistry` przeniesiony do JVM conversation-core, 4 testy z app do core; body bez zmian, tylko import w runtime. JVM, AndroidTest compile, pełny host (266 Python), friction PASS. Pozostałe ewentualne neutralne reguły oceniać osobno, bez wymuszania wydzieleń. [Checkpoint](checkpoints/2026-11-06-mod-stage3-session-registry-core.md).

## Etap 4 — inne duże pliki

Oddzielne przeglądy CallRealtimeSessionOrchestrator (758 linii), LocalTextCallGateDProductIntegration (718) i Gemma4LiteRtTextBackend (625). Wydzielać wyłącznie sprawdzone czyste odpowiedzialności, bez mieszania lokalnego modelu z realtime transportem. Nie przywracać dawnych runnerów.

## Etap 5 — balast

Potwierdzić brak produkcyjnych i testowych referencji, użyć przez manifest, reflection/JNI, scenariusze diagnostyczne i skrypty Mac. Dopiero wtedy usuwać jedną małą grupę klas/wrapperów na commit. Nie usuwać historycznych RED, checkpointów, nagrań ani używanych probe.

## Bramka każdego etapu

1. Świeży main HEAD = origin/main, worktree clean, brak konkurencyjnych claims.
2. Mapa zależności i testy ochronne; tylko jedna odpowiedzialność.
3. Celowany JVM, odpowiedni native compile i pełny host gate, audit_product_friction, git diff --check.
4. Weryfikacja grant/STOP/call-ownership, niezmiennych 30+15 s i end_call dopiero po TX. Test fizyczny tylko przy zmianie zachowania telefonii lub nowej przesłance.
5. Commit [skip ci], push, czysty HEAD, checkpoint PASS vs QA_PENDING. Brak ponawiania ukończonych testów.

[Bieżący handoff](HANDOFF_NEXT_CHAT.md) · [Pełne zamknięcie etapów 0–1](checkpoints/2026-11-06-modularization-handoff-final.md).

## Oddzielenie UX od logiki — nowa, niezależna sekwencja (po Sprint 6)

**Nie jest jeszcze wykonana — to osobny przyszły zakres.** Iteracja UX Sprint 6 jest **zamknięta**: [finalny checkpoint, host i S22 PASS](checkpoints/2026-11-07-ux-sprint6-final-handoff.md) na funkcjonalnym `1b359146`. Nie mylić gotowego UX z niewykonanym rozdzieleniem Activity i kontrolerów.

### Mapa faktycznego sprzężenia

| Android Activity | Obecna odpowiedzialność mieszana | Pożądana granica |
|---|---|---|
| `CampaignToolActivity` (~25 KB) | render statusu oraz historycznej diagnostyki, role, grantu, START/STOP | Najpierw **tylko** `CampaignExecutionStatusPresenter`: czysta funkcja record → model UI; grant/akcje bez zmian |
| `ConversationHistoryActivity` (~19 KB) | lista, IO, obsługa Back, delete single/bulk, dotyk dymku i MediaPlayer | `ConversationHistoryController` + typowany `HistoryScreenState` i wynik operacji; Android player oraz UI pozostają w app |
| `ConversationSkillLabActivity` (~21 KB) | UI, Thread, `TextCallAgentBackend`, cancel, timeout, score i scroll | `SkillLabRunner` + model zdarzeń, bez `View`; Activity wyłącznie subskrybuje stan/renderuje |
| `AudioEngineSettingsActivity` (~19 KB) | UI, profile, readiness, download i preview | Stopniowo używać istniejących providerów/store/probe, bez nowego duplikatu |
| `LanguageModelActivity` (~12 KB) | UI statusu transferu i wybór modelu | read-only prezentacja stanu transferu, nie przenosić `LanguageModelTransferService` |

**Istniejące prawidłowe granice:** `GeneralSettingsController`, `IncomingAssistantSettingsController`, `ConversationToolsSettingsController`, `ConversationRunConfiguration`, `AudioEngineProfileStore`, `RecordedConversationHistory`, `ArchivedConversationPlayer`. Reużywaj je, nie dokładaj adapterów o tej samej roli.

### UX-01 — wyłącznie prezentacja Kampanii

- Wejście: zapis historycznego statusu, locale; wyjście: typowana etykieta/ton/znaczenie `requested vs confirmed vs unknown`. **Brak** `Context`, `Service`, I/O, mutacji grantu, odczytu SIM, `TelecomManager` w parserze/presenterze.
- UI renderuje tylko zwrócony stan. Surowy status pozostaje w zwijanym panelu diagnostycznym; nie wolno sugerować sukcesu po `targeted_disconnect_requested`.
- Testy RED→GREEN: mapowanie unknown, żądanie bez potwierdzenia, stan bez zapisu, PL/EN i surowa diagnostyka. Mały source sprint z pełną odpowiednią bramką.
- Nie refaktoryzować równocześnie `CampaignAgentControl`, `CampaignAuthorizationStore`, executora, STOP ani callbacków.

### UX-02 — historia i bezpieczne kasowanie

- Controller posiada asynchroniczną kolejkę IO, read-only list/load, `deleteCompleted`, `deleteAllCompleted`; model wyników `Loading/Ready/Error/Deleted` i `deleted/skipped/failed`. View tylko renderuje i wyświetla potwierdzenie.
- **Ta sama** `RecordedConversationHistory` jako jedyne źródło plików. Dwa niezależne miejsca do usuwania byłyby regresją. Nigdy nie kasuj realnych danych w Android testach; testuj na unikalnych syntetycznych folderach i anuluj dialog.
- Zachować ochronę COMPLETE + END, symlink, path traversal, listy aktywnych sesji i stan telefonu przy audio. MediaPlayer jest Android-only, bez prób przenoszenia go do JVM core.
- Przetestować Back/destroy, spóźnioną odpowiedź IO, dwu-klik Delete, szybko następujące play/stop i nieobecny WAV; dopiero potem manualny UX.

### UX-03 — Laboratorium i stan modelu

- Worker i lifecycle dostają jeden właścicielski `SkillLabRunner`; przenosi cancel, timeout, callback i stop. `SkillLabUiState` jest immutable, UI nie tworzy modelu ani nie oblicza score. `SkillLabScoring` pozostaje czystą logiką.
- Testy kontraktu: ostatnia tura widoczna, poprawny STOP, brak spóźnionych callbacków po anulowaniu/nowym run, brak Telephony effects, timeout, score END_CALL **nie** jako ocena semantyczna.
- Mac host najpierw, fizyczny test modelu tylko po dostępności exact S22. Nie zmieniać toru TX, PCM/PFD ani istniejących callbacków rozmowy telefonicznej.

### Reguła kadencji

Najpierw `UX-01`, osobne checkpoint/QA; potem `UX-02`, `UX-03` i pozostałe ustawienia, **nigdy** jeden duży refaktor. Jeśli nowy podział zwiększa zależności lub wymaga zmiany telefonii, zakończ `NO_CHANGE` zamiast symulować postęp. Szczegóły kolejności: [ROADMAP](ROADMAP.md).
