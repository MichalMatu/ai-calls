# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E; GSM RX/TX zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: bez merge, domyślny backend niezmieniony. Mac: testy celowane i APK PASS. S22+ Android 16: syntetyczny SDP ↔ RTP/PCMU 8 kHz ↔ PCM 16 kHz ↔ Realtime 24 kHz: RX/TX/STOP PASS; obcy adres SDP odrzucony. Tylko `127.0.0.1`, bez Shizuku; ADB wyłącznie do QA. Stabilny APK przywrócony, SHA-256 PASS. Bez rozmowy.

## Następny sprint — S8: bez Shizuku

Bezpieczne zewnętrzne SIP/SDP/SRTP, potem lokalne STT/LLM/TTS. `APP_PRIVATE_LITERT` jawnie opcjonalne; model na S22+ niezweryfikowany.

## Otwarte

GSM PCM: blokada Android/OEM. Lokalny SDP/RTP nie ma SIP, SRTP, uwierzytelnienia ani PSTN. Test 4–6 tur tylko po zgodzie na numer, koszty i scenariusz. Bez PII/audio w Git.
