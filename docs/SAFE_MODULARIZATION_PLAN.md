# AI Calls — plan bezpiecznej modularizacji (2026-11-06)

## Cel i stan bazowy

Jedna działająca aplikacja Android, bez zmian zachowania INCOMING/OUTGOING, przy lepszej izolacji reguł biznesowych i możliwości testów JVM. W audycie na main 068f85ccb298291741021954ba102e5350b00ba0 projekt zawiera 6 modułów Gradle i 220 produkcyjnych plików Kotlin: 197 w module app, resztę w audio-bridge, realtime-client, conversation-core oraz conversation-tools; privileged-helper ma inne źródła. To nie jest jeden fizyczny folder: app ma istniejące pakiety. Problemem jest granica kompilacji i zależności, nie sama liczba plików.

Funkcjonalny baseline: dwie rzeczywiste rozmowy A8→S22 potwierdziły end_call REQUESTED/VALIDATED/DISPATCHED/CONFIRMED; osobny 30+15 s timeout przeszedł RED→GREEN/host/native i został potwierdzony przez właściciela w realnej rozmowie. Ostatni pełny timeline timeoutu nadal QA_PENDING. Nowe rozdzielenie modułów nie może zmienić tej semantyki.

## Twarde niezmienniki — stop przy złamaniu dowolnego

- **Nie zmieniać** Samsung/Shizuku, przekazywania PFD, PCM RX/TX, audio pump, privileged helper, Call ownership, SIM/grantu, STOP, takeover ani sekwencji TTS→pełny TX→end_call przy zwykłym refaktorze.
- Brak drugiego conversation runtime, powielenia runnera, magazynu grantów, logiki autoryzacji, narzędzi i pipeline STT/TTS.
- Nie zmieniać czasu 30 s i 15 s, wypowiadanych komunikatów, zachowania po wykryciu głosu, retry STT bez mowy ani zasad przerwania.
- Żadnych nowych modułów Gradle, frameworków DI, migracji storage, zmian API publicznego lub porządkowania repo przez masowe kasowanie w pierwszych etapach.
- Zawsze zachować dokładne powody zakończenia, jednorazowy dispatch narzędzia, odpowiednią diagnostykę, prywatne transkrypty/audio tylko na urządzeniu/Macu.
- Każda zmiana ma własny zakres, test i commit main [skip ci]. Jeden etap = jedna odpowiedzialność. Nie łączyć refaktoryzacji z nowymi funkcjami.

## Etap 0 — kontrakt zerowy i mapa zależności (bez zmiany produkcyjnego flow)

**Zmiana:** wskazać wejścia i zależności głównych adapterów; utrwalić istniejące testy z nazwami przypadków i wynikami. Nie powielać już wykonanych fizycznych rozmów. W razie brakującego testu dodać tylko deterministyczne kontrakty dla: end_call po playback, 30+15 s, mowa podczas grace, STT no-match, nowa generacja, STOP/takeover i cofnięcie autoryzacji. Odróżniać już istniejące pokrycie od luki. Dodać niewielki statyczny test granic importów jedynie tam, gdzie zapobiega realnej regresji.

**Gate:** odpowiednie testy JVM i aktualne scripts/verify_host.sh, git diff --check, audit_product_friction; zero zmiany APK/telefonu przy samych testach/dokumentacji. **Wyjście:** mapa odpowiedzialności, bazowe wyniki, lista realnych luk testowych.

## Etap 1 — pierwszy niski-risk move: IncomingIdleTimeout do conversation-core

**Zakres tylko jednej klasy:** przenieść niezależny od Androida licznik z app/conversation/IncomingIdleTimeout.kt do istniejącego conversation-core; zachować dokładny algorytm i nazwy akcji WARN/CLOSE. Przenieść do core testy czystej logiki; zostawić teksty komunikatów i adaptację do Handler/SystemClock/Audio w app. Dostosować minimalną liczbę importów; bez nowych adapterów ani generowania innego kodu.

**Gate:** porównanie semantyki starej/nowej klasy przez testy: granice 30 000/15 000 ms, monotoniczny zegar, reset mową, brak resetu retry STT, czas pomiędzy nasłuchami, wywołania wielokrotne i jedno zakończenie. Następnie core JVM + app JVM, pełny host/lint/APK; celowany native off-call S22, jeśli cross-module wiring wpływa na aplikację. Brak fizycznego dzwonienia bez nowej przesłanki. **Rollback:** pojedynczy revert commita.

## Etap 2 — spójne typowane wyniki i granice odpowiedzialności

**Zakres:** rozważyć przeniesienie tylko Android-niezależnych ConversationOutcome/ConversationOutcomeBoundary obok ConversationTermination do conversation-core, gdy mapa wywołań pokazuje korzyść. Nie ruszać samego scenariusza, modelu ani wykonawcy. W app pozostawić implementacje hooków, recorder, systemowe callbacki i authorization adapter. Jeśli ruch generuje nieproporcjonalnie dużo importów, odroczyć.

**Gate:** JVM dla COMPLETED/FAILED/STOP/CALL_ENDED/HUMAN_TAKEOVER/POLICY_REVOKED/OWNERSHIP_CHANGED; testy stale callback i podwójnego finish. Pełny host oraz native tylko dla zmienionej integracji. Jeden commit.

