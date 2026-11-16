# AI Calls — MVP

**Cel:** S22: Call → PCM → STT → LLM → TTS → PCM → STOP.
**Rollback:** `mvp-s22-2026-11-15`, APK 0.2.0; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

Granite 4.0 H 1B i Test Lab działają off-call według użytkownika. S2–S7: 60/60 torów, 18/18 tur i 5/5 STOP na wcześniejszych SHA. GSM RX/TX nadal wymaga Shizuku; realny Call niezaliczony.

## Podział pracy

`main`: C1–C3/G6.1/EN E2E. `feature/s8-no-shizuku`: eksperyment, bez merge ani naruszania stabilnego APK. Domyślny backend pozostaje Shizuku.

## Następny sprint — S8: bez Shizuku

`CallMediaSessionRuntime` wybiera wymienny backend. `ANDROID_PUBLIC_API_PROBE` bezpiecznie odmawia GSM PCM; `ROLE_DIALER` i `RECORD_AUDIO` nie dają RX/TX. `VOICE_DOWNLINK` wymaga uprzywilejowanego `CAPTURE_AUDIO_OUTPUT`; publiczne API nie wstrzykuje PCM do GSM. OEM: BLOCKED bez potwierdzonego API. Alternatywa: app-owned VoIP/SIP i przekierowanie z osobną zgodą/kosztem. `APP_PRIVATE_LITERT` daje jawny lokalny LLM bez Shizuku (inny model niż Qwen), wymaga testów na S22.

## Otwarte

Exact Call, RX/TX, STOP, barge-in, 4–6 tur, END_CALL i reboot: PHYSICAL_OPEN bez fizycznych prób. Przed instalacją/realnym Call sprawdź IDLE, wersję, zgody i rollback. Bez PII/WAV w Git.
