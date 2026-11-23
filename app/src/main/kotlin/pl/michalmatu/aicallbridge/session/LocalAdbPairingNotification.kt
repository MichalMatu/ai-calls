package pl.michalmatu.aicallbridge.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.util.UUID
import pl.michalmatu.aicallbridge.R

/**
 * Keeps the system's Wi-Fi pairing dialog on screen:
 * the user replies to a notification rather than switching to AI Calls.
 * No pairing code is written to preferences, notification text, or logcat.
 */
internal object LocalAdbPairingNotification {
    const val ACTION_REPLY = "pl.michalmatu.aicallbridge.session.LOCAL_ADB_PAIR_REPLY"
    const val EXTRA_TOKEN = "local_adb_pair_token"
    const val REMOTE_INPUT_KEY = "local_adb_code"
    const val CHANNEL_ID = "ai_calls_gsm_pair_v1"
    const val NOTIFICATION_ID = 74142
    private const val PREF_NAME = "local_adb_pair_reply_session"
    private const val TOKEN = "token"
    private const val EXPIRES_AT = "expires_at_elapsed"
    private const val EXPIRY_MS = 180_000L

    fun canNotify(context: Context): Boolean {
        if (!GsmShellMediaProtocol.supportedPackage(context.packageName)) return false
        val permission = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return permission &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun begin(context: Context): Boolean {
        if (!canNotify(context)) return false
        val nonce = UUID.randomUUID().toString()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, nonce)
            .putLong(EXPIRES_AT, SystemClock.elapsedRealtime() + EXPIRY_MS)
            .apply()
        val intent = Intent(context, LocalAdbPairingReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_TOKEN, nonce)
        }
        // MUTABLE is required specifically for system RemoteInput direct reply.
        val pending = PendingIntent.getBroadcast(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val reply = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(context.getString(R.string.local_adb_notification_enter_code))
            .build()
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_edit,
            context.getString(R.string.local_adb_notification_enter_code),
            pending
        ).addRemoteInput(reply).build()
        val builder = builder(context)
            .setContentTitle(context.getString(R.string.local_adb_notification_title))
            .setContentText(context.getString(R.string.local_adb_notification_howto))
            .setOngoing(true)
            .addAction(action)
        return runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, builder.build())
            true
        }.getOrDefault(false)
    }

    /** One-time code acceptance; stale notifications and replay are rejected. */
    fun consume(context: Context, supplied: String?): Boolean {
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expected = preferences.getString(TOKEN, null)
        val expiresAt = preferences.getLong(EXPIRES_AT, 0L)
        val now = SystemClock.elapsedRealtime()
        if (expected == null || supplied == null || supplied != expected ||
            now <= 0L || expiresAt <= now || expiresAt - now > EXPIRY_MS) return false
        return preferences.edit().remove(TOKEN).remove(EXPIRES_AT).commit()
    }

    fun result(context: Context, status: LocalAdbGsmController.Outcome) {
        if (!canNotify(context)) return
        val label = when (status) {
            LocalAdbGsmController.Outcome.PAIRED -> R.string.local_adb_notification_paired
            LocalAdbGsmController.Outcome.INVALID_INPUT -> R.string.local_adb_invalid
            LocalAdbGsmController.Outcome.NOT_DISCOVERED -> R.string.local_adb_not_discovered
            else -> R.string.local_adb_failed
        }
        val open = Intent(context, LocalAdbGsmSetupActivity::class.java)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID,
                    builder(context)
                        .setContentTitle(context.getString(R.string.local_adb_notification_title))
                        .setContentText(context.getString(label))
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build())
        }
    }

    private fun builder(context: Context): Notification.Builder {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.local_adb_notification_title),
            NotificationManager.IMPORTANCE_DEFAULT))
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
    }
}
