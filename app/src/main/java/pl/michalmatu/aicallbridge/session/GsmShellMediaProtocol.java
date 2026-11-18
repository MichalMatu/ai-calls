package pl.michalmatu.aicallbridge.session;

/**
 * Private, on-device GSM audio control contract between the S8 app and an
 * ADB-authorized shell process. This is NOT a general-purpose shell/RPC API.
 *
 * Identity is checked using kernel LocalSocket peer credentials on BOTH sides.
 * The helper independently resolves the installed S8 application's UID; the
 * caller may never provide an arbitrary accepted UID in the handshake.
 */
public final class GsmShellMediaProtocol {
    public static final String SOCKET_NAME = "aicalls_s8_gsm_shell_v1";
    public static final String S8_PACKAGE = "pl.michalmatu.aicallbridge.s8lab";
    public static final int SHELL_UID = 2000;
    public static final int MAGIC = 0x41434731; // ACG1
    public static final int OK = 0x31;
    public static final int ERROR = 0x30;
    public static final int PREPARE = 1;
    public static final int START = 2;
    public static final int HEARTBEAT = 3;
    public static final int SAMPLE_RATE_HZ = 16_000;

    private GsmShellMediaProtocol() {}

    public static boolean mayTrustShell(int peerUid) {
        return peerUid == SHELL_UID;
    }

    public static boolean mayServeApp(int peerUid, int installedS8AppUid) {
        return peerUid >= 10_000 && installedS8AppUid >= 10_000
            && peerUid == installedS8AppUid;
    }

    public static boolean validGeneration(long generation) {
        return generation > 0L;
    }
}
