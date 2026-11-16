package pl.michalmatu.aicallbridge.session;

import java.util.Objects;

/**
 * Bounded SDP offer/answer for a SYNTHETIC app-owned localhost RTP/PCMU lab.
 *
 * No SIP dialog, no SRTP, no remote call and no trust in user-provided SDP.
 * RTP/AVP is intentionally accepted only on the literal IPv4 loopback address.
 * A future network-facing implementation MUST use authenticated signaling and
 * SRTP rather than reusing this class for remote media.
 *
 * RFC 8866 (SDP) and RFC 3264 (offer/answer) profile subset, fail closed.
 */
public final class S8LoopbackSdp {
    private static final String LOOPBACK = "127.0.0.1";
    private static final int MAX_BYTES = 1024;
    private static final int MAX_LINES = 12;

    /** Only a negotiated localhost PCMU track; never implies network authorization. */
    public record NegotiatedMedia(int peerRtpPort, int payloadType, int clockRateHz) {}

    private S8LoopbackSdp() {}

    /**
     * The owner must control the source socket at this exact port before offering it.
     * Session id is a positive monotonic local identifier, not a phone number.
     */
    public static String offer(int boundLocalRtpPort, long sessionId) {
        checkPort(boundLocalRtpPort);
        if (sessionId <= 0) throw new IllegalArgumentException("invalid_sdp_session_id");
        return "v=0\r\n"
            + "o=- " + sessionId + " 1 IN IP4 " + LOOPBACK + "\r\n"
            + "s=-\r\n"
            + "c=IN IP4 " + LOOPBACK + "\r\n"
            + "t=0 0\r\n"
            + "m=audio " + boundLocalRtpPort + " RTP/AVP 0\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=ptime:20\r\n"
            + "a=sendrecv\r\n";
    }

    /**
     * Accept only the single audio media stream this implementation can render.
     * Unknown attributes, media, codec profiles, direction changes and transports
     * are rejected, NOT silently ignored. Dynamic payloads are out of scope.
     */
    public static NegotiatedMedia acceptLoopbackAnswer(String answer) {
        Objects.requireNonNull(answer, "answer");
        if (answer.length() == 0 || answer.length() > MAX_BYTES
            || !answer.endsWith("\r\n") || answer.indexOf('\0') >= 0
            || answer.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("invalid_sdp_framing");
        }
        String[] lines = answer.split("\r\n", -1);
        if (lines.length < 7 || lines.length > MAX_LINES || !lines[lines.length - 1].isEmpty()) {
            throw new IllegalArgumentException("invalid_sdp_line_count");
        }
        boolean version = false, origin = false, name = false, connection = false;
        boolean time = false, media = false, rtpmap = false;
        boolean ptime = false, direction = false;
        int port = -1;
        int phase = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.length() > 256 || line.indexOf('\n') >= 0
                || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("invalid_sdp_line");
            }
            if (line.equals("v=0") && !version && phase == 0) {
                version = true; phase = 1;
            } else if (line.startsWith("o=") && !origin && phase == 1) {
                // Only non-sensitive anonymous session IDs. Prevents unexpected
                // addressing, FQDN resolution or embedded credentials/PII.
                String[] fields = line.substring(2).split(" ", -1);
                if (fields.length != 6 || !fields[0].equals("-")
                    || !decimalId(fields[1]) || !decimalId(fields[2])
                    || !fields[3].equals("IN") || !fields[4].equals("IP4")
                    || !fields[5].equals(LOOPBACK)) {
                    throw new IllegalArgumentException("invalid_sdp_origin");
                }
                origin = true; phase = 2;
            } else if (line.equals("s=-") && !name && phase == 2) {
                name = true; phase = 3;
            } else if (line.equals("c=IN IP4 " + LOOPBACK) && !connection && phase == 3) {
                connection = true; phase = 4;
            } else if (line.equals("t=0 0") && !time && phase == 4) {
                time = true; phase = 5;
            } else if (line.startsWith("m=") && !media && phase == 5) {
                String[] fields = line.substring(2).split(" ", -1);
                if (fields.length != 4 || !fields[0].equals("audio")
                    || !fields[2].equals("RTP/AVP") || !fields[3].equals("0")) {
                    throw new IllegalArgumentException("unsupported_sdp_media");
                }
                port = parsePort(fields[1]);
                media = true; phase = 6;
            } else if (line.equals("a=rtpmap:0 PCMU/8000") && media
                && !rtpmap && phase == 6) {
                rtpmap = true; phase = 7;
            } else if (line.equals("a=ptime:20") && rtpmap
                && !ptime && phase == 7) {
                ptime = true; phase = 8;
            } else if (line.equals("a=sendrecv") && ptime
                && !direction && phase == 8) {
                direction = true; phase = 9;
            } else {
                throw new IllegalArgumentException("unsupported_or_unsafe_sdp_field");
            }
        }
        if (!(version && origin && name && connection && time
            && media && rtpmap && ptime && direction)) {
            throw new IllegalArgumentException("incomplete_sdp_pcpu_offer_answer");
        }
        return new NegotiatedMedia(port, RtpPcmuCodec.RTP_PAYLOAD_TYPE,
            RtpPcmuCodec.SAMPLE_RATE_HZ);
    }

    private static boolean decimalId(String value) {
        if (value.isEmpty() || value.length() > 20) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static int parsePort(String value) {
        if (value.length() > 5 || value.isEmpty()) {
            throw new IllegalArgumentException("invalid_sdp_rtp_port");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("invalid_sdp_rtp_port");
            }
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid_sdp_rtp_port");
        }
        checkPort(parsed);
        return parsed;
    }

    private static void checkPort(int port) {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("invalid_sdp_rtp_port");
        }
    }
}
