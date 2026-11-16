package pl.michalmatu.aicallbridge.session;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Strict, OFFLINE SIP Digest SHA-256 subset (RFC 7616 + RFC 8760).
 *
 * This only builds a REGISTER Authorization header for later use within a
 * separately authenticated SIPS/TLS session. No socket, registration, INVITE,
 * media, Shizuku, credentials persistence, MD5 or algorithm downgrade.
 * Does NOT claim SIP mutual authentication or TLS channel binding.
 */
public final class S8SipDigestSha256 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CHALLENGE = 1536;

    public record Challenge(String realm, String nonce, String opaque) {}

    private S8SipDigestSha256() {}

    /** Parses the value of WWW-Authenticate, never a whole SIP response. */
    public static Challenge parseChallenge(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.startsWith("Digest ") || value.length() > MAX_CHALLENGE) {
            throw new IllegalArgumentException("digest_sha256_challenge_required");
        }
        Map<String, String> fields = new HashMap<>();
        String attrs = value.substring(7);
        int i = 0;
        while (i < attrs.length()) {
            while (i < attrs.length() && attrs.charAt(i) == ' ') i++;
            int keyStart = i;
            while (i < attrs.length() && isName(attrs.charAt(i))) i++;
            if (keyStart == i) throw new IllegalArgumentException("invalid_digest_attribute_name");
            String name = attrs.substring(keyStart, i).toLowerCase(Locale.ROOT);
            if (i == attrs.length() || attrs.charAt(i++) != '=') {
                throw new IllegalArgumentException("invalid_digest_attribute_assignment");
            }
            String parsed;
            if (i < attrs.length() && attrs.charAt(i) == '"') {
                i++;
                int begin = i;
                while (i < attrs.length() && attrs.charAt(i) != '"') {
                    if (!safeValue(attrs.charAt(i))) {
                        throw new IllegalArgumentException("unsafe_digest_challenge_value");
                    }
                    i++;
                }
                if (i == attrs.length()) {
                    throw new IllegalArgumentException("unterminated_digest_attribute");
                }
                parsed = attrs.substring(begin, i++);
            } else {
                int begin = i;
                while (i < attrs.length() && attrs.charAt(i) != ',') {
                    if (attrs.charAt(i) == ' ' || !safeValue(attrs.charAt(i))) {
                        throw new IllegalArgumentException("unsafe_digest_challenge_value");
                    }
                    i++;
                }
                parsed = attrs.substring(begin, i);
            }
            if (parsed.isEmpty() || fields.putIfAbsent(name, parsed) != null) {
                throw new IllegalArgumentException("duplicate_or_empty_digest_attribute");
            }
            if (i == attrs.length()) break;
            if (attrs.charAt(i++) != ',') {
                throw new IllegalArgumentException("invalid_digest_attribute_separator");
            }
            if (i == attrs.length()) throw new IllegalArgumentException("trailing_digest_comma");
        }
        // No unknown extensions until their security implications are reviewed.
        for (String key : fields.keySet()) {
            if (!key.equals("realm") && !key.equals("nonce") && !key.equals("qop")
                && !key.equals("algorithm") && !key.equals("opaque")
                && !key.equals("charset") && !key.equals("stale")
                && !key.equals("userhash")) {
                throw new IllegalArgumentException("unsupported_digest_attribute");
            }
        }
        if (!"SHA-256".equalsIgnoreCase(fields.get("algorithm"))) {
            // Never silently downgrade to MD5 or accept a missing default.
            throw new IllegalArgumentException("digest_sha256_algorithm_required");
        }
        if (fields.containsKey("charset")
            && !"UTF-8".equalsIgnoreCase(fields.get("charset"))) {
            throw new IllegalArgumentException("digest_utf8_required");
        }
        if (fields.containsKey("userhash")
            && !"false".equalsIgnoreCase(fields.get("userhash"))) {
            throw new IllegalArgumentException("digest_userhash_unsupported");
        }
        if (fields.containsKey("stale")
            && !"false".equalsIgnoreCase(fields.get("stale"))) {
            throw new IllegalArgumentException("digest_stale_retry_unsupported");
        }
        String realm = required(fields, "realm", 1, 128);
        String nonce = required(fields, "nonce", 8, 256);
        String qop = required(fields, "qop", 4, 64);
        boolean permitsAuth = false;
        for (String candidate : qop.split(",", -1)) {
            String selected = candidate.trim();
            if (selected.equalsIgnoreCase("auth")) permitsAuth = true;
            else if (!selected.equalsIgnoreCase("auth-int")) {
                throw new IllegalArgumentException("unsupported_digest_qop");
            }
        }
        if (!permitsAuth) throw new IllegalArgumentException("digest_auth_qop_required");
        String opaque = fields.get("opaque");
        if (opaque != null && opaque.length() > 256) {
            throw new IllegalArgumentException("digest_opaque_too_long");
        }
        return new Challenge(realm, nonce, opaque);
    }

    /** Fresh 128-bit nonce, only for a single attempted authenticated REGISTER. */
    public static String newCnonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        try {
            return hex(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /**
     * Pure Authorization header value, never a network write.
     * The password char[] belongs to the caller; it is never retained/mutated.
     * The returned value is itself sensitive (replayable while nonce is valid).
     */
    public static String registerAuthorization(
        Challenge challenge, String username, char[] password,
        S8SipsOptionsProtocol.Endpoint endpoint, String cnonce
    ) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(endpoint, "endpoint");
        S8SipsOptionsProtocol.Endpoint checked = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + endpoint.host() + ":" + endpoint.port()
        );
        if (!safeUsername(username) || password.length == 0 || password.length > 1024
            || !isHexCnonce(cnonce)) {
            throw new IllegalArgumentException("invalid_digest_credentials_or_cnonce");
        }
        String uri = "sips:" + checked.host()
            + (checked.port() == 5061 ? "" : ":" + checked.port());
        String realm = requiredValue(challenge.realm(), 1, 128);
        String nonce = requiredValue(challenge.nonce(), 8, 256);
        String opaque = challenge.opaque();
        if (opaque != null) requiredValue(opaque, 1, 256);

        byte[] passwordBytes = null;
        ByteBuffer buffer = null;
        String response;
        try {
            buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            passwordBytes = new byte[buffer.remaining()];
            buffer.get(passwordBytes);

            MessageDigest hash = sha256();
            hash.update((username + ":" + realm + ":").getBytes(StandardCharsets.UTF_8));
            hash.update(passwordBytes);
            String ha1 = hex(hash.digest());

            String ha2 = digestHex("REGISTER:" + uri);
            response = digestHex(ha1 + ":" + nonce + ":00000001:" + cnonce
                + ":auth:" + ha2);
        } finally {
            if (passwordBytes != null) Arrays.fill(passwordBytes, (byte) 0);
            if (buffer != null && buffer.hasArray()) Arrays.fill(buffer.array(), (byte) 0);
        }
        String header = "Digest username=\"" + username + "\", realm=\"" + realm
            + "\", nonce=\"" + nonce + "\", uri=\"" + uri
            + "\", algorithm=SHA-256, qop=auth, nc=00000001, cnonce=\""
            + cnonce + "\", response=\"" + response + "\"";
        if (opaque != null) header += ", opaque=\"" + opaque + "\"";
        return header;
    }

    private static String digestHex(String text) {
        return hex(sha256().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("sha256_unavailable", error);
        }
    }

    private static String hex(byte[] digest) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] chars = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            chars[i * 2] = alphabet[(digest[i] >>> 4) & 15];
            chars[i * 2 + 1] = alphabet[digest[i] & 15];
        }
        return new String(chars);
    }

    private static boolean safeUsername(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexCnonce(String value) {
        if (value == null || value.length() != 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String required(Map<String, String> fields, String key, int min, int max) {
        return requiredValue(fields.get(key), min, max);
    }

    private static String requiredValue(String value, int min, int max) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new IllegalArgumentException("missing_or_invalid_digest_value");
        }
        for (int i = 0; i < value.length(); i++) {
            if (!safeValue(value.charAt(i))) {
                throw new IllegalArgumentException("unsafe_digest_header_value");
            }
        }
        return value;
    }

    private static boolean safeValue(char c) {
        return c >= 0x20 && c <= 0x7e && c != '"' && c != '\\';
    }

    private static boolean isName(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '-';
    }
}
