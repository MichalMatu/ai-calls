package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The shell must never mistake a delayed STOP from an old runtime for a new call. */
public final class GsmBinderSessionTokenSequenceTest {
    @Test public void distinctRuntimeOwnersNeverReuseWireGeneration() {
        // Local CallMediaSessionCoordinator.start() can report generation=1
        // again when a new coordinator is constructed.
        long firstOwnerWire = GsmBinderSessionTokenSequence.next();
        long secondOwnerWire = GsmBinderSessionTokenSequence.next();
        assertTrue(firstOwnerWire > 0);
        assertTrue(secondOwnerWire > firstOwnerWire);
    }

    @Test public void manySequentialSessionsRemainStrictlyIncreasing() {
        long last = GsmBinderSessionTokenSequence.next();
        for (int i = 0; i < 128; ++i) {
            long next = GsmBinderSessionTokenSequence.next();
            assertTrue(next > last);
            last = next;
        }
    }
}
