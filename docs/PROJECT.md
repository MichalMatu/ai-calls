# AI Calls — wydanie MVP

**Rollback** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`).

## Stan MVP

Kandydat do prób użytkownika to APK `.s8lab` na S22+ z Androidem 16. Binder host używa UID 2000. Zakres obejmuje Incoming i Outgoing, lokalne STT/model/TTS, notatki i END_CALL.

Na wcześniejszym kodzie odbiór fizyczny potwierdził oba kierunki GSM, RX/TX, WAV, SAVE_NOTE i END_CALL. Osobno na `b2d0b0b8` potwierdzono 9 testów UI i zapisu Lab oraz lokalne dialogi bez połączenia. Te wyniki są przypisane do sprawdzonych wtedy wersji.

## Następny sprint

1. [QA](operations/DEVELOPMENT.md) na kodzie kandydata i budowa APK `.s8lab`.
2. Odbiór odpowiedzi w [Test Lab](modules/CONVERSATION.md) i próby GSM przez użytkownika.
3. Aktualizacja golden po odbiorze wersji z jej SHA.

## Otwarte

- Instalacja kandydata i kontrola hosta po restarcie aplikacji na S22+.
- Incoming i Outgoing po 4–6 tur. Oceń polski STT/TTS, naturalność, pamięć i korekty, barge-in, STOP/takeover, END_CALL po TTS, zapis notatki, transkrypcję i fragmenty WAV.
- Osobna kwalifikacja standardowego APK release pod kątem podpisu i IPC.

## Po odbiorze GSM

Zachowany prototyp SIP/PJSUA2 ma historyczne testy JNI i syntetycznego audio. Następna bramka to konto testowe, jedna rozmowa TLS/SRTP z PCM w obie strony oraz STOP. Przed dystrybucją sprawdź licencję. [Kod i dowody prototypu](https://github.com/MichalMatu/ai-calls/tree/dffb072036ccc839566306ce9df1dde173bcc6b5/prototypes/s8-pjsua2).

**WSTRZYMANE:** Shizuku, Actions, schedulery i `WRITE_SECURE_SETTINGS`.
