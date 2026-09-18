package pl.michalmatu.aicallbridge.session;

@FunctionalInterface
public interface CallMediaMonotonicClock {
    long nowMs();
}
