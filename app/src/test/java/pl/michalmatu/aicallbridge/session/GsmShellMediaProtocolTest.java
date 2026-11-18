package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GsmShellMediaProtocolTest {
    @Test public void acceptsOnlyVerifiedShellOnClientSide() {
        assertTrue(GsmShellMediaProtocol.mayTrustShell(2000));
        for (int uid : new int[] {0, 1000, 1001, 10_123, 20_000}) {
            assertFalse(GsmShellMediaProtocol.mayTrustShell(uid));
        }
    }

    @Test public void serverAcceptsOnlyResolvedS8PackageUid() {
        assertTrue(GsmShellMediaProtocol.mayServeApp(10_123, 10_123));
        assertFalse(GsmShellMediaProtocol.mayServeApp(10_123, 10_124));
        assertFalse(GsmShellMediaProtocol.mayServeApp(2_000, 2_000));
        assertFalse(GsmShellMediaProtocol.mayServeApp(0, 0));
        assertFalse(GsmShellMediaProtocol.mayServeApp(10_123, -1));
        assertFalse(GsmShellMediaProtocol.mayServeApp(-1, 10_123));
    }

    @Test public void onlyPositiveGenerationsMayControlPcm() {
        assertTrue(GsmShellMediaProtocol.validGeneration(1L));
        assertTrue(GsmShellMediaProtocol.validGeneration(Long.MAX_VALUE));
        assertFalse(GsmShellMediaProtocol.validGeneration(0L));
        assertFalse(GsmShellMediaProtocol.validGeneration(-1L));
    }
}
