package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;

import rikka.shizuku.Shizuku;

/**
 * One package-scoped readiness source for GSM call media.
 * Probe is read-only: it cannot start PCM, perform a call or grant privileges.
 * The helper must be started separately through an authorized one-shot ADB
 * command. A false "ready" still fails closed in the actual bind handshake.
 */
public final class GsmMediaTransportReadiness {
    private static final String LOG_TAG = "AiCallsGsmShell";

    private GsmMediaTransportReadiness() {}

    public static boolean isAvailable(Context context) {
        Objects.requireNonNull(context, "context");
        return selectedAvailable(context.getPackageName(),
            GsmMediaTransportReadiness::shellAvailable,
            GsmMediaTransportReadiness::legacyAvailable);
    }

    /** Pure selector for test; never falls back from S8 shell to Shizuku. */
    static boolean selectedAvailable(String packageName,
        java.util.function.BooleanSupplier shell,
        java.util.function.BooleanSupplier legacy
    ) {
        return CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB
            ? shell.getAsBoolean() : legacy.getAsBoolean();
    }

    private static boolean shellAvailable() {
        // Kernel abstract Unix socket, not TCP. Closing before handshake is
        // safe: the host never calls prepare/start without explicit commands.
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(
                GsmShellMediaProtocol.SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            int peerUid = socket.getPeerCredentials().getUid();
            boolean trusted = GsmShellMediaProtocol.mayTrustShell(peerUid);
            if (!trusted) Log.w(LOG_TAG, "readiness_peer_uid_rejected=" + peerUid);
            return trusted;
        } catch (IOException | RuntimeException unavailable) {
            // S8-only diagnostic: class and socket error, never call/audio content.
            Log.w(LOG_TAG, "readiness_socket_failure="
                + unavailable.getClass().getSimpleName() + ":" + unavailable.getMessage());
            return false;
        }
    }

    private static boolean legacyAvailable() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
}
