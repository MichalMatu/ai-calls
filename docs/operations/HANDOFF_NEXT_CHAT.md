# AI Calls — handoff separacji warstw (23.11.2026)

Aktualny potwierdzony `main@3d138ab8`: PR #79 (fabryka STT/TTS runtime), #80 (czysta polityka startu Lab), #81 (bramki kierunku importów) i #83 (native probe modeli w Data) scalone. Wcześniejszy #77 zachował 34 ekrany i zachowanie UI. Bez redesignu, bez prawdziwych rozmów GSM.

**Dowody:** `agent-control/.agent/results/aicalls-*-20261123-*.json`. Każdy z #79/#80/#81/#83: zielony Mac build/test i S22+ 9/9. Po #79–81 połączone QA `main@3f6351a4`: 534 Python, build APK i 9/9 UI PASS; Binder przywrócony, telefon IDLE, dane zachowane. Nie ekstrapolować wyników na kolejne SHA.

## Konfiguracja i recovery S22+ (tylko jeśli wymaga ponowienia)

Flow instalacji/QA został wykonany. Poniższa procedura dotyczy wyłącznie późniejszego przywracania hosta lub sprawdzania ustawień.

1. Pobrać APK z README; sprawdzić instalację APK, rolę Telefon i uprawnienia: mikrofon, telefon/SIM, powiadomienia.
2. **Ustawienia → GSM audio → Zarządzaj parowaniem**; włączyć debugowanie bezprzewodowe i parowanie kodem.
3. Wysunąć szufladę powiadomień; wpisać kod w odpowiedzi na powiadomienie AI Calls (RemoteInput). Przejść też ręczne pole kodu; nigdy nie zapisywać kodu w logach.
4. Ręcznie uruchomić host GSM; sprawdzić Binder, IDLE, STOP/takeover i sytuację po reboocie.
5. Screenshoty/pixel diff pozostają osobnym QA wizualnym; nie deklarować ich bez nowych dowodów.

Bez automatycznych rozmów i schedulerów. [Stan](../PROJECT.md).

## Otwarte bramki

PR #78 (natywny cykl TTS), #82 (lista zadań), #84 (tworzenie zadania), #85 (walidacja scenariusza), #86 (diagnostyka zakończenia runtime) pozostają do potwierdzenia dokładnego SHA, S22+ i scalania. Mapa 34 Activity jest audytem źródeł, nie dowodem pełnego wizualnego QA. Przed pracą sprawdzić obecny HEAD, wyniki Local Agenta i PR-y; nie powtarzać zakończonego QA. Bez Codex, Work, GitHub Actions i połączeń GSM.
