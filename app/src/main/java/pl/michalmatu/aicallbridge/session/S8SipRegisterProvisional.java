package pl.michalmatu.aicallbridge.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal RFC 3261 TCP/TLS response demux: skip at most two *correlated*
 * 100 Trying interim responses before one final REGISTER reply.
 *
 * All responses have Content-Length: 0, a complete CRLFCRLF header and
 * matched Via/Call-ID/CSeq/From/To. No body, redirect, 180, 183, unsolicited
 * events, multiple Via, compact headers or transaction multiplexing.
 * The injected stream MUST be the one verified SIPS/TLS session used to send
 * the corresponding REGISTER. Socket read timeouts belong to the transport.
 */
final class S8SipRegisterProvisional {
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_PROVISIONAL = 2;

    private S8SipRegisterProvisional() {}

    static InputStream nextFinal(InputStream stream, S8SipsRegisterDraft.Draft pending)
        throws IOException {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(pending, "pending");

        for (int attempt = 0; attempt <= MAX_PROVISIONAL; attempt++) {
            byte[] header = oneHeader(stream);
            Map<String, String> fields = parseFields(header);
            String start = fields.get(":status");
            String via = fields.get("via");
            String expectedVia = "SIP/2.0/TLS " + pending.viaSentBy()
                + ";branch=" + pending.viaBranch();
            if (via == null || !(via.equals(expectedVia)
                || via.startsWith(expectedVia + ";"))
                || !pending.callId().equals(fields.get("call-id"))
                || !(pending.cseq() + " REGISTER").equals(fields.get("cseq"))
                || !pending.expectedFrom().equals(fields.get("from"))
                || !pending.matchesToHeader(fields.get("to"))
                || !"0".equals(fields.get("content-length"))) {
                throw new IOException("sip_register_provisional_correlation_failed");
            }

            if (start.startsWith("SIP/2.0 100 ")) {
                if (attempt == MAX_PROVISIONAL) {
                    throw new IOException("sip_register_too_many_provisional_responses");
                }
                continue;
            }
            if (!start.startsWith("SIP/2.0 ") || start.length() < 12) {
                throw new IOException("sip_register_invalid_response_status");
            }
            String number = start.substring(8, 11);
            for (int index = 0; index < number.length(); index++) {
                if (number.charAt(index) < '0' || number.charAt(index) > '9') {
                    throw new IOException("sip_register_invalid_response_status");
                }
            }
            int code = Integer.parseInt(number);
            if (code < 200 || code > 699) {
                throw new IOException("sip_register_unsupported_provisional_status");
            }
            return new ByteArrayInputStream(header);
        }
        throw new IOException("sip_register_no_final_response");
    }

    private static byte[] oneHeader(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        int state = 0;
        while (bytes.size() < MAX_HEADER_BYTES) {
            int c = stream.read();
            if (c < 0) throw new IOException("sip_register_response_truncated");
            if (c == 0 || c > 127) throw new IOException("sip_register_non_ascii_header");
            bytes.write(c);
            state = switch (state) {
                case 0 -> c == '\r' ? 1 : 0;
                case 1 -> c == '\n' ? 2 : c == '\r' ? 1 : 0;
                case 2 -> c == '\r' ? 3 : 0;
                case 3 -> c == '\n' ? 4 : 0;
                default -> 4;
            };
            if (state == 4) return bytes.toByteArray();
        }
        throw new IOException("sip_register_response_header_limit");
    }

    private static Map<String, String> parseFields(byte[] raw) throws IOException {
        String[] lines = new String(raw, StandardCharsets.US_ASCII).split("\\r\\n", -1);
        if (lines.length < 8 || lines.length > 64 || !lines[lines.length-1].isEmpty()
            || !lines[lines.length-2].isEmpty()) {
            throw new IOException("sip_register_malformed_header");
        }
        Map<String, String> result = new HashMap<>();
        result.put(":status", lines[0]);
        for (String line : lines) {
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IOException("sip_register_bare_newline");
            }
        }
        for (int i = 1; i < lines.length - 2; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new IOException("sip_register_folded_header");
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                throw new IOException("sip_register_malformed_header");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            for (int k = 0; k < name.length(); k++) {
                char c = name.charAt(k);
                if (!((c >= 'a' && c <= 'z') || c == '-')) {
                    throw new IOException("sip_register_bad_header_name");
                }
            }
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || result.putIfAbsent(name, value) != null) {
                throw new IOException("sip_register_duplicate_header");
            }
        }
        return result;
    }
}
