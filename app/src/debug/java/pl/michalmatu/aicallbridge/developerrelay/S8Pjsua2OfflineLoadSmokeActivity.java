package pl.michalmatu.aicallbridge.developerrelay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Debug-APK-only, owner-initiated JNI loading probe. No SIP configuration,
 * transport, registration, audio/media, credentials or phone calls.
 *
 * Manifest is disabled without explicit local -Ps8NativePrototype=true, and
 * even then requires the shell signature-protected DUMP permission.
 */
public final class S8Pjsua2OfflineLoadSmokeActivity extends Activity {
    public static final String REPORT_FILE = "s8-pjsua2-offline-load-report.txt";
    private final AtomicBoolean started = new AtomicBoolean();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new TextView(this) {{
            setText("S8 JNI offline compatibility probe (no SIP network)");
        }});
        if (!getPackageName().endsWith(".s8lab") ||
            !started.compareAndSet(false, true)) {
            finish();
            return;
        }
        Thread probe = new Thread(this::runProbe, "s8-pjsua2-offline-load");
        probe.setDaemon(true);
        probe.start();
    }

    private void runProbe() {
        boolean pjsuaLoaded = false;
        boolean verifierLoaded = false;
        boolean tlsRejectedEmpty = false;
        boolean srtpRejectedInvalid = false;
        boolean endpointCreated = false;
        boolean endpointDestroyed = false;
        Object endpoint = null;
        Class<?> endpointType = null;
        String status = "failed";
        String errorCode = "none";
        try {
            System.loadLibrary("pjsua2");
            pjsuaLoaded = true;
            System.loadLibrary("s8srtpaudit");
            verifierLoaded = true;

            Class<?> tls = Class.forName(
                "pl.michalmatu.aicalls.sip.S8Pjsua2TlsAudit");
            Method checkIncoming = tls.getDeclaredMethod(
                "nativeRxTransportMatchesVerifiedTls", long.class, long.class);
            checkIncoming.setAccessible(true);
            tlsRejectedEmpty = Boolean.FALSE.equals(
                checkIncoming.invoke(null, 0L, 0L));

            Class<?> srtp = Class.forName(
                "pl.michalmatu.aicalls.sip.S8Pjsua2SrtpAudit");
            Method checkSrtp = srtp.getDeclaredMethod(
                "nativeSrtpActive", int.class, int.class);
            checkSrtp.setAccessible(true);
            srtpRejectedInvalid = Boolean.FALSE.equals(
                checkSrtp.invoke(null, -1, 0));

            if (!tlsRejectedEmpty || !srtpRejectedInvalid)
                throw new IllegalStateException("security_default_failed");

            endpointType = Class.forName("org.pjsip.pjsua2.Endpoint");
            endpoint = endpointType.getConstructor().newInstance();
            endpointType.getMethod("libCreate").invoke(endpoint);
            endpointCreated = true;
            endpointType.getMethod("libDestroy").invoke(endpoint);
            endpointDestroyed = true;
            status = "pass";
        } catch (Throwable failure) {
            // Only a coarse error class; never serialize SIP, keys or stack traces.
            String kind = failure.getClass().getSimpleName();
            errorCode = kind.matches("[A-Za-z]{1,40}") ? kind : "native_error";
        } finally {
            if (endpoint != null && endpointType != null) {
                try {
                    if (endpointCreated && !endpointDestroyed) {
                        endpointType.getMethod("libDestroy").invoke(endpoint);
                        endpointDestroyed = true;
                    }
                    endpointType.getMethod("delete").invoke(endpoint);
                } catch (Throwable ignored) {
                    status = "failed";
                    errorCode = "native_cleanup_failed";
                }
            }
            if (!endpointDestroyed) status = "failed";
            String report =
                "probe=s8_pjsua2_offline_jni\n" +
                "status=" + status + "\n" +
                "pjsua2_jni_loaded=" + pjsuaLoaded + "\n" +
                "srtp_audit_jni_loaded=" + verifierLoaded + "\n" +
                "tls_zero_handle_rejected=" + tlsRejectedEmpty + "\n" +
                "srtp_invalid_call_rejected=" + srtpRejectedInvalid + "\n" +
                "endpoint_created=" + endpointCreated + "\n" +
                "endpoint_destroyed=" + endpointDestroyed + "\n" +
                "real_call=false\n" +
                "sip_transport_created=false\n" +
                "sip_registered=false\n" +
                "error_code=" + errorCode + "\n" +
                "report_complete=true\n";
            writePrivateReport(report);
            runOnUiThread(this::finish);
        }
    }

    private void writePrivateReport(String content) {
        File tmp = new File(getFilesDir(), REPORT_FILE + ".tmp");
        File dst = new File(getFilesDir(), REPORT_FILE);
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } catch (Exception ignored) {
            return;
        }
        if (!tmp.renameTo(dst)) tmp.delete();
    }
}
