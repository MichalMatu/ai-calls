// Experimental PJSIP 2.17 Android arm64 JNI extension. Never packaged into
// the product APK while PJSIP distribution/license gate is unresolved.
// No key material, crypto suite names or SIP identifiers leave this function.
#include <jni.h>
#include <pjsua-lib/pjsua.h>
#include <pjmedia/transport.h>
#include <pjmedia/transport_srtp.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_pl_michalmatu_aicalls_sip_S8Pjsua2SrtpAudit_nativeSrtpActive(
    JNIEnv* /*env*/, jclass /*clazz*/, jint call_id, jint media_idx)
{
    if (call_id < 0 || media_idx < 0 || media_idx > 15)
        return JNI_FALSE;

    pjmedia_transport_info info;
    pjmedia_transport_info_init(&info);

    const pj_status_t result = pjsua_call_get_med_transport_info(
        static_cast<pjsua_call_id>(call_id),
        static_cast<unsigned>(media_idx),
        &info
    );
    if (result != PJ_SUCCESS)
        return JNI_FALSE;

    // PJSIP's SRTP layer sits above UDP/ICE. Do NOT infer security from
    // SDP's RTP/SAVP or PJSUA2's AccountMediaConfig alone.
    for (unsigned i = 0; i < info.specific_info_cnt; ++i) {
        const pjmedia_transport_specific_info& part = info.spc_info[i];
        if (part.type != PJMEDIA_TRANSPORT_TYPE_SRTP ||
            part.cbsize < static_cast<int>(sizeof(pjmedia_srtp_info)))
            continue;
        const auto* srtp =
            reinterpret_cast<const pjmedia_srtp_info*>(part.buffer);

        if (srtp->active == PJ_TRUE &&
            srtp->use == PJMEDIA_SRTP_MANDATORY &&
            srtp->rx_policy.name.slen > 0 &&
            srtp->tx_policy.name.slen > 0) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}
