package pl.michalmatu.aicalls.sip;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.pjsip.pjsua2.OnTransportStateParam;
import org.pjsip.pjsua2.SipRxData;
import org.pjsip.pjsua2.SslCertInfo;
import org.pjsip.pjsua2.TlsInfo;
import org.pjsip.pjsua2.pjsip_transport_state;

/**
 * Tracks only established PJSIP TLS transports with a verified peer certificate.
 *
 * Endpoint.onTransportState() MUST feed every event. During the *synchronous*
 * Account.onIncomingCall() callback, native code compares the actual received
 * pjsip_rx_data transport pointer with this certified, live transport handle.
 * No SIP header, remote URI, transport name or configuration flag alone can
 * authorize an incoming call. Missing JNI or native state always denies.
 *
 * Prototype-only; never package before security and licensing review.
 */
public final class S8Pjsua2TlsAudit implements AutoCloseable {
    private final Set<Long> verifiedHandles = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public void onTransportState(OnTransportStateParam event) {
        if (closed || event == null) return;
        final long handle = event.getHnd();
        if (handle == 0L) return;
        verifiedHandles.remove(handle); // Includes DISCONNECTED / SHUTDOWN.
        if (event.getState() != pjsip_transport_state.PJSIP_TP_STATE_CONNECTED ||
            event.getLastError() != 0 || !isTlsType(event.getType())) return;

        final TlsInfo info = event.getTlsInfo();
        if (info == null || !info.getEstablished() || info.getVerifyStatus() != 0L)
            return;
        final SslCertInfo remote = info.getRemoteCertInfo();
        if (remote == null) return;
        final String subject = remote.getSubjectCn();
        if (subject == null || subject.isBlank()) {
            // Never consider missing peer identity as a successful verification.
            return;
        }
        if (!closed) verifiedHandles.add(handle);
    }

    /** Only call with SipRxData that is still alive inside onIncomingCall(). */
    public boolean verifiedIncomingNow(SipRxData sip) {
        if (closed || sip == null || verifiedHandles.isEmpty()) return false;
        final long rxData = sip.getPjRxData();
        if (rxData == 0L || !S8Pjsua2SrtpAudit.loadPrototypeVerifier()) return false;
        for (Long handle : verifiedHandles) {
            if (closed || !verifiedHandles.contains(handle)) return false;
            try {
                if (nativeRxTransportMatchesVerifiedTls(rxData, handle) &&
                    !closed && verifiedHandles.contains(handle)) return true;
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isTlsType(String type) {
        return type != null &&
            ("tls".equalsIgnoreCase(type) || "tls6".equalsIgnoreCase(type));
    }

    @Override public void close() {
        closed = true;
        verifiedHandles.clear();
    }

    private static native boolean nativeRxTransportMatchesVerifiedTls(
        long liveRxDataPointer, long verifiedTransportHandle
    );
}
