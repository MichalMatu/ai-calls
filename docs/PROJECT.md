# AI Calls — MVP

**Cel:** S22: Call → PCM → STT → LLM → TTS → PCM → STOP/END_CALL.
**Powrót:** tag `mvp-s22-2026-11-15`, [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md). APK `0.2.0`; działający kod `956b078`.

## Stan projektu

Model Granite 4.0 H 1B i Test Lab: działa wg użytkownika (off-call). Historia: tap → szczegóły, przytrzymanie → potwierdzenie usunięcia; odbiór na S22. S2–S7: 60/60 torów, 18/18 tur, 5/5 STOP na wcześniejszych SHA. GSM PCM RX/TX nadal wymaga Shizuku; realny Call niezakwalifikowany.

## Podział pracy

`main`: C1–C3/G6.1/EN E2E; punkt powrotu `mvp-s22-2026-11-15`. `feature/s8-no-shizuku`: izolowany S8 od `main`; bez auto-merge, instalacji S8 na S22 ani zmian Shizuku. Integracja po odbiorze.

## Następny sprint — S8: bez Shizuku

**Oddzielnie od C1–C3.** Android 16/S22: porównać dialer/grant z `VOICE_DOWNLINK` RX i `CALL_ASSISTANT` TX; wydzielić backend z fallbackiem Shizuku. Porównać OEM GSM oraz VoIP/SIP. PASS dopiero po RX/TX/STOP/exact Call; brak wspieranego API = blokada.

## Otwarte

C1–C3: prawdziwe 4–6 tur, barge-in, END_CALL, odsłuch. G6.1 BOOT/retry/takeover: **PHYSICAL_OPEN**. Realny Call wyłącznie po zgodzie i IDLE. Bez nowych schedulerów, masowego dial i WAV/PII w Git.
