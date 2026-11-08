# AI Calls — F1: priorytet funkcjonalny i przygotowanie izolowanego CRUD Sejfu

Data: 2026-11-08. Start GitHub `main@fa21f88decf481d17741ce22828037bb8ef621b2`; nowy test source `6a6d2a133e175d805fe14a4c8b6102578230dc3b`.

**Decyzja:** właściciel nie chce czekać na kolejne kosmetyczne przeglądy ani NAV-01. Priorytet to faktyczne użycie Skills/Sejfu/scenariuszy i eksperyment wejścia audio bezpośrednio do Gemmy.

**Sprawdzony stan implementacji:**
- Agent AI zawiera edytor Skills/presetów, Sejf standardowy i własny, scenariusze, przygotowanie; Lab ma rzeczywistą lokalną inferencję tekstową, historyczne dwie tury i STOP PASS.
- `AndroidCustomIdentityVault`: zapis AES-GCM i metadane typów TEXT/NUMBER/DATE/SECRET; w aplikacji obsługuje dodawanie/edycję/usuwanie, bez automatycznego ujawniania AI. Testy właścicielskich danych nie są dozwolone. Scenariusze niewykonawcze to drafty, nie potwierdzenie prawdziwej rozmowy.
- `GemmaAudioDirectInputProvider` wykonuje eksperymentalne PCM 16 kHz → WAV → `Content.AudioBytes`, ale `AudioEngineRegistry` ma tę opcję wyłączoną `integrated=false`, a `AudioEngineReadiness` odmawia `audio_direct_s22_unqualified`. Native test `AndroidGemmaAudioNativeOffcallTest` istnieje, lecz nie dowodzi obecnej gotowości S22.

**Rzeczywiste działania:**
1. `aicalls-f1-corepreflight-20261108-v1`: repo i Mac na `fa21f88` czyste, potem ADB `RFCT70L7E8J` **not found**, task failed/1. Brak prawa ogłosić fizycznego PASS.
2. Dodano `AndroidCustomVaultFunctionalAcceptanceTest`: w izolowanym noBackup katalogu cztery syntetyczne rekordy, reopen, zmiana metadanych, odmowy walidacji i duplikatów, sprawdzenie braku plaintext w zapisie, bezpieczne skasowanie wyłącznie testowych rekordów.
3. `aicalls-f1-custom-vault-test-compile-20261108-v1` **DONE/0**: `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL, checkout clean na `6a6d2a1`. **Nie uruchamiano** tego testu na S22; kompilacja != device acceptance.

**Kolejna rzeczywista sekwencja:** po odzyskaniu ADB i sprawdzeniu `IDLE` uruchomić izolowane testy `AndroidCustomVaultFunctionalAcceptanceTest`, `AndroidPersonalScenarioStoreContractTest`, `AndroidSkillPresetMatrixTest.aEncryptedEditVersionSwitchAndRollbackInIsolatedStore`; potem ręczne UI dodaj/edytuj/zapisz/odtwórz z fikcyjnymi polami/Skills, Lab model+STOP. Gemma native audio osobno po pomiarze RAM/thermal i bez utrudniania innych aplikacji. Dokładne statuty w `docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md`. CALL/real effects NOT_RUN.

## Druga próba fizycznego F1 — urządzenie zajęte przez ShellyLink

- `aicalls-f1-sync-and-device-check-20261108-v1`: Mac repo zsynchronizowane i czyste, S22 chwilowo ADB CONNECTED, foreground launcher.
- `aicalls-f1-s22-preflight-before-fixture-tests-20261108-v1`: `SM-S906B`, `mCallState=0`, Launcher, APK AI Calls 0.2.0 code 2, test APK wcześniej zainstalowany.
- `aicalls-f1-s22-isolated-skill-vault-scenario-20261108-v1`: **FAILED przed instalacją**. Pierwszy etap `:app:assembleDebugAndroidTest` **PASS**; etap bezpiecznej instalacji przerwany przez fence foreground (wymagany Launcher). **Ani nowego test APK nie zainstalowano, ani żadnego z trzech testów nie uruchomiono.**
- `aicalls-f1-s22-recheck-why-install-fenced-20261108-v1`: ADB `device`, `mCallState=0`, ale foreground `app.shellylink.mobile/.MainActivity`. To równoległa aktywna aplikacja, nie problem implementacji AI Calls; nie przerywać jej ekranów. **Status testów: NOT_RUN_DEVICE / DEVICE_BUSY**, Mac build PASS, żadnego fałszywego PASS.
- **Dalszy krok:** uruchomić testy dopiero po odzyskaniu wolnego foreground i ponownym preflight. Priorytet nadal Sejf → Skills → Scenariusze, potem osobne Gemma Audio. Nie wracać do kosmetycznego UX.
