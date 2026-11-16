package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class S8RtpLoopbackSmokeProbeTest {
    @Test public void appOwnedRtpLoopbackPassesTxRxAndStopWithoutTelephone() throws Exception {
        S8RtpLoopbackSmokeProbe.Result result = S8RtpLoopbackSmokeProbe.run();
        assertTrue("TX", result.tx());
        assertTrue("RX", result.rx());
        assertTrue("STOP", result.stop());
        assertTrue(result.allPassed());
    }
}
