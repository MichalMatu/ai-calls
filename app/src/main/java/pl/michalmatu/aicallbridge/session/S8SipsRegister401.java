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
 * Offline strict 401 parser for ONE pending REGISTER transaction.
 *
 * The CALLER must supply bytes from the same platform-authenticated SIPS/TLS
 * channel as the REGISTER request. Does not open sockets, follow redirects,
 * register an account or send authentication material.
 */
final class S8SipsRegister401 {
    private static final int MAX_BYTES = 8192;
    private static final int MAX_LINES = 64;

    private S8SipsRegister401() {}

    static S8SipDigestSha256.Challenge readChallenge(
        InputStream input, S8SipsRegisterDraft.Draft pending
    ) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(pending, "pending");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        int state = 0;
        while (bytes.size() < MAX_BYTES) {
            int c = input.read();
            if (c < 0) throw new IOException("register_401_incomplete");
            if (c == 0 || c > 127) throw new IOException("register_401_invalid_encoding");
            bytes.write(c);
            state = switch (state) {
                case 0 -> c == '\r' ? 1 : 0;
                case 1 -> c == '\n' ? 2 : c == '\r' ? 1 : 0;
                case 2 -> c == '\r' ? 3 : 0;
                case 3 -> c == '\n' ? 4 : 0;
                default -> 4;
            };
            if (state == 4) break;
        }
        if (state != 4) throw new IOException("register_401_oversize");
        String[] lines = bytes.toString(StandardCharsets.US_ASCII).split("\\r\\n", -1);
        if (lines.length < 9 || lines.length > MAX_LINES
            || !lines[lines.length - 1].isEmpty()
            || !lines[lines.length - 2].isEmpty()
            || !lines[0].startsWith("SIP/2.0 401 ")) {
            throw new IOException("register_401_invalid_status_or_framing");
        }
        for (String line : lines) {
            if (line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                throw new IOException("register_401_bare_newline");
            }
        }

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length - 2; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new IOException("register_401_folded_or_empty_header");
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                throw new IOException("register_401_header_invalid");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            for (int p = 0; p < name.length(); p++) {
                char c = name.charAt(p);
                if (!((c >= 'a' && c <= 'z') || c == '-')) {
                    throw new IOException("register_401_header_name_invalid");
                }
            }
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || headers.putIfAbsent(name, value) != null) {
                throw new IOException("register_401_header_duplicate_or_empty");
            }
        }
        String via = headers.get("via");
        String expectedVia = "SIP/2.0/TLS " + pending.viaSentBy()
            + ";branch=" + pending.viaBranch();
        if (via == null || !(via.equals(expectedVia)
            || via.startsWith(expectedVia + ";"))
            || !pending.callId().equals(headers.get("call-id"))
            || !(pending.cseq() + " REGISTER").equals(headers.get("cseq"))
            || !"0".equals(headers.get("content-length"))
            || !headers.containsKey("to") || !headers.containsKey("from")
            || headers.containsKey("proxy-authenticate")) {
            throw new IOException("register_401_transaction_mismatch");
        }
        String challenge = headers.get("www-authenticate");
        if (challenge == null) throw new IOException("register_401_missing_digest");
        try {
            return S8SipDigestSha256.parseChallenge(challenge);
        } catch (IllegalArgumentException rejected) {
            throw new IOException("register_401_digest_not_supported");
        }
    }
}
