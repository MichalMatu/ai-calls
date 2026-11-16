package pl.michalmatu.aicallbridge.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * An intentionally small, NON-DIALING SIP/TLS OPTIONS transaction.
 *
 * Only the exact sips:domain[:port] authority is supported. No URI userinfo,
 * SIP registration, credentials, redirects, phone calls or SDP bodies.
 * A verified OPTIONS response is NOT authorization to establish VoIP media.
 * RFC 3261 OPTIONS; TLS certificate verification belongs to the transport.
 */
public final class S8SipsOptionsProtocol {
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_HEADER_LINES = 64;
    private static final int DEFAULT_TLS_PORT = 5061;
    private static final SecureRandom RANDOM = new SecureRandom();

    private S8SipsOptionsProtocol() {}

    public record Endpoint(String host, int port) {}

    /** Bounds untrusted configuration before the network stack can resolve it. */
    public static Endpoint parseEndpoint(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 260
            || !raw.startsWith("sips:")) {
            throw new IllegalArgumentException("sips_tls_required");
        }
        String authority = raw.substring(5);
        if (authority.indexOf('@') >= 0 || authority.indexOf('/') >= 0
            || authority.indexOf('?') >= 0 || authority.indexOf('#') >= 0
            || authority.indexOf(';') >= 0 || authority.indexOf('[') >= 0
            || authority.indexOf(']') >= 0 || authority.indexOf('%') >= 0
            || authority.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("unsupported_sips_authority");
        }
        String host = authority;
        int port = DEFAULT_TLS_PORT;
        int delimiter = authority.indexOf(':');
        if (delimiter >= 0) {
            if (delimiter != authority.lastIndexOf(':')) {
                throw new IllegalArgumentException("invalid_sips_port");
            }
            host = authority.substring(0, delimiter);
            String number = authority.substring(delimiter + 1);
            if (number.isEmpty() || number.length() > 5) {
                throw new IllegalArgumentException("invalid_sips_port");
            }
            for (int i = 0; i < number.length(); i++) {
                if (number.charAt(i) < '0' || number.charAt(i) > '9') {
                    throw new IllegalArgumentException("invalid_sips_port");
                }
            }
            try {
                port = Integer.parseInt(number);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid_sips_port");
            }
        }
        if (port < 1024 || port > 65535 || host.length() < 4 || host.length() > 253) {
            throw new IllegalArgumentException("invalid_sips_target");
        }
        host = host.toLowerCase(Locale.ROOT);
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            throw new IllegalArgumentException("sips_dns_name_required");
        }
        boolean lastLabelHasLetter = false;
        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            if (label.isEmpty() || label.length() > 63
                || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                throw new IllegalArgumentException("invalid_sips_dns_label");
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
                    throw new IllegalArgumentException("invalid_sips_dns_label");
                }
                if (index == labels.length - 1 && c >= 'a' && c <= 'z') {
                    lastLabelHasLetter = true;
                }
            }
        }
        if (!lastLabelHasLetter || host.equals("localhost.localdomain")) {
            throw new IllegalArgumentException("sips_fqdn_required");
        }
        return new Endpoint(host, port);
    }

    /** Created entirely locally; does not open a socket or send data. */
    public static Transaction newOptions(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        // Revalidate even if Endpoint was directly constructed by another caller.
        Endpoint checked = parseEndpoint(
            "sips:" + endpoint.host() + ":" + endpoint.port()
        );
        String branch = "z9hG4bK" + hexToken();
        String callId = hexToken() + "@client.invalid";
        String fromTag = hexToken();
        String target = "sips:" + checked.host()
            + (checked.port() == DEFAULT_TLS_PORT ? "" : ":" + checked.port());
        // RFC 3261 mandatory Via, To, From, Call-ID, CSeq, Max-Forwards.
        // No Contact/Authorization or route set; no SDP and no media.
        String request =
            "OPTIONS " + target + " SIP/2.0\r\n"
            + "Via: SIP/2.0/TLS client.invalid;branch=" + branch + ";rport\r\n"
            + "Max-Forwards: 0\r\n"
            + "To: <" + target + ">\r\n"
            + "From: <sips:probe@client.invalid>;tag=" + fromTag + "\r\n"
            + "Call-ID: " + callId + "\r\n"
            + "CSeq: 1 OPTIONS\r\n"
            + "Accept: application/sdp\r\n"
            + "Content-Length: 0\r\n\r\n";
        return new Transaction(checked, branch, callId, request);
    }

    public record Transaction(Endpoint endpoint, String branch, String callId, String wire) {}

    /** Sanitized status only; no SIP headers/body or phone/account identifiers escape. */
    public record ResponseStatus(int code, boolean available) {}

    /**
     * Consume only a single bounded, body-free response matching this request.
     * The caller MUST have a socket read timeout; this parser has no clock.
     */
    public static ResponseStatus readResponse(InputStream input, Transaction transaction)
        throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(transaction, "transaction");
        ByteArrayOutputStream output = new ByteArrayOutputStream(512);
        int matched = 0;
        while (output.size() < MAX_HEADER_BYTES) {
            int current = input.read();
            if (current == -1) throw new IOException("sip_response_truncated");
            // CRLFCRLF is the only acceptable header framing.
            if (current == 0 || current > 127) {
                throw new IOException("invalid_sip_header_encoding");
            }
            output.write(current);
            matched = switch (matched) {
                case 0 -> current == '\r' ? 1 : 0;
                case 1 -> current == '\n' ? 2 : current == '\r' ? 1 : 0;
                case 2 -> current == '\r' ? 3 : 0;
                case 3 -> current == '\n' ? 4 : 0;
                default -> 4;
            };
            if (matched == 4) break;
        }
        if (matched != 4) throw new IOException("sip_headers_exceed_limit");
        String raw = output.toString(StandardCharsets.US_ASCII);
        String[] lines = raw.split("\\r\\n", -1);
        if (lines.length < 4 || lines.length > MAX_HEADER_LINES
            || !lines[lines.length - 1].isEmpty()
            || !lines[lines.length - 2].isEmpty()) {
            throw new IOException("invalid_sip_headers");
        }
        // A bare LF/CR within any line is not SIP CRLF framing.
        for (String line : lines) {
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IOException("invalid_sip_crlf_framing");
            }
        }
        String[] status = lines[0].split(" ", 3);
        if (status.length != 3 || !status[0].equals("SIP/2.0")
            || status[1].length() != 3 || !decimal(status[1])) {
            throw new IOException("invalid_sip_response_status");
        }
        int code = Integer.parseInt(status[1]);
        if (code < 200 || code > 699) {
            throw new IOException("non_final_sip_response_unsupported");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length - 2; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new IOException("sip_header_folding_rejected");
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon >= line.length() - 1) {
                throw new IOException("invalid_sip_header");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            for (int j = 0; j < name.length(); j++) {
                char c = name.charAt(j);
                if (!((c >= 'a' && c <= 'z') || c == '-')) {
                    throw new IOException("invalid_sip_header_name");
                }
            }
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || headers.putIfAbsent(name, value) != null) {
                throw new IOException("duplicate_or_empty_sip_header");
            }
        }
        if (!"0".equals(headers.get("content-length"))
            || !transaction.callId().equals(headers.get("call-id"))
            || !"1 OPTIONS".equals(headers.get("cseq"))) {
            throw new IOException("sip_transaction_mismatch_or_body_rejected");
        }
        String via = headers.get("via");
        String expectedVia = "SIP/2.0/TLS client.invalid;branch=" + transaction.branch();
        if (via == null || !(via.equals(expectedVia)
            || via.startsWith(expectedVia + ";"))) {
            throw new IOException("sip_via_branch_mismatch");
        }
        // This is only the server response to a no-call OPTIONS probe.
        return new ResponseStatus(code, code == 200);
    }

    private static String hexToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static boolean decimal(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return !value.isEmpty();
    }
}
