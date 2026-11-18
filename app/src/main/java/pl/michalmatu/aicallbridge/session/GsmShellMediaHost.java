package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;

import pl.michalmatu.aicallbridge.helper.samsung.SamsungCallMediaSessionController;
import pl.michalmatu.aicallbridge.shizuku.PrivilegedCallContexts;

/**
 * Explicit ADB-shell entry point for S8 GSM; independent of Shizuku.
 *
 * Bootstrapped with `adb shell CLASSPATH=<installed S8 APK> app_process ...`,
 * never from an unprivileged Android Activity. It performs no phone call,
 * has no TCP listener, public RPC, arbitrary command executor or scheduling.
 * The only accepted app UID is resolved locally from the S8 package.
 *
 * A lost socket unconditionally revokes BOTH Samsung PCM directions. Existing
 * controller/heartbeat and audio routing are reused without duplication.
 */
public final class GsmShellMediaHost {
    private GsmShellMediaHost() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 || Process.myUid() != GsmShellMediaProtocol.SHELL_UID)
            throw new SecurityException("gsm_shell_requires_explicit_adb_shell_identity");
        if (Looper.myLooper() == null) Looper.prepare();

        // app_process does not have a regular application Context. Initialize
        // its system ActivityThread before constructing trusted shell contexts.
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = threadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        systemMain.invoke(null);

        Context system = PrivilegedCallContexts.create().system();
        // Never cache the package UID: uninstall/reinstall can invalidate the
        // authority while this explicitly started shell process is still alive.
        try (LocalServerSocket listener =
                 new LocalServerSocket(GsmShellMediaProtocol.SOCKET_NAME)) {
            while (true) {
                try (LocalSocket peer = listener.accept()) {
                    int installedUid = system.getPackageManager().getPackageUid(
                        GsmShellMediaProtocol.S8_PACKAGE, 0
                    );
                    if (!GsmShellMediaProtocol.mayServeApp(
                        peer.getPeerCredentials().getUid(), installedUid)) {
                        continue;
                    }
                    peer.setSoTimeout(6_000);
                    serveOwnedSession(peer);
                } catch (IOException | RuntimeException ignored) {
                    // Private/no-content diagnostics only. A fresh app-owned
                    // session may connect after a failed or stopped generation.
                }
            }
        }
    }

    private static void serveOwnedSession(LocalSocket peer) throws IOException {
        SamsungCallMediaSessionController controller = new SamsungCallMediaSessionController();
        PrivilegedCallContexts.Pair contexts = null;
        GsmShellSessionGuard guard = null;
        long ownedGeneration = -1L;
        try {
            DataInputStream input = new DataInputStream(peer.getInputStream());
            DataOutputStream output = new DataOutputStream(peer.getOutputStream());
            if (input.readInt() != GsmShellMediaProtocol.MAGIC) return;
            ownedGeneration = input.readLong();
            if (!GsmShellMediaProtocol.validGeneration(ownedGeneration)) return;
            guard = new GsmShellSessionGuard(ownedGeneration);
            output.writeByte(GsmShellMediaProtocol.OK);
            output.flush();

            while (true) {
                int action;
                try {
                    action = input.readInt();
                } catch (EOFException ended) {
                    return;
                }
                long generation = input.readLong();
                if (generation != ownedGeneration) return;
                switch (action) {
                    case GsmShellMediaProtocol.PREPARE -> {
                        int rate = input.readInt();
                        if (!guard.prepare(generation, rate)) return;
                        contexts = PrivilegedCallContexts.create();
                        controller.prepare(rate, contexts.shell());
                        output.writeByte(GsmShellMediaProtocol.OK);
                        output.flush();
                    }
                    case GsmShellMediaProtocol.START -> {
                        if (contexts == null || !guard.start(generation)) return;
                        SamsungCallMediaSessionController.Endpoints endpoints =
                            controller.start(contexts.system(), contexts.shell());
                        contexts = null;
                        ParcelFileDescriptor downlink = null;
                        ParcelFileDescriptor uplink = null;
                        try {
                            downlink = endpoints.takeDownlinkReadEnd();
                            uplink = endpoints.takeUplinkWriteEnd();
                            FileDescriptor[] outgoing = {
                                downlink.getFileDescriptor(), uplink.getFileDescriptor()
                            };
                            peer.setFileDescriptorsForSend(outgoing);
                            output.writeByte(GsmShellMediaProtocol.OK);
                            output.flush();
                        } finally {
                            closeQuietly(downlink);
                            closeQuietly(uplink);
                            endpoints.close();
                        }
                    }
                    case GsmShellMediaProtocol.HEARTBEAT -> {
                        if (!guard.heartbeat(generation) || !controller.heartbeat()) return;
                        output.writeByte(GsmShellMediaProtocol.OK);
                        output.flush();
                    }
                    default -> {
                        return;
                    }
                }
            }
        } catch (Exception error) {
            // Close socket and both PCM paths; never disclose caller or audio.
            throw new IOException("gsm_shell_session_failed", error);
        } finally {
            if (guard != null) guard.stop();
            controller.abortNow();
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) {}
    }
}
