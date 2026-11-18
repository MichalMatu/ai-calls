package pl.michalmatu.aicallbridge.developerrelay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import pl.michalmatu.aicallbridge.session.GsmShellBinderRegistryProvider;
import pl.michalmatu.aicallbridge.session.GsmBinderSessionTokenSequence;
import pl.michalmatu.aicallbridge.session.IGsmShellMediaService;

/**
 * DUMP-gated no-call S8 Binder roundtrip. It never invokes prepare/start,
 * takes a PCM pipe, touches telephony or reads any caller information.
 */
public final class S8GsmBinderRoundtripProbeActivity extends Activity {
    public static final String REPORT_FILE = "s8-gsm-binder-roundtrip.txt";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("S8 GSM Binder no-call roundtrip");
        setContentView(view);

        new Thread(() -> {
            String outcome;
            try {
                IGsmShellMediaService service = GsmShellBinderRegistryProvider.availableService();
                boolean registered = service != null;
                boolean verified = registered && service.getShellUid() == 2000;
                long reservedToken = GsmBinderSessionTokenSequence.next();
                boolean unownedHeartbeatRejected = registered && !service.heartbeat(reservedToken);
                if (registered) service.abortNow(reservedToken); // reserved, never reused
                boolean registeredAfterStaleStop = registered && service.getShellUid() == 2000;
                outcome = "probe=s8_gsm_binder_roundtrip\n"
                    + "host_registered=" + registered + "\n"
                    + "host_binder_uid_verified=" + verified + "\n"
                    + "unowned_heartbeat_rejected=" + unownedHeartbeatRejected + "\n"
                    + "registered_after_stale_stop=" + registeredAfterStaleStop + "\n"
                    + "probe_complete=true\n";
            } catch (Exception error) {
                outcome = "probe=s8_gsm_binder_roundtrip\n"
                    + "host_binder_uid_verified=false\n"
                    + "failure_class=" + error.getClass().getSimpleName() + "\n"
                    + "probe_complete=true\n";
            }
            try (FileOutputStream stream = new FileOutputStream(
                     new File(getFilesDir(), REPORT_FILE))) {
                stream.write(outcome.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // Absence is failure; no private logging.
            }
            runOnUiThread(this::finish);
        }, "s8-gsm-binder-no-call-probe").start();
    }
}
