# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E. S2–S7: wcześniejsze odbiory 60/60 torów, 18/18 tur i 5/5 STOP. Stabilny GSM RX/TX nadal zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: bez merge do `main`, zachowany domyślny backend Shizuku. S8: kompilacja i testy PASS; off-call uruchomienie oraz diagnostyka na S22+ PASS. Potwierdzony rollback do oryginalnego APK; nie wykonano rozmowy.

## Następny sprint — S8: bez Shizuku

`CallMediaSessionRuntime`: wymienne backendy. `ANDROID_PUBLIC_API_PROBE` odmawia GSM PCM. `ROLE_DIALER`/`RECORD_AUDIO` nie zapewniają RX/TX; `VOICE_DOWNLINK` wymaga uprzywilejowanego `CAPTURE_AUDIO_OUTPUT`; brak publicznego GSM TX. `AppOwnedVoipCallMediaSessionBackend`: exact-call, STOP i race testy PASS, ale brak transportu SIP/RTP. `APP_PRIVATE_LITERT`: jawny lokalny LLM bez Shizuku; odbiór fizycznego modelu pozostaje otwarty.

## Otwarte

GSM: blokada systemowa/OEM bez potwierdzonego API. Realny Call, RX/TX/STOP, 4–6 tur, END_CALL i reboot: PHYSICAL_OPEN. Rzeczywiste rozmowy wymagają zgody na numer, koszt i scenariusz. Bez PII/audio w Git.
