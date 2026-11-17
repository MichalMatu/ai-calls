// Prototype JNI for PJSIP 2.17. The rdata pointer may ONLY be used inside
// the synchronous Account.onIncomingCall() callback; it becomes invalid after
// callback return. The trusted transport handle comes from Endpoint's
// TLS onTransportState() event with verified peer certificate evidence.
#include <jni.h>
#include <stdint.h>
#include <pjsip/sip_transport.h>
#include <pjsip/sip_transport_tls.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_pl_michalmatu_aicalls_sip_S8Pjsua2TlsAudit_nativeRxTransportMatchesVerifiedTls(
    JNIEnv* /*env*/, jclass /*klass*/, jlong rx_pointer, jlong trusted_transport)
{
    if (rx_pointer == 0 || trusted_transport == 0)
        return JNI_FALSE;

    const auto* rdata = reinterpret_cast<const pjsip_rx_data*>(
        static_cast<uintptr_t>(rx_pointer));
    const pjsip_transport* transport = rdata->tp_info.transport;
    if (transport == nullptr ||
        reinterpret_cast<uintptr_t>(transport) !=
            static_cast<uintptr_t>(trusted_transport))
        return JNI_FALSE;

    const auto type = static_cast<pjsip_transport_type_e>(transport->key.type);
    if (type != PJSIP_TRANSPORT_TLS && type != PJSIP_TRANSPORT_TLS6)
        return JNI_FALSE;
    if (!PJSIP_TRANSPORT_IS_SECURE(transport))
        return JNI_FALSE;
    return JNI_TRUE;
}
