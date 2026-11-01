# AI Calls — kampania poprawkowa, 2026-11-01

**Zakres:** sześć ustaleń AUDIT-01...06 z `docs/REAUDIT_2026-11-01.md`. Bazowy HEAD: `17188ab26a363262d63b8e674e275023e286d700`. Zmiany produktowe na `main`; przed dalszą pracą zawsze sprawdzić najnowszy HEAD.

## Rezultaty i pozostałe bramki

| Ustalenie | Poprawka w kodzie | Weryfikacja |
|---|---|---|
| AUDIT-01, fałszywe powodzenie CLIR | `ClirCarrierEvidence`: rozpoznanie wyraźnego komunikatu CLIR; negacje, pytania, brak odniesienia do CLIR odrzucane. Usunięto prosty `contains` i potwierdzenie sukcesu z samego tokenu nadzorcy | 14 przypadków samodzielnego testu Kotlin/JVM: PASS; testy JUnit zapisane, ale Gradle nieuruchomiony |
| AUDIT-02, rozłączanie obcego połączenia | Usunięto ogólne `TelecomManager.endCall()`; `CampaignCallOwnershipPolicy` wymaga identyfikatora konkretnego połączenia oraz selektywnego zakończenia | **OTWARTA BRAMKA:** obecnie wykonawca blokuje wybieranie numeru; brak rzeczywistego kontrolera własnego połączenia |
| AUDIT-03, cofnięta zgoda | Kontrola `isAuthorizedInFlight` w trakcie dialogu, przed ujawnieniem PHONE, wydaniem mowy/PCM i zatwierdzeniem efektu; watchdog 500 ms | Kod połączony; pełne testy Android wymagane |
| AUDIT-04, wyścigi zgód | Wspólna blokada procesu dla magazynu; jednoczesna autoryzacja i rezerwacja próby; testy współbieżnych rezerwacji/cofnięcia | Testy Android dodane, nieuruchomione |
| AUDIT-05, przedwczesne zakończenie sukcesem | Raport operatora jest stanem przejściowym `clir_carrier_reported_success`; brak `workflow.complete(SUCCESS)` bez niezależnego potwierdzenia | Weryfikacja `*#31#` nadal niewykonana i niezintegrowana |
| AUDIT-06, nieokreślona karta SIM | Jedyny dostępny, domyślny `PhoneAccountHandle` jest warunkiem; do intencji dodano wybrane konto, a scope ponownie porównano | Test realnego jedno-/dwu-SIM nadal potrzebny; połączenia blokuje AUDIT-02 |

## Obowiązujące ograniczenia

- **Nie wolno zdejmować blokady wybierania numeru** do czasu wdrożenia zweryfikowanej kontroli własnego połączenia z selektywnym rozłączeniem. Samo `isInCall()`, `MODE_IN_CALL` i `PhoneAccountHandle` nie zapewniają własności.
- Nie wolno używać `TelecomManager.endCall()` do przerwania połączenia, którego tożsamości nie potwierdzono.
- Stwierdzenie operatora o włączeniu CLIR nie jest równoznaczne z niezależnym dowodem stanu sieci.
- Nie odczytywać treści PHONE do logów, repo, modeli lub relay.
- Zamrożone granice Samsung/media/Shizuku pozostają nietknięte bez przyczyny.
- Brak lokalnej sesji Local Agenta; zmiany wykonane przez GitHub Connector. Bez Codex, Work i GitHub Actions; brak połączeń komórkowych.

## Testy do wykonania przez Local Agent

1. Pobierz świeży `origin/main`, sprawdź clean checkout i bieżącą sesję Local Agenta. Nie korzystaj z nieaktualnego pliku `.agent/status/daemon.json` jako dowodu obecnej aktywności.
2. `./gradlew testDebugUnitTest`, zbuduj aplikację i uruchom bezpieczną instrumentację (w tym `AndroidCampaignAuthorizationStoreContractTest` z izolowanymi preferencjami testowego APK). Usuń usterki kompilacji/regresji.
3. Przetestuj odwołanie zgody w trakcie rozmowy oraz wyścigi z podpisaniem zmiany i emisją PCM bez realnego dzwonienia.
4. Zaimplementuj i fizycznie sprawdź kontroler konkretnego połączenia i selektywne rozłączanie, bez przejmowania/kończenia obcych połączeń. Dopiero wtedy dopuść wybieranie.
5. Sprawdź aktualną gotowość Shizuku, właściwe konto SIM, telefon `IDLE`, istniejący grant i jego dokładny zakres.
6. Dopiero z faktyczną, aktualną autoryzacją aplikacji wykonaj pojedynczy test `*100`; zachowaj zanonimizowane dowody i osobno sprawdź `*#31#`, a następnie bezpieczne `IDLE`. Nie uznawaj CLIR za włączony bez tych dowodów.

## Stan potwierdzony 2026-11-01

Przeszło 14 przypadków samodzielnego smoke-testu `kotlinc`/JVM detektora CLIR po poprawce polskiego `ł`. Testy Gradle, Android instrumentation i próby na S22 **nie zostały wykonane**. Ostatni historyczny stan S22 z 2026-10-08: `shizuku_binder_unavailable`, brak akceptacji CLIR enable. Dzisiejszy stan urządzenia jest nieznany.

**Wynik kampanii:** ochrona fail-closed w kodzie; pełna akceptacja urządzeniowa i pełna funkcjonalność autonomicznego połączenia — otwarte.
