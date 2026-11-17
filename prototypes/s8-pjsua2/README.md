# S8 PJSUA2 — referencja techniczna, nie osobny plan

The S8 branch has two separately compiled Android ARM64 native libraries:
`libpjsua2.so` from pinned PJSIP 2.17/OpenSSL 3.5.9 and
`libs8srtpaudit.so` that queries authenticated SRTP and matches incoming
SIP receive data to the exact certificate-verified TLS transport.

**Nie wykonano prawdziwej rejestracji SIP ani zewnętrznego połączenia.** Stabilny APK i domyślny S8 debug nie zawierają PJSIP. Jednorazowo, tylko po opt-in, testowano PJSIP/JNI offline na S22+ i przywrócono poprzedni APK (SHA PASS). Licencja dystrybucyjna nadal nierozstrzygnięta. [Jedyny plan GSM → SIP](../../docs/PROJECT.md).

## Zaimplementowane kontrakty (opis, nie backlog)

- `S8Pjsua2VerifiedEndpoint` przekazuje stan natywnego TLS do `S8Pjsua2TlsAudit`; utracony transport nie jest uznawany za zweryfikowany.
- `S8Pjsua2SecureIncomingAccount` wymaga TLS i SRTP; domyślnie nie rejestruje konta. Niezweryfikowany `INVITE` jest odrzucany przed Ringing/UI.
- `S8Pjsua2NativeCallBridge` dostarcza tożsamość połączenia, sygnalizację i port PCM zgodny z obecnym kontrolerem S8.
- `S8Pjsua2SrtpAudit` sprawdza aktywne natywne SRTP i polityki RX/TX; brak JNI/kluczy odcina PCM.
- `S8Pjsua2PcmPort` obsługuje 16 kHz mono, bufor 16 ramek i odwołanie przy STOP.

Kontrakty skompilowano, ale nie potwierdzono przesyłania głosu przez faktyczną rozmowę SIP. Kolejność dalszych prac jest **wyłącznie w [PROJECT](../../docs/PROJECT.md)**.

## Current verification

- Mac Local Agent: PJSIP 2.17 Android ARM64 JNI compilation, OpenSSL 3.5.9,
  16 KB ELF alignment, TLS+SRTP native audit JNI link, generated PJSUA2 Java
  compilation — **PASS**.
- Mac Local Agent: native-call bridge directly compiled against actual
  `app` Java `MediaPort`, `CallMediaEndpointLease` and
  `S8SecureSipDialogGate` classes — **PASS**.
- S22+: syntetyczny RTP/STT/Gemma/TTS 2/4/6 tur, STOP i 5% strat — **PASS** tylko lokalnie.
- S22+: opt-in JNI offline, negatywne próby TLS/SRTP, Endpoint create/destroy i rollback APK — **PASS** (receipt `aicalls-s8-s22-reconnected-jni-offline-rollback-20261117-v3`).
- **Nieprzetestowane w rozmowie:** konto SIP, negocjacja SDP, dwukierunkowy SRTP i PCM z rozmówcą. Ładowanie JNI oraz cykl Endpoint zostały później potwierdzone offline na S22+; to wynik historyczny, nie kolejna lista zadań.

Build scripts: `scripts/bin/build-s8-pjsip-prototype.sh` and
`scripts/bin/build-s8-srtp-verifier-prototype.sh`. Both require explicit
one-shot opt-in flags. No scheduling or GitHub Actions.

## Historyczny test JNI bez połączeń

Opt-in debug `-Ps8NativePrototype=true` dołączał do osobnej aplikacji S8 Lab lokalnie zbudowane PJSUA2 i JNI; zwykły debug i release nie miały tych bibliotek. Test zabezpieczony `android.permission.DUMP` odrzucił błędne identyfikatory i zweryfikował tworzenie/zamknięcie silnika. Nie powstał transport ani konto SIP, rejestracja ani połączenie. Po teście przywrócono wcześniejszy S8 APK i potwierdzono SHA stabilnej aplikacji.

Techniczny skrypt budowania: `scripts/bin/build-s8-pjsip-prototype.sh`, `scripts/bin/build-s8-srtp-verifier-prototype.sh`. **To referencja do wykonanych testów, nie polecenie ich powtarzania.** Dalsza kolejność prac: [PROJECT](../../docs/PROJECT.md).
