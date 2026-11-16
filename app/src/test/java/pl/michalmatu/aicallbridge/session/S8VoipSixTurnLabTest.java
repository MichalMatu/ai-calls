package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class S8VoipSixTurnLabTest {
    @Test public void completesSixDistinctNonSilentBidirectionalPhasesWithActualRtp()
        throws Exception {
        try (S8VoipSixTurnLab lab = new S8VoipSixTurnLab()) {
            S8VoipSixTurnLab.Result result = lab.run();
            assertEquals(6, result.phasesCompleted());
            assertEquals(72, result.rxFrames());
            assertEquals(72, result.txFrames());
            assertTrue(result.sdp());
            assertTrue(result.publicEndpointRejected());
            assertTrue(result.stopped());
            assertTrue(result.passed());
            assertThrows(IllegalStateException.class, lab::run);
        }
    }

    @Test public void stopBeforeStartDoesNotOpenRtpAndCannotRestart() throws Exception {
        var lab = new S8VoipSixTurnLab();
        lab.close();
        S8VoipSixTurnLab.Result result = lab.run();
        assertEquals(0, result.phasesCompleted());
        assertEquals(0, result.rxFrames());
        assertEquals(0, result.txFrames());
        assertTrue(result.stopped());
        assertFalse(result.passed());
        assertThrows(IllegalStateException.class, lab::run);
    }

    @Test public void concurrentStopUnblocksActiveRtpWithoutWaitingForSixPhases()
        throws Exception {
        var lab = new S8VoipSixTurnLab();
        FutureTask<S8VoipSixTurnLab.Result> job = new FutureTask<>(lab::run);
        Thread worker = new Thread(job, "s8-voip-lab-stop-test");
        worker.setDaemon(true);
        worker.start();
        // Cancel during worker startup or while sockets are actively exchanging.
        Thread.sleep(45);
        lab.close();
        S8VoipSixTurnLab.Result stopped = job.get(2, TimeUnit.SECONDS);
        worker.join(200);
        assertFalse("no background VoIP worker after STOP", worker.isAlive());
        assertTrue(stopped.stopped());
        assertFalse("STOP prevents completed session", stopped.passed());
    }
}
