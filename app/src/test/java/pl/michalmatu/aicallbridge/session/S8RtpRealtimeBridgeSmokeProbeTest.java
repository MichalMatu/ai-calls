package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;
import org.junit.Test;

/** No phone calls, recording, SIP credentials or device permissions. */
public final class S8RtpRealtimeBridgeSmokeProbeTest {
    @Test public void localRtpTraversesExistingRealtimePcmBoundaryBothWaysAndStops()
        throws Exception {
        S8RtpRealtimeBridgeSmokeProbe.Result result =
            S8RtpRealtimeBridgeSmokeProbe.run();
        assertTrue("RTP to Realtime RX", result.rx());
        assertTrue("Realtime to RTP TX", result.tx());
        assertTrue("Coordinator STOP", result.stop());
        assertTrue(result.allPassed());
    }
}
