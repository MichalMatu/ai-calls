# AI Calls — F1: S22 device-functional core + eksperymentalne Gemma Audio Direct

2026-11-08 | repo `MichalMatu/ai-calls`, `main` | GitHub Connector + Mac Local Agent | brak Codex/Work/GitHub Actions.

## Co jest PASS — bez naciągania znaczenia

- `aicalls-f1-s22-ready-preflight-20261108-v2` — `main@e30799e`, clean Mac, S22 `SM-S906B`, `RFCT70L7E8J`, AI Calls foreground, `mCallState=0`, app test APK installed.
- `aicalls-f1-s22-fixture-functional-20261108-v2` **DONE/0, 3/3 AndroidTest**. Targeted install tylko test APK `-r -t`, bez wipe:
  1. `AndroidCustomVaultFunctionalAcceptanceTest` **1/1**: izolowany AES-GCM custom Sejf: TEXT/NUMBER/DATE/SECRET, wrażliwość, create/update/reopen, duplikaty/błędny typ, brak plaintext w blob, delete. Nie dotyka danych właściciela.
  2. `AndroidPersonalScenarioStoreContractTest` **1/1**: izolowany szyfrowany własny scenariusz, kopia fabryczna, 2 wersje i zapis, brak nadpisania szablonu.
  3. `AndroidSkillPresetMatrixTest#aEncryptedEditVersionSwitchAndRollbackInIsolatedStore` **1/1**: syntetyczna instrukcja, własna kopia presetu, wersjonowanie, wyłączenie/ponowne przypięcie instrukcji, odczyt i brak plaintext. Bez ingerencji w bieżący preset.
  Postcheck `mCallState=0`, git clean.
- `aicalls-f1-gemma-audio-s22-resource-preflight-20261108-v1`: memory `MemAvailable=2942188 kB`, thermal 0, bateria 32.4°C, AP 34.4°C. `aicalls-f1-gemma-native-direct-audio-s22-20261108-v1` nie wykonał testu z powodu brakującego `timeout` (Mac exit 127). Korekta portable Python: `aicalls-f1-gemma-native-direct-audio-s22-20261108-v2` **DONE/0, AndroidGemmaAudioNativeOffcallTest 1/1 PASS (9.328 s)**. Weryfikacja natywnej ścieżki Engine audio backend dla syntetycznej ciszy PCM16 z `Content.AudioBytes`; PASS dopuszcza odpowiedź pustego transkryptu. Postcheck `mCallState=0`, thermal 0 i MemAvailable 3378640 kB. Brak prawdziwej mowy i porównania WER/CER; registry nadal `integrated=false`, `audio_direct_s22_unqualified`.

## UI — niezamknięte, bez naruszania obcych aplikacji

- Nowy `AndroidCustomVaultUiAcceptanceTest` (source `6b2d813`): wstrzykuje izolowany magazyn do ekranu podczas AndroidTest; próbuje utworzyć/zmienić/usunąć wpis przez prawdziwy AlertDialog, bez danych właściciela.
- `aicalls-f1-s22-vault-ui-acceptance-20261108-v1` **FAILED po pierwszym etapie**, w którym AndroidTest APK **BUILD SUCCESSFUL** na Macu. Następny etap instalacji został poprawnie zatrzymany przez kontrolę wyłączności foreground; niczego nie instalowano w tej próbie.
- `aicalls-f1-s22-foreground-after-ui-test-block-20261108-v1` **DONE/0**: `pl.pockethost.companion/.MainActivity` foreground; Android Call IDLE. Nie zgłaszać błędu AI Calls bez reprodukcji. UI test **NOT_RUN**, nie PASSED.

## Dalsze konkretne testy

1. Na wolnym S22 uruchomić dokładnie `AndroidCustomVaultUiAcceptanceTest`; ewentualną usterkę UI naprawić i potwierdzić w tym samym przebiegu. Dopiero potem ręczna edycja fikcyjnych pól przez operatora.
2. Interakcje edytora Skills i własnych scenariuszy przez UI z izolowanymi rekordami oraz pełną ścieżką `UI → store → reload`. Poprzedni 3/3 PASS backendu zachować.
3. Native `GemmaAudioDirectInputProvider` przetestować na kontrolowanym, nieosobowym polskim PCM z oczekiwaną transkrypcją i stanem STOP, mierząc słowa/latencję/memory; nie włączać w profile przed potwierdzeniem.
4. Kontynuować `docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md`; `NAV-01` i manualna UX12 nie blokują F1. F-080/F-122/F-123/F-124 nadal OPEN, real Call NOT_RUN.

Brak zmian w runtime połączeń, konfiguracji modeli, sekretach, numerach, sieci, pobieraniu; nie publikowano prywatnych zrzutów/WAV/transkryptów.
