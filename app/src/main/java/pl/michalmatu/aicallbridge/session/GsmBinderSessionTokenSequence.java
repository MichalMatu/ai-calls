package pl.michalmatu.aicallbridge.session;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide wire IDs for the S8 Binder host. Local coordinator generations
 * restart at 1 for each runtime, so they must not be sent directly to the
 * long-lived shell host: a late STOP(1) could otherwise abort a newer call.
 *
 * An app-process restart invalidates its provider registration; the old shell
 * host shuts down rather than accepting a reused ID from another process.
 */
public final class GsmBinderSessionTokenSequence {
    private static final AtomicLong NEXT = new AtomicLong();

    private GsmBinderSessionTokenSequence() {}

    public static long next() {
        long token = NEXT.incrementAndGet();
        if (!GsmShellMediaProtocol.validGeneration(token))
            throw new IllegalStateException("gsm_binder_wire_generation_exhausted");
        return token;
    }
}
