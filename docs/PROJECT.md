# AI Calls — MVP 0.2.3 (release candidate)

**Stan: odbiór 0.2.3 na S22+ / Android 16 (23.11.2026).** Główna aplikacja AI Calls zawiera parowanie Wireless ADB i obsługę GSM. `.s8lab` służy lokalnym testom deweloperskim.

## Potwierdzone

- Na kandydacie `4040efc8` zainstalowanym z APK: zgoda Androida na ograniczone ustawienia, domyślna aplikacja Telefon, wybór SIM, pierwsze parowanie z powiadomienia, autoryzacja ADB i rejestracja hosta GSM. Telefon w IDLE, bez rozmowy.
- Wcześniejsze fizyczne PL GSM, notatki szyfrowane i WAV: PASS na wydaniu 0.2.1; nie przenosić tego wyniku automatycznie na nowy APK.
- Refaktor granic: host JVM/Gradle i 9/9 bezpiecznych testów UI na S22+ dla zweryfikowanych PR #78–#86. Żadnych rozmów GSM w tych testach.
- Pełna mapa 34 Activity i ustawień: [UI](architecture/UI_SCREEN_MAP.md). Importy kontroluje `module_boundary_gate.py`.

## Otwarte

Fizyczne rozmowy GSM na bieżącym APK, docelowe podpisywanie/IPC i kwalifikacja produkcyjna. W publicznym APK 0.2.2 parowanie i host GSM były dostępne wyłącznie w wersji deweloperskiej `.s8lab`.

**WSTRZYMANE:** Shizuku, GitHub Actions, schedulery, `WRITE_SECURE_SETTINGS`.

## Następny sprint

Nieplanowany. Projekt odłożony po publikacji MVP; nowe prace wymagają osobnej decyzji.
