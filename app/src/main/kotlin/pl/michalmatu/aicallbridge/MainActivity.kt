package pl.michalmatu.aicallbridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            text = "Milestone 0.5: device capability probe ready"
            textSize = 15f
            setTextIsSelectable(true)
        }

        val requestMicButton = Button(this).apply {
            text = "Grant microphone permission"
            setOnClickListener { requestMicrophonePermissionIfNeeded() }
        }

        val capabilityProbeButton = Button(this).apply {
            text = "Run device capability probe"
            setOnClickListener { runCapabilityProbe() }
        }

        val probeCaptureButton = Button(this).apply {
            text = "Probe call downlink capture"
            setOnClickListener {
                statusView.text = "Capture backend not implemented yet. Capability probe comes first."
            }
        }

        val probeInjectionButton = Button(this).apply {
            text = "Probe call uplink injection"
            setOnClickListener {
                statusView.text = "Injection backend not implemented yet. Capability probe comes first."
            }
        }

        val takeoverButton = Button(this).apply {
            text = "TAKE OVER / STOP AI AUDIO"
            isAllCaps = true
            setOnClickListener {
                statusView.text = "Takeover requested. No injector is active in Milestone 0.5."
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(TextView(this@MainActivity).apply {
                text = "Android AI Call Bridge"
                textSize = 24f
            })
            addView(requestMicButton)
            addView(capabilityProbeButton)
            addView(probeCaptureButton)
            addView(probeInjectionButton)
            addView(takeoverButton)
            addView(
                statusView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 24 },
            )
        }

        setContentView(
            ScrollView(this).apply {
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )

        if (intent.getBooleanExtra(EXTRA_RUN_CAPABILITY_PROBE, false)) {
            runCapabilityProbe()
        }
    }

    private fun runCapabilityProbe() {
        statusView.text = try {
            CapabilityProbe(this).run()
        } catch (error: Throwable) {
            "Capability probe failed: ${error.javaClass.simpleName}: ${error.message}"
        }
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            statusView.text = "Microphone permission already granted"
            return
        }

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            statusView.text = if (granted) {
                "Microphone permission granted"
            } else {
                "Microphone permission denied"
            }
        }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1001
        const val EXTRA_RUN_CAPABILITY_PROBE = "run_probe"
    }
}
