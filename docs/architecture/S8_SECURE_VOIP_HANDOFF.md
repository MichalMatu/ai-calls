# S8 — VoIP no-Shizuku: stan i bramki do testów

**Gałąź:** `feature/s8-no-shizuku`; bez merge do `main`. Stabilny checkpoint: `mvp-s22-2026-11-15`.
**Cel:** samodzielne lokalne STT → Gemma E2B → zatwierdzona odpowiedź → TTS → szyfrowane media własnego połączenia VoIP. Żaden mechanizm nie dostarcza PCM rozmowy GSM przez zwykłe uprawnienia Androida.

## Co rzeczywiście istnieje

- `S8VoipLocalSpeechSession`: 1–6 półdupleksowych tur, STOP najpierw odwołuje media danej generacji, ochrona przed spóźnionymi callbackami i odpowiedzią bez zatwierdzenia. Testy STOP w fazach LISTENING, PROCESSING i SPEAKING.
- `AppOwnedLoopbackRtpMediaPort` + `RtpPcm16kAdapterMediaPort`: **wyłącznie localhost**, PCMU/RTP 8 kHz ↔ PCM16LE 16 kHz. Walidacja pełnych 20-ms pakietów, ignorowanie powtórek/opóźnionych pakietów, błąd po 6 s bez RX. Brak jitter buffera; pojedyncze luki do 2 pakietów (40 ms) są dopełniane ciszą, a większe skoki sekwencji nie powodują dużych alokacji.
- `S8NativeVoipTwoTurnSmokeActivity`: uruchamiany jawnie test **2–6 tur syntetycznego głosu** przez rzeczywiste lokalne STT, Gemma, approval, TTS i lokalny RTP RX/TX. Model nie jest atrapą, ale **nie jest to rozmowa z zewnętrznym rozmówcą**. Opcjonalnie `loss_every_n_packets` (0 lub 20–100), raport wyłącznie liczników, opóźnień i pamięci; bez audio i transkryptu. STOP przez ekran lub opuszczenie testu.
- `S8VoipLabActivity`: wybór testu 2/4/6 tur w normalnej aplikacji (bez ADB podczas użytkowania). Odrębny debug APK `pl.michalmatu.aicallbridge.s8lab`; `main` i pakiet stabilny nietknięte.
- `S8SipsTlsRegisterClient`: tylko ręczny REGISTER przez zweryfikowany TLS z SHA-256 Digest. Rejestracja **nie oznacza połączenia**.
- `S8SecureSipDialogGate`: testowana bramka stanów RINGING → NEGOTIATING → ACTIVE wyłącznie po dowodzie TLS i uwierzytelnionego SRTP; STOP, reject INVITE, zdalny CANCEL przed odebraniem oraz BYE/ACK po rozpoczęciu dialogu. To **kontrakt adaptera natywnego**, nie parser, klient, biblioteka SRTP ani działające połączenie.

## Wyniki i blokady — 17 listopada 2026

- Mac Local Agent: preflight, testy celowane S8, kompilacja APK, scenariusze STOP, walidacja RTP i utracone/opóźnione pakiety — **PASS**. Dowody w `MichalMatu/local-agent` / `agent-control` / `task-results`.
- **PHYSICAL PASS — 2/2 rzeczywiste lokalne tury AI na S22+** (syntetyczny głos przez prawdziwy RTP/Android STT/Gemma E2B/approval/TTS/RTP), SHA APK, telefon IDLE, stabilny APK bez zmian: 393 RX, 205 TX, 2 transkrypcje, 2 zatwierdzenia, 26 094 ms łącznie, najdłuższa odpowiedź 12 843 ms, 0 straconych pakietów w tym przebiegu; próbka max heap Java 32 957 328 B, native heap 326 706 272 B (to **nie pełne RSS/PSS**). Brak prawdziwego połączenia telefonicznego. Dowód: task result `aicalls-s8-6bfbd94-s22-native-2turn-rtp-worker-20261117-v1`.
- Poprzedni test na urządzeniu ujawnił brak wpisu raportu w whitelist (`PrivateDiagnosticReportStore`) i rozpoczęcie inicjalizacji socketu RTP z wątku głównego Androida. Po celowanych poprawkach raport i start sesji ACTIVE przeszły; weryfikacja rollbacku po nieudanych próbach: SHA PASS. **4/6 fizycznych tur pozostaje OPEN**, podobnie jak fizyczne testy STOP/5% strat i długie próby. Nowe rozbicie czasu na capture, STT+model i TTS dodano do raportu, lecz wymaga oddzielnego pomiaru na telefonie.
- Host Mac: Android NDK **brak**, SWIG **brak**, źródła PJSIP **brak**; CMake i make dostępne. Bez nich nie ma zweryfikowanej kompilacji natywnego SIP/SRTP.
- Brak konta testowego i niezależnej zgody na konkretny numer/koszty. Bez tej zgody nie wykonywać połączeń.

