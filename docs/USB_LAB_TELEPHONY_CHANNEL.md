# AI Calls — prywatny kanał USB/ADB: Laboratorium i telefonia (2026-11-08)

## Jednoprzebiegowy odbiór S22 2026-11-09 — GUI/USB i AI Skills

`aicalls-onepass-s22-gui-usb-skills-instrument-20261109-v1`: fizyczny `AndroidUsbGuiLabConcurrencyTest` **1/1 PASS**. Lab GUI otrzymał natywną dzierżawę modelu, konkurencyjna próba przez **wspólny kontroler USB** została odrzucona (`another_lab_model_session_active`), po GUI STOP USB odzyskał zasób. **F-150 VERIFIED dla pojedynczego backendu w procesie**; nie twierdzić, że jednocześnie wysłano zewnętrzny broadcast ADB podczas aktywnego GUI.

`aicalls-onepass-s22-usb-call-negatives-skills-ab-20261109-v1` **DONE/0**: faktyczny USB CLI `natural/formal/message`, Gemma E2B 4096 **3×2=6/6 tur** z prywatnym zapisem i STOP; 3/3 proste przypomnienie losowego kodu. Trzy poprawne odmowy takeover/resume/owned STOP przy braku Call **3/3**; pozytywne interwencje w rzeczywistej rozmowie **NOT_RUN**, wymagają osobnej autoryzacji. [Pełny checkpoint](checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md).

## Weryfikacja historyczna 2026-11-09 — background native Lab PASS

Odbiornik USB nie wymaga pierwszego planu AI Calls ani Launcher. Działa przez `adb shell run-as`, prywatną skrzynkę i broadcast `DUMP`. Operatorowy guard `foreground` był zbyt restrykcyjny, nie wynikał z systemowego ograniczenia kanału. Zostają bramki bezpieczeństwa: dokładny S22, ADB, Call IDLE, brak kampanii i konkurencyjnej sesji natywnej. `aicalls-usb-lab-background-two-turn-20261109-v3` **DONE/0**: Gemma E2B 4096, `lab-start`, **2/2 `lab-send` z odpowiedzią**, `lab-save` 2 tury prywatnie na Mac oraz `lab-stop` OK. F-153 **VERIFIED** (regresja stdin i fizyczny PASS). Wcześniejszy `REFUSED` przy PocketHost to ograniczenie skryptu testowego, nie usterka USB. F-150 GUI+USB concurrency i real Call intervention **NOT_RUN**. [Checkpoint](checkpoints/2026-11-09-usb-lab-background-two-turn-f153-verified.md).


## Aktualny odbiór 2026-11-09 — częściowo potwierdzony S22

Debug APK oraz AndroidTest zainstalowane na S22 bez wipe. Kontrakt `AndroidUsbLabControlContractTest` **3/3 PASS** (DUMP/debug, off-call status i odmowy bez owned Call). Prawdziwe USB CLI `status`, `config-get`, `ram` **3/3 PASS** na S22 po F-152 `VERIFIED` (poprawne cytowanie `sh -c`); Mac pełny gate PASS. Właściwy `lab-start` E2B 4096 i `lab-stop` także wykonały się, lecz pierwsze SEND zwróciło brak stdin. F-153 naprawione w źródle, host PASS; fizyczny retest był bezpiecznie zablokowany przez PocketHost foreground. **Dwie tury Gemmy przez USB jeszcze NOT_RUN na poprawionej wersji CLI.** Live Call takeover/resume/owned STOP i GUI/USB concurrency również NOT_RUN. [Checkpoint 2026-11-09](checkpoints/2026-11-09-nav01-f151-usb-f152-reacceptance.md). Nie ponawiać zatwierdzonych przypadków, nie obchodzić odmowy.

## Stan odbioru — historyczny 2026-11-08

**Pełny checkpoint 2026-11-08:** [USB Lab + telefonia — kod, QA Mac, granice S22 i handoff](checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md). Najnowszy Mac gate `aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` **DONE/0** (370 Python, kompilacja APK/AndroidTest, lint, audio). **Nowa APK USB i fizyczny takeover na S22 NOT_RUN**, choć starsza ścieżka `run-as` miała osobny pozytywny preflight. F-150 wyłączność modelu na Mac PASS, device OPEN.

