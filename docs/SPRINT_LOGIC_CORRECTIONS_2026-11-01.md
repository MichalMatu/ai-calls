# AI Calls — duży sprint logicznych poprawek, 2026-11-01

**Punkt wejścia:** `main` `ce97d40fcbcefd87b2076a045e5c578545ae89b3`. Stan po sprincie: pobrać nowy HEAD. Praca przez GitHub Connector, bez dostępnego świeżego bindingu Local Agenta. Bez realnego połączenia, ADB, Shizuku i GitHub Actions.

## Co rzeczywiście zmieniono

1. **CLIR carrier evidence:** polskie pytania zakończone `?` i `？`, a także słowa `prawda`/`zgadza się` nie mogą dać prowizorycznego sukcesu. Negacje nadal nie są akceptowane. Dodatkowe testy regresyjne.
2. **Deterministyczny dialog przed Gemma:** `ClirDeterministicDialogueBackend` uruchamia istniejący `PhraseMatrix` dla ograniczonego zbioru neutralnych odpowiedzi (powitanie, powtórzenie celu, pytanie o powód rozmowy), dla innych wejść deleguje do istniejącego `GateCHybridDialogueBackend`. Po tej warstwie nadal działa `CallTextAgentOutputApprovalPolicy`. Dopasowanie nie może wystawić permitu ani ujawnić PHONE. **Nie jest to pełna integracja `CallPlanTurnCoordinator` dla wszystkich interakcji.**
3. **Trasa CLIR:** `ClirRouteEvidence` wymaga jawnego odniesienia do usługi lub zastrzegania numeru, zamiast podciągu `clir` w dowolnej wypowiedzi. Wykryty testem defekt odmiany `usługę`/`ustawieniami` został naprawiony.
4. **Sesja medialna:** `activeMediaGeneration` pochodzi z bieżącego aktywnego `CallMediaSessionSnapshot`, jest resetowana przy zamknięciu/awarii i sprawdzana przed ujawnieniem PHONE, permit oraz TX. Usunięto `generation=0L`. `LocalSpeechTextPipeline` ponownie sprawdza aktualność generacji po callbacku transkrypcji.
5. **Fakty operatora:** komunikat o sukcesie po zużyciu permitu może domknąć etap odbioru niezależnie od kolejnego TTS; wynik nadal **tylko provisional**, bez niezależnej kontroli sieci i bez `workflow.complete(SUCCESS)`.
6. **SIM:** `CampaignPhoneAccountResolver` dopuszcza dwa zainstalowane konta, jeśli istnieje jednoznaczne wybrane domyślne konto wychodzące `tel` powiązane z aktualnym domyślnym voice subscription. Zniknęło `accounts.size == 1`. Nadal nie wolno zakładać, że wybrany SIM identyfikuje aktywne połączenie.
7. **Uprawnienia:** tworzenie grantu może żądać tylko `READ_PHONE_STATE`; mikrofon/dzwonienie wymagane dopiero dla wykonania. Przy braku kontrolera ekran pokazuje brak capability **zanim** zapyta o uprawnienia.
8. **Własność połączenia:** usunięto martwą stałą `CampaignCallOwnershipProof(false,false)` z wykonawcy. `CampaignCallControllerRegistry` przyjmuje faktyczny `CampaignOwnedCallController`, który jest odpowiedzialny za własny dial, identyfikację bieżącego połączenia i selektywne `disconnectExactCall()`. Wykonawca weryfikuje numer i `PhoneAccountHandle` zwróconej sesji oraz używa wyłącznie jej rozłączania. **NIE MA jeszcze instalowanego adaptera Android/S22**, dlatego realny dial wciąż jest niedostępny. Nie zastępować tym portem adaptera zwracającego tylko `isInCall` lub `TelecomManager.endCall()`.

## Potwierdzenia

- **27/27** przypadków Kotlin/JVM dla kodu source-equivalent `ClirCarrierEvidence` i `ClirRouteEvidence` po poprawieniu odmian PL; wykrycie regresji przed poprawką również zarejestrowane.
- Nowe/wzmocnione testy w repo: `ClirCarrierEvidenceTest`, `ClirRouteEvidenceTest`, `ClirDeterministicDialogueBackendTest`, `CampaignPhoneAccountResolverTest`.
- Statycznie potwierdzono brak w bieżącym wykonawcy `CampaignCallOwnershipProof(false,false)`, `accounts.size==1`, `generation=0L` oraz ogólnego wywołania `TelecomManager.endCall()`.

## Niewykonane / nadal otwarte

- Brak pełnego Gradle/Android build, testów JVM całego repo i instrumentacji na S22 (tu nie ma Android SDK/Gradle). Nie deklarować HOST_GREEN.
- **P0** brak rzeczywistego Android call-bound controller: dopiero po jego wdrożeniu i testach rzeczywistych można uruchomić kampanię bez ryzyka rozłączenia innej rozmowy. Port jest realnym punktem integracji, nie gotowym adapterem.
- Brak fizycznego Shizuku binder/telephony RX/TX, dual-SIM i `IDLE` proof; stary `shizuku_binder_unavailable` historyczny.
- Brak niezależnej sieciowej kontroli CLIR `*#31#` oraz terminalnego workflow success.
- `PhraseMatrix` obecnie jedynie do neutralnych przewidywalnych odpowiedzi, kolejne scenariusze wymagają podłączenia zatwierdzonego `CallPlan` / testów realnego dialogu.

## Następny bezpieczny krok

Z prawidłowym bieżącym Local Agent binding: wykonać `./gradlew testDebugUnitTest`, Android build i bezpieczne testy instrumentacyjne; naprawić wynikające z nich błędy, wdrożyć call-specific kontroler zgodny z interface i zaakceptować go fizycznie. Dopiero potem realny call `*100` pod aktualnym grantem, niezależne `*#31#`, selektywne zakończenie i potwierdzone `IDLE`. Bez przejmowania domyślnej roli dialera lub cofania Android privacy controls dla obejścia ograniczenia.
