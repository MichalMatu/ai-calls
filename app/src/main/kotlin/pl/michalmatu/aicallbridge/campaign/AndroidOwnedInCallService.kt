package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import pl.michalmatu.aicallbridge.incoming.AndroidIncomingAssistant
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantPolicyStore
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import android.content.Context
import android.os.SystemClock
import android.telecom.Call
import android.telecom.InCallService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Default-dialer only. It owns no unrelated call: those remain available to the human UI,
 * while autonomous teardown is restricted to the matched Call instance.
 */
class AndroidOwnedInCallService : InCallService() {
    private val incoming by lazy { AndroidIncomingAssistant(applicationContext) { observedCalls.toList() } }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        observedCalls.addIfAbsent(call)
        incomingAssistant = incoming
        // Call.Details (number, SIM, direction) may be incomplete in onCallAdded.
        // Retry exact-match binding when Telecom publishes new details/state.
        val callback = object : Call.Callback() {
            override fun onDetailsChanged(changed: Call, details: Call.Details) {
                tryAttachExactSession(changed)
            }
            override fun onStateChanged(changed: Call, state: Int) {
                tryAttachExactSession(changed, state)
            }
        }
        callCallbacks.put(call, callback)?.let { old -> call.unregisterCallback(old) }
        call.registerCallback(callback)
        tryAttachExactSession(call)
        showCallNotification()

    }

    private fun tryAttachExactSession(call: Call, state: Int = call.state) {
        pendingSession.get()?.attachIfMatching(call, SystemClock.elapsedRealtime())
        pendingSession.get()?.observeState(call, state)
        observedCalls.forEach(incoming::observe)
    }

    override fun onCallRemoved(call: Call) {
        callCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        observedCalls.remove(call)
        incoming.removed(call)
        val session = pendingSession.get()
        if (session != null && session.isBoundTo(call)) {
            session.onExactCallRemoved(call)
            pendingSession.compareAndSet(session, null)
        }
        if (observedCalls.isEmpty()) {
            getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIFICATION)
        } else showCallNotification()
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        if (incomingAssistant === incoming) incomingAssistant = null
        incoming.close()
        super.onDestroy()
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
        // Android 13+ can deny notifications. Never crash call ownership or TX teardown.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return
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
        @Volatile private var incomingAssistant: AndroidIncomingAssistant? = null
        internal fun incomingPolicyChanged() { incomingAssistant?.policyChanged() }
        internal fun takeOverIncoming(call: Call) { incomingAssistant?.takeOver(call) }
        internal fun resumeIncoming(call: Call): Boolean = incomingAssistant?.resume(call) == true
        internal fun incomingState(call: Call): String? = incomingAssistant?.snapshot(call)?.let {
            it.state.name + (it.reason?.let { reason -> ":$reason" } ?: "")
        }
        internal fun incomingStatus(): String = incomingAssistant?.status ?: "MANUAL"
        internal fun stopIncomingAi(context: Context): Boolean {
            // Revoke the live generation before opening persistent settings.
            incomingAssistant?.stopAll()
            return runCatching { IncomingAssistantPolicyStore(context).disable(); true }.getOrDefault(false)
        }

        private val pendingSession = AtomicReference<AndroidTelecomOwnedCallSession?>(null)
        private val observedCalls = CopyOnWriteArrayList<Call>()
        private val callCallbacks = ConcurrentHashMap<Call, Call.Callback>()

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
