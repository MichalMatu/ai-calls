package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Read-only GSM host readiness; other package identities never fall back to Shizuku. */
public final class GsmMediaTransportReadiness {
    private GsmMediaTransportReadiness() {}

    public static boolean isAvailable(Context context) {
        Objects.requireNonNull(context, "context");
        return selectedAvailable(context.getPackageName(),
            () -> GsmShellBinderRegistryProvider.availableService() != null);
    }

    /** Single authorized GSM path; no implicit privilege escalation. */
    static boolean selectedAvailable(String packageName, BooleanSupplier shell) {
        return CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB
            && shell.getAsBoolean();
    }
}
