# AI Calls — audyt logiczny architektury i sztucznych blokad (2026-11-01)

Status: **AUDIT / bez modyfikacji logiki produkcyjnej**. Źródło: `main` HEAD `d3149cb31ce42d91afbedfa468a1f001bb32bf6c`. Przeczytano: `AGENTS.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/ARCHITECTURE.md`, `docs/GENERIC_PHONE_TASK_AUTHORITY.md`, `docs/CAMPAIGN_AUTHORIZATION_GRANT.md` i krytyczne klasy kampanii, dialogu, zatwierdzania i no-call. Dodatkowe testy ręczne: lokalny kotlinc/JVM, bez Androida i urządzenia.

## Werdykt

**Model koncepcyjny jest rozsądny**, ale połączenie warstw nie jest kompletne. **Nie** wszystkie sztuczne blokady usunięto, a wytyczne są wewnętrznie niespójne: wcześniejsze wymaganie kontynuowania fizycznej akceptacji nadpisano bezwarunkową blokadą wprowadzoną na podstawie wcześniejszego audytu. Właściwe oddzielenie: wymaganie **nie kończyć cudzego połączenia** jest prawdziwe, ale dwie flagi `false` na stałe nie są implementacją sprawdzania tego wymagania.

## Ustalenia priorytetowe

### L-01 / P0 — martwa ścieżka telefonu

