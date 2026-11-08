# AI Calls — LAB-SEMANTIC-01 — trzy prawdziwe rozmowy Gemma 4 E2B na S22 (2026-11-08)

## Zakres i ograniczenia

Cel: sprawdzić rzeczywistą pamięć informacji między turami, zmianę tematu, sprostowanie, powrót do poprawionych danych oraz reakcję na brak informacji. Nie używano czterech usuniętych fabrycznych historii. Nie wykonywano połączeń ani efektów zewnętrznych.

**Środowisko:** Samsung S22 `SM-S906B`, ADB `RFCT70L7E8J`, `mCallState=0`, brak kampanii. W każdym z trzech prywatnych plików dowodowych model `GEMMA_4_E2B` potwierdzony odczytem tylko wybranego identyfikatora (`aicalls-semantic-s22-model-id-private-20261108-v1` DONE/0). Silnik: `SkillLabRunner.startManual/submitManual`, jedna natywna sesja na siedem tur; za każdym razem nowy losowy kod próbki.

## Wyniki testu jakości — mierzone na urządzeniu

| Sesja | Tur | Zmiana tematu | Pamięć kodu | Sprostowanie | Kod po sprostowaniu | Pamięć końcowa | Brak zgadywania numeru | Śr. czas odpowiedzi |
| --- | ---: | --- | --- | --- | --- | --- | --- | ---: |
| 1 | 7 | PASS | PASS | PASS | PASS | PASS | PASS po prywatnej niezależnej kontroli | 4142 ms |
| 2 | 7 | PASS | PASS | PASS | PASS | PASS | PASS | 4103 ms |
| 3 | 7 | PASS | PASS | PASS | PASS | PASS | PASS | 3867 ms |

**Łącznie: 3/3 sesje, 21/21 tur zakończonych technicznie, 0 propozycji END_CALL; wszystkie 18 sprawdzeń sześciu kryteriów zaliczone w tej ograniczonej próbie.** Przeciętna latencja odpowiedzi po trzech sesjach ok. 4,04 s. N=3 nie jest dowodem niezawodności poza tymi przypadkami ani kompletną oceną halucynacji.

Początkowy wzorzec dla `unknown_fact_cautious` był zbyt wąski: pierwsza odpowiedź miała `false`, mimo że nie zawierała żadnego nowego numeru. Prywatna analiza na S22 (`aicalls-semantic-unknown-local-classifier-20261108-v1`, `...unknown-classifier-refined-20261108-v2` DONE/0) potwierdziła **wyraźne zastrzeżenie braku wiedzy**, bez liczbowego identyfikatora. W teście AndroidTest poprawiono językowy predykat oraz odrzucanie deklaracji niepewności połączonej ze zmyślonym numerem: `main@4a3c49b6e02b06ff5937c3dd6d961a13a6a9d3f2`; próby 2 i 3 użyły poprawionego APK testowego.

## Dowody wykonawcze

- `aicalls-semantic-dialog-s22-preflight-20261108-v1`: DONE/0, clean Mac, S22 AI Calls foreground, IDLE, no campaign.
- `aicalls-semantic-native-mac-gate-20261108-v1`: DONE/0, pełny `scripts/verify_host.sh` PASS, 365 Python, Gradle, AndroidTest i audio audit.
- `aicalls-semantic-native-s22-seven-turn-20261108-v1`: DONE/0, realna pierwsza sesja 7 tur, test instrumentacyjny 1/1 PASS, bez wipe, IDLE i clean.
- `aicalls-semantic-unknown-classifier-mac-final-20261108-v1`: DONE/0, pełny gate po poprawce testu PASS.
- `aicalls-semantic-native-s22-repeats-20261108-v1`: prawidłowe `REFUSED` w preflighcie — aplikacja opuściła foreground, **testy nie wystartowały**.
- `aicalls-semantic-s22-foreground-readonly-20261108-v1`: DONE/0, fizyczny ekran systemowego Samsung Launcher i IDLE.
- `aicalls-semantic-native-s22-repeats-20261108-v2`: DONE/0, bezpieczny Launcher/AI Calls preflight, nowy AndroidTest APK bez wipe, **2/2 powtórki PASS**, na końcu IDLE, clean Mac.
- `aicalls-semantic-s22-model-id-private-20261108-v1`: DONE/0, wszystkie trzy profile jawnie `GEMMA_4_E2B`.

Pełne odpowiedzi i pytania z losowymi etykietami są w trzech osobnych plikach `lab_semantic_probe_*.json` w prywatnym `noBackupFilesDir` aplikacji na S22. **Nie kopiowano transkryptów do GitHub, logcat ani wyników Local Agenta**; przekazywano wyłącznie skategoryzowane wyniki.

## Co naprawdę pozostaje OPEN

- **Human semantic review:** niezależny recenzent nie ocenił zdań tura po turze; osiem wymiarów `LabDialogueReport` pozostaje `UNASSESSED` do ręcznego review z dowodem.
- **Grounded factuality:** inne otwarte pytania i halucynacje, dłuższe konteksty oraz przeciwny/inny zestaw promptów nadal NOT_RUN; syntetyczna próba sama podpowiadała brak numeru.
- **Editor UI / A-B / F2 audio:** pełny klikalny CRUD edytora, zestaw fizycznych porównań modeli i STT/TTS/polski PCM osobno OPEN.
- Android nie publikuje ogólnego zewnętrznego API czatu przez ADB; użyto prawdziwej instrumentacji aplikacji i istniejącego lokalnego backendu, bez implementowania publicznego endpointu.
