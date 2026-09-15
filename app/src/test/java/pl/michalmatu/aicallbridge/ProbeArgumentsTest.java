package pl.michalmatu.aicallbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ProbeArgumentsTest {
    @Test
    public void noArgsDefaultsToInventory() {
        ProbeArguments parsed = ProbeArguments.parse(new String[0]);
        assertEquals(ProbeArguments.Mode.INVENTORY, parsed.mode());
    }

    @Test
    public void captureDownlinkAcceptsBoundedDurationWithoutRecording() {
        ProbeArguments parsed = ProbeArguments.parse(new String[] {"capture-downlink", "1000"});
        assertEquals(ProbeArguments.Mode.CAPTURE_DOWNLINK, parsed.mode());
        assertEquals(1000, parsed.durationMs());
        assertNull(parsed.outputPath());
    }

    @Test
    public void captureDownlinkAcceptsExplicitTemporaryOutput() {
        ProbeArguments parsed = ProbeArguments.parse(
            new String[] {"capture-downlink", "2000", "/data/local/tmp/aicallbridge-downlink.pcm"}
        );
        assertEquals("/data/local/tmp/aicallbridge-downlink.pcm", parsed.outputPath());
    }

    @Test
    public void captureDownlinkRejectsOutputOutsideTemporaryDirectory() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ProbeArguments.parse(
                new String[] {"capture-downlink", "1000", "/sdcard/call.pcm"}
            )
        );
    }

    @Test
    public void captureDownlinkRejectsZeroDuration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ProbeArguments.parse(new String[] {"capture-downlink", "0"})
        );
    }

    @Test
    public void captureDownlinkRejectsOverFiveSeconds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ProbeArguments.parse(new String[] {"capture-downlink", "30000"})
        );
    }

    @Test
    public void injectToneAcceptsBoundedFixture() {
        ProbeArguments parsed = ProbeArguments.parse(
            new String[] {"inject-tone", "400", "1000", "0.05"}
        );
        assertEquals(ProbeArguments.Mode.INJECT_TONE, parsed.mode());
        assertEquals(400, parsed.durationMs());
        assertEquals(1000, parsed.frequencyHz());
        assertEquals(0.05, parsed.amplitude(), 0.000001);
    }

    @Test
    public void unknownModeIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ProbeArguments.parse(new String[] {"unknown-mode"})
        );
    }
}
