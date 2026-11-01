# AI Calls — sprint Wireless ADB + bezpieczny lifecycle rozmów (2026-11-01)

Baseline: `0bb6183170a6c4b04d2a17de9d76c4b985dda1f1` (`main`); po zmianach zawsze sprawdź świeży HEAD. Repozytorium: `MichalMatu/ai-calls`. Sprint wykonany przez GitHub Connector i lokalny Linux sandbox; **bez dostępu wykonawczego do Mac/S22/ADB** w tym czacie. Nie używano GitHub Actions, Codex ani Work. Nie wykonywano połączeń.

## Zmiany

1. **Wireless ADB discovery:** nowy `scripts/aicall_tools/device/s22_adb_discovery.py` wykorzystuje `adb devices -l`, bezpieczne `adb mdns services`, `adb connect` dla prywatnych adresów `_adb-tls-connect._tcp`, a następnie weryfikuje **dokładne** `ro.product.model` i `ro.serialno`. Preferuje podłączony transport Wi-Fi, dopuszcza USB, odmawia przy wieloznaczności lub błędnej tożsamości. Nie paruje urządzenia i nie zmienia ustawień Android.
2. **Preflight Local Agent:** `scripts/local_agent/device_readiness.sh` przestał utożsamiać numer sprzętowy USB z transportem ADB. Automatycznie wybiera zweryfikowany transport, weryfikuje stan `IDLE` i wywołuje istniejący probe Shizuku/audio. Wspiera `AICALL_SERIAL` jako jawne nadpisanie, nadal weryfikowane fizycznie.
3. **Legacy hangup:** `Adb.hangup()` odmawia wykonywania globalnego `KEYCODE_ENDCALL`. Usunięto z `chatgpt_relay_live_call.py` retry i fallback `cmd telecom end-call`, także dla trybu attach. Przy cudzej rozmowie cleanup kończy **tylko** sesję relay/aplikacyjną; rozmowa pozostaje nietknięta.
4. **Legacy dial:** `Adb.dial()` nie pozwala uruchomić telefonu bez konkretnego kontrolera. `autonomous_call_loop.run_once` działa jedynie z jawnym `attach_existing_call=True` i potwierdzonym `OFFHOOK`, bez wybierania i rozłączania. `relay-live` również działa tylko w `--attach-existing-call`; tryb automatyczny odmawia **przed** dotknięciem ADB. `scripts/local_agent/clir_iteration.sh` odmawia starej pętli automatycznego CLIR (exit 3).
5. **Transport w relay CLI:** przy braku `--serial` wybiera zweryfikowany Wireless ADB/USB automatycznie, bez sztucznego wymogu bindowania portu USB. Wewnętrzna zgoda na Wi-Fi dotyczy tylko wyniku zweryfikowanego discovery.
6. **Nienaruszanie Bluetooth:** stary attach automatycznie wyłączał Bluetooth na telefonie. Usunięto automatyczne przełączanie; gdy Bluetooth jest włączony, diagnostyka odmówi pracy zamiast zmieniać trasę istniejącej rozmowy. Utrzymano wymagane bezpieczne sprawdzenia audio i kontrolę jawnej zgody na modyfikację CLIR.

## Weryfikacja

- 12/12 — standalone Python smoke z funkcjami discovery i mock ADB w sandboxie: USB/Wi-Fi, reconnect mDNS, fałszywa tożsamość, offline, niejednoznaczność, publiczne IP, błędny serial.
- 12/12 — statyczna inspekcja bieżących plików repo przez GitHub connector: brak wykonywalnego globalnego `KEYCODE_ENDCALL` i `cmd telecom end-call`; brak `adb.dial()`/`adb.hangup()` w obu legacy runnerach; źródła testów nowego gate.
- Dodano testy unittest w repo: `test_s22_adb_discovery.py`, testy fail-closed `test_s22_call_control.py`, `test_autonomous_call_loop.py`, `test_chatgpt_relay_live_call.py`. Całego repo Python test suite **nie** uruchomiono bez połączenia z lokalnym workspace; smoke w sandboxie obejmuje tylko kod discovery source-equivalent.
- Brak testu bieżącego Wireless Debugging, Shizuku, Gradle/Android build, ADB lub S22. Wszelkie archiwalne dowody ADB z 7–8 października są historyczne.

## Ważna granica

**Nie jest to działający adapter zakończenia własnego połączenia.** `CampaignCallControllerRegistry` w Androidzie nadal nie ma kontrolera dającego prawdziwy call-specific uchwyt i selektywne zakończenie. Nie wolno podmienić go na `TelecomManager.endCall()`, `KEYCODE_ENDCALL`, `cmd telecom end-call` ani weryfikację z samego globalnego `isInCall`. Diagnostyka attach-only dotyczy **wyłącznie rozmowy jawnie uruchomionej przez operatora**; nie może być uznana za autonomiczny dial.

## Kolejna akceptacja sprzętowa

Na aktywnym Local Agent: pobierz świeży `main`; uruchom `PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -t scripts -p 'test_*.py'`, `bash scripts/local_agent/host_verify.sh` (Gradle, lint, Android, unittest), `bash scripts/local_agent/device_readiness.sh` bez wywołania połączenia. Zapisz aktualny `adb devices -l`, mDNS, model i `IDLE`, przy czym raport powinien maskować dane osobowe. Następnie zaprojektuj i udowodnij call-specific Android adapter oraz niezależną weryfikację `*#31#` **przed** powrotem do automatycznych realnych rozmów. Każda rozmowa wymaga osobnej ważnej autoryzacji grantu na dokładny zakres.
