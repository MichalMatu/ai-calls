package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import pl.michalmatu.aicallbridge.audio.PcmFrame;
import pl.michalmatu.aicallbridge.realtime.RealtimePcmFrameAdapter;

/**
 * Pure PCM/data-plane bridge between one coordinator-owned telephony endpoint lease and Realtime.
 *
 * <p>This class never closes the lease and never calls Binder/Shizuku. It only frames, converts and
 * counts PCM. The first terminal data-plane condition is retained for the orchestrator, which owns
 * whole-generation fail-safe cleanup through {@link CallMediaSessionCoordinator}.</p>
 */
public final class CallRealtimePcmBridge {
    private final CallMediaEndpointLease lease;
    private final TelephonyPcmStreamFramer framer;
    private final RealtimePcmFrameAdapter adapter;
    private final AtomicLong downlinkBytes = new AtomicLong();
    private final AtomicLong downlinkFrames = new AtomicLong();
    private final AtomicLong uplinkBytes = new AtomicLong();
    private final AtomicLong uplinkFrames = new AtomicLong();
    private final AtomicReference<String> terminalReason = new AtomicReference<>();

    public CallRealtimePcmBridge(CallMediaEndpointLease lease) {
        this(lease, new TelephonyPcmStreamFramer(), new RealtimePcmFrameAdapter());
    }

    CallRealtimePcmBridge(
        CallMediaEndpointLease lease,
        TelephonyPcmStreamFramer framer,
        RealtimePcmFrameAdapter adapter
    ) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.framer = Objects.requireNonNull(framer, "framer");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    /**
     * Reads one exact 20 ms telephony frame and converts it to mono PCM16LE 24 kHz.
     * Returns null only for clean downlink EOF, which also makes this bridge terminal.
     */
    public PcmFrame readRealtimeInputFrame(long monotonicTimestampNs) throws IOException {
        requireActive();
        try {
            PcmFrame telephony = framer.read20MsFrame(lease.downlink(), monotonicTimestampNs);
            if (telephony == null) {
                markTerminal("downlink_eof");
                return null;
            }
            PcmFrame realtime = adapter.toRealtime(telephony);
            downlinkBytes.addAndGet(telephony.getData().length);
            downlinkFrames.incrementAndGet();
            return realtime;
        } catch (IOException error) {
            markTerminal("downlink_failed:" + describe(error));
            throw error;
        } catch (RuntimeException error) {
            markTerminal("downlink_failed:" + describe(error));
            throw error;
        }
    }

    /** Converts one Realtime PCM frame to the frozen telephony format and writes it as-is. */
    public void writeRealtimeOutputFrame(PcmFrame realtimeFrame) throws IOException {
        requireActive();
        try {
            PcmFrame telephony = adapter.toTelephony(
                Objects.requireNonNull(realtimeFrame, "realtimeFrame")
            );
            framer.writeFrame(lease.uplink(), telephony);
            uplinkBytes.addAndGet(telephony.getData().length);
            uplinkFrames.incrementAndGet();
        } catch (IOException error) {
            markTerminal("uplink_failed:" + describe(error));
            throw error;
        } catch (RuntimeException error) {
            markTerminal("uplink_failed:" + describe(error));
            throw error;
        }
    }

    public CallRealtimePcmBridgeSnapshot snapshot() {
        return new CallRealtimePcmBridgeSnapshot(
            downlinkBytes.get(),
            downlinkFrames.get(),
            uplinkBytes.get(),
            uplinkFrames.get(),
            terminalReason.get()
        );
    }

    private void requireActive() {
        String reason = terminalReason.get();
        if (reason != null) {
            throw new IllegalStateException("Realtime PCM bridge is terminal: " + reason);
        }
    }

    private void markTerminal(String reason) {
        terminalReason.compareAndSet(null, reason);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ":" + message.replace('\n', ' ').replace('\r', ' ');
    }
}
