package pl.michalmatu.aicallbridge.ui

import pl.michalmatu.aicallbridge.R

/** Human names for public Android facts. Unknown types stay unknown in the technical report. */
internal object CapabilityLabels {
    val sources = linkedMapOf("MIC" to R.string.cap_source_mic,
        "VOICE_COMMUNICATION" to R.string.cap_source_communication,
        "VOICE_RECOGNITION" to R.string.cap_source_recognition,
        "VOICE_CALL" to R.string.cap_source_call,
        "VOICE_DOWNLINK" to R.string.cap_source_downlink,
        "VOICE_UPLINK" to R.string.cap_source_uplink)

    fun device(type: String): Int = when (type) {
        "BUILTIN_EARPIECE" -> R.string.cap_earpiece
        "BUILTIN_SPEAKER", "BUILTIN_SPEAKER_SAFE" -> R.string.cap_speaker
        "BUILTIN_MIC" -> R.string.cap_mic
        "TELEPHONY" -> R.string.cap_telephony
        "REMOTE_SUBMIX" -> R.string.cap_submix
        "BLUETOOTH_SCO", "BLE_HEADSET" -> R.string.cap_bt_sco
        "BLUETOOTH_A2DP", "BLE_SPEAKER", "BLE_BROADCAST" -> R.string.cap_bt_a2dp
        "WIRED_HEADSET" -> R.string.cap_wired_headset
        "WIRED_HEADPHONES" -> R.string.cap_wired_headphones
        "USB_DEVICE", "USB_ACCESSORY", "USB_HEADSET" -> R.string.cap_usb
        "HDMI", "HDMI_ARC", "HDMI_EARC" -> R.string.cap_hdmi
        else -> R.string.cap_device_unknown
    }

    fun mode(raw: String?): Int = when (raw?.substringBefore('(')) {
        "NORMAL" -> R.string.cap_mode_normal
        "RINGTONE" -> R.string.cap_mode_ringing
        "IN_CALL" -> R.string.cap_mode_call
        "IN_COMMUNICATION" -> R.string.cap_mode_communication
        "CALL_SCREENING" -> R.string.cap_mode_screening
        else -> R.string.cap_ui_unknown
    }
}
