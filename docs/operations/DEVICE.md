# Samsung S22 — hardware/USB i fizyczna kwalifikacja

**Urządzenie docelowe:** Samsung Galaxy S22+ `SM-S906B`, Android 16/API 36/One UI 8, Shizuku UserService/shell UID 2000. Sprawdzaj realne `ro.product.model`, identyfikator zgodny z prywatną konfiguracją hosta, rolę dialera, uprawnienia, bieżący Telecom IDLE oraz dostępność Shizuku. ADB IP:port nie dowodzi tożsamości urządzenia.

## Zamrożony transport
- Samsung RX `VOICE_DOWNLINK` i TX `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT` → `TELEPHONY_TX`, osobne sprawdzone kolejności kontekstu/atrybucji, TX `com.android.shell`.
- PCM mono 16-bit LE wewnętrznie, stereo duplication dopiero na Samsung TX, przekazywane PFD, nie per-frame Binder; jedna wspólna generacja RX+TX i selektywny cleanup.
- Watchdog `CallModeWatchdog`, heartbeat, endpoint-loss → cleanup obu stron, natychmiastowe abort/TAKE OVER, helper/app death fail-safe. Bazowa weryfikacja Phase 2D `59b0505537a53306acdab6a2a66ca6eed2b3f1c0` istnieje w Git; nie powtarzaj całej macierzy jeśli tych granic nie dotykasz.
- Wąskie wyjątki statycznego lint dla prywatnego API/shell nie są zgodą na poszerzanie suppressions.

## Bezpieczny flow fizyczny
1. Najpierw Mac unit/lint + anti-friction. Nie buduj APK przy każdej zmianie; `device-build` tylko tuż przed rzeczywiście zaplanowaną, autoryzowaną instalacją. Bez wymuszania wipe i bez zmiany profilu użytkownika.
2. Weryfikacja exact device + Shizuku/permission + stan **IDLE**, scope autoryzacji i czy badanie potrzebuje prawdziwej rozmowy. Nie wykonywać prawdziwych połączeń na podstawie ogólnego upoważnienia do programowania.
3. Testuj tylko nowy/zmieniony kontrakt: G6 INCOMING takeover/resume i local barge-in; G6 OUTGOING IVR/outcome po odrębnej zgodzie. Bez obcego call hangup, bez globalnych hacków i bez prób na nieustalonych numerach.
4. Receipts mogą przekazywać tylko SHA, statystyki bez PII, statusy i kod regresji. Prywatne WAV, transkrypcje i sprawy rozmówcy zostają na urządzeniu/Macu (0700/0600); eksport wyłącznie według polityki właściciela, bez GitHub/logcat.

## USB i diagnostyka
`scripts/bin/aicalls`, `scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py`, `ai_calls_usb_lab.py`, `export_private_call_evidence.sh` są istniejącym kanałem. Read-only ADB readiness nie daje prawa do dial, STOP ani rozszerzenia grantu. Raport `NO_DATA` oznacza brak dostępnych prywatnych danych, nie błąd modelu. [Safety](../architecture/SECURITY.md), [state](../PROJECT.md).