[`AppOwnedClirCampaignExecutor.kt:38-50`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedClirCampaignExecutor.kt#L38-L50) podaje `CampaignCallOwnershipProof(exactCallIdentityBound=false, targetedTerminationAvailable=false)`. `CampaignCallOwnershipPolicy` wykonuje `false && false`: każde wykonanie kończy się przed sprawdzeniem uprawnień i bez autoryzowanej próby. Reszta produktu jest statycznie nieosiągalna z tej ścieżki. Jest to **sztuczny blocker w kodzie**, mimo że przyczyna, którą miał zabezpieczyć, jest prawdziwa. Nie usuwać bez ścieżki bezpiecznego zakończenia konkretnego połączenia.

**Naprawa zgodna z celem:** wyodrębnić `CallLifecycleController` / `CallOwnershipEvidence`, pozyskiwać prawdziwy identyfikator i możliwość selektywnego hangup z faktycznej warstwy telefonicznej, dopuszczać połączenie tylko w razie spełnienia realnego stanu; kod nie powinien podstawiać stałych flag. Warianty testowe/no-call powinny być niezależnie dostępne. Jeśli konkretnego uchwytu nie da się uzyskać na Samsung stock, nie deklarować autonomicznej akceptacji, ale nie udawać że kod sprawdza własność.

### L-02 / P1 — nadmierna blokada SIM

[`AppOwnedClirCampaignExecutor.kt:90-100`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedClirCampaignExecutor.kt#L90-L100) wymaga `accounts.size == 1`. Jest to **silniejsze niż specyfikacja**: prawidłowo wybrane domyślne konto dla `tel` może istnieć przy dwóch aktywnych kontach. Wystarczy weryfikacja jednoznacznie wskazanego konta i zgodności z rzeczywistym subscription/zakresem grantu, nie liczba wszystkich dostępnych kont. Samo `PhoneAccountHandle` też nie jest dowodem tożsamości faktycznego aktywnego połączenia.

### L-03 / P1 — opisana ścieżka deterministyczna nie jest użyta w kampanii

Wytyczne deklarują `PhraseMatrix/deterministic -> bounded Gemma -> fallback`. Jednak [`ChatRelayLiveCallProbe.kt:148-162`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt#L148-L162) tworzy bezpośrednio `GateCHybridDialogueBackendFactory`, która używa klasyfikatora modelowego. Ta ścieżka nie podłącza `PhraseMatrix` jako pierwszego etapu. To realny rozjazd między szkieletem koncepcyjnym a wiringiem produktu.

### L-04 / P1 — pozorne sprawdzanie generacji i trasy

[`ChatRelayLiveCallProbe.kt:130-143`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt#L130-L143): `AuthorizedPhoneFactScope.generation = 0L` jest stałe i nie odpowiada generacji aktywnej sesji medialnej. Snapshot i request dostają tę samą wartość, więc kontrola równości generacji nie wykryje nieaktualnej sesji. [`routeVerified` w liniach 288-298 i 512-517](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt#L288-L298) ustawiany jest na podstawie prostego słowa `CLIR` w transkrypcji, a jednocześnie umożliwia ujawnienie `PHONE` i zatwierdzenie efektu. To za słabe jak na „route verified”; zdarzenia muszą być przypisane do rzeczywistej sesji, połączenia i stanu dialogu.

### L-05 / P1 — parser pozytywnej wypowiedzi wciąż ma fałszywe wyniki

[`ClirCarrierEvidence.kt:24-35`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/ClirCarrierEvidence.kt#L24-L35) usuwa interpunkcję, potem sprawdza słowa. Lokalnie uruchomiono bieżącą, równoważną implementację Kotlin/JVM: **`Usługa CLIR jest aktywna?` -> true**, **`CLIR jest aktywny, prawda?` -> true**. Pytanie nie jest faktycznym oświadczeniem sukcesu. 14 poprzednich testów nie obejmowało tych przypadków. Obecny wynik jest tylko provisional, więc nie powinien automatycznie zamknąć workflow, ale nadal generuje błędną informację do dalszej weryfikacji.

### L-06 / P1 — wynik operatora i workflow nie są domknięte

[`ChatRelayLiveCallProbe.kt:497-510`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt#L497-L510) rejestruje provisional carrier success, ale brak następnego wykonawcy niezależnej weryfikacji CLIR i terminalnego sukcesu. `completionTracker` jest tworzony, lecz nie jest w tej ścieżce użyty do dopuszczonego `complete`; `workflow.complete` nie jest wykonywane. Celowe nieoznaczanie skutku jako potwierdzonego jest poprawne, ale brakuje docelowej implementacji do osiągnięcia sukcesu.

### L-07 / P1 — brak bezpiecznego domknięcia połączenia

[`AppOwnedClirCampaignExecutor.kt:250-254`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedClirCampaignExecutor.kt#L250-L254) zastąpił rzeczywistą operację hangup raportem `owned_hangup_unavailable`. Przy ewentualnym zdjęciu L-01 połączenie może pozostać aktywne po zakończeniu silnika, co narusza wymaganie cleanup-to-IDLE. Nie wolno „naprawić” tego przywracając ogólne `TelecomManager.endCall()`, bo może odrzucić bieżące obce wywołanie.

### L-08 / P2 — inne sztuczne zależności i nadmiary

- [`CampaignToolActivity.kt:124-136, 216-222`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignToolActivity.kt#L124-L136) żąda `RECORD_AUDIO` i `CALL_PHONE` nawet przy samym **utworzeniu zgody**; do nadania scope wymagany jest stan konta, nie prawa do rozmowy już teraz.
- [`ChatRelayLiveCallProbe.kt:433-451`](https://github.com/MichalMatu/ai-calls/blob/d3149cb31ce42d91afbedfa468a1f001bb32bf6c/app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt#L433-L451) uznaje przychodzący komunikat operatora dopiero po wykonaniu kolejnej odpowiedzi TTS i zakończeniu jej odtwarzania. Uzyskanie dowodu nie powinno zależeć od tego, czy aplikacja zdołała wypowiedzieć własne „dziękuję”.
- W `AGENTS.md` i dokumentach po kampanii dodano „safety override” wpisujący L-01 jako wymóg. Dokumentacja **nie może sama uzasadniać sztucznego stałego `false`**. Należy zachować właściwy wymóg bezpieczeństwa i opisać realną bramkę techniczną.

## Właściwe i niewłaściwe blokady

**Uzasadnione:** autoryzacja dokładnego task/target/effect/SIM; obecność właściwych uprawnień przed faktycznym użyciem; stan IDLE przed dial; sprawdzenie bieżącej aktywnej sesji; zabezpieczenie ujawnienia PHONE; zakaz kończenia obcych połączeń; niezależny dowód CLIR; prawdziwy binder Shizuku, gdy technicznie potrzebny.

**Sztuczne lub pozorne:** stałe `false` jako „proof” własności; bezwarunkowe `accounts.size==1`; użycie `generation=0` w polu mającym zabezpieczać zmianę sesji; wczesne żądanie uprawnień do mikrofonu do samej autoryzacji; samoreferencyjny dokument „safety override”.

## Docelowy minimalny przepływ

`AuthorizedGrant -> Resolve exact SIM/target -> readiness(IDLE, Shizuku, real call controller) -> owned dial -> bounded deterministic PhraseMatrix -> Gemma -> application output authority -> typed permit for real effect -> carrier provisional evidence -> independent network verification -> verified completion -> targeted owned-call cleanup/IDLE.`

Rozdzielić `dev probe` od `product controller`. Nie tworzyć nowej bramki CLIR-only. Model nie ma prawa sam tworzyć pozwolenia lub obwieszczać sukcesu.

## Granice tego audytu

Przeprowadzono przegląd kodu i test adversarialny lokalnej logiki `ClirCarrierEvidence` (bez Androida). **Nie** uruchomiono Gradle, instrumentacji, S22, rzeczywistej telefonii ani Shizuku. Nie modyfikowano produkcyjnej logiki w ramach tego audytu. Kontrolne uruchomienie obecnego Local Agenta nie było dostępne. Wynik nie stanowi fizycznej akceptacji.
