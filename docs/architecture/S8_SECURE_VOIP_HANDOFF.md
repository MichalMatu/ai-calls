# S8 — VoIP no-Shizuku: stan i bramki do testów

**Gałąź:** `feature/s8-no-shizuku`; bez merge do `main`. Stabilny checkpoint: `mvp-s22-2026-11-15`.
**Cel:** samodzielne lokalne STT → Gemma E2B → zatwierdzona odpowiedź → TTS → szyfrowane media własnego połączenia VoIP. Żaden mechanizm nie dostarcza PCM rozmowy GSM przez zwykłe uprawnienia Androida.

## Co rzeczywiście istnieje

- `S8VoipLocalSpeechSession`: 1–6 półdupleksowych tur, STOP najpierw odwołuje media danej generacji, ochrona przed spóźnionymi callbackami i odpowiedzią bez zatwierdzenia. Testy STOP w fazach LISTENING, PROCESSING i SPEAKING.
- `AppOwnedLoopbackRtpMediaPort` + `RtpPcm16kAdapterMediaPort`: **wyłącznie localhost**, PCMU/RTP 8 kHz ↔ PCM16LE 16 kHz. Walidacja pełnych 20-ms pakietów, ignorowanie powtórek/opóźnionych pakietów, błąd po 6 s bez RX. Brak jitter buffera; pojedyncze luki do 2 pakietów (40 ms) są dopełniane ciszą, a większe skoki sekwencji nie powodują dużych alokacji.
- `S8NativeVoipTwoTurnSmokeActivity`: uruchamiany jawnie test **2–6 tur syntetycznego głosu** przez rzeczywiste lokalne STT, Gemma, approval, TTS i lokalny RTP RX/TX. Model nie jest atrapą, ale **nie jest to rozmowa z zewnętrznym rozmówcą**. Opcjonalnie `loss_every_n_packets` (0 lub 20–100), raport wyłącznie liczników, opóźnień i pamięci; bez audio i transkryptu. STOP przez ekran lub opuszczenie testu.
- `S8VoipLabActivity`: wybór testu 2/4/6 tur w normalnej aplikacji (bez ADB podczas użytkowania). Odrębny debug APK `pl.michalmatu.aicallbridge.s8lab`; `main` i pakiet stabilny nietknięte.
- `S8SipsTlsRegisterClient`: tylko ręczny REGISTER przez zweryfikowany TLS z SHA-256 Digest. Rejestracja **nie oznacza połączenia**.
- `S8SecureSipDialogGate` + `S8AuthenticatedVoipMediaPort` + `S8SecureVoipSpeechController`: testowane powiązanie dokładnej tożsamości dialogu SIP, odwołania PCM przed BYE i półdupleksowego STT/Gemma/TTS. Testy celowane i kompilacja APK — PASS (commit `f4ea746`); **nadal bez natywnego transportu wewnątrz APK**.
- `prototypes/s8-pjsua2` (niepakowany): skompilowany na Macu PJSUA2 2.17 dla Android ARM64, osobny prototyp INVITE → 180/RING → ręczne odbieranie/odrzucenie/BYE, 16k PCM AudioMediaPort z ograniczoną kolejką i czyszczeniem przy STOP. PJSUA2 Java API/port — `javac PASS`. 
- Natywny `S8Pjsua2SrtpAudit`: odczyt `pjmedia_srtp_info.active` i polityk kryptograficznych RX/TX z transportu PJSIP, dodatkowo wymaga bieżącej weryfikacji TLS. JNI arm64 zbudowane i połączone — PASS, ale **SIP/TLS/SRTP runtime na urządzeniu pozostaje OPEN**; jeśli JNI albo kluczy brakuje, PCM jest zamknięty.

## Wyniki i blokady — 17 listopada 2026

