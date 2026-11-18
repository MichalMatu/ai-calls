package pl.michalmatu.aicallbridge.session;

/** Pure ordering rule for one-way STOP racing ahead of Binder PREPARE. */
public final class GsmBinderCancellationPolicy {
    private GsmBinderCancellationPolicy() {}

    public static long afterStop(long revokedThrough, long stoppedWireToken) {
        if (!GsmShellMediaProtocol.validGeneration(stoppedWireToken))
            return revokedThrough;
        return Math.max(revokedThrough, stoppedWireToken);
    }

    public static boolean mayPrepare(long requestedWireToken, long revokedThrough) {
        return GsmShellMediaProtocol.validGeneration(requestedWireToken)
            && requestedWireToken > revokedThrough;
    }
}
