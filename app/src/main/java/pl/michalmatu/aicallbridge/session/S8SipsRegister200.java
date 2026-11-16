package pl.michalmatu.aicallbridge.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Strict offline parser for one final REGISTER 200 OK.
 *
 * Accepts only a single, exact app-owned Contact binding with an explicit
 * bounded expiration and the original Call-ID/CSeq/Via branch. Per RFC 3261,
 * normal registrar responses may enumerate several bindings; this deliberate
 * subset rejects them rather than attributing another device's binding to us.
 * Input must come from the SAME previously verified SIPS/TLS session.
 */
final class S8SipsRegister200 {
    private static final int MAX_BYTES = 8192;
    private static final int MAX_LINES = 64;

    private S8SipsRegister200() {}

    static int readAcceptedExpiration(InputStream input, S8SipsRegisterDraft.Draft pending)
        throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(pending, "pending");
        if (pending.cseq() != 2) {
            throw new IllegalStateException("register_success_requires_authenticated_retry");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
        int state = 0;
        while (buffer.size() < MAX_BYTES) {
            int c = input.read();
            if (c < 0) throw new IOException("register_200_truncated");
            if (c == 0 || c > 127) throw new IOException("register_200_header_encoding");
            buffer.write(c);
            state = switch (state) {
                case 0 -> c == '\r' ? 1 : 0;
                case 1 -> c == '\n' ? 2 : c == '\r' ? 1 : 0;
                case 2 -> c == '\r' ? 3 : 0;
                case 3 -> c == '\n' ? 4 : 0;
                default -> 4;
            };
            if (state == 4) break;
        }
        if (state != 4) throw new IOException("register_200_header_limit");
        String[] lines = buffer.toString(StandardCharsets.US_ASCII).split("\\r\\n", -1);
        if (lines.length < 9 || lines.length > MAX_LINES
            || !lines[lines.length - 1].isEmpty()
            || !lines[lines.length - 2].isEmpty()
            || !lines[0].startsWith("SIP/2.0 200 ")) {
            throw new IOException("register_200_status_or_framing");
        }
        for (String line : lines) {
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IOException("register_200_invalid_line_ending");
            }
        }
        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length - 2; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new IOException("register_200_folded_header_rejected");
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                throw new IOException("register_200_bad_header");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            for (int j = 0; j < name.length(); j++) {
                char c = name.charAt(j);
                if (!((c >= 'a' && c <= 'z') || c == '-')) {
                    throw new IOException("register_200_bad_header_name");
                }
            }
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || headers.putIfAbsent(name, value) != null) {
                throw new IOException("register_200_duplicate_header");
            }
        }
        String expectedVia = "SIP/2.0/TLS " + pending.viaSentBy()
            + ";branch=" + pending.viaBranch();
        String via = headers.get("via");
        if (via == null || !(via.equals(expectedVia)
            || via.startsWith(expectedVia + ";"))
            || !pending.callId().equals(headers.get("call-id"))
            || !(pending.cseq() + " REGISTER").equals(headers.get("cseq"))
            || !"0".equals(headers.get("content-length"))
            || !pending.expectedFrom().equals(headers.get("from"))
            || !pending.matchesToHeader(headers.get("to"))
            || headers.containsKey("www-authenticate")
            || headers.containsKey("proxy-authenticate")
            || headers.containsKey("proxy-authorization")
            || headers.containsKey("location")
            || headers.containsKey("authorization")) {
            throw new IOException("register_200_transaction_mismatch");
        }

        // Strict single-binding subset. RFC 3261 allows several Contacts; full
        // list reconciliation is an OPEN prerequisite for a real registrar.
        String contact = headers.get("contact");
        String prefix = pending.expectedContact() + ";expires=";
        if (contact == null || !contact.startsWith(prefix)) {
            throw new IOException("register_200_missing_exact_contact");
        }
        String lifetime = contact.substring(prefix.length());
        int expiry = decimalSeconds(lifetime);
        if (expiry < 60 || expiry > pending.requestedExpires()) {
            throw new IOException("register_200_expiration_out_of_bounds");
        }
        String topExpires = headers.get("expires");
        if (topExpires != null && (decimalSeconds(topExpires) != expiry)) {
            throw new IOException("register_200_expiration_conflict");
        }
        return expiry;
    }

    private static int decimalSeconds(String value) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 5) {
            throw new IOException("register_200_invalid_expiration");
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                throw new IOException("register_200_invalid_expiration");
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IOException("register_200_invalid_expiration");
        }
    }
}
