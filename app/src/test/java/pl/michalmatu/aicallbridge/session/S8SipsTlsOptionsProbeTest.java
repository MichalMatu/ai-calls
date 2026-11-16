package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;
import org.junit.Test;

/** Proves fail-closed checks occur before any DNS query, TLS or SIP bytes. */
public final class S8SipsTlsOptionsProbeTest {
    @Test public void cannotContactAnyHostWithoutExplicitOwnerApproval() {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        assertThrows(SecurityException.class, () ->
            S8SipsTlsOptionsProbe.query(target, 1200, false));
        assertThrows(SecurityException.class, () ->
            S8SipsTlsOptionsProbe.query(null, 1200, false));
    }

    @Test public void rejectsUnboundedTimeoutAndForgedEndpointsBeforeDNS() {
        var endpoint = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(endpoint, 0, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(endpoint, 99_999, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(
                new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061), 1200, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(
                new S8SipsOptionsProtocol.Endpoint("example.org", 80), 1200, true));
    }
}
