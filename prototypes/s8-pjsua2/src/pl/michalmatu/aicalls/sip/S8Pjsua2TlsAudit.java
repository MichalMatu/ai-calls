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
        final TlsInfo info = event.getTlsInfo();
        final SslCertInfo remote = info == null ? null : info.getRemoteCertInfo();
        final String subject = remote == null ? null : remote.getSubjectCn();
        if (!S8Pjsua2TlsEvidencePolicy.trusted(
            isTlsType(event.getType()),
            event.getState() == pjsip_transport_state.PJSIP_TP_STATE_CONNECTED,
            event.getLastError(),
            info != null && info.getEstablished(),
            info == null ? -1L : info.getVerifyStatus(),
            subject != null && !subject.isBlank()
        )) return;
        if (!closed) verifiedHandles.add(handle);
    }

    /** Only call inside the live synchronous onIncomingCall() callback. */
    public long verifiedTransportForIncoming(SipRxData sip) {
        if (closed || sip == null || verifiedHandles.isEmpty()) return 0L;
        final long rxData = sip.getPjRxData();
        if (rxData == 0L || !S8Pjsua2SrtpAudit.loadPrototypeVerifier()) return 0L;
        for (Long handle : verifiedHandles) {
            if (closed || !verifiedHandles.contains(handle)) continue;
            try {
                if (nativeRxTransportMatchesVerifiedTls(rxData, handle) &&
                    !closed && verifiedHandles.contains(handle)) return handle;
            } catch (RuntimeException | LinkageError ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /** Persist only this opaque transport handle, never raw SIP pointers. */
    public boolean stillVerified(long handle) {
        return !closed && handle != 0L && verifiedHandles.contains(handle);
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
