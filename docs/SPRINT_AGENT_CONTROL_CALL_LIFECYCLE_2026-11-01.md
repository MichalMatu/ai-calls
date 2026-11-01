# AI Calls — Local Agent GitHub transport + call lifecycle hardening — 2026-11-01

## Bieżąca obserwacja

- Zweryfikowano `agent-control` jako kanał GH transportu: `.agent/tasks/*.json` jest kolejką, `.agent/runs/*.json` i `.agent/results/*.json` dają dowód wykonania, `.agent/status/daemon.json` odzwierciedla heartbeat. Poprzednie zlecenia na `agent-control` potwierdzają działanie takiego protokołu 7–16 października.
- Wysłano `aicalls-sprint-host-phone-preflight-20261101-v1`, `dedupe_revision=2`, na istniejące wiązanie repozytorium. Zawiera: świeży `main`, pełny Python unittest, `scripts/local_agent/host_verify.sh` (Gradle / lint / APK), `scripts/local_agent/device_readiness.sh` (tylko diagnostyka, bez połączenia).
- Wysłano także polecenie `.agent/daemon/control.json` typu `status` z nowym identyfikatorem. **W momencie sporządzenia tego checkpointu zadanie nie zostało podjęte**; nie pojawiły się `runs`/`results`, a ostatni `daemon.json.updated_at` wciąż wskazuje **2026-10-19**. Zapisy GitHub dowodzą jedynie poprawnego kolejkowania, nie aktywnego wykonania.
- W tej rozmowie GitHub Connector może tworzyć zlecenia, ale **nie może zdalnie uruchomić procesu na Macu, gdy daemon jest wyłączony**. Nie mylić tego z wymogiem ręcznego przepisywania komend przez operatora.

## Poprawki źródłowe w tym sprincie

1. **Bezpieczne Wireless ADB mDNS**: `s22_adb_discovery.mdns_connect_endpoints` przyjmuje wyłącznie `_adb-tls-connect._tcp` o nazwie `adb-RFCT70L7E8J-...`, z prywatnym IPv4 i prawidłowym portem. Nie próbuje łączyć pobliskich obcych urządzeń, nawet gdy emitują poprawną usługę. Po połączeniu nadal sprawdza rzeczywiste `ro.product.model` i `ro.serialno`.
2. **Cykl życia własnej rozmowy**: `AppOwnedClirCampaignExecutor` rozpoznaje wyjątki podczas odczytu identyfikatora połączenia, braku serwisu audio, błędu konfiguracji/probe, błędu callbacku i odrzucenia zadania przez Android `Handler`. Dla już uzyskanego uchwytu każda z tych ścieżek próbuje `disconnectExactCall()`, nie globalnego rozłączenia.
3. **Zgoda przed dialem**: po rezerwacji grantu `isAuthorizedInFlight` jest wywoływane ponownie bezpośrednio przed `callController.dial`. Adapter nadal musi sprawdzić odwołanie w swojej własnej granicy efektu, aby zamknąć nieusuwalny zewnętrzny wyścig czasowy.
4. **Bez obejścia brakującego adaptera**: `CampaignCallControllerRegistry.available()` wciąż nie ma rzeczywistej implementacji instalowanej na Androidzie, zatem autonomiczny dial nadal jest świadomie niedostępny.

## Testy i ograniczenia

- Ponownie wykonano w sandboxie standalone Python mock tests dla source-equivalent `s22_adb_discovery`: **12/12 PASS**, dodatkowo `py_compile` PASS. Nie są to testy bieżącego urządzenia ani pełny zestaw unit testów repo.
- Plik testów w repo rozszerzono o odrzucenie mDNS o błędnej nazwie usługi.
- **Nie ma** jeszcze wyników pełnego Python/Gradle/Android/ADB, ponieważ zadanie na agent-control czeka na aktywnego workera. Nie deklarować `HOST_GREEN`, `PROVEN_S22`, bieżącego ADB połączonego ani sieciowego CLIR.
- Nie wykonano żadnego połączenia, rozłączenia ani modyfikacji CLIR. Ogólny `KEYCODE_ENDCALL` i `cmd telecom end-call` pozostają niedozwolone.

## Akceptacja / następny krok

Najpierw potrzebny **świeży heartbeat Local Agenta na Macu** i wynik już zakolejkowanego zadania. Po jego odbiorze naprawić konkretny błąd budowania/testów. Dopiero potem powrócić do implementacji call-specific Android controller i sprzętowej akceptacji na S22, bez zastępowania uchwytu dzwonienia globalnym `isInCall`/hangup.
