# AI Calls — bezpieczna modularizacja

**Cel:** poprawić granice zależności, nie zmieniając działania połączeń. 197 plików Kotlin w app było liczbą **przed etapem 1**; obecnie jest 196. Są rozdzielone na pakiety, ale wiele niezależnych reguł nadal kompiluje się razem z Androidem. Nie optymalizować pod sztuczny limit liczby plików.

## Zrealizowane

- **Etap 0 — DONE:** mapa odwołań, regresje timeoutu/end_call/takeover/STOP, zielona bramka hosta, bez zmian produkcyjnych. [Checkpoint](checkpoints/2026-11-06-mod-stage0-baseline.md).
- **Etap 1 — DONE:** pojedyncza klasa IncomingIdleTimeout przeniesiona do conversation-core, ciało algorytmu zgodne z poprzednią wersją. Czyste testy trafiły do core, integracyjne playback pozostały w app; pełny host i native test compile PASS. [Checkpoint](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md).

## Niezmienniki podczas dalszych etapów

- Nie zmieniać Samsung/Shizuku, PCM/PFD, audio pump, Handler/TTS, własności Call, SIM/grantu, STOP, przejęcia, autoryzacji skutków ani kolejności **zatwierdzony tekst → pełny TX → end_call**.
- Zachować 30 s aktywnego nasłuchu → ostrzeżenie → 15 s aktywnego nasłuchu → pożegnanie; odpowiedź głosem resetuje stan, bezmowny retry STT go nie resetuje. Model i synteza nie zużywają tego czasu.
- Jeden runtime, jeden mechanizm narzędzi, jeden tor polityk i jedna baza dowodów. Nie kopiować runnerów, magazynów ani wrapperów kompatybilnościowych bez potrzeby.
- Nie modyfikować wielu odpowiedzialności naraz. Jeden mały zakres i jeden commit na main. Nie zmieniać zachowania podczas refaktoryzacji struktur.

## Etap 2 — następny, wyłącznie typowane wyniki

Zidentyfikować użycia ConversationOutcome/ConversationOutcomeBoundary w pięciu plikach produkcyjnych app, ConversationRunResult w trzech i ConversationTermination w istniejącym core. Wydzielić najwyżej jeden kontrakt czystej logiki do conversation-core **tylko jeśli** zmniejsza sprzężenie. Zostawić Android runtime, recorder i callbacki platformowe w app. Zmienić wszystkie bezpośrednie importy oraz dopasować testy bez dodatkowych aliasów.

**Akceptacja:** zachowanie COMPLETED/FAILED/STOP/CALL_ENDED/HUMAN_TAKEOVER/POLICY_REVOKED/OWNERSHIP_CHANGED, brak spóźnionego TX i podwójnego finish; testy JVM i pełny scripts/verify_host.sh, audit_product_friction, kompilacja AndroidTest w razie zmiany importów. Jeżeli bilans zależności jest niekorzystny, zakończyć etap dokumentowanym NO_CHANGE zamiast sztucznego refaktoru.

## Etap 3 — AndroidConversationRuntime

815 linii, aktywny wspólny wykonawca INCOMING/OUTGOING. Wyjmować pojedyncze neutralne reguły stanu tury lub kodów zdarzeń; nie przenosić audio, Telecom, lifecycle ani obsługi jedynego aktywnego Call. Każdy krok osobno chroniony testem stale callback/generation/STOP/timeout/playback i pełną bramką.

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
