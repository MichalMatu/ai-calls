package pl.michalmatu.aicallbridge.session;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import pl.michalmatu.aicallbridge.shizuku.IShizukuCallMediaService;
import pl.michalmatu.aicallbridge.shizuku.ShizukuCallMediaUserService;
import rikka.shizuku.Shizuku;

/** Production app-side Shizuku adapter for the frozen privileged call-media AIDL contract. */
public final class ShizukuCallMediaSessionBackend
    implements CallMediaSessionBackend, AutoCloseable {

    private static final long BIND_TIMEOUT_MS = 10_000L;

    private final Shizuku.UserServiceArgs userServiceArgs;
    private final ScheduledExecutorService controlExecutor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aicall-shizuku-control");
            thread.setDaemon(true);
            return thread;
        });

    private Binding activeBinding;
    private boolean closed;

    public ShizukuCallMediaSessionBackend(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        userServiceArgs = new Shizuku.UserServiceArgs(
            new ComponentName(appContext, ShizukuCallMediaUserService.class)
        )
            .daemon(false)
            .processNameSuffix("call_media")
            .tag("call-media-v1")
            .version(1)
            .debuggable(false);
    }

    @Override
    public void bind(long generation, BindCallback callback) {
        Objects.requireNonNull(callback, "callback");
        final Binding binding;
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Shizuku call-media backend is closed");
            }
            if (activeBinding != null) {
                throw new IllegalStateException("call-media UserService already bound");
            }
            binding = new Binding(generation, callback);
            activeBinding = binding;
        }
        executeControl(
            () -> beginBind(binding),
            () -> failBinding(binding, new IllegalStateException("Shizuku control executor stopped"))
        );
    }

    @Override
    public void prepare(long generation, int sampleRateHz) {
        IShizukuCallMediaService service = serviceFor(generation);
        try {
            service.prepare(sampleRateHz);
        } catch (Throwable error) {
            throw asRuntime("failed to prepare Shizuku call media", error);
        }
    }

    @Override
    public CallMediaEndpointLease start(long generation) {
        IShizukuCallMediaService service = serviceFor(generation);
        ParcelFileDescriptor downlink = null;
        ParcelFileDescriptor uplink = null;
        try {
            service.startMedia();
            downlink = service.takeDownlinkReadEnd();
            uplink = service.takeUplinkWriteEnd();
            ShizukuCallMediaEndpointLease lease = new ShizukuCallMediaEndpointLease(
                downlink,
                uplink
            );
            downlink = null;
            uplink = null;
            return lease;
        } catch (Throwable error) {
            closeQuietly(downlink);
            closeQuietly(uplink);
            throw asRuntime("failed to start Shizuku call media", error);
        }
    }

    @Override
    public boolean heartbeat(long generation) {
        IShizukuCallMediaService service = serviceFor(generation);
        try {
            return service.heartbeat();
        } catch (Throwable error) {
            throw asRuntime("Shizuku call-media heartbeat failed", error);
        }
    }

    /** Non-blocking from the caller: the privileged Binder abort is serialized on controlExecutor. */
    @Override
    public void abort(long generation) {
        IShizukuCallMediaService service = serviceForOrNull(generation);
        if (service == null) {
            return;
        }
        executeControl(() -> {
            try {
                service.abortNow();
            } catch (Throwable ignored) {
                // Local endpoint close and the helper watchdog remain independent fail-safes.
            }
        }, null);
    }

    /** Locally detaches immediately, then removes the UserService asynchronously after abort. */
    @Override
    public void unbind(long generation) {
        final Binding binding;
        synchronized (this) {
            if (activeBinding == null || activeBinding.generation != generation) {
                return;
            }
            binding = activeBinding;
            activeBinding = null;
        }
        binding.cancelTimeout();
        binding.detachDeathRecipient();
        executeControl(() -> unbindRemote(binding), null);
    }

    @Override
    public void close() {
        final Long generationToClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            generationToClose = activeBinding == null ? null : activeBinding.generation;
        }
        if (generationToClose != null) {
            abort(generationToClose);
            unbind(generationToClose);
        }
        controlExecutor.shutdown();
    }

    private void beginBind(Binding binding) {
        if (!isCurrent(binding)) {
            return;
        }
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                throw new IllegalStateException("Shizuku binder is unavailable or unsupported");
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Shizuku permission is required");
            }

            ScheduledFuture<?> timeout = controlExecutor.schedule(
                () -> failBinding(
                    binding,
                    new TimeoutException("Shizuku UserService bind timed out after " + BIND_TIMEOUT_MS + " ms")
                ),
                BIND_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            );
            synchronized (this) {
                if (!isCurrentLocked(binding)) {
                    timeout.cancel(false);
                    return;
                }
                binding.bindTimeout = timeout;
            }
            Shizuku.bindUserService(userServiceArgs, binding);
        } catch (Throwable error) {
            failBinding(binding, error);
        }
    }

    private void failBinding(Binding binding, Throwable error) {
        boolean claimed;
        synchronized (this) {
            claimed = isCurrentLocked(binding);
            if (claimed) {
                activeBinding = null;
            }
        }
        if (!claimed) {
            return;
        }
        binding.cancelTimeout();
        binding.detachDeathRecipient();
        binding.callback.onBindFailed(binding.generation, error);
        executeControl(() -> unbindRemote(binding), null);
    }

    private void unbindRemote(Binding binding) {
        try {
            Shizuku.unbindUserService(userServiceArgs, binding, true);
        } catch (Throwable ignored) {
            // Endpoint close/helper watchdog remain independent fail-safe cleanup paths.
        }
    }

    private synchronized IShizukuCallMediaService serviceFor(long generation) {
        IShizukuCallMediaService service = serviceForOrNullLocked(generation);
        if (service == null) {
            throw new IllegalStateException(
                "call-media UserService is not connected for generation " + generation
            );
        }
        return service;
    }

    private synchronized IShizukuCallMediaService serviceForOrNull(long generation) {
        return serviceForOrNullLocked(generation);
    }

    private IShizukuCallMediaService serviceForOrNullLocked(long generation) {
        if (activeBinding == null || activeBinding.generation != generation) {
            return null;
        }
        return activeBinding.service;
    }

    private synchronized boolean isCurrent(Binding binding) {
        return isCurrentLocked(binding);
    }

    private boolean isCurrentLocked(Binding binding) {
        return activeBinding == binding;
    }

    private void executeControl(Runnable action, Runnable onRejected) {
        try {
            controlExecutor.execute(action);
        } catch (RejectedExecutionException error) {
            if (onRejected != null) {
                onRejected.run();
            }
        }
    }

    private final class Binding implements ServiceConnection {
        final long generation;
        final BindCallback callback;
        final AtomicBoolean disconnectNotified = new AtomicBoolean(false);
        IShizukuCallMediaService service;
        IBinder binder;
        IBinder.DeathRecipient deathRecipient;
        ScheduledFuture<?> bindTimeout;

        Binding(long generation, BindCallback callback) {
            this.generation = generation;
            this.callback = callback;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder connectedBinder) {
            if (!isCurrent(this)) {
                return;
            }
            IShizukuCallMediaService connectedService =
                IShizukuCallMediaService.Stub.asInterface(connectedBinder);
            IBinder.DeathRecipient recipient = this::notifyDisconnected;
            try {
                connectedBinder.linkToDeath(recipient, 0);
            } catch (Throwable error) {
                failBinding(this, error);
                return;
            }

            synchronized (ShizukuCallMediaSessionBackend.this) {
                if (!isCurrentLocked(this)) {
                    try {
                        connectedBinder.unlinkToDeath(recipient, 0);
                    } catch (Throwable ignored) {
                        // Stale binder may already be dead.
                    }
                    return;
                }
                service = connectedService;
                binder = connectedBinder;
                deathRecipient = recipient;
            }
            cancelTimeout();

            executeControl(() -> {
                if (isCurrent(this)) {
                    callback.onBound(generation);
                }
            }, () -> failBinding(this, new IllegalStateException("Shizuku control executor stopped")));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            notifyDisconnected();
        }

        void notifyDisconnected() {
            if (isCurrent(this) && disconnectNotified.compareAndSet(false, true)) {
                callback.onDisconnected(generation);
            }
        }

        void cancelTimeout() {
            ScheduledFuture<?> timeout;
            synchronized (ShizukuCallMediaSessionBackend.this) {
                timeout = bindTimeout;
                bindTimeout = null;
            }
            if (timeout != null) {
                timeout.cancel(false);
            }
        }

        void detachDeathRecipient() {
            IBinder currentBinder;
            IBinder.DeathRecipient currentRecipient;
            synchronized (ShizukuCallMediaSessionBackend.this) {
                currentBinder = binder;
                currentRecipient = deathRecipient;
                binder = null;
                deathRecipient = null;
                service = null;
            }
            if (currentBinder != null && currentRecipient != null) {
                try {
                    currentBinder.unlinkToDeath(currentRecipient, 0);
                } catch (Throwable ignored) {
                    // Binder may already be dead.
                }
            }
        }
    }

    private static RuntimeException asRuntime(String message, Throwable error) {
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(message, error);
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Throwable ignored) {
            // Failed endpoint transfer cleanup.
        }
    }
}
