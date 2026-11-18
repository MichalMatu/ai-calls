package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.content.pm.PackageManager;
import java.util.Objects;

import rikka.shizuku.Shizuku;

/**
 * One package-scoped readiness source for GSM call media.
 * Probe is read-only: it cannot start PCM, perform a call or grant privileges.
 * The helper must be started separately through an authorized one-shot ADB
 * command. A false "ready" still fails closed in the actual bind handshake.
 */
public final class GsmMediaTransportReadiness {
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
        // Observing the app-owned Binder registry is read-only and cannot
        // start PCM. Actual bind verifies the shell UID through Binder.
        return GsmShellBinderRegistryProvider.availableService() != null;
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
