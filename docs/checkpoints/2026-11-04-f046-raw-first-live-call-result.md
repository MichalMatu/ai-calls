# F-046 — pierwszy fizyczny test Gemma Direct RAW, 2026-11-04

**Wynik: częściowy sukces.** Po wyłączeniu parsera JSON model rzeczywiście wygenerował DWIE kolejne własne wypowiedzi, obie wypuszczone do TTS/TX. Stary problem `DIRECT_REJECTED` w turze 2 **nie wystąpił**. To jeszcze nie jest dowód stabilności długiej rozmowy ani wykonania CLIR.

## Dowody bez tekstu prywatnego

- Exact S22, po teście `IDLE` dwa odczyty. Brak wywołania nowej rozmowy przez agenta. Lokalny eksport prywatny `USB_EXPORT=PASS`, 23 sesje, **jedna nowa** od poprzedniej analizy. Nowa sesja: `2026-11-04 03:46:12 Europe/Warsaw`, `COMPLETE=true`, koniec `owned_call_ended`.
- Prywatne archiwum wyłącznie Mac: `~/Documents/AI-Calls/private-exports/call-evidence-20261104T025141Z-GNTRwmH6.tar.gz` (0600, 30 647 529 bajtów). Prywatny podgląd offline: `~/Documents/AI-Calls/private-exports/review-20261104T025141Z-GNTRwmH6/index.html`. Pełny tekst/PCM nie jest wypychany do GitHub ani wyniku zadań.
- Events: `COUNTERPART_STT=2`, `AGENT_APPROVED_TEXT=2`, `AGENT_DECISION=2`, `AGENT_ACK_TX=2`, `AGENT_FULL_TX=2`, `RX_PCM=3`, `TX_ACK_PCM=2`, `TX_PCM=2`, `END=1`.
- **T1:** biuro/kanał RX STT: 17 znaków / 3 słowa; Gemma: `SPEAK/GEMMA_DIRECT_RAW`, 4143 ms, 120 znaków / 17 słów, dotyczy CLIR/prezentacji numeru, pytanie; `WRITE_COMPLETE`, 261930 bajtów TX. WAV RX 3,54 s; ACK 1,512 s; TX 8,185 s.
- **T2:** biuro/kanał RX STT: 73 znaki / 11 słów (zawiera zaprzeczenie „nie”, ale nie pozwala samo na wniosek o odmowie), Gemma: `SPEAK/GEMMA_DIRECT_RAW`, 2544 ms, 153 znaki / 23 słowa, dotyczy zastrzegania prezentacji numeru/CLIR, pytanie; `WRITE_COMPLETE`, 316494 bajtów TX. WAV RX 9,56 s; ACK 1,512 s; TX 9,890 s.
- **T3:** RX WAV 17,08 s z sygnałem o niezerowej amplitudzie, ale **brak `COUNTERPART_STT` i trzeciej odpowiedzi modelu**; rozmowa zakończyła się `owned_call_ended`.
- Wszystkie 7 WAV poprawne, amplituda niezerowa: RX RMS ok. 2279/2729/2680, TX RMS ok. 3695/3724. To potwierdza sygnał w lokalnych plikach, nie odsłuch przez odbiorcę.
- Zapowiedź `AGENT_ACK_TX` jest gotową wypowiedzią aplikacji, a **nie Gemmą**. Nie ustalono, czy odbiorca to człowiek czy IVR. Wypowiedź biura z trzeciego okna nie ma rozpoznanego tekstu; nie udawać wiedzy o jej znaczeniu bez prywatnego odsłuchu RX.

## Decyzja

- **F-046: poprawa potwierdzona dla pierwszych 2 tur live; nadal OPEN dla trwałego dialogu**, ponieważ pojawiło się trzecie okno RX bez STT i nastąpiło rozłączenie. Nie mylić `WRITE_COMPLETE` z far-end hearing.
- Następne: lokalna diagnostyka końcowego RX→STT / powodu zakończenia, niezależna ocena brzmienia i treści biura w prywatnym podglądzie. Nie ponawiać autonomicznie połączenia, nie uogólniać TaskBrief przed stabilizacją; semantyczny hardening F-045 nadal otwarty.
