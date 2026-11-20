# AI Calls — MVP / wydanie do testów

**Rollback GSM:** `s22-gsm-mvp-golden-2026-11-19` → `f0940d51`. Nie przesuwać ani nie usuwać tagu.

## Potwierdzone na S22+ (19.11.2026)
- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX, SAVE_NOTE i END_CALL CONFIRMED, 22 WAV.
- Outgoing: 7 tur / 2:37, 8 STT, 24 WAV, jedna próba bez retry, FINISHED/ANSWERED.
- Host Binder UID 2000 działa bez Wi-Fi/ADB wireless. WRITE_REVOKED podczas barge-in nie oznacza samoistnie utraty audio.
- Poprzednia instalacja: `2a617bb0`, QA/build PASS, host przywrócony w IDLE. To **nie** potwierdza instalacji nowszego UI.

## Kod zamknięty (20.11.2026)
- PR #37–49: Home, Incoming, Outgoing, zadania, historia i scenariusze; po merge.
- PR #50: AI Tools (END_CALL/SAVE_NOTE), zwijany Notebook, prostszy Preparation; merge `7afb3886`.
- Mac Local Agent, QA v2 dla `5e38431`: Python, preflight, AndroidTest **kompilacja**, pełny host QA i APK build PASS (exit 0). **Brak** dowodu uruchomienia instrumentacji UI lub testu GSM na tej wersji.

## P0 — bramka przed realnym użyciem
1. Na aktualnym `main`: czyste drzewo, `preflight`, `full`, `device-build`; zapis SHA, wyników i APK; przegląd debug/log/sekretów oraz brak nowych błędów.
2. Zostawić tylko aktywne gałęzie, `agent-control` i rollback; usuwać wyłącznie zweryfikowane, scalone gałęzie, nie dane użytkownika.
3. S22+ tylko IDLE: backup istniejącej APK, sprawdzenie ADB/hosta, instalacja `.s8lab`, kontrola SHA i rollbacku. **Bez próbnych połączeń w tej bramce.**
4. Smoke UI: Home, Incoming/Outgoing, Tasks, Calls/WAV, AI Tools, Notebook, Preparation, Skills, Vault, Settings; zapis, anulowanie, obrót i nawigacja. Instrumentacja na emulatorze nie zastępuje testu telefonu.

## OPEN — testy rzeczywiste (osobna zgoda)
Incoming/Outgoing 4–6 tur; polski STT/TTS i jakość dialogu, barge-in, stop/takeover, END_CALL po TTS, SAVE_NOTE, WAV, transkrypcje, historia i uprawnienia. Każdy problem z dowodem, bez nowej przebudowy UX bez potrzeby.

**Zakazy:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md) · [Handoff](operations/HANDOFF_NEXT_CHAT.md).
