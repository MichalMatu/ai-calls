# AI Calls — wydanie MVP

**Rollback** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`).

## Stan MVP

MVP `.s8lab` na S22+/Android 16 używa hosta Binder UID 2000. Obejmuje oba kierunki GSM, lokalne STT/model/TTS, notatki i END_CALL.

Na wcześniejszym kodzie potwierdzono fizycznie oba kierunki GSM, RX/TX, WAV, SAVE_NOTE i END_CALL.

Kod `97d447db` przeszedł `preflight`, `full`, `device-build` i 12 testów UI/Lab na emulatorze. [QA i SHA APK](https://github.com/MichalMatu/ai-calls/pull/66).

S22+ z `97d447db` miał zgodny podpis i SHA. Poza połączeniem przeszedł UI/Lab 10/10, dwie tury modelu, STOP i reset.

W krótkiej próbie Incoming AI odebrało i mówiło.

Próba transportu Outgoing na `97d447db` potwierdziła ACTIVE, rozłączenie tego samego Call i powrót do IDLE.

Outgoing na `ce5a4fdd` potwierdził 5 tur z RX/TX, dane po korekcie, wysłanie END_CALL i IDLE. Powitanie było po angielsku.

Na `ce5a4fdd` host wrócił po restarcie aplikacji w 1,5 s. Na `b850b504` USB Lab potwierdził korektę koloru i odmowę wysłania SMS-a. Dokładne powtarzanie kodów pozostaje otwarte.

## Następny sprint

1. Pełne próby GSM i odbiór odpowiedzi w [Test Lab](modules/CONVERSATION.md).
2. Tag golden po pełnym odbiorze.

## Otwarte

- Incoming 4–6 tur i powtórka powitania Outgoing. Sprawdź barge-in, STOP/takeover, notatki i WAV.
- APK release zbudowane. Do wydania potrzebuje podpisu i obsługi IPC Binder.

## Po odbiorze GSM

Prototyp SIP/PJSUA2 i skrypty są pod tagiem `archive/sip-voip-s22-2026-11-21`. Następna bramka to konto testowe, rozmowa TLS/SRTP z PCM w obie strony i STOP. Przed dystrybucją sprawdź licencję. [Kod i dowody](https://github.com/MichalMatu/ai-calls/tree/archive/sip-voip-s22-2026-11-21/prototypes/s8-pjsua2).

[Dalsze ścieżki](modules/CONVERSATION.md).

**WSTRZYMANE:** Shizuku, Actions, schedulery i `WRITE_SECURE_SETTINGS`.
