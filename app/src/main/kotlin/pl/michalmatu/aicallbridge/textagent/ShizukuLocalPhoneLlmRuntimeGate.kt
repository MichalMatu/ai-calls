package pl.michalmatu.aicallbridge.textagent

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import pl.michalmatu.aicallbridge.shizuku.ILocalPhoneLlmRuntimeService
import pl.michalmatu.aicallbridge.shizuku.LocalPhoneLlmRuntimeUserService
import rikka.shizuku.Shizuku

/** App-side owner for the fixed phone-local llama.cpp Shizuku UserService. */
internal class ShizukuLocalPhoneLlmRuntimeGate(context: Context) : LocalPhoneLlmRuntimeGate {
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, LocalPhoneLlmRuntimeUserService::class.java),
    )
        .daemon(false)
        .processNameSuffix("phone_llm")
        .tag("phone-llm-runtime-v1")
        .version(1)
        .debuggable(false)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "aicall-phone-llm-runtime").apply { isDaemon = true }
    }
    private val lock = Any()
    private var generation = 0L
    private var pending: Request? = null
    private var connection: Connection? = null
    private var closed = false

    override fun ensureReady(listener: LocalPhoneLlmRuntimeGate.Listener) {
        val request: Request
        synchronized(lock) {
            if (closed) {
                listener.onError("runtime_gate_closed")
                return
            }
            generation += 1
            request = Request(generation, listener)
            pending = request
        }
        execute { ensureOrBind(request) }
    }

    override fun cancel() {
        synchronized(lock) {
            generation += 1
            pending = null
        }
    }

    override fun close() {
        val connectionToClose: Connection?
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            pending = null
            connectionToClose = connection
            connection = null
        }
        if (connectionToClose != null) {
            execute { closeConnection(connectionToClose) }
        }
        executor.shutdown()
    }

    private fun ensureOrBind(request: Request) {
        if (!isCurrent(request)) return

        val existing = synchronized(lock) { connection }
        val service = existing?.service
        if (service != null) {
            ensureRemoteReady(request, existing, service)
            return
        }
        if (existing != null) {
            return
        }

        val created = Connection()
        synchronized(lock) {
            if (!isCurrentLocked(request) || connection != null) return
            connection = created
        }
        beginBind(created)
    }

    private fun beginBind(target: Connection) {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                failConnection(target, "runtime_shizuku_unavailable")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                failConnection(target, "runtime_shizuku_permission_required")
                return
            }

            val timeout = executor.schedule(
                { failConnection(target, "runtime_bind_timeout") },
                BIND_TIMEOUT_MS,
                TimeUnit.MILLISECONDS,
            )
            synchronized(lock) {
                if (connection !== target || closed) {
                    timeout.cancel(false)
                    return
                }
                target.bindTimeout = timeout
            }
            Shizuku.bindUserService(userServiceArgs, target)
        } catch (_: Throwable) {
            failConnection(target, "runtime_bind_failed")
        }
    }

    private fun ensureRemoteReady(
        request: Request,
        target: Connection,
        service: ILocalPhoneLlmRuntimeService,
    ) {
        if (!isCurrent(request) || !isCurrent(target)) return
        try {
            if (service.processUid != SHELL_UID) {
                failConnection(target, "runtime_wrong_uid")
                return
            }
            if (!service.ensureReady()) {
                failRequest(request, "runtime_start_failed")
                return
            }
            completeReady(request)
        } catch (_: Throwable) {
            if (target.binder?.isBinderAlive == false) {
                failConnection(target, "runtime_service_disconnected")
            } else {
                failRequest(request, "runtime_start_failed")
            }
        }
    }

    private fun completeReady(request: Request) {
        val listener = synchronized(lock) {
            if (!isCurrentLocked(request)) return
            pending = null
            request.listener
        }
        listener.onReady()
    }

    private fun failRequest(request: Request, reason: String) {
        val listener = synchronized(lock) {
            if (!isCurrentLocked(request)) return
            pending = null
            request.listener
        }
        listener.onError(reason)
    }

    private fun failConnection(target: Connection, reason: String) {
        val listener = synchronized(lock) {
            if (connection !== target) return
            connection = null
            target.cancelTimeout()
            target.detachDeathRecipient()
            val current = pending
            if (current != null && current.generation == generation) {
                pending = null
                current.listener
            } else {
                null
            }
        }
        try {
            Shizuku.unbindUserService(userServiceArgs, target, true)
        } catch (_: Throwable) {
            // Best-effort removal; a dead UserService needs no further cleanup.
        }
        listener?.onError(reason)
    }

    private fun closeConnection(target: Connection) {
        try {
            target.service?.stopServer()
        } catch (_: Throwable) {
            // UserService removal below is the final process-level cleanup boundary.
        }
        target.cancelTimeout()
        target.detachDeathRecipient()
        try {
            Shizuku.unbindUserService(userServiceArgs, target, true)
        } catch (_: Throwable) {
            // Closing must remain idempotent even if Shizuku already removed the service.
        }
    }

    private fun isCurrent(request: Request): Boolean = synchronized(lock) {
        isCurrentLocked(request)
    }

    private fun isCurrentLocked(request: Request): Boolean =
        !closed && pending === request && request.generation == generation

    private fun isCurrent(target: Connection): Boolean = synchronized(lock) {
        !closed && connection === target
    }

    private fun execute(action: () -> Unit) {
        try {
            executor.execute(action)
        } catch (_: RejectedExecutionException) {
            // A concurrent close intentionally invalidates pending work.
        }
    }

    private data class Request(
        val generation: Long,
        val listener: LocalPhoneLlmRuntimeGate.Listener,
    )

    private inner class Connection : ServiceConnection {
        var service: ILocalPhoneLlmRuntimeService? = null
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var bindTimeout: ScheduledFuture<*>? = null

        override fun onServiceConnected(name: ComponentName?, connectedBinder: IBinder?) {
            if (connectedBinder == null || !isCurrent(this)) {
                if (connectedBinder == null) failConnection(this, "runtime_bind_failed")
                return
            }
            val connectedService = ILocalPhoneLlmRuntimeService.Stub.asInterface(connectedBinder)
            val recipient = IBinder.DeathRecipient {
                execute { failConnection(this, "runtime_service_disconnected") }
            }
            try {
                connectedBinder.linkToDeath(recipient, 0)
            } catch (_: Throwable) {
                failConnection(this, "runtime_service_disconnected")
                return
            }

            synchronized(lock) {
                if (connection !== this || closed) {
                    try { connectedBinder.unlinkToDeath(recipient, 0) } catch (_: Throwable) {}
                    return
                }
                service = connectedService
                binder = connectedBinder
                deathRecipient = recipient
                cancelTimeout()
            }
            execute {
                val request = synchronized(lock) { pending }
                if (request != null) {
                    ensureRemoteReady(request, this, connectedService)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            execute { failConnection(this, "runtime_service_disconnected") }
        }

        fun cancelTimeout() {
            val timeout = bindTimeout
            bindTimeout = null
            timeout?.cancel(false)
        }

        fun detachDeathRecipient() {
            val currentBinder = binder
            val currentRecipient = deathRecipient
            binder = null
            deathRecipient = null
            service = null
            if (currentBinder != null && currentRecipient != null) {
                try { currentBinder.unlinkToDeath(currentRecipient, 0) } catch (_: Throwable) {}
            }
        }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 10_000L
        const val SHELL_UID = 2000
    }
}
