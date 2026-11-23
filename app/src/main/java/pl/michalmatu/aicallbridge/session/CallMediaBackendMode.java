package pl.michalmatu.aicallbridge.session;

/** One supported GSM media transport; every other package identity is blocked. */
public enum CallMediaBackendMode {
    GSM_SHELL_ADB,
    UNAVAILABLE;

    public static CallMediaBackendMode forPackage(String packageName) {
        return GsmShellMediaProtocol.supportedPackage(packageName)
            ? GSM_SHELL_ADB : UNAVAILABLE;
    }
}
