# AI Calls — USB Lab w tle, F-153 fizycznie potwierdzone (2026-11-09)

## Wniosek
Prywatny kanał USB/ADB **nie wymaga** pierwszego planu AI Calls ani ekranu Launcher: `UsbLabControlReceiver` obsługuje chroniony broadcast i prywatną skrzynkę aplikacji także wtedy, gdy użytkownik korzysta z innej aplikacji. Wcześniejszy guard operatora wymagający AI Calls/Launcher foreground był niepotrzebnie restrykcyjny; nie był uprawnieniem Androida ani wymogiem architektury. Dla off-call USB Lab konieczne pozostają: dokładny S22 przez ADB, telefon IDLE, brak aktywnej kampanii/czatu i brak innej trwającej sesji Lab; bez rzeczywistych Call ani zmiany konfiguracji.

## Dowód
- Źródło: `main@2adb1245581ca44cc6013eafb15b64a75b9a2222` (bez nowych zmian kodu). Mac Local Agent `macbook-michal` / S22 `SM-S906B` z USB ADB. Task `aicalls-usb-lab-background-two-turn-20261109-v3` **DONE/0** i czysty checkout po wykonaniu.
- Bramka: `CALL_IDLE=True`, `AI_RUNNING=False`, poprzednia sesja Lab w stanie `STOPPED`; brak wymogu foreground.
- `lab-start` Gemma E2B / 4096: **PASS**. `lab-send` dwukrotnie `lab_turn_queued`; `lab-status` wykazał kolejno `COUNT=1` i `COUNT=2`, `PHASE=INPUT_READY`: **2/2 techniczne tury PASS**.
- `lab-save`: **PASS**, `private_lab_transcript_saved_on_mac`, `TURNS=2`, prywatny katalog Mac; w zdalnym receipt bez tekstu odpowiedzi. `lab-stop`: `lab_stop_requested`, `ok=true`. Postflight `CALL_IDLE=True`.
- Mac full gate i regresja stdin F-153: `aicalls-usb-f153-mac-host-qa-20261109-v1` **DONE/0**, 6 testów USB helper + 5 testów Lab, pełny `scripts/verify_host.sh` PASS. **F-153 VERIFIED**.
- Poprzednia próba v2 `REFUSED` wyłącznie z powodu starego operatorowego sprawdzenia foreground; nie świadczyła o niemożliwości korzystania z USB w tle. F-152 status/config/RAM i Android receiver contract nadal fizyczny PASS.

## Zakres niepotwierdzony
Nie wykonano realnego Call ani dodatnich takeover/resume/exact owned STOP. Nie zmieniano danych Sejfu ani zezwoleń. Dwie krótkie tury nie dowodzą wieloturowej jakości semantycznej F-148 ani współbieżności natywnego backendu GUI+USB F-150; te zadania nadal OPEN.
