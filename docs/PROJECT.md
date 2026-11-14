# AI Calls — MVP

**Cel:** bezpieczne INCOMING/OUTGOING na S22: SIM/Call → RX PCM → rozpoznawanie → Gemma → TTS → TX PCM → STOP, z prywatnym WAV i diagnostyką.

## Następny sprint
- Weryfikuj niezależnie wejścia Android STT (bazowe), Parakeet PL i Gemma Audio Direct (eksperymentalne); wyjścia Android TTS PL i Piper.
- Ustal gotowość, prewarm, rozpoznanie, format odpowiedzi audio, czasy RX→STT→model→TTS→TX, opóźnienia pierwszej/kolejnych tur, echo, barge-in, STOP i odzyskiwanie po błędzie.
- Mierz na syntetycznych próbkach i kontrolowanych połączeniach. S22: AI/Android STT/TTS, WAV i END_CALL potwierdzone 14.11.2026. Gemma Audio Direct: tor dwustopniowy ASR → JSON (3/3 próbki) i pojedyncze rzeczywiste połączenie przychodzące działają jako **MVP**, nie jako pełna kwalifikacja produkcyjna. Otwarte: regresja na urządzeniu po P1, długa mowa, opóźnienia, barge-in, STOP i powtarzalne END_CALL. Piper zainstalowany, polski głos niepotwierdzony.
- Bez strojenia promptów, powitania, stylu ani zawartości odpowiedzi. Bez zmian realnych połączeń bez zgody; nie zapisuj WAV/transkryptów w Git.

## PÓŹNIEJ — osobny duży sprint JAKOŚĆ DIALOGU
Pamięć/korekty, niepowtarzanie pytań, naturalne odpowiedzi, formy grzecznościowe, godziny, przywitanie, pożegnanie oraz rozmowy wieloturowe. Oceniać dopiero na stabilnym torze I/O.

## Otwarte
- **G6.1 — kampanie wieloodbiorcowe**, alarm/BOOT/retry, takeover/resume i playback: **PHYSICAL_OPEN**.
- Wizyta: brak potwierdzonej rezerwacji.
