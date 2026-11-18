package pl.michalmatu.aicallbridge.session;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opt-in GSM media backend for an independently ADB-authorized shell host.
 *
 * It neither invokes ADB nor launches a privileged process. Without a running,
 * UID-verified helper it fails closed. Existing Shizuku runtime is untouched.
 * STOP closes the socket immediately (even during a blocked prepare/start)
 * and the helper aborts its two PCM directions on EOF.
 */
public final class GsmShellCallMediaSessionBackend implements CallMediaSessionBackend {
    private static final int IO_TIMEOUT_MS = 5_000;

    private final Object lock = new Object();
    private final ExecutorService connector = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aicalls-gsm-shell-connect");
        thread.setDaemon(true);
        return thread;
    });
    private long expectedGeneration = -1L;
    private LocalSocket pending;
    private Remote connected;
    private boolean closed;

    @Override
    public void bind(long generation, BindCallback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            if (closed || expectedGeneration != -1L
                || !GsmShellMediaProtocol.validGeneration(generation)) {
                callback.onBindFailed(generation,
                    new IllegalStateException("gsm_shell_media_unavailable"));
                return;
            }
            expectedGeneration = generation;
        }
        try {
            connector.execute(() -> connect(generation, callback));
        } catch (RuntimeException error) {
            abort(generation);
            callback.onBindFailed(generation, error);
        }
    }

    private void connect(long generation, BindCallback callback) {
        LocalSocket socket = new LocalSocket();
        try {
            synchronized (lock) {
                if (closed || expectedGeneration != generation) return;
                pending = socket;
            }
            socket.connect(new LocalSocketAddress(
                GsmShellMediaProtocol.SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(IO_TIMEOUT_MS);
            if (!GsmShellMediaProtocol.mayTrustShell(socket.getPeerCredentials().getUid())) {
                throw new SecurityException("gsm_shell_peer_uid_rejected");
            }
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.writeInt(GsmShellMediaProtocol.MAGIC);
            output.writeLong(generation);
            output.flush();
            if (input.readUnsignedByte() != GsmShellMediaProtocol.OK)
                throw new SecurityException("gsm_shell_auth_rejected");

            Remote candidate = new Remote(generation, socket, input, output);
            synchronized (lock) {
                if (closed || expectedGeneration != generation || pending != socket) return;
                pending = null;
                connected = candidate;
            }
            callback.onBound(generation);
            return;
        } catch (Throwable error) {
            synchronized (lock) {
                if (expectedGeneration == generation) expectedGeneration = -1L;
                if (pending == socket) pending = null;
                if (connected != null && connected.socket == socket) connected = null;
            }
            callback.onBindFailed(generation, error);
        } finally {
            boolean retained;
            synchronized (lock) {
                retained = connected != null && connected.socket == socket;
            }
            if (!retained) closeQuietly(socket);
        }
    }

    @Override
    public void prepare(long generation, int sampleRateHz) {
        if (sampleRateHz != GsmShellMediaProtocol.SAMPLE_RATE_HZ)
            throw new IllegalArgumentException("gsm_shell_unsupported_sample_rate");
        Remote current = current(generation);
        synchronized (current.ioLock) {
            try {
                current.output.writeInt(GsmShellMediaProtocol.PREPARE);
                current.output.writeLong(generation);
                current.output.writeInt(sampleRateHz);
                current.output.flush();
                requireOk(current.input);
            } catch (IOException error) {
                throw new IllegalStateException("gsm_shell_prepare_failed", error);
            }
        }
    }

    @Override
    public CallMediaEndpointLease start(long generation) {
        Remote current = current(generation);
        synchronized (current.ioLock) {
            FileDescriptor[] received = null;
            ParcelFileDescriptor downlink = null;
            ParcelFileDescriptor uplink = null;
            try {
                current.output.writeInt(GsmShellMediaProtocol.START);
                current.output.writeLong(generation);
                current.output.flush();
                requireOk(current.input);
                received = current.socket.getAncillaryFileDescriptors();
                if (received == null || received.length != 2)
                    throw new IOException("gsm_shell_endpoints_missing");
                downlink = ParcelFileDescriptor.dup(received[0]);
                uplink = ParcelFileDescriptor.dup(received[1]);
                CallMediaEndpointLease lease =
                    new ShizukuCallMediaEndpointLease(downlink, uplink);
                downlink = null;
                uplink = null;
                return lease;
            } catch (IOException error) {
                throw new IllegalStateException("gsm_shell_start_failed", error);
            } finally {
                closeQuietly(downlink);
                closeQuietly(uplink);
                if (received != null) for (FileDescriptor descriptor : received) {
                    try { Os.close(descriptor); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @Override
    public boolean heartbeat(long generation) {
        Remote current = current(generation);
        synchronized (current.ioLock) {
            try {
                current.output.writeInt(GsmShellMediaProtocol.HEARTBEAT);
                current.output.writeLong(generation);
                current.output.flush();
                return current.input.readUnsignedByte() == GsmShellMediaProtocol.OK;
            } catch (IOException error) {
                return false;
            }
        }
    }

    /** Never waits for the I/O lock: physical local STOP takes priority. */
    @Override
    public void abort(long generation) {
        LocalSocket socket = detach(generation);
        closeQuietly(socket);
    }

    @Override
    public void unbind(long generation) {
        abort(generation);
    }

    @Override
    public void close() {
        LocalSocket socket;
        LocalSocket waiting;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            expectedGeneration = -1L;
            socket = connected == null ? null : connected.socket;
            connected = null;
            waiting = pending;
            pending = null;
        }
        closeQuietly(socket);
        closeQuietly(waiting);
        connector.shutdownNow();
    }

    private Remote current(long generation) {
        synchronized (lock) {
            if (closed || expectedGeneration != generation || connected == null)
                throw new IllegalStateException("gsm_shell_generation_not_bound");
            return connected;
        }
    }

    private LocalSocket detach(long generation) {
        synchronized (lock) {
            if (expectedGeneration != generation) return null;
            expectedGeneration = -1L;
            LocalSocket socket = connected == null ? pending : connected.socket;
            connected = null;
            pending = null;
            return socket;
        }
    }

    private static void requireOk(DataInputStream input) throws IOException {
        if (input.readUnsignedByte() != GsmShellMediaProtocol.OK)
            throw new IOException("gsm_shell_remote_rejected");
    }

    private static void closeQuietly(AutoCloseable item) {
        if (item != null) try { item.close(); } catch (Exception ignored) {}
    }

    private static final class Remote {
        final Object ioLock = new Object();
        final long generation;
        final LocalSocket socket;
        final DataInputStream input;
        final DataOutputStream output;

        Remote(long generation, LocalSocket socket,
            DataInputStream input, DataOutputStream output) {
            this.generation = generation;
            this.socket = socket;
            this.input = input;
            this.output = output;
        }
    }
}
