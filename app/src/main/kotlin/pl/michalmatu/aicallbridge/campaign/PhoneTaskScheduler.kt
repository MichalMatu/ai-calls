package pl.michalmatu.aicallbridge.campaign

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore

/** Serial private IO, shared by UI/alarm/service; no phone numbers or transcripts in Intents. */
internal object PhoneTaskWork {
    val io = Executors.newSingleThreadExecutor()
    val main = Handler(Looper.getMainLooper())
    val generation = AtomicLong(0)
    @Volatile var activeTaskId: String? = null
    @Volatile var activeAttemptId: String? = null
    private var recovered = false // Access on io only.

    fun store(context: Context) = AndroidConversationPreparationStore(context.applicationContext)

    fun recover(context: Context) {
        if (recovered) return
        store(context).updatePhoneTasks { PhoneTaskQueue.recover(it, System.currentTimeMillis()) }
        recovered = true
        PhoneTaskScheduler.reconcile(context)
    }

    /** Immediate fence before async persistence or resource cleanup. */
    fun invalidate() { generation.incrementAndGet() }

    fun stop(context: Context, taskId: String? = null, takeover: Boolean = false,
        issue: PhoneTaskIssue? = if (takeover) PhoneTaskIssue.TAKEOVER else null) {
        if (taskId == null || taskId == activeTaskId) invalidate()
        val app = context.applicationContext
        io.execute {
            runCatching {
                store(app).updatePhoneTasks { tasks -> tasks.map {
                    if (taskId == null || it.id == taskId) PhoneTaskQueue.interrupt(it,
                        System.currentTimeMillis(), cancel = !takeover,
                        issue = issue) else it
                } }
                PhoneTaskScheduler.reconcile(app)
            }.onFailure { PhoneTaskScheduler.attention(app) }
        }
    }
}

internal object PhoneTaskScheduler {
    const val ACTION_WAKE = "pl.michalmatu.aicallbridge.PHONE_TASK_WAKE"
    private const val REQUEST = 4210
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, PhoneTaskAlarmReceiver::class.java).setAction(ACTION_WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** One replaceable OS alarm: survives process death and never carries authorization. */
    fun reconcile(context: Context, deferUntil: Long = 0): Boolean {
        val alarm = requireNotNull(context.getSystemService(AlarmManager::class.java))
        val store = PhoneTaskWork.store(context)
        val alarmAllowed = allowed(context)
        val now = System.currentTimeMillis()
        val tasks = store.updatePhoneTasks { tasks -> tasks.map { task ->
            // A grant can return while the process is dead; the broadcast is not a
            // guaranteed single source of recovery. Persist restoration before arming.
            PhoneTaskQueue.settle(
                if (alarmAllowed) PhoneTaskQueue.resumeAlarmPermission(task) else task, now)
        } }
        val next = tasks.mapNotNull(PhoneTaskQueue::nextAt).minOrNull()
        if (next == null) { alarm.cancel(pending(context)); return true }
        if (!alarmAllowed) {
            store.updatePhoneTasks { list -> list.map {
                if (it.state == PhoneTaskState.READY && it.recipients.none { r ->
                    r.state == PhoneRecipientState.IN_FLIGHT })
                    it.copy(state = PhoneTaskState.PAUSED, issue = PhoneTaskIssue.ALARM_PERMISSION) else it
            } }
            alarm.cancel(pending(context))
            attention(context)
            return false
        }
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                maxOf(next, deferUntil, System.currentTimeMillis() + 1_000L), pending(context))
        } catch (_: SecurityException) {
            // Permission can be revoked between canScheduleExactAlarms and setExact.
            alarm.cancel(pending(context))
            store.updatePhoneTasks { list -> list.map {
                if (it.state == PhoneTaskState.READY) it.copy(
                    state = PhoneTaskState.PAUSED, issue = PhoneTaskIssue.ALARM_PERMISSION) else it
            } }
            attention(context)
            return false
        }
        return true
    }

    fun attention(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel("phone_tasks", context.getString(
            R.string.task_notifications), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, REQUEST,
            Intent(context, PhoneTasksActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Lock-screen text has no recipient, task goal or answer.
        runCatching { manager.notify(REQUEST, Notification.Builder(context, "phone_tasks")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.task_attention))
            .setContentText(context.getString(R.string.task_open_details))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open).setAutoCancel(true).build()) }
    }
}

/** Boot/package/time events only restore alarms; they never directly dial or start a mic FGS. */
class PhoneTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wake = intent.action == PhoneTaskScheduler.ACTION_WAKE
        if (!wake && intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        val app = context.applicationContext
        PhoneTaskWork.io.execute {
            try {
                PhoneTaskWork.recover(app)
                val now = System.currentTimeMillis()
                val tasks = PhoneTaskWork.store(app).updatePhoneTasks { list ->
                    list.map { PhoneTaskQueue.settle(it, now) }
                }
                if (wake && tasks.any { PhoneTaskQueue.nextAt(it)?.let { due -> due <= now } == true }) {
                    try {
                        app.startForegroundService(Intent(app, AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_RUN_PHONE_TASKS))
                    } catch (_: RuntimeException) {
                        PhoneTaskWork.store(app).updatePhoneTasks { list -> list.map {
                            if (it.state == PhoneTaskState.READY &&
                                PhoneTaskQueue.nextAt(it)?.let { due -> due <= now } == true)
                                it.copy(state = PhoneTaskState.PAUSED, issue = PhoneTaskIssue.BACKGROUND_START) else it
                        } }
                        PhoneTaskScheduler.attention(app)
                        PhoneTaskScheduler.reconcile(app)
                    }
                } else PhoneTaskScheduler.reconcile(app)
            } catch (_: Exception) {
                PhoneTaskScheduler.attention(app)
            } finally { pending.finish() }
        }
    }
}
