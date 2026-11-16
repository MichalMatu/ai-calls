# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan

`main`: stabilne C1–C3/G6.1/EN E2E; GSM RX/TX wymaga Shizuku. `feature/s8-no-shizuku`: eksperyment bez merge; domyślny backend bez zmian.

## Odbiór S8

Mac: celowane testy i APK PASS. Samsung S22+ Android 16: syntetyczny RTP/PCMU 8 kHz ↔ aplikacyjny PCM 16 kHz ↔ istniejący most Realtime PCM 24 kHz: RX, TX i STOP **PASS**. Lokalny ruch wyłącznie `127.0.0.1`, bez Shizuku. ADB użyte tylko do QA. Oryginalny APK przywrócony, SHA-256 PASS. Bez realnego połączenia.

## Dalej

GSM RX/TX: blokada Android/OEM. Działający loopback to nie SIP: brak SIP/SDP, SRTP, autoryzacji i PSTN. Dla prawdziwego VoIP konieczny zabezpieczony transport, potem lokalne STT/LLM/TTS i test 4–6 tur. `APP_PRIVATE_LITERT` jest jawnie opcjonalne; odbiór modelu na S22+ otwarty. Realne połączenia tylko za osobną zgodą na numer/koszt/scenariusz; bez PII/audio w Git.
