package pl.michalmatu.aicallbridge.developerrelay

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * No-touch supervised call diagnostics for an authorized ADB shell.
 *
 * The DUMP-gated manifest component can observe the actual app-context
 * AudioManager mode and Telecom call state without touching the dialer,
 * asking for UI input, attaching media, sending PCM, or ending a call.
 * It is intentionally separate from the consent-bearing live-call activity.
 */
class SupervisedCallReadinessProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshot = SupervisedCallReadinessDiagnostics.capture(this)
        val saved = SupervisedCallReadinessDiagnostics.write(this, snapshot)
        setContentView(TextView(this).apply {
            text = if (saved) "Diagnostic snapshot saved" else "Diagnostic write failed"
        })
        finish()
    }
}
