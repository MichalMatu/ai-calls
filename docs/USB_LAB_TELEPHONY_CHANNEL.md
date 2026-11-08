# AI Calls — prywatny kanał USB/ADB: Laboratorium i telefonia (2026-11-08)

## Stan odbioru

**SOURCE/HOST PASS**, **DEVICE QA NOT_RUN** dla nowego kanału. W repozytorium `main@114d33805b6b3e2dc550684bd6e956617445432c` znajdują się: odbiornik Android `UsbLabControlReceiver`, wspólny kontroler `UsbLabControl`, konsola Mac `ai_calls_usb_lab.py` i AndroidTest `AndroidUsbLabControlContractTest`. Mac `aicalls-usb-live-config-contract-mac-qa-20261108-v3` **DONE/0**, pełny `scripts/verify_host.sh` PASS, AndroidTest/APK/lint, 370 testów Python PASS. `aicalls-usb-lab-telephony-permission-qa-20261108-v2` **DONE/0**: wcześniej naprawiona walidacja uprawnienia `READ_PHONE_STATE`, 5 nowych testów CLI PASS.

**Nowego APK nie zainstalowano na S22:** próba publikacji zadania sprzętowego z komendami interwencji została zablokowana przed wykonaniem. Nie obchodzić tej odmowy innym mechanizmem; nie twierdzić, że live takeover/STOP albo nowy USB receiver zostały fizycznie sprawdzone. Starszy odczyt ADB `aicalls-lab-usb-mailbox-preflight-20261108-v1` DONE/0 potwierdził S22 `SM-S906B`, telefon IDLE, kampanię nieaktywną, AI Calls foreground i działające `run-as`. **Nie jest to odbiór nowego kodu.**

## Architektura i uprawnienia

- **Mac:** `scripts/aicall_tools/diagnostics/ai_calls_usb_lab.py`, dokładny S22 przez ADB. Żaden publiczny HTTP/TCP/WebSocket ani serwer na telefonie.
- **Android:** `UsbLabControlReceiver` z `android.permission.DUMP` oraz runtime `FLAG_DEBUGGABLE`. Jeden prywatny plik `files/usb-lab-control/request.json` tworzy Mac przez `adb shell run-as`; komunikat `am broadcast -n ...` przenosi **wyłącznie 128-bitowy nonce**. Android usuwa żądanie przed przetworzeniem. `response.json` w prywatnym magazynie zawiera tylko kody, liczniki, stan i metryki.
- **Model:** `UsbLabControl` jest cienkim adapterem do istniejącego `AndroidSkillLabSessionFactory` i `SkillLabRunner`, nie drugim backendem inferencji. START ustala preset i budżet kontekstu jednej sesji; kolejne SEND idą do tej samej sesji, STOP działa przez istniejący runner. Domyślnie Lab USB **4096**, telefoniczny backend **2048**; 8192 wyłącznie na jawne życzenie.
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