## Etap 3 — rozbiór AndroidConversationRuntime bez drugiego runtime

815-liniowy AndroidConversationRuntime jest aktywnym punktem integracji dla INCOMING i OUTGOING. Dzielić go według odpowiedzialności, nie mechanicznie na pliki po 200 linii. Najpierw wyjąć **czyste** obliczanie stanu tury i kodów zdarzeń, następnie — po mapie skutków ubocznych — pomocniczą obsługę diagnostyki/rozdział danych. Handler, downlink pump, aktywne Call, TTS/PCM, odtwarzanie i teardown zostają w dotychczasowym runtime.

**Gate po każdej mikrozmianie:** jeden punkt wejścia i jedno miejsce odpowiedzialne za STOP/cancel; symulowane opóźnione callbacki, turn generation, timeout/no-speech i zatwierdzanie po TX; pełny host gate. Native S22, gdy dotykamy callbacków/syntezy; kontrolowany fizyczny A8→S22 tylko gdy zmieniło się wiązanie runtime/Telecom lub po serii takich zmian. Nie kumulować kilku nieprzetestowanych migracji.

## Etap 4 — pozostałe duże pliki i laboratorium

CallRealtimeSessionOrchestrator (758 linii), LocalTextCallGateDProductIntegration (718) oraz Gemma4LiteRtTextBackend (625): osobny audyt zależności i własności zasobów, dopiero potem ewentualne rozdzielenie czystych translatorów/formatów/walidatorów. Nie mieszać realtime-client z lokalnym Gemma runtime; eksperymentalne ścieżki pozostają testowalne. Nie zmieniać licencji, modeli, ich inicjalizacji ani polityki wyboru na etapie czyszczenia.

**Gate:** testy dokładnego API/modelu i historycznych scenariuszy, full host; natywne tylko w razie zmiany integracji. Warunek wejścia: wcześniejsze etapy GREEN i czysty HEAD.

## Etap 5 — bezpieczne usuwanie balastu

**Usuwalne kandydatury:** nieużywane aliasy, nieosiągalne stare wrappery po migracji, duplikaty dokumentacji aktywnej i naprawdę martwe klasy. Każda kandydatura wymaga dowodu: referencje Kotlin/Java, manifest, reflection/JNI, diagnostyczne entrypointy, testy, skrypty Mac/ADB, konfiguracje Gradle i dostępne fizyczne przepływy. `LocalPhoneLlmLiveCallProbe` i `GenericPhoneTaskAcceptanceProbe` mają produkcyjne wejścia w DiagnosticProbeActivity, więc nie są obecnie martwym kodem. Historycznych checkpointów, RED, prywatnych dowodów i działających providerów nie usuwać.

**Gate:** raport before/after referencji, build/lint/test i brak ubytku kontraktów. Jedna mała grupa usunięć na commit, bez przypadkowych zmian zachowania.

## Reużywalna bramka akceptacji każdego kroku

1. Świeży main, brak konkurencyjnych zadań/dirty checkout, wyznaczone dokładne pliki oraz oczekiwany brak zmiany zachowania.
2. Istniejący test ochronny albo RED dla nowej luki; zmiana jednego kontraktu lub przeniesienie jednej klasy.
3. Celowane JVM, a przy zmianie granicy biblioteki: cały host scripts/verify_host.sh, lint/APK i audit_product_friction.
4. Test off-call Android tylko dla zmiany Android-wiring. Fizyczna rozmowa tylko dla zmiany Telecom, rozłączenia, przejęcia, RX/TX, sekwencji odtwarzania lub zaobserwowanej regresji; nie powtarzać pełnej macierzy bez przesłanki.
5. Kontrola, czy `end_call` pozostał po pełnym TX, idle 30+15 s bez zmian, STOP/grant/takeover wygrywają, brak drugiego runnera; diagnostyka i osobne wyniki efektów zachowane.
6. Commit na main z [skip ci], publikacja i odczyt HEAD; checkpoint PASS vs QA_PENDING, bez deklarowania fizycznego sukcesu na podstawie hosta.

## Najbliższa decyzja i kolejność

Zacząć od **Etapu 0**, następnie **Etapu 1**. Są małe, odwracalne i zmierzają do zmniejszenia zależności app bez ingerencji w media. Etapy 2–5 uruchamiać pojedynczo po dowodach, bez sztywnego celu „mniej niż N plików” i bez cięcia kodu dla samej estetyki. Oddzielny sprint funkcjonalny: manual/takeover/AI resume/STOP i odpowiedź po timeout warning; oddzielna inwestycja F-080 w pełne archiwum/player. Nie łączyć ich z refaktoryzacją.

## Kryterium końcowe

Zależności biznesowe w istniejących JVM modułach rosną tam, gdzie faktycznie są Android-niezależne; app pozostaje adapterem platformowym i kompozycją. Ten sam produkt/aplikacja, te same końcowe zachowania i zabezpieczenia, bez dodatkowych ręcznych kroków. Liczba plików w app jest metryką pomocniczą, nie samodzielnym KPI.