**SOURCE/HOST PASS**, **DEVICE QA NOT_RUN** dla nowego kanału. W repozytorium `main@114d33805b6b3e2dc550684bd6e956617445432c` znajdują się: odbiornik Android `UsbLabControlReceiver`, wspólny kontroler `UsbLabControl`, konsola Mac `ai_calls_usb_lab.py` i AndroidTest `AndroidUsbLabControlContractTest`. Mac `aicalls-usb-live-config-contract-mac-qa-20261108-v3` **DONE/0**, pełny `scripts/verify_host.sh` PASS, AndroidTest/APK/lint, 370 testów Python PASS. `aicalls-usb-lab-telephony-permission-qa-20261108-v2` **DONE/0**: wcześniej naprawiona walidacja uprawnienia `READ_PHONE_STATE`, 5 nowych testów CLI PASS.

**Nowego APK nie zainstalowano na S22:** próba publikacji zadania sprzętowego z komendami interwencji została zablokowana przed wykonaniem. Nie obchodzić tej odmowy innym mechanizmem; nie twierdzić, że live takeover/STOP albo nowy USB receiver zostały fizycznie sprawdzone. Starszy odczyt ADB `aicalls-lab-usb-mailbox-preflight-20261108-v1` DONE/0 potwierdził S22 `SM-S906B`, telefon IDLE, kampanię nieaktywną, AI Calls foreground i działające `run-as`. **Nie jest to odbiór nowego kodu.**

## Architektura i uprawnienia

- **Mac:** `scripts/aicall_tools/diagnostics/ai_calls_usb_lab.py`, dokładny S22 przez ADB. Żaden publiczny HTTP/TCP/WebSocket ani serwer na telefonie.
- **Android:** `UsbLabControlReceiver` z `android.permission.DUMP` oraz runtime `FLAG_DEBUGGABLE`. Jeden prywatny plik `files/usb-lab-control/request.json` tworzy Mac przez `adb shell run-as`; komunikat `am broadcast -n ...` przenosi **wyłącznie 128-bitowy nonce**. Android usuwa żądanie przed przetworzeniem. `response.json` w prywatnym magazynie zawiera tylko kody, liczniki, stan i metryki.
- **Model:** `UsbLabControl` jest cienkim adapterem do istniejącego `AndroidSkillLabSessionFactory` i `SkillLabRunner`, nie drugim backendem inferencji. START ustala preset i budżet kontekstu jednej sesji; kolejne SEND idą do tej samej sesji, STOP działa przez istniejący runner. Domyślnie Lab USB **4096**, telefoniczny backend **2048**; 8192 wyłącznie na jawne życzenie.
- **Współbieżność:** `main@fdb003d` dodał jedną procesową dzierżawę natywnego backendu współdzieloną przez UI Lab i USB Lab, by nie uruchomić podwójnego LiteRT. Ta poprawka przeszła pełny Mac gate (`aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` DONE/0, 370 Python i AndroidTest APK), lecz fizyczna próba konkurencji jest nadal NOT_RUN; [F-150](../BLOCKERS.md).
- **Prywatność:** treść wysyłana przez stdin do ADB (nie w argumentach `am`). Odpowiedzi modelu zapisują się wyłącznie w `files/usb-lab-control/transcript.json` pod `run-as`; `lab-save` kopiuje je lokalnie na Mac do `~/Documents/AI-Calls/private-exports/lab-sessions/` (`0700` katalog, `0600` plik). Nie wysyłać transkryptów do GitHub, wyników Local Agent ani standardowego logcat.
- **Cykl:** kanał działa w debugowym procesie aplikacji. Śmierć procesu kończy sesję modelu: nie należy twierdzić, że natywny kontekst przetrwał. Ostatni plik prywatnego transkryptu może pozostać do przeglądu. Nowy START tworzy nową sesję.

## Konsola Mac

W checkout `/Users/michal/agent-workspace/repos/ai-calls/work`:

