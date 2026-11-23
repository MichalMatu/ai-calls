package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class GsmMediaTransportReadinessTest {
    @Test public void acceptedLabUsesOnlyItsShellTransport() {
        AtomicInteger shellCalls = new AtomicInteger();
        boolean available = GsmMediaTransportReadiness.selectedAvailable(
            GsmShellMediaProtocol.S8_PACKAGE,
            () -> { shellCalls.incrementAndGet(); return true; }
        );
        assertTrue(available);
        assertEquals(1, shellCalls.get());
        assertEquals(CallMediaBackendMode.GSM_SHELL_ADB,
            CallMediaBackendMode.forPackage(GsmShellMediaProtocol.S8_PACKAGE));
    }

    @Test public void missingShellBlocksEvenIfTheAppIsApproved() {
        assertFalse(GsmMediaTransportReadiness.selectedAvailable(
            GsmShellMediaProtocol.S8_PACKAGE, () -> false));
    }

    @Test public void publicReleaseUsesTheSameAuthorizedShellTransport() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(GsmMediaTransportReadiness.selectedAvailable(
            GsmShellMediaProtocol.APP_PACKAGE,
            () -> { calls.incrementAndGet(); return true; }));
        assertEquals(1, calls.get());
        assertEquals(CallMediaBackendMode.GSM_SHELL_ADB,
            CallMediaBackendMode.forPackage(GsmShellMediaProtocol.APP_PACKAGE));
    }

    @Test public void otherPackageIdentitiesNeverRunTheShellProbe() {
        for (String packageName : new String[] {
                "pl.michalmatu.aicallbridge.untrusted", "com.example.unknown", "" }) {
            AtomicInteger shellCalls = new AtomicInteger();
            assertFalse(GsmMediaTransportReadiness.selectedAvailable(
                packageName, () -> { shellCalls.incrementAndGet(); return true; }));
            assertEquals(0, shellCalls.get());
            assertEquals(CallMediaBackendMode.UNAVAILABLE,
                CallMediaBackendMode.forPackage(packageName));
        }
    }
}
