package pl.michalmatu.aicallbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.time.Instant

class CapabilityProbe(private val context: Context) {
    fun run(): String {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val lines = mutableListOf<String>()

        lines += "probe_version=1"
        lines += "timestamp_utc=${Instant.now()}"
        lines += "manufacturer=${Build.MANUFACTURER}"
        lines += "brand=${Build.BRAND}"
        lines += "model=${Build.MODEL}"
        lines += "device=${Build.DEVICE}"
        lines += "product=${Build.PRODUCT}"
        lines += "android_release=${Build.VERSION.RELEASE}"
        lines += "sdk=${Build.VERSION.SDK_INT}"
        lines += "build_display=${Build.DISPLAY}"
        lines += "build_fingerprint=${Build.FINGERPRINT}"
        lines += "permission_record_audio=${permissionState(Manifest.permission.RECORD_AUDIO)}"
        lines += "permission_modify_audio_settings=${permissionState(Manifest.permission.MODIFY_AUDIO_SETTINGS)}"

        lines += "audio_mode=${audioModeName(audioManager.mode)}(${audioManager.mode})"
        lines += "microphone_muted=${audioManager.isMicrophoneMute}"
        lines += "speakerphone_on=${audioManager.isSpeakerphoneOn}"
        lines += "output_sample_rate=${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "unknown"}"
        lines += "output_frames_per_buffer=${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "unknown"}"

        if (Build.VERSION.SDK_INT >= 31) {
            val communicationDevice = audioManager.communicationDevice
            lines += "communication_device=${communicationDevice?.let(::deviceSummary) ?: "none"}"
            lines += "available_communication_devices=${audioManager.availableCommunicationDevices.joinToString(";") { deviceSummary(it) }.ifEmpty { "none" }}"
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL).sortedWith(
            compareBy<AudioDeviceInfo> { it.type }.thenBy { it.id },
        )
        lines += "audio_device_count=${devices.size}"
        devices.forEachIndexed { index, device ->
            lines += "audio_device[$index]=${deviceSummary(device)}"
        }

        val telephonyDevices = devices.filter { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
        lines += "type_telephony_count=${telephonyDevices.size}"
        lines += "type_telephony_present=${telephonyDevices.isNotEmpty()}"

        probeAudioSource(lines, "MIC", MediaRecorder.AudioSource.MIC)
        probeAudioSource(lines, "VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        probeAudioSource(lines, "VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION)
        probeAudioSource(lines, "VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL)
        probeAudioSource(lines, "VOICE_DOWNLINK", MediaRecorder.AudioSource.VOICE_DOWNLINK)
        probeAudioSource(lines, "VOICE_UPLINK", MediaRecorder.AudioSource.VOICE_UPLINK)

        val report = lines.joinToString("\n") + "\n"
        File(context.filesDir, REPORT_FILE_NAME).writeText(report)
        return report
    }

    private fun permissionState(permission: String): String =
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"

    private fun probeAudioSource(lines: MutableList<String>, label: String, source: Int) {
        val sampleRate = 16_000
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else 4096

        var record: AudioRecord? = null
        try {
            record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            val state = if (record.state == AudioRecord.STATE_INITIALIZED) "initialized" else "uninitialized"
            lines += "audio_source_$label=result:$state,state:${record.state},session:${record.audioSessionId},buffer:$bufferSize"
        } catch (error: Throwable) {
            val message = error.message?.replace('\n', ' ')?.replace('\r', ' ') ?: ""
            lines += "audio_source_$label=error:${error.javaClass.simpleName}:$message"
        } finally {
            try {
                record?.release()
            } catch (_: Throwable) {
                // Probe cleanup must not hide the primary result.
            }
        }
    }

    private fun deviceSummary(device: AudioDeviceInfo): String {
        val direction = when {
            device.isSource && device.isSink -> "source+sink"
            device.isSource -> "source"
            device.isSink -> "sink"
            else -> "neither"
        }
        val rates = device.sampleRates.joinToString(",").ifEmpty { "unspecified" }
        val channels = device.channelCounts.joinToString(",").ifEmpty { "unspecified" }
        return "id=${device.id},type=${audioDeviceTypeName(device.type)}(${device.type}),dir=$direction,product=${sanitize(device.productName.toString())},rates=$rates,channels=$channels"
    }

    private fun sanitize(value: String): String = value.replace('\n', ' ').replace('\r', ' ').replace(';', ',')

    private fun audioModeName(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        AudioManager.MODE_CALL_SCREENING -> "CALL_SCREENING"
        else -> "UNKNOWN"
    }

    private fun audioDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX_LINE"
        AudioDeviceInfo.TYPE_IP -> "IP"
        AudioDeviceInfo.TYPE_BUS -> "BUS"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "REMOTE_SUBMIX"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
        else -> "TYPE_$type"
    }

    companion object {
        const val REPORT_FILE_NAME = "capability-report.txt"
    }
}
