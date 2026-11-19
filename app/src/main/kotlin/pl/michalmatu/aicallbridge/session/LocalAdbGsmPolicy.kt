package pl.michalmatu.aicallbridge.session

/** Immutable, bounded shell commands. No caller-provided command or remote target. */
internal object LocalAdbGsmPolicy {
    const val LOOPBACK = "127.0.0.1"
    const val STATUS = "content call --uri content://pl.michalmatu.aicallbridge.s8lab.gsmbinder --method status"
    const val START = "sh -c 'APP=pl.michalmatu.aicallbridge.s8lab; " +
        "APK=\"\$(pm path \"\$APP\" | sed -n \"s/^package://p\" | head -n 1)\"; " +
        "test -n \"\$APK\" && test -r \"\$APK\" || exit 7; " +
        "CLASSPATH=\"\$APK\" nohup app_process /system/bin " +
        "--nice-name=aicalls_gsm_binder pl.michalmatu.aicallbridge.session.GsmShellBinderMediaHost " +
        "</dev/null >/dev/null 2>&1 &'"

    fun port(input: String): Int? =
        input.takeIf { it.length in 1..5 && it.all { digit -> digit in '0'..'9' } }
            ?.toIntOrNull()?.takeIf { it in 1..65535 }

    fun pairingCode(input: String): Boolean =
        input.length == 6 && input.all { it in '0'..'9' }

    fun deviceApproved(model: String, release: String, boot: String, uid: String): Boolean =
        model.trim() == "SM-S906B" && release.trim() == "16" &&
            boot.trim() == "1" && uid.trim() == "2000"

    fun callIdle(telephony: String, audio: String): Boolean {
        val states = Regex("""mCallState=(\d+)""").findAll(telephony).map { it.groupValues[1] }.toList()
        return states.isNotEmpty() && states.all { it == "0" } &&
            Regex("""Actual mode = MODE_NORMAL\b""").containsMatchIn(audio)
    }

    fun hostRegistered(response: String): Boolean = response.contains("host_registered=true")
}
