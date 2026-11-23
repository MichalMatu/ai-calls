# AI Calls — handoff po scaleniu Front/Back PR #77

23.11.2026: `main@8c4517dc`, PR #77 scalony. Kod `edb32655` przeszedł 528 testów Python, JVM/app/lint, budowę APK i 9/9 bezpiecznych testów S22+ (`.s8lab`). Po instalacji przywrócono host Binder. Telefon IDLE, dane zachowane, bez rozmów GSM. Raporty: `agent-control/.agent/results/aicalls-frontback-s22-*-20261123-v1.json`.

## Konfiguracja i recovery S22+ (tylko jeśli wymaga ponowienia)

Flow instalacji/QA został wykonany. Poniższa procedura dotyczy wyłącznie późniejszego przywracania hosta lub sprawdzania ustawień.

1. Pobrać APK z README; sprawdzić instalację APK, rolę Telefon i uprawnienia: mikrofon, telefon/SIM, powiadomienia.
2. **Ustawienia → GSM audio → Zarządzaj parowaniem**; włączyć debugowanie bezprzewodowe i parowanie kodem.
3. Wysunąć szufladę powiadomień; wpisać kod w odpowiedzi na powiadomienie AI Calls (RemoteInput). Przejść też ręczne pole kodu; nigdy nie zapisywać kodu w logach.
4. Ręcznie uruchomić host GSM; sprawdzić Binder, IDLE, STOP/takeover i sytuację po reboocie.
5. Screenshoty/pixel diff pozostają osobnym QA wizualnym; nie deklarować ich bez nowych dowodów.

Bez automatycznych rozmów i schedulerów. [Stan](../PROJECT.md).

## Kolejny obszar kodu

Oddzielić cykl życia TTS/probe i duże runtime/Lab od widoków z testami charakteryzacyjnymi. Nie zmieniać funkcjonalności ani UI; bieżące QA na poprzednim SHA nie weryfikuje przyszłych zmian.
