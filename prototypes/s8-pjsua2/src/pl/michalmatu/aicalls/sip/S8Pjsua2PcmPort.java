package pl.michalmatu.aicalls.sip;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.AudioMediaPort;
import org.pjsip.pjsua2.ByteVector;
import org.pjsip.pjsua2.MediaFormatAudio;
import org.pjsip.pjsua2.MediaFrame;
import org.pjsip.pjsua2.pjmedia_format_id;
import org.pjsip.pjsua2.pjmedia_frame_type;

/**
 * Compile-only prototype: bounded 16 kHz mono PCM bridge for native PJSUA2.
 *
 * No packet I/O is owned here. A separately audited native SRTP verifier
 * supplies the cheap, nonblocking authenticatedNow predicate. The predicate
 * MUST NOT merely trust a configured "SRTP mandatory" flag. Without live
 * keying proof, callbacks return no frames and no AI voice is exposed.
 *
 * Prototype is deliberately outside Android app source sets and APK packaging.
 */
public final class S8Pjsua2PcmPort extends AudioMediaPort implements AutoCloseable {
    private static final int FRAME_BYTES = 640;
    private static final int MAX_QUEUED_FRAMES = 16;
    private final BooleanSupplier authenticatedNow;
    private final ArrayBlockingQueue<byte[]> incoming =
        new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
    private final ArrayBlockingQueue<byte[]> outgoing =
        new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger rxDropped = new AtomicInteger();
    private final AtomicInteger txDropped = new AtomicInteger();

    public S8Pjsua2PcmPort(BooleanSupplier authenticatedNow) {
        this.authenticatedNow =
            Objects.requireNonNull(authenticatedNow, "authenticatedNow");
    }

    /** Call on native media owner thread after endpoint startup. */
    public void createMonoPcm16Port() throws java.lang.Exception {
        if (closed.get()) throw new IllegalStateException("s8_pcm_closed");
        MediaFormatAudio format = new MediaFormatAudio();
        format.init(pjmedia_format_id.PJMEDIA_FORMAT_L16, 16_000, 1, 20_000, 16);
        createPort("s8-secure-voip-ai", format);
    }

    /** Attach only after live SRTP auth, not on configured SDP policy alone. */
    public void connectAuthenticated(AudioMedia peer) throws java.lang.Exception {
        if (!allowed()) throw new SecurityException("s8_live_srtp_required");
        peer.startTransmit(this);
        try {
            startTransmit(peer);
        } catch (java.lang.Exception error) {
            try { peer.stopTransmit(this); } catch (java.lang.Exception ignored) {}
            throw error;
        }
        if (!allowed()) {
            disconnect(peer);
            throw new SecurityException("s8_srtp_lost_on_connect");
        }
    }

    /** On owner thread after local STOP; callbacks already return NO FRAME. */
    public void disconnect(AudioMedia peer) {
        close();
        try { peer.stopTransmit(this); } catch (java.lang.Exception ignored) {}
        try { stopTransmit(peer); } catch (java.lang.Exception ignored) {}
    }

    private boolean allowed() {
        try { return !closed.get() && authenticatedNow.getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    @Override public void onFrameReceived(MediaFrame frame) {
        if (!allowed() ||
            frame.getType() != pjmedia_frame_type.PJMEDIA_FRAME_TYPE_AUDIO ||
            frame.getSize() != FRAME_BYTES) return;
        final ByteVector buffer = frame.getBuf();
        if (buffer == null || buffer.size() < FRAME_BYTES) return;
        final byte[] pcm = new byte[FRAME_BYTES];
        for (int i = 0; i < FRAME_BYTES; i++) {
            pcm[i] = (byte) (buffer.get(i) & 0xff);
        }
        if (!allowed()) {
            Arrays.fill(pcm, (byte) 0);
            return;
        }
        if (!incoming.offer(pcm)) {
            wipe(incoming.poll());
            rxDropped.incrementAndGet();
            if (!incoming.offer(pcm)) Arrays.fill(pcm, (byte) 0);
        }
    }

    @Override public void onFrameRequested(MediaFrame frame) {
        if (!allowed()) {
            frame.setType(pjmedia_frame_type.PJMEDIA_FRAME_TYPE_NONE);
            frame.setSize(0);
            return;
        }
        byte[] bytes = outgoing.poll();
        if (bytes == null) bytes = new byte[FRAME_BYTES];
        if (!allowed()) {
            Arrays.fill(bytes, (byte) 0);
            frame.setType(pjmedia_frame_type.PJMEDIA_FRAME_TYPE_NONE);
            frame.setSize(0);
            return;
        }
        final ByteVector output = new ByteVector(FRAME_BYTES, (short) 0);
        for (int i = 0; i < FRAME_BYTES; i++) {
            output.set(i, (short) (bytes[i] & 0xff));
        }
        Arrays.fill(bytes, (byte) 0);
        frame.setBuf(output);
        frame.setSize(FRAME_BYTES);
        frame.setType(pjmedia_frame_type.PJMEDIA_FRAME_TYPE_AUDIO);
    }

    /** Returns one full 20 ms frame or 0; never returns partial audio. */
    public int pollInput(byte[] destination) {
        if (destination.length < FRAME_BYTES)
            throw new IllegalArgumentException("s8_pcm_frame_too_short");
        if (!allowed()) return 0;
        final byte[] bytes = incoming.poll();
        if (bytes == null) return 0;
        System.arraycopy(bytes, 0, destination, 0, FRAME_BYTES);
        Arrays.fill(bytes, (byte) 0);
        if (!allowed()) {
            Arrays.fill(destination, 0, FRAME_BYTES, (byte) 0);
            return 0;
        }
        return FRAME_BYTES;
    }

    /** Enqueue one 20ms approved TTS frame; no caller array retained. */
    public boolean offerApprovedOutput(byte[] approvedPcm) {
        if (approvedPcm == null || approvedPcm.length != FRAME_BYTES ||
            !allowed()) return false;
        final byte[] owned = approvedPcm.clone();
        if (!allowed()) {
            Arrays.fill(owned, (byte) 0);
            return false;
        }
        if (!outgoing.offer(owned)) {
            wipe(outgoing.poll());
            txDropped.incrementAndGet();
            if (!outgoing.offer(owned)) {
                Arrays.fill(owned, (byte) 0);
                return false;
            }
        }
        return true;
    }

    public int droppedIncomingFrames() { return rxDropped.get(); }
    public int droppedOutgoingFrames() { return txDropped.get(); }

    /** Revokes PCM immediately; native owner must later detach and delete port. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        byte[] bytes;
        while ((bytes = incoming.poll()) != null) wipe(bytes);
        while ((bytes = outgoing.poll()) != null) wipe(bytes);
    }

    private static void wipe(byte[] bytes) {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
}
