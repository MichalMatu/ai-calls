package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class GsmMediaTransportReadinessTest {
    @Test public void s8LabUsesOnlyOwnShellTransport() {
        AtomicInteger shell = new AtomicInteger();
        AtomicInteger legacy = new AtomicInteger();
        boolean ready = GsmMediaTransportReadiness.selectedAvailable(
            GsmShellMediaProtocol.S8_PACKAGE,
            () -> { shell.incrementAndGet(); return false; },
            () -> { legacy.incrementAndGet(); return true; }
        );
        assertFalse(ready);
        assertEquals(1, shell.get());
        assertEquals(0, legacy.get());
        assertEquals(CallMediaBackendMode.GSM_SHELL_ADB,
            CallMediaBackendMode.forPackage(GsmShellMediaProtocol.S8_PACKAGE));
    }

    @Test public void stablePackageRemainsOnProvenLegacyPath() {
        AtomicInteger shell = new AtomicInteger();
        AtomicInteger legacy = new AtomicInteger();
        boolean ready = GsmMediaTransportReadiness.selectedAvailable(
            "pl.michalmatu.aicallbridge",
            () -> { shell.incrementAndGet(); return false; },
            () -> { legacy.incrementAndGet(); return true; }
        );
        assertTrue(ready);
        assertEquals(0, shell.get());
        assertEquals(1, legacy.get());
        assertEquals(CallMediaBackendMode.SHIZUKU_LEGACY,
            CallMediaBackendMode.forPackage("pl.michalmatu.aicallbridge"));
    }

    @Test public void anUnexpectedPackageNeverGetsTheS8PrivilegedTransport() {
        assertEquals(CallMediaBackendMode.SHIZUKU_LEGACY,
            CallMediaBackendMode.forPackage("com.example.unknown"));
    }
}
