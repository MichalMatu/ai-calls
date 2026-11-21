# AI Calls — wydanie MVP

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`).

## Stan MVP

Testowy pakiet `.s8lab` na S22+/Android 16: Binder UID 2000, GSM Incoming/Outgoing, lokalne STT/Gemma/TTS, notatki i END_CALL. APK release wymaga podpisu i obsługi Binder IPC.

**Dowody dla wcześniejszych wersji:** na `97d447db` przeszły preflight/full/device-build, 12 testów UI/Lab emulatora i 10/10 kontroli S22+ poza rozmową; Incoming odebrał i mówił. Na `ce5a4fdd` Outgoing wykonał 5 tur RX/TX, END_CALL i IDLE, ale powitanie było po EN. Na `b850b504` USB Lab potwierdził korektę koloru i odmowę SMS. Dokładne powtarzanie kodów pozostało otwarte.

`1c5a979`: dodano profile PL/EN, E2B/E4B i poprawki etykiet/README; fizyczny odbiór GSM **tej wersji** nie został udokumentowany.

## Następny sprint

1. Potwierdzić dostępność Local Agenta, czysty `main`, SHA i stan hosta.
2. Lab syntetyczny PL/EN, E2B/E4B: pamięć, kody/numer, pożegnanie, odmowy.
3. Na S22+ po 4–6 tur Incoming/Outgoing w PL/EN; sprawdzić barge-in, STOP/takeover, WAV, notatki, END_CALL, IDLE.
4. Po poprawkach powtórzyć QA, sprawdzić README/APK/rollback i podpisać protokół PASS/HOLD z SHA.

## Otwarte

Pełna kwalifikacja GSM na najnowszym SHA, podpis i IPC APK release. SIP/PJSUA2 po odbiorze GSM: prototyp w tagu `archive/sip-voip-s22-2026-11-21`. [Dalsze ścieżki](modules/CONVERSATION.md).

**Wstrzymane:** Shizuku, Actions, schedulery, `WRITE_SECURE_SETTINGS`.
