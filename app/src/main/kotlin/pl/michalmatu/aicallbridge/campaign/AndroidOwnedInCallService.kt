package pl.michalmatu.aicallbridge.campaign

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.telecom.Call
import android.telecom.InCallService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Default-dialer only. It owns no unrelated call: those remain available to the human UI,
 * while autonomous teardown is restricted to the matched Call instance.
 */
class AndroidOwnedInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        observedCalls.addIfAbsent(call)
        val session = pendingSession.get()
        if (session != null && session.attachIfMatching(call, SystemClock.elapsedRealtime())) {
            // OnCallAdded may arrive after STOP. Session performs deferred targeted cleanup.
        }
        showCallNotification()
    }

    override fun onCallRemoved(call: Call) {
        observedCalls.remove(call)
        val session = pendingSession.get()
        if (session != null && session.isBoundTo(call)) {
            pendingSession.compareAndSet(session, null)
        }
        if (observedCalls.isEmpty()) {
            getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIFICATION)
        } else showCallNotification()
        super.onCallRemoved(call)
    }

    private fun showCallNotification() {
        val notifications = getSystemService(NotificationManager::class.java) ?: return
        notifications.createNotificationChannel(
            NotificationChannel("aicalls_incall", "Phone calls", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AndroidCallControlsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notifications.notify(
            CALL_NOTIFICATION,
            Notification.Builder(this, "aicalls_incall")
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Trwająca rozmowa")
                .setContentText("Otwórz panel rozmowy, aby odebrać lub zakończyć")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .build()
        )
    }

    companion object {
        const val OWNED_REQUEST_ID = "pl.michalmatu.aicallbridge.OWNED_REQUEST_ID"
        private const val CALL_NOTIFICATION = 3217
        private val pendingSession = AtomicReference<AndroidTelecomOwnedCallSession?>(null)
        private val observedCalls = CopyOnWriteArrayList<Call>()

        internal fun reserve(session: AndroidTelecomOwnedCallSession): Boolean {
            val old = pendingSession.get()
            if (old != null) {
                if (!old.expired(SystemClock.elapsedRealtime())) return false
                if (!pendingSession.compareAndSet(old, null)) return false
            }
            return pendingSession.compareAndSet(null, session)
        }

        internal fun abandon(session: AndroidTelecomOwnedCallSession) {
            pendingSession.compareAndSet(session, null)
        }

        internal fun currentCall(): Call? =
            observedCalls.firstOrNull { it.state == Call.STATE_RINGING } ?:
                observedCalls.firstOrNull { it.state != Call.STATE_DISCONNECTED }

        internal fun incomingCall(): Call? =
            observedCalls.firstOrNull { it.state == Call.STATE_RINGING }
    }
}
