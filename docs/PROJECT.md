# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1; GSM PCM bez Shizuku blokuje Android/OEM.

## Podział pracy

S8: `feature/s8-no-shizuku`, bez merge. Oddzielny ARM64 APK `pl.michalmatu.aicallbridge.s8lab`; S22+ PHYSICAL PASS: localhost RTP, 6 faz, 72 RX/72 TX, SDP/STOP. Stabilna aplikacja bez zmian (SHA PASS).

Nowy tryb ręczny: syntetyczne TTS → rzeczywiste Android STT → zatwierdzenie odpowiedzi → TTS, bez GSM i LLM. STOP i uprawnienia na żądanie; test Mac/APK PASS, S22 PHYSICAL_OPEN: raport niekompletny, przywrócono poprzedni APK S8 (SHA PASS), później ADB niedostępne. `S8VoipLocalSpeechSession`: TTS wymaga aprobaty tej samej tury, cleanup STT/TTS na wątku właściciela (unit PASS).

## Następny sprint — S8: bez Shizuku

Potwierdzić STT/TTS fizycznie; model LiteRT w prywatnym sandboxie S8, potem SIP INVITE/SDP/SRTP i 4–6 prawdziwych tur VoIP.

## Otwarte

Brak realnego SIP operatora, SRTP, LLM i rozmowy. REGISTER tylko ręczny na koncie testowym, może zmienić Contact. Realny call wyłącznie po zgodzie na numer/koszt/scenariusz. Bez PII/audio w Git.
