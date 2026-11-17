package pl.michalmatu.aicalls.sip;

/** Pure, JNI-free guard shared by PJSUA2 transport callbacks and focused QA. */
public final class S8Pjsua2TlsEvidencePolicy {
    private S8Pjsua2TlsEvidencePolicy() {}

    public static boolean trusted(
        boolean tlsTransport, boolean connected, int transportError,
        boolean handshakeEstablished, long certificateVerifyStatus,
        boolean remoteCertificateIdentityPresent
    ) {
        return tlsTransport && connected && transportError == 0 &&
            handshakeEstablished && certificateVerifyStatus == 0L &&
            remoteCertificateIdentityPresent;
    }
}
