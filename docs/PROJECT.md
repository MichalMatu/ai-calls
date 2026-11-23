# AI Calls — MVP 0.2.3 (release candidate)

**Stan: poprawka parowania Wireless ADB w trakcie fizycznego odbioru; publikacja 0.2.3 po udanym teście (23.11.2026).** Kod na `main`; aplikacja testowa `.s8lab` instalowana obok głównego dialera. Zmiany FE/Presentation/Domain/Data zamknięto na obecny zakres bez redesignu.

## Potwierdzone

- Wcześniejsze fizyczne PL GSM, notatki szyfrowane i WAV: PASS na wydaniu 0.2.1; nie przenosić tego wyniku automatycznie na nowy APK.
- Refaktor granic: host JVM/Gradle i 9/9 bezpiecznych testów UI na S22+ dla zweryfikowanych PR #78–#86. Żadnych rozmów GSM w tych testach.
- Pełna mapa 34 Activity i ustawień: [UI](architecture/UI_SCREEN_MAP.md). Importy kontroluje `module_boundary_gate.py`.

## Otwarte

Pełne fizyczne EN GSM i outgoing na bieżącym APK; docelowy model podpisywania/IPC i kwalifikacja produkcyjna. MVP nie jest wydaniem produkcyjnym. Wydanie 0.2.2 nie udostępniało aktywnego hosta GSM w pakiecie produkcyjnym; poprawka 0.2.3 wymaga czystego parowania użytkownika.

**WSTRZYMANE:** Shizuku, GitHub Actions, schedulery, `WRITE_SECURE_SETTINGS`.

## Następny sprint

Nieplanowany. Projekt odłożony po publikacji MVP; nowe prace wymagają osobnej decyzji.
