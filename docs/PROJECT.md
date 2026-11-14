# AI Calls — MVP

**Cel:** bezpieczne INCOMING/OUTGOING na S22: SIM/Call → RX PCM → rozpoznawanie → Gemma → TTS → TX PCM → STOP, z prywatnym WAV i diagnostyką.

## Następny sprint
- Porównaj wg [macierzy testów](../benchmarks/audio/AUDIO_ROUTE_QUALITY.md) Android STT, Parakeet PL i Gemma Audio Direct z Android TTS PL/Piper; gotowość, prewarm, WER, RX→model→TTS→TX, opóźnienia, echo, barge-in, STOP i błędy.
- S22 14.11.2026: Android STT/TTS, WAV, END_CALL potwierdzone. Gemma Audio Direct MVP: ASR→JSON 3/3 syntetyczne próbki i jedna rozmowa. Otwarte: P1 na telefonie, długa mowa, opóźnienia, barge-in, STOP, powtarzalne END_CALL i polski głos Piper.
- Bez strojenia dialogu i realnych rozmów bez zgody. WAV i transkrypty poza Git.

## PÓŹNIEJ — JAKOŚĆ DIALOGU
Pamięć/korekty, brak powtarzania pytań, naturalne odpowiedzi, godziny, powitanie/pożegnanie i wieloturowość. Dopiero po stabilizacji I/O.

## Otwarte
- **G6.1 — kampanie wieloodbiorcowe**, alarm/BOOT/retry, takeover/resume i playback: **PHYSICAL_OPEN**.
- Wizyta: brak potwierdzonej rezerwacji.
