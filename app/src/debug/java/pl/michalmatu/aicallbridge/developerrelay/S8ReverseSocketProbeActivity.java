package pl.michalmatu.aicallbridge.developerrelay;

import android.app.Activity;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * S8 debug-only, DUMP-gated no-audio transport diagnostic. Opens a transient
 * app-owned Unix socket to test whether a verified shell UID may connect to it.
 * No call, privileged command, PCM, model or personal data is accessed.
 */
public final class S8ReverseSocketProbeActivity extends Activity {
    private static final String SOCKET = "aicalls_s8_reverse_probe_v1";
    private static final String FILE = "s8-reverse-socket-probe.txt";
    private volatile LocalServerSocket listener;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("S8 no-call reverse socket probe");
        setContentView(text);
        try {
            listener = new LocalServerSocket(SOCKET);
            writeReport("probe=reverse_socket\nlistening=true\n");
        } catch (Exception error) {
            writeReport("probe=reverse_socket\nlistening=false\nerror="
                + error.getClass().getSimpleName() + "\n");
            finish();
            return;
        }
        new Thread(() -> {
            String result;
            try (LocalSocket peer = listener.accept()) {
                int uid = peer.getPeerCredentials().getUid();
                result = "connected=true\npeer_uid_is_shell=" + (uid == 2000) + "\n";
            } catch (Exception error) {
                result = "connected=false\nerror=" + error.getClass().getSimpleName() + "\n";
            }
            writeReport("probe=reverse_socket\n" + result + "probe_complete=true\n");
            runOnUiThread(this::finish);
        }, "s8-reverse-socket-probe").start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            LocalServerSocket server = listener;
            if (server != null) try { server.close(); } catch (Exception ignored) {}
        }, 10_000);
    }

    private void writeReport(String report) {
        try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), FILE))) {
            stream.write(report.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Test result remains absent rather than writing sensitive logcat.
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) try { listener.close(); } catch (Exception ignored) {}
    }
}
