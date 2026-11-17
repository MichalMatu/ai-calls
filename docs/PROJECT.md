# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1, bez zmian. GSM PCM bez Shizuku blokuje Android/OEM.

## Podział pracy

S8 `feature/s8-no-shizuku`, osobny ARM64 APK `pl.michalmatu.aicallbridge.s8lab`. **S22+ PHYSICAL PASS 2026-11-17:** RTP/SDP/STOP 6 faz, 72 RX/72 TX; polskie STT → aprobata odpowiedzi → lokalne TTS PCM. Po aktualizacji RTP 72/72 ponownie PASS, stabilny APK SHA bez zmian, backup S8 zachowany. Żadnych realnych Calls.

Nowy ręczny test czterech tur LiteRT/STOP i prywatny raport: Mac unit/APK PASS; S22 brak modelu → bezpieczne 0 tur i brak inferencji (PASS). Emulator: 2 testy UI OK, sprzątnięty proces. S8 nie ma pliku E2B, na Macu brak zweryfikowanej kopii; pobieranie 2,6 GB nieuruchomione.

## Następny sprint — S8: bez Shizuku

Dostarczyć zweryfikowany E2B do sandboxu S8; 4–6 lokalnych tur STT→LLM→TTS; następnie SIP INVITE/SDP/SRTP.

## Otwarte

Brak działającej rozmowy VoIP, GSM PCM, SRTP i inferencji LLM. REGISTER wyłącznie na koncie testowym; realny Call tylko po zgodzie na numer/koszt. Bez PII/audio w Git.
