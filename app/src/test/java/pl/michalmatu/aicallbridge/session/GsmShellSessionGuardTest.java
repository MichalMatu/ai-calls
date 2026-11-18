package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class GsmShellSessionGuardTest {
    @Test public void cannotStartOrHeartbeatBeforePreparation() {
        GsmShellSessionGuard guard = new GsmShellSessionGuard(17L);
        assertFalse(guard.start(17L));
        assertFalse(guard.heartbeat(17L));
        assertFalse(guard.prepare(18L, 16000));
        assertFalse(guard.prepare(17L, 48000));
        assertTrue(guard.prepare(17L, 16000));
        assertFalse(guard.prepare(17L, 16000)); // no repeat prepare
        assertFalse(guard.heartbeat(17L));
        assertFalse(guard.start(18L));
        assertTrue(guard.start(17L));
        assertFalse(guard.start(17L)); // no repeat or second PCM lease
        assertFalse(guard.heartbeat(18L));
        assertTrue(guard.heartbeat(17L));
        guard.stop();
        assertFalse(guard.heartbeat(17L));
        assertFalse(guard.start(17L));
        assertFalse(guard.prepare(17L, 16000));
    }

    @Test public void invalidGenerationsNeverCreatePrivilegedSessions() {
        for (long generation : new long[] {0L, -1L, Long.MIN_VALUE}) {
            try {
                new GsmShellSessionGuard(generation);
                fail("invalid generation opened a shell session");
            } catch (IllegalArgumentException expected) {
                // Fail closed before touching Android media or a phone.
            }
        }
    }
}