## Docelowy adapter SIP/SRTP

Wybrany kierunek techniczny: **PJSIP/PJSUA2 2.17+**, warunkowo po przeglądzie licencji i zależności. PJSUA2 obsługuje SIP TLS i SRTP, a `AudioMediaPort.onFrameReceived/onFrameRequested` może spiąć PCM własnego VoIP z istniejącą sesją STT/TTS.

1. Zweryfikowane TLS (certyfikat/host), REGISTER i obsługa przychodzących INVITE; dokładne powiązanie Call-ID, dialogu i identity. Brak nieautoryzowanych automatycznych połączeń.
2. SDP offer/answer i ICE/NAT według możliwości dostawcy. **Wymagać SRTP (SDES tylko przez zweryfikowane TLS albo DTLS-SRTP)**; żadnego fallback na RTP/AVP poza localhost; brak kluczy, PII lub PCM w logach.
3. Po pozytywnym uwierzytelnieniu SRTP adapter `MediaPort` udostępnia PCM16 i sygnalizuje `S8SecureSipDialogGate.onAuthenticatedSrtp`; sesja STT/TTS nie może rozpocząć TX wcześniej.
4. Dla przychodzącej rozmowy: ringing → jawne przyjęcie/tryb asystenta → media → BYE, CANCEL, timeout, STOP, śmierć procesu, utrata TLS lub kluczy; każde zakończenie najpierw odwołuje media lokalne, potem sygnalizację.
5. Dopiero po testach offline z atrapą SIP, lokalnym serwerem TLS/SRTP i emulatorze: test rzeczywistego własnego konta/połączenia po osobnej zgodzie.

**Wymagania narzędziowe:** Android NDK, SWIG, źródła PJSIP, zależności TLS. Sprawdzić zgodność strony prawnej przed dołączeniem natywnego artefaktu: PJSIP jest udostępniany na GPL-2.0-or-later lub odrębnej licencji komercyjnej. Nie pobierać przypadkowych prebuilt `.so` i nie pisać własnego SRTP.

Źródła:
- https://docs.pjsip.org/en/2.17/get-started/android/
- https://docs.pjsip.org/en/latest/get-started/android/sip-sdk-features.html
- https://docs.pjsip.org/en/latest/specific-guides/audio/audio_frame_manipulation.html
- https://docs.pjsip.org/en/latest/specific-guides/security/srtp.html
- https://www.pjsip.org/licensing.htm

## Bramki akceptacyjne

1. S22+ ponownie widoczny, stan IDLE i SHA stabilnego APK — **PASS 2026-11-17**; przed każdą nową instalacją ponowić kontrolę IDLE/SHA i utworzyć jej osobną kopię rollback, bez czyszczenia danych.
2. Fizyczne **2 tury PASS**, **4/6 OPEN** z prawdziwym lokalnym modelem i RTP; osobne wyniki, nie powtarzać wcześniej zaliczonych testów bez powodu.
3. STOP podczas RX/modelu/TTS (oraz zamykanie aplikacji), czasy reakcji, max Java/native memory, utrata 5% i nieuporządkowane RTP; bez transkryptów w raporcie.
4. Po integracji natywnej: testy TLS/SDP/SRTP i sieci, SIP INVITE/ACK/BYE/CANCEL; UI przyjęcia/odrzucenia i STOP. Brak połączeń rzeczywistych bez osobnej autoryzacji.