- Mac Local Agent: preflight, testy celowane S8, kompilacja APK, scenariusze STOP, walidacja RTP i utracone/opóźnione pakiety — **PASS**. Dowody w `MichalMatu/local-agent` / `agent-control` / `task-results`.
- **PHYSICAL PASS — 2/2 rzeczywiste lokalne tury AI na S22+** (syntetyczny głos przez prawdziwy RTP/Android STT/Gemma E2B/approval/TTS/RTP), SHA APK, telefon IDLE, stabilny APK bez zmian: 393 RX, 205 TX, 2 transkrypcje, 2 zatwierdzenia, 26 094 ms łącznie, najdłuższa odpowiedź 12 843 ms, 0 straconych pakietów w tym przebiegu; próbka max heap Java 32 957 328 B, native heap 326 706 272 B (to **nie pełne RSS/PSS**). Brak prawdziwego połączenia telefonicznego. Dowód: task result `aicalls-s8-6bfbd94-s22-native-2turn-rtp-worker-20261117-v1`.
- Poprzedni test na urządzeniu ujawnił brak wpisu raportu w whitelist (`PrivateDiagnosticReportStore`) i rozpoczęcie inicjalizacji socketu RTP z wątku głównego Androida. Po celowanych poprawkach raport i start sesji ACTIVE przeszły; weryfikacja rollbacku po nieudanych próbach: SHA PASS. **Fizyczne 4/4 i 6/6 tur PASS** na S22+ (bez operatora i realnych połączeń): 4 tury 46 921 ms, RTP 790 RX/404 TX, max przechwytywanie 4204 ms, max STT+Gemma 7985 ms, max TTS 1524 ms, próbka Java heap 39 998 352 B/native 326 812 224 B; 6 tur 61 493 ms, RTP 1184 RX/603 TX, max przechwytywanie 4243 ms, max STT+Gemma 7028 ms, max TTS 1107 ms, próbka Java heap 47 489 936 B/native 326 882 640 B. W obu próbach zero syntetycznych strat RTP. Dowody: `aicalls-s8-55ff2e1-s22-4turn-rtp-latency-20261117-v1` i `aicalls-s8-e064f40-s22-6turn-native-rtp-20261117-v1`. **Fizyczne STOP PASS**: LISTENING 1 ms, PROCESSING 2 ms, SPEAKING 5 ms (lokalny czas STOP, nie gwarancja natychmiastowego zatrzymania całego natywnego modelu). **5% syntetycznych strat RTP na S22+ PASS**: 4/4 tury, 39 z 790 pakietów utraconych, 751 dostarczonych, 790 odczytanych po dopełnieniu ciszą; czas 42 695 ms, max STT+Gemma 6092 ms. Dowody: `aicalls-s8-e064f40-s22-stop-three-phases-20261117-v1` i `aicalls-s8-e064f40-s22-4turn-five-percent-rtp-loss-20261117-v1`. Rozbicie czasu na capture, STT+model i TTS zostało zmierzone na urządzeniu w próbach 4 i 6 tur.
- Host Mac: **NDK r28c, SWIG 4.5.1, przypięty PJSIP 2.17 i OpenSSL 3.5.9 — gotowe**. `configure-android` + `make`: `libpjsua2.so` ARM64 PASS, biblioteka JNI SRTP audit PASS; ELF AArch64, segmenty LOAD wyrównane do 16 KB. SHA256 `libpjsua2.so`: `c3dce85a6f44a64b1a37739e4e352dfae563d63e2227703c19f4f0bf7883371d`. SHA256 osobnego JNI audit: `0e7c8918d0140ca6a472708e4e577b91c9e2646cae4a413a098ea3eebb342188`. **Nie pakować do produktu** przed decyzją licencyjną i testem rzeczywistego transportu TLS/SRTP.
- Brak konta testowego i niezależnej zgody na konkretny numer/koszty. Bez tej zgody nie wykonywać połączeń.

## Docelowy adapter SIP/SRTP

Wybrany kierunek techniczny: **PJSIP/PJSUA2 2.17+**, warunkowo po przeglądzie licencji i zależności. PJSUA2 obsługuje SIP TLS i SRTP, a `AudioMediaPort.onFrameReceived/onFrameRequested` może spiąć PCM własnego VoIP z istniejącą sesją STT/TTS.

1. Zweryfikowane TLS (certyfikat/host), REGISTER i obsługa przychodzących INVITE; dokładne powiązanie Call-ID, dialogu i identity. Brak nieautoryzowanych automatycznych połączeń.
2. SDP offer/answer i ICE/NAT według możliwości dostawcy. **Wymagać SRTP (SDES tylko przez zweryfikowane TLS albo DTLS-SRTP)**; żadnego fallback na RTP/AVP poza localhost; brak kluczy, PII lub PCM w logach.
3. Po pozytywnym uwierzytelnieniu SRTP adapter `MediaPort` udostępnia PCM16 i sygnalizuje `S8SecureSipDialogGate.onAuthenticatedSrtp`; sesja STT/TTS nie może rozpocząć TX wcześniej.
4. Dla przychodzącej rozmowy: ringing → jawne przyjęcie/tryb asystenta → media → BYE, CANCEL, timeout, STOP, śmierć procesu, utrata TLS lub kluczy; każde zakończenie najpierw odwołuje media lokalne, potem sygnalizację.
5. Dopiero po testach offline z atrapą SIP, lokalnym serwerem TLS/SRTP i emulatorze: test rzeczywistego własnego konta/połączenia po osobnej zgodzie.

**Narzędzia Mac są gotowe.** Sprawdzić zgodność strony prawnej przed dołączeniem natywnego artefaktu: PJSIP jest udostępniany na GPL-2.0-or-later lub odrębnej licencji komercyjnej. Nie pobierać przypadkowych prebuilt `.so` i nie pisać własnego SRTP.

Źródła:
- https://docs.pjsip.org/en/2.17/get-started/android/
- https://docs.pjsip.org/en/latest/get-started/android/sip-sdk-features.html
- https://docs.pjsip.org/en/latest/specific-guides/audio/audio_frame_manipulation.html
- https://docs.pjsip.org/en/latest/specific-guides/security/srtp.html
- https://www.pjsip.org/licensing.htm

## Bramki akceptacyjne

1. S22+ ponownie widoczny, stan IDLE i SHA stabilnego APK — **PASS 2026-11-17**; przed każdą nową instalacją ponowić kontrolę IDLE/SHA i utworzyć jej osobną kopię rollback, bez czyszczenia danych.
2. Fizyczne **2, 4 i 6 tur PASS** na S22+ z prawdziwym lokalnym modelem i RTP, przy syntetycznym wejściu i lokalnym UDP; nie powtarzać wcześniej zaliczonych testów bez powodu.
3. **STOP RX/model/TTS PASS na S22+**, 5% utrata RTP PASS, czasy odpowiedzi i próbki Java/native heap zebrane; brak logowanych transkryptów. Dalszy monitoring rzeczywistej pamięci RSS/PSS, długie sesje i NAT/jitter po wdrożeniu SIP pozostają OPEN.
4. Po integracji natywnej: testy TLS/SDP/SRTP i sieci, SIP INVITE/ACK/BYE/CANCEL; UI przyjęcia/odrzucenia i STOP. Brak połączeń rzeczywistych bez osobnej autoryzacji.
