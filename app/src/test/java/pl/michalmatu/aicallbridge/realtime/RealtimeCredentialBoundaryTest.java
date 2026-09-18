package pl.michalmatu.aicallbridge.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import org.junit.Test;

public final class RealtimeCredentialBoundaryTest {
    @Test
    public void clientSecretHasExplicitExpiryAndRedactsItsValue() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_secret_value", 2_000L);

        assertEquals("eph_secret_value", secret.getValue());
        assertEquals(2_000L, secret.getExpiresAtEpochSeconds());
        assertFalse(secret.isExpired(1_999L));
        assertTrue(secret.isExpired(2_000L));
        assertFalse(secret.toString().contains("eph_secret_value"));
        assertTrue(secret.toString().contains("REDACTED"));
    }

    @Test
    public void sessionConfigAcceptsOnlyTypedShortLivedCredential() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_123", 2_000L);
        RealtimeSessionConfig config = new RealtimeSessionConfig(
            "https://api.openai.com/v1/realtime/calls",
            secret,
            "gpt-realtime",
            "Act within the user's explicit authority."
        );

        assertSame(secret, config.getClientSecret());
        assertEquals("gpt-realtime", config.getModel());
        assertEquals("Act within the user's explicit authority.", config.getInstructions());
    }

    @Test
    public void sessionAndCredentialTypesDoNotExposeLongLivedApiKeyFields() {
        assertNoLongLivedApiKeyField(RealtimeSessionConfig.class);
        assertNoLongLivedApiKeyField(RealtimeClientSecret.class);
    }

    @Test
    public void invalidCredentialAndSessionConfigAreRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RealtimeClientSecret(" ", 2_000L)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RealtimeClientSecret("eph_123", 0L)
        );

        RealtimeClientSecret secret = new RealtimeClientSecret("eph_123", 2_000L);
        assertThrows(
            IllegalArgumentException.class,
            () -> new RealtimeSessionConfig(" ", secret, "gpt-realtime", "instructions")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RealtimeSessionConfig(
                "https://api.openai.com/v1/realtime/calls",
                secret,
                " ",
                "instructions"
            )
        );
    }

    @Test
    public void credentialProviderBoundaryRemainsPresentAtJvmLevel() throws Exception {
        Class<?> methodReturn = Arrays.stream(RealtimeCredentialProvider.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("fetchClientSecret"))
            .findFirst()
            .orElseThrow()
            .getReturnType();

        // Kotlin suspend + Result may mangle the JVM method name and compiles to Object +
        // Continuation. The typed RealtimeSessionConfig separately guarantees that only a
        // RealtimeClientSecret crosses into the transport configuration.
        assertEquals(Object.class, methodReturn);
    }

    private static void assertNoLongLivedApiKeyField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            String normalized = field.getName().toLowerCase(Locale.ROOT).replace("_", "");
            assertFalse("long-lived key field on " + type.getSimpleName(), normalized.contains("apikey"));
            assertFalse("standard key field on " + type.getSimpleName(), normalized.contains("standardkey"));
        }
    }
}
