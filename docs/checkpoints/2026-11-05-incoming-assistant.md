# INCOMING / manual / przejęcie — 2026-11-05

Baza `19c3019`; bezpośredni lokalny Codex, Git/Gradle. Local Agent sprawdzony read-only: idle, bez aktywnego zadania; brak zleceń, Work i Actions. [Zakres modułu](../INCOMING_ASSISTANT.md).

## Zmiana i granice

Zaakceptowane uwagi o zadaniu obejmującym wiele rozmów, koordynacji według postępu, pochodzeniu faktów, warunkach samodzielności, naturalnym przekazywaniu głosu i trwałym stanie dopisano do planu/roadmapy. Implementacja tego zakresu dostarcza niezależny moduł JVM conversation-core i adapter INCOMING, ustawienia AI/manual, profil przychodzący, wspólny runtime i przejęcie bez rozłączenia, również dla dokładnego wychodzącego Call.

Nie zaimplementowano całego przyszłego koordynatora wielorozmowowego, narzędzi kalendarza, pełnego audio po przejęciu, playera, scenariuszy wizyt i scalonej dystrybucji. Sekretarka jest pierwszym scenariuszem przyjmowania wiadomości bez skutków zewnętrznych. Nie dodano wyboru SCRIPT/Gemma do przychodzącego: UI jawnie opisuje model bez skryptu; wychodzące dwa tryby pozostają. Nie dodano limitu prób/tur, TTL, whitelisty numerów lub osobnych potwierdzeń każdego przychodzącego.

## Receipts

- Rdzeń RED: `/tmp/ai-calls-incoming-core-red.log`, brak typów przed implementacją; GREEN `/tmp/ai-calls-incoming-core-green.log` (pierwsze 11 kontraktów).
- Pierwszy build adaptera FAIL compile: `/tmp/ai-calls-incoming-first-build.log`, nullable profil (F-081), brak wdrożenia. Poprawiony build/unit `/tmp/ai-calls-incoming-host-unit.log` PASS.
- Fault portu readiness RED: `/tmp/ai-calls-incoming-readiness-red.log`, jawny wyjątek, następnie GREEN w pełnym rdzeniu; nie fizyczny fault-injection Keystore (F-082).
- Drugi wariant build FAIL: `/tmp/ai-calls-incoming-build-green.log`, przesłonięcie readiness metody (F-083), brak wdrożenia. `/tmp/ai-calls-incoming-build-fixed.log`: unit i oba APK PASS.
- Czat powitania RED: `/tmp/ai-calls-incoming-greeting-red.log`, pominięta tura 0 (F-084), potem test wspólnego feed/history w pełnym host.
- Pełna bramka `/tmp/ai-calls-incoming-full-host.log`: pierwszy HOST_GREEN 800 JVM/266 Python/lint/oba APK/audyty. Po przeglądzie STOP `/tmp/ai-calls-incoming-full-host-reviewed.log`: PASS. Końcowa dokładna wersja źródła w `/tmp/ai-calls-incoming-final-host.log`: **host_quality_gate_green=true**, **803 JVM** (730 app, 12 conversation-core, 51 realtime-client, 10 privileged-helper), **266 Python**, lint wszystkich modułów Androida, oba APK i audyty PASS; bez failed/errors/skipped.
- STOP persistence RED `/tmp/ai-calls-incoming-stop-persistence-red.log`, potem 3 testy portu SharedPreferences GREEN: uszkodzony stary zapis, idempotentne disable i jawny błąd commit (F-085). To nie fault-injection fizycznej pamięci S22.
- F-086: sfinalizowanie recordera przy odmowie przed utworzeniem runtime; test native izolowanego archiwum przygotowany, nieuruchomiony.
- Przygotowano 6 natywnych kontraktów AndroidIncomingAssistantContractTest: izolowana polityka, ekran bez mutacji, powitanie bez STT/modelu, anulowanie i późne PCM dwie syntetyczne tury rzeczywistego wybranego modelu z rolą odbierającą oraz zamknięcie recordera po odmowie startu przed audio. Bez uruchomienia nie oznaczać PASS.

- APK app SHA-256 `646f88d9a2e466621d659d807e1e93e7e26da8dc1c33b5e3de84b13da7a5d37d`; AndroidTest `63ec5ab7174d62e136c42c2d5f0ca89df0a859303e781d2c2b5dd8caaf12d10f`. Są to zbudowane artefakty, bez nowej instalacji lub porównania podpisu urządzenia.
- audit_product_friction i git diff --check PASS; 141 lokalnych odnośników w zmienianych dokumentach bez braków.

## Urządzenie — QA_PENDING

Próba świeżego preflight USB: ADB nie wykrywa RFCT70L7E8J, lista urządzeń pusta. Nie instalowano APK, nie zmieniano polityki produkcyjnej na telefonie, nie wykonywano nowych połączeń ani testów native. Właściciel dostał pytanie o ponowne podłączenie. Nie używano alternatywnego transportu.

Poprzednie odczyty S22 i zainstalowany runtime `e72bb15` są historią wcześniejszego zakresu, nie świeżym odczytem tej zmiany. Odbiór, słyszalność powitania/dialogu, przejęcie z faktycznym przywróceniem głosu użytkownika oraz brak późnego TX na przychodzącym Call mają QA_PENDING. F-080 i wcześniejsze defekty jakości/skutków pozostają w [BLOCKERS](../../BLOCKERS.md).

Prywatny katalog receipts: `~/Documents/AI-Calls/local-codex/2026-11-05-incoming-assistant/` (0700, pliki 0600). Przy podłączeniu najpierw model/2× IDLE/kampania/retry/rola/zakres, potem zgodność podpisów i instalacja bez czyszczenia danych, testy i postcheck. Rzeczywisty INCOMING wymaga kontrolowanego rozmówcy/końcówki i przyjętej w aplikacji właściwej polityki, nie połączenia do Orange.
