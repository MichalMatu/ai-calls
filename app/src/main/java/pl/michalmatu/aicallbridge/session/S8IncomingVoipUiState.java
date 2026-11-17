package pl.michalmatu.aicallbridge.session;

import java.util.Objects;

/**
 * Presentation-only incoming-VoIP state. Does not create SIP, expose caller data,
 * allow accept without a verified native transport, or infer success from REGISTER.
 */
public record S8IncomingVoipUiState(
    String statusCode,
    boolean canAccept,
    boolean canReject,
    boolean canHangUp,
    boolean authenticatedMedia
) {
    public static S8IncomingVoipUiState from(
        S8SecureSipDialogGate.Snapshot snapshot, boolean nativeSipAttached
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!nativeSipAttached) return new S8IncomingVoipUiState(
            "native_sip_unavailable", false, false, false, false
        );
        return switch (snapshot.state()) {
            case IDLE -> new S8IncomingVoipUiState(
                "waiting", false, false, false, false
            );
            case RINGING -> new S8IncomingVoipUiState(
                "ringing", true, true, false, false
            );
            case NEGOTIATING -> new S8IncomingVoipUiState(
                "negotiating", false, false, true, false
            );
            case ACTIVE -> snapshot.mediaReady()
                ? new S8IncomingVoipUiState("connected", false, false, true, true)
                : new S8IncomingVoipUiState("security_failure", false, false, true, false);
            case STOPPED -> new S8IncomingVoipUiState(
                "ended", false, false, false, false
            );
            case FAILED -> new S8IncomingVoipUiState(
                "failed", false, false, false, false
            );
        };
    }
}
