package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class AndroidPublicCallMediaSessionBackendTest {
    @Test
    public void bindFailsClosedInsteadOfClaimingNativeRxTx() {
        AndroidPublicCallMediaSessionBackend backend = new AndroidPublicCallMediaSessionBackend();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        backend.bind(19L, new CallMediaSessionBackend.BindCallback() {
            @Override public void onBound(long generation) {
                fail("public Android APIs cannot supply cellular PCM");
            }
            @Override public void onDisconnected(long generation) {
                fail("no service was connected");
            }
            @Override public void onBindFailed(long generation, Throwable error) {
                assertEquals(19L, generation);
                failure.set(error);
            }
        });
        assertNotNull(failure.get());
        assertTrue(failure.get() instanceof UnsupportedOperationException);
        assertEquals(AndroidPublicCallMediaSessionBackend.BLOCKED_REASON,
            failure.get().getMessage());
        assertFalse(backend.heartbeat(19L));
    }

    @Test
    public void deniedBackendNeverLeasesAudioAndStopIsSafe() {
        AndroidPublicCallMediaSessionBackend backend = new AndroidPublicCallMediaSessionBackend();
        try {
            backend.start(1L);
            fail("must not return synthetic media endpoints");
        } catch (UnsupportedOperationException expected) {
            assertEquals(AndroidPublicCallMediaSessionBackend.BLOCKED_REASON,
                expected.getMessage());
        }
        backend.abort(1L);
        backend.unbind(1L);
        backend.close();
    }
}
