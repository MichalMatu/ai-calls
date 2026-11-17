# S8 PJSUA2 (isolated prototype — not in the APK)

The S8 branch has two separately compiled Android ARM64 native libraries:
`libpjsua2.so` from pinned PJSIP 2.17/OpenSSL 3.5.9 and
`libs8srtpaudit.so` that queries authenticated SRTP and matches incoming
SIP receive data to the exact certificate-verified TLS transport.

**No public SIP account, automatic call, registration, or end-to-end remote
SIP test has been performed.** Neither library is packaged into the stable app
or S8 debug APK. Distribution/license review (GPL-2.0-or-later vs commercial)
is still open.

## Source and gate order

1. A native-worker-owned `S8Pjsua2VerifiedEndpoint` feeds **all** PJSUA2
   `onTransportState` callbacks into `S8Pjsua2TlsAudit`. Only established
   TLS/TLS6 transports with a verified peer certificate are held. On
   disconnect/error, the exact handle is revoked.
2. Create the native TLS transport with
   `S8Pjsua2SecureIncomingAccount.verifiedTlsTransport(trustedCaPath)`
   and verify PJSUA2 resolved transport type is TLS, not UDP/TCP. Create
   a SIPS account with mandatory SRTP. Registration remains disabled by
   default; do not automatically contact any SIP provider.
3. Only inside synchronous `onIncomingCall`, JNI compares the live
   `pjsip_rx_data->tp_info.transport` with the certified native TLS handle.
   Unmatched or unverifiable INVITEs are rejected **before Ringing/UI**.
   Never persist or use `SipRxData.pjRxData` after the callback.
4. The host's `Listener.onIncoming(exactIdentity)` creates a fresh
   `S8Pjsua2NativeCallBridge` using the account, TLS audit and audio index.
   Pass `bridge.signaling()`, `bridge.verifiedMedia()`, and
   `bridge.mediaPort()` to `S8SecureVoipSpeechController`. Call
   `onVerifiedInvite(exactIdentity)` to show the incoming UI. Do not
   answer automatically.
5. Explicit Accept calls the controller's `accept(exactIdentity)`, which
   sends native 200 OK via `S8Pjsua2SecureIncomingAccount`.
6. On the PJSUA2 **media-state callback**, call
   `bridge.attachAuthenticatedMedia()` **on the native media worker**.
   If and only if it returns true, call
   `controller.onAuthenticatedSrtp(exactIdentity)`. The bridge requires
   live RX+TX SRTP keys, current verified TLS, the exact call, a 16kHz mono
   PJSUA2 `AudioMediaPort`, and a bounded 16-frame PCM queue.
7. STOP/remote BYE/transport loss always revokes the PCM lease first,
   wipes buffers, and ends signaling; old identities and late callback
   generations are not reused. The account and Endpoint SWIG lifecycle
   must be shut down on their owning worker after disconnected callbacks,
   not from inside those callbacks.

## Current verification

- Mac Local Agent: PJSIP 2.17 Android ARM64 JNI compilation, OpenSSL 3.5.9,
  16 KB ELF alignment, TLS+SRTP native audit JNI link, generated PJSUA2 Java
  compilation — **PASS**.
- Mac Local Agent: native-call bridge directly compiled against actual
  `app` Java `MediaPort`, `CallMediaEndpointLease` and
  `S8SecureSipDialogGate` classes — **PASS**.
- S22+ local synthetic RTP/STT/Gemma/TTS: 2/4/6 turns, three STOP phases,
  and four turns with 5% packet loss — **PASS** for localhost only.
- **OPEN:** native SIP account TLS peer/hostname verification in a real
  session, SDP negotiation, SRTP RX/TX at runtime, actual JNI libraries loaded
  on S22+, Account/Endpoint SWIG cleanup in Android lifecycle, and licensing
  before third-party distribution.

Build scripts: `scripts/bin/build-s8-pjsip-prototype.sh` and
`scripts/bin/build-s8-srtp-verifier-prototype.sh`. Both require explicit
one-shot opt-in flags. No scheduling or GitHub Actions.

## Optional owner-local Android JNI smoke (no network)

The normal S8 debug build uses `-Ps8NativePrototype=false` and has no
PJSUA2 native binaries or active JNI smoke entry point. An **explicit**
`-Ps8NativePrototype=true` includes the pinned generated PJSUA2 Java source
and native `arm64-v8a` libraries from the outside-repo Mac workspace.
Release builds do not use these debug source directories. Missing JNI input
files fail the opt-in build during configuration.

The debug-only `S8Pjsua2OfflineLoadSmokeActivity` is protected by
`android.permission.DUMP`, is disabled without the opt-in flag, and is
intended only for one-shot ADB/shell QA on the owner's separate
`pl.michalmatu.aicallbridge.s8lab` package. It checks both JNI libraries,
rejects a null TLS transport and an invalid SRTP call, then creates and
destroys a PJSUA2 Endpoint **without** creating a transport, account,
registration, media stream, or real call. Its report is private and
contains only counters, booleans and coarse error codes.

Before installing: verify S22+ ADB, phone IDLE, stable APK SHA; back up the
current S8 APK; after the smoke always restore it and recheck both SHAs.
The first physical attempt was blocked by ADB disconnection **before
installation**. After reconnection, the isolated offline JNI smoke **PASS**
on S22+: both libraries loaded, zero TLS handle and invalid SRTP Call ID
rejected, Endpoint creation/destruction PASS. Before/after S8 APK SHA
rollback PASS and stable app SHA PASS. Receipt:
`aicalls-s8-s22-reconnected-jni-offline-rollback-20261117-v3`.
No network, SIP transport, registration or actual call was performed.

The optional local smoke does **not** authorize publication/distribution of
PJSIP or real SIP traffic. Licensing and end-to-end SIP/SDP/SRTP remain open.
