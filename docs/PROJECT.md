# AI Calls — MVP

**Cel:** S22: Call → PCM → STT → LLM → TTS → PCM → STOP/END_CALL.
**Powrót:** tag `mvp-s22-2026-11-15`, [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md). APK `0.2.0`; działający kod `956b078`.

## Stan projektu

Model Granite 4.0 H 1B i Test Lab: działa wg użytkownika (off-call). Historia: tap → szczegóły, przytrzymanie → potwierdzenie usunięcia; odbiór na S22. S2–S7: 60/60 torów, 18/18 tur, 5/5 STOP na wcześniejszych SHA. GSM PCM RX/TX nadal wymaga Shizuku; realny Call niezakwalifikowany.

## Podział pracy

`main`: C1–C3, G6.1, EN E2E; stabilny punkt powrotu `mvp-s22-2026-11-15` bez zmian. `feature/s8-no-shizuku`: izolowany S8, start z aktualnego `main`. Bez automatycznego merge do `main`, instalacji APK S8 na S22 ani naruszania działającego Shizuku; integracja dopiero po testach i odbiorze.

## Następny sprint — S8: bez Shizuku

[S8: implementacja i blokady](architecture/S8_NO_SHIZUKU.md).


**Osobny sprint** (nie łączyć z C1–C3). Sprawdzić uprawnienia Android 16/S22: rola dialera i zwykłe granty vs. uprzywilejowane `VOICE_DOWNLINK` RX i `CALL_ASSISTANT` TX. Wydzielić backend audio, zachować fallback Shizuku. Porównać direct GSM (OEM/system) i VoIP/SIP z przekierowaniem. Wykazać RX, TX, STOP, bezpieczeństwo exact Call; brak wspieranego API = dokumentacja blokady, nie PASS.

## Otwarte

C1–C3: prawdziwe 4–6 tur, barge-in, END_CALL, odsłuch. G6.1 BOOT/retry/takeover: **PHYSICAL_OPEN**. Realny Call wyłącznie po zgodzie i IDLE. Bez nowych schedulerów, masowego dial i WAV/PII w Git.
