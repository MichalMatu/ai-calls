# AI Calls — wydanie MVP

**Rollback** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`).

## Stan MVP

Kandydat do prób użytkownika to APK `.s8lab` na S22+ z Androidem 16. Binder host używa UID 2000. Zakres obejmuje Incoming i Outgoing, lokalne STT/model/TTS, notatki i END_CALL.

Wcześniejszy odbiór fizyczny potwierdził oba kierunki GSM, RX/TX, WAV, SAVE_NOTE i END_CALL. Wynik dotyczy wcześniejszego kodu.

Kod kandydata `97d447db` przeszedł `preflight`, `full` i `device-build`. Wyniki obejmują 1380 testów JVM, 486 Python oraz 12 testów UI i Lab na emulatorze. [PR i SHA APK](https://github.com/MichalMatu/ai-calls/pull/66).

S22+ z `97d447db`: podpis i SHA zgodne, UI/Lab 10/10 bez połączeń. Lokalny model przeszedł dwie tury, STOP i reset. Po restarcie procesu przez instrumentację przywrócono rejestrację hosta i zweryfikowano Binder UID 2000.

W szybkiej próbie użytkownika AI odebrało połączenie przychodzące i mówiło.

## Następny sprint

1. Pełne próby GSM i odbiór odpowiedzi w [Test Lab](modules/CONVERSATION.md).
2. Tag golden po pełnym odbiorze.

## Otwarte

- Automatyczne odzyskiwanie hosta po restarcie procesu.
- Incoming i Outgoing po 4–6 tur. Oceń polski STT/TTS, naturalność, pamięć i korekty, barge-in, STOP/takeover, END_CALL po TTS, zapis notatki, transkrypcję i fragmenty WAV.
- Osobna kwalifikacja standardowego APK release pod kątem podpisu i IPC.

## Po odbiorze GSM

Prototyp SIP/PJSUA2 i skrypty są pod tagiem `archive/sip-voip-s22-2026-11-21`. Następna bramka to konto testowe, rozmowa TLS/SRTP z PCM w obie strony i STOP. Przed dystrybucją sprawdź licencję. [Kod i dowody](https://github.com/MichalMatu/ai-calls/tree/archive/sip-voip-s22-2026-11-21/prototypes/s8-pjsua2).

Kolejne ścieżki mają punkty startowe w [Rozmowa i Test Lab](modules/CONVERSATION.md).

**WSTRZYMANE:** Shizuku, Actions, schedulery i `WRITE_SECURE_SETTINGS`.
