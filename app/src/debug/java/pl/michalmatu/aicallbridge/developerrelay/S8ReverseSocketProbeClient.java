package pl.michalmatu.aicallbridge.developerrelay;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;

/** One-shot shell-side reverse Unix socket probe. No audio or privileged command. */
public final class S8ReverseSocketProbeClient {
    private S8ReverseSocketProbeClient() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 || Process.myUid() != 2000)
            throw new SecurityException("shell_uid_required");
        try (LocalSocket peer = new LocalSocket()) {
            peer.connect(new LocalSocketAddress(
                "aicalls_s8_reverse_probe_v1", LocalSocketAddress.Namespace.ABSTRACT));
            System.out.println("REVERSE_SHELL_TO_APP_CONNECTED=true");
            System.out.println("REVERSE_PEER_IS_APP=" + (peer.getPeerCredentials().getUid() >= 10000));
        }
    }
}
