package pl.michalmatu.aicallbridge.session

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification direct-reply entrypoint; strictly private, short-lived and user initiated. */
class LocalAdbPairingReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!GsmShellMediaProtocol.supportedPackage(context.packageName) ||
            intent.action != LocalAdbPairingNotification.ACTION_REPLY) return
        if (!LocalAdbPairingNotification.consume(
                context, intent.getStringExtra(LocalAdbPairingNotification.EXTRA_TOKEN))) return
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(LocalAdbPairingNotification.REMOTE_INPUT_KEY)?.toString()
            .orEmpty()
        if (!LocalAdbGsmPolicy.pairingCode(code)) {
            LocalAdbPairingNotification.result(context,
                LocalAdbGsmController.Outcome.INVALID_INPUT)
            return
        }
        // Work is only the one-time ADB pairing. Bootstrap remains explicit
        // in the Activity, avoiding a long-running broadcast or background daemon.
        val pending = goAsync()
        val application = context.applicationContext
        Thread({
            try {
                LocalAdbPairingNotification.result(application,
                    LocalAdbGsmController(application).pair(code))
            } finally {
                pending.finish()
            }
        }, "aicalls-local-pair-once").apply { isDaemon = true }.start()
    }
}
