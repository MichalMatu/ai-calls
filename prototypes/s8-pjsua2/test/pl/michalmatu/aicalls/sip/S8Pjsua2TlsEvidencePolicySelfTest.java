package pl.michalmatu.aicalls.sip;

/** Native-free focused regression, executed using plain java -ea. */
public final class S8Pjsua2TlsEvidencePolicySelfTest {
    private S8Pjsua2TlsEvidencePolicySelfTest() {}

    private static void require(boolean accepted, String scenario) {
        if (!accepted) throw new AssertionError(scenario);
    }

    public static void main(String[] args) {
        // Trusted transport is allowed only with all evidence present.
        require(S8Pjsua2TlsEvidencePolicy.trusted(
            true, true, 0, true, 0L, true), "validated TLS should pass");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            false, true, 0, true, 0L, true), "UDP/TCP plaintext rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            true, false, 0, true, 0L, true), "disconnected TLS rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            true, true, 1, true, 0L, true), "transport error rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            true, true, 0, false, 0L, true), "TLS handshake pending rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            true, true, 0, true, 1L, true), "invalid certificate rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            true, true, 0, true, 0L, false), "anonymous peer rejected");
        require(!S8Pjsua2TlsEvidencePolicy.trusted(
            false, false, 1, false, 1L, false), "unknown transport denied");
        System.out.println("S8_INCOMING_TLS_EVIDENCE_POLICY=PASS");
    }
}
