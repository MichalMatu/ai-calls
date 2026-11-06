# AI Calls — fizyczne QA ustawień i archiwalnego odsłuchu, S22

**Data:** 2026-11-06. **Urządzenie:** Samsung SM-S906B, dokładna tożsamość sprzętu zweryfikowana przez Mac Local Agent. **Kod aplikacji:** `d59b459a9a50d2f9c8f037f2116d802a9b288617` (+ dokumentacja `70d0b0d`), testy instrumentacyjne dopisano i opublikowano jako `a700575678071f722212dcbdc99c219066c423da` i `a1dd6af0efc37b0c00b5e88b4d5812851e6f1ba5`. Żadna z dwóch ostatnich zmian nie zmienia produkcyjnego APK.

## Odbiór QA

| Etap | Rzeczywisty wynik | Mac task |
|---|---|---|
| Nowy preflight S22 | **PASS**, 2× IDLE, dokładna tożsamość S22, kampania nieaktywna | `aicalls-20261106-settings-ux-s22-reconnected-preflight-v1` DONE/0 |
| Instalacja i podstawowy układ | **PASS**: podpis instalowanego APK taki sam jak zainstalowanego; `adb install -r -t`, bez wymazywania danych; `AndroidUiAccordionContractTest` 2/2 | `aicalls-20261106-settings-ux-s22-targeted-ui-v1` — wrapper FAILED/1 tylko przy kontroli tekstu raportu następnego testu |
| Skills i Laboratorium, wejścia bez edycji | **PASS 1/1**: `AndroidSkillPresetMatrixTest#bSkillsAndLaboratoryScreensLaunchWithoutMutatingActivePreset` | `aicalls-20261106-settings-ux-s22-skills-result-diagnosis-v1` DONE/0: Android `OK (1 test)`, `INSTRUMENTATION_CODE: -1` |
| Matryca nowych ekranów | **PASS 3/3**: osiem ekranów, główny przycisk do archiwum, bezpieczne okno `FLAG_SECURE`, lista historii; ustawienia runtime, profil audio, przychodząca polityka i skillsy przed/po bez zmian | `aicalls-20261106-settings-ux-s22-navigation-matrix-device-v1` DONE/0 |
| Test playera na prawdziwym S22 | **PASS 1/1**: syntetyczny cichy WAV (100 ms) zapisany w tymczasowym prywatnym folderze i odtworzony z dymka czatu po sprawdzeniu stanu telefonu | `aicalls-20261106-settings-ux-s22-synthetic-playback-device-v1` — Android `OK (1 test)`; wrapper FAILED/1 przy błędnym shell `sh -c` w kontroli sprzątania |
| Kontrola sprzątania i bezpieczeństwa | **PASS**: 0 katalogów `9999999999999-qa-*`; rzeczywiste `pending_retry=false` po parsowaniu XML; S22 IDLE, bez kampanii | `aicalls-20261106-settings-ux-s22-synthetic-cleanup-diagnosis-v1`, `aicalls-20261106-settings-ux-s22-continuation-sanitized-read-v1` DONE/0 |

**Łącznie Android UI:** 2+1+3+1 = **7/7 testów PASS**, bez połączenia, inferencji modelu, zmiany polityk ani usuwania danych użytkownika. Nie publikowano prywatnych nagrań, transkryptów ani identyfikatorów sesji.

## Host quality gates i źródło testów

`aicalls-20261106-settings-ux-s22-navigation-matrix-source-gates-v1` i `aicalls-20261106-settings-ux-s22-synthetic-playback-source-gates-v1` DONE/0: kompilacja AndroidTest, pełny `scripts/verify_host.sh` (270 Python), `ANTI_FRICTION=PASS`. Opublikowano tylko dwa nowe instrumentacyjne testy (commity `a700575`, `a1dd6af`) po PASS urządzenia. Mac checkout zweryfikowany po publikacji.

## Granice dowodów i dalsza praca

- Odsłuch **istniejącego segmentu** jest fizycznie potwierdzony na syntetycznym WAV. Nie sprawdzano głośnej, prawdziwej rozmowy ani pełnego nagrania; nie oznacza to pełnego pokrycia RX/TX. **F-080 nadal OPEN**: ciągły zapis sesji (również manual), player całości, płynne przewijanie i pokrycie luk.
- Potwierdzono otwarcie ekranów i trwałość ustawień bez ich edycji. Nie wykonano pełnej matrycy zmiany/zapisu każdej wartości ani oceny wizualnej UX przez użytkownika. LIVE CALL, takeover i naturalność Gemmy pozostają osobnymi QA_PENDING.
- Dwa błędy wrapperów to błędy **odczytu raportów/złożenia `adb shell run-as ... sh -c`**, nie błędy aplikacji: `INSTRUMENTATION_CODE: -1` oznacza Android `RESULT_OK`; `println` z testu nie musi pojawić się w raportowanym `am instrument`; a obecność pliku `campaign_continuation_v1.xml` nie dowodzi aktywnego retry. Stosować bezpośrednie `run-as cat` i parsować `grant_id`, bez ujawniania wartości. Końcowa kontrola rzeczywistego `pending_retry=false` jest potwierdzona oddzielnym wynikiem.
- Pozostały etap wizualnego dopracowania pięciu kategorii i docelowego paska nawigacji, oraz niezależny sprint F-080.