```bash
CLI="python3 scripts/aicall_tools/diagnostics/ai_calls_usb_lab.py"

$CLI status
$CLI config-get
$CLI ram
$CLI watch --interval 2
$CLI lab-start --context-tokens 4096 --preset natural
$CLI lab-status
printf '%s' 'Wiadomość próbna' | $CLI lab-send --session-id <UUID_Z_LAB_START>
$CLI lab-save
$CLI lab-stop --session-id <UUID_Z_LAB_START>
```

`lab-save` podaje jedynie wynik, identyfikator sesji i liczbę tur; surowy tekst pozostaje w prywatnym pliku Mac. Nie kopiować prywatnych promptów do komend Local Agent/GitHub. `watch` wyświetla bezpieczne metadane statusu połączenia i PSS RAM; kończy się Ctrl+C.

Konfiguracja:

```bash
$CLI profile-check --input ANDROID_STT --dialogue GEMMA_4_E2B --output ANDROID_TTS_PL
$CLI profile-set   --input ANDROID_STT --dialogue GEMMA_4_E2B --output ANDROID_TTS_PL
$CLI profile-reset
$CLI incoming-config --mode manual --record no
$CLI incoming-config --mode ai --account-index 0 --record no
```

`profile-*` deleguje do **istniejącego** `UsbDebugBridge`; jego warunki IDLE, role dialera, brak kampanii/pending retry, lokalna weryfikacja TTS pozostają w mocy. `incoming-config` używa **istniejącego** `IncomingAssistantSettingsController`; AI wymaga jawnego wyboru istniejącego konta/SIM, nagrywanie jest jawnie `yes/no`. Zmiana polityki nie odbiera ani nie inicjuje połączenia. Przy niejednoznacznym koncie SIM wybrać je w istniejącym UI systemowym/aplikacyjnym. Nie dodawać numerów ani sekretów do żądań USB.

Interwencja **tylko podczas rozpoznanej rozmowy**:

```bash
$CLI call-status
$CLI call-takeover      # zatrzymaj AI i oddaj rozmowę człowiekowi
$CLI call-resume        # tylko aktywna, obsługiwana rozmowa przychodząca
$CLI call-stop-owned    # anuluj zadanie i zatrzymaj wyłącznie własne outgoing Call
$CLI incoming-ai-disable
```

Nie ma `place-call`, dowolnego `disconnect`, omijania zgody na działanie, ani zmiany profilu silnika w połowie połączenia. `call-takeover` wywołuje istniejący `AndroidOwnedInCallService.takeOverIncoming` lub dokładnie własne `AppOwnedClirCampaignExecutor.takeOver`. `call-resume` używa tylko istniejącego `resumeIncoming`. `call-stop-owned` deleguje do `AppOwnedCampaignService.ACTION_CANCEL_OUTGOING_TASK`, który odwołuje kontynuację i rozłącza wyłącznie dokładnie własny Call; nie jest globalnym `endCall`. Komendy mogą zwrócić odmowę, jeśli połączenie nie jest dopuszczone lub nie należy do tego kontrolera.

## Granice i kolejny odbiór

**Niezaimplementowane**: wpisywanie dowolnych instrukcji do żywego toru mowy / TTS w trakcie własnego połączenia (brak zweryfikowanego bezpiecznego portu do tego). Interwencja oznacza obecnie przejęcie człowieka, wznowienie przychodzącego AI, STOP własnego zadania i podgląd metadanych. To nie jest zdalny RCE ani API do dowolnej telekomunikacji.

**DEVICE QA OPEN:** gdy wykonanie sprzętowe jest dopuszczone, zweryfikować nową APK bez wipe, manifest DUMP, `status/config-get/ram`, odrzucenie interwencji przy IDLE, `AndroidUsbLabControlContractTest` 3/3, dwie prawdziwe tury Gemmy przez USB, STOP i prywatny zapis na Mac; po oddzielnym konkretnie autoryzowanym połączeniu sprawdzić pozytywnie takeover/resume/exact-stop i latencję STOP. Bez pozytywnej fizycznej próby nie nazywać sterowania live `PROVEN_S22`.

Powiązane: [istniejący most USB i eksport audio](USB_PRIVATE_DIAGNOSTICS.md), [laboratorium i jego wymagania](TEST_LAB_SCOPE.md), [autoryzacja i prywatność](SECURITY_PRIVACY.md), [blokery](../BLOCKERS.md).
