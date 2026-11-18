package pl.michalmatu.aicallbridge.session;

/** Explicit opt-in transport selection. Never infer cellular PCM from dialer role alone. */
public enum CallMediaBackendMode {
    /** Existing, unchanged cellular media path. */
    SHIZUKU_LEGACY,

    /** Explicit S8 GSM adapter to the UID-verified, ADB-launched shell host. */
    GSM_SHELL_ADB,

    /** Fail-closed probe until a supported Android/OEM PCM implementation is verified. */
    ANDROID_PUBLIC_API_PROBE;

    /** Package-scoped safety gate: the isolated lab never silently selects Shizuku. */
    public static CallMediaBackendMode forPackage(String packageName) {
        if (GsmShellMediaProtocol.S8_PACKAGE.equals(packageName)) {
            return GSM_SHELL_ADB;
        }
        return SHIZUKU_LEGACY;
    }
}
