package pl.michalmatu.aicallbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.shizuku.ShizukuUserServiceProbe
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelImporter
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelInstallResult
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var runtimePreferences: CallRuntimePreferences
    private lateinit var selectedAudioMode: CallAudioMode
    private lateinit var selectedTextLlmProvider: TextLlmProvider
    private lateinit var textLlmProviderSpinner: Spinner
    private lateinit var modelImporter: AndroidGemma4ModelImporter
    private lateinit var modelImportButton: Button
    private val modelImportExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aicall-gemma4-import").apply { isDaemon = true }
    }
    private var pendingShizukuProbe = false
    private var pendingShizukuLiveProbe = false

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (pendingShizukuProbe) {
            runShizukuProbe(pendingShizukuLiveProbe)
        }
    }

    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener {
            requestCode,
            grantResult,
        ->
        if (requestCode != REQUEST_SHIZUKU) {
            return@OnRequestPermissionResultListener
        }

        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            runShizukuProbe(pendingShizukuLiveProbe)
        } else {
            pendingShizukuProbe = false
            pendingShizukuLiveProbe = false
            statusView.text = "Shizuku permission denied"
            Log.i(TAG, "shizuku_probe_permission=denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtimePreferences = CallRuntimePreferences(this)
        modelImporter = AndroidGemma4ModelImporter(this)
        val initialSelection = runtimePreferences.load()
        selectedAudioMode = initialSelection.audioMode
        selectedTextLlmProvider = initialSelection.textLlmProvider

        statusView = TextView(this).apply {
            text = runtimeSelectionSummary()
            textSize = 15f
            setTextIsSelectable(true)
        }

        val audioModeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                CallAudioMode.entries.map { it.displayName },
            )
            setSelection(CallAudioMode.entries.indexOf(selectedAudioMode))
        }

        textLlmProviderSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                TextLlmProvider.entries.map { it.displayName },
            )
            setSelection(TextLlmProvider.entries.indexOf(selectedTextLlmProvider))
            isEnabled = selectedAudioMode.usesTextLlm
        }

        audioModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedAudioMode = CallAudioMode.entries[position]
                runtimePreferences.saveAudioMode(selectedAudioMode)
                textLlmProviderSpinner.isEnabled = selectedAudioMode.usesTextLlm
                statusView.text = runtimeSelectionSummary()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        textLlmProviderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedTextLlmProvider = TextLlmProvider.entries[position]
                runtimePreferences.saveTextLlmProvider(selectedTextLlmProvider)
                statusView.text = runtimeSelectionSummary()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        modelImportButton = Button(this).apply {
            text = "Import Gemma 4 model"
            setOnClickListener { chooseGemma4Model() }
        }

        val requestMicButton = Button(this).apply {
            text = "Grant microphone permission"
            setOnClickListener { requestMicrophonePermissionIfNeeded() }
        }

        val capabilityProbeButton = Button(this).apply {
            text = "Run device capability probe"
            setOnClickListener { runCapabilityProbe() }
        }

        val shizukuProbeButton = Button(this).apply {
            text = "Run Shizuku UserService probe"
            setOnClickListener { runShizukuProbe(false) }
        }

        val probeCaptureButton = Button(this).apply {
            text = "Probe call downlink capture"
            setOnClickListener {
                statusView.text = "Downlink backend is under Phase 2 validation."
            }
        }

        val probeInjectionButton = Button(this).apply {
            text = "Probe call uplink injection"
            setOnClickListener {
                statusView.text = "Uplink backend is under Phase 2 validation."
            }
        }

        val takeoverButton = Button(this).apply {
            text = "TAKE OVER / STOP AI AUDIO"
            isAllCaps = true
            setOnClickListener {
                statusView.text = "Takeover requested. Active transport cleanup is handled fail-safe."
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(TextView(this@MainActivity).apply {
                text = "Android AI Call Bridge"
                textSize = 24f
            })
            addView(TextView(this@MainActivity).apply { text = "Audio mode" })
            addView(audioModeSpinner)
            addView(TextView(this@MainActivity).apply { text = "LLM provider (text mode)" })
            addView(textLlmProviderSpinner)
            addView(modelImportButton)
            addView(requestMicButton)
            addView(capabilityProbeButton)
            addView(shizukuProbeButton)
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

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)

        if (intent.getBooleanExtra(EXTRA_RUN_CAPABILITY_PROBE, false)) {
            runCapabilityProbe()
        }
    }

    override fun onDestroy() {
        modelImportExecutor.shutdownNow()
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        super.onDestroy()
    }

    private fun runtimeSelectionSummary(): String = buildString {
        append("Audio mode: ").append(selectedAudioMode.displayName).append('\n')
        when (selectedAudioMode) {
            CallAudioMode.LOCAL_STT_TTS -> append("LLM provider: ").append(selectedTextLlmProvider.displayName)
            CallAudioMode.OPENAI_REALTIME_AUDIO -> append("LLM provider: OpenAI Realtime audio; text preference preserved")
            CallAudioMode.LOCAL_REALTIME_AUDIO -> append("LLM provider: local realtime audio engine; text preference preserved")
        }
        append('\n')
        val modelReadiness = modelImporter.readiness()
        when (modelReadiness.state) {
            Gemma4ModelReadinessState.READY ->
                append("Gemma 4 model: READY (").append(modelReadiness.file.length()).append(" bytes)")

            Gemma4ModelReadinessState.MISSING ->
                append("Gemma 4 model: MISSING")

            Gemma4ModelReadinessState.INVALID ->
                append("Gemma 4 model: INVALID (").append(modelReadiness.reason ?: "unknown").append(')')
        }
    }

    private fun chooseGemma4Model() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_IMPORT_GEMMA4_MODEL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_GEMMA4_MODEL) return
        if (resultCode != RESULT_OK) {
            statusView.text = "Gemma 4 model import cancelled"
            return
        }
        val uri = data?.data
        if (uri == null) {
            statusView.text = "Gemma 4 model import failed: model_source_missing"
            return
        }

        modelImportButton.isEnabled = false
        statusView.text = "Importing Gemma 4 model and verifying SHA-256…"
        modelImportExecutor.execute {
            val result = modelImporter.import(uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                modelImportButton.isEnabled = true
                statusView.text = when (result) {
                    is Gemma4ModelInstallResult.Success ->
                        "Gemma 4 model ready: ${result.modelId}; ${result.bytesWritten} bytes; sha256=${result.sha256}"

                    is Gemma4ModelInstallResult.Failure ->
                        "Gemma 4 model import failed: ${result.reason}"
                }
            }
        }
    }

    private fun runCapabilityProbe() {
        statusView.text = try {
            CapabilityProbe(this).run()
        } catch (error: Throwable) {
            "Capability probe failed: ${error.javaClass.simpleName}: ${error.message}"
        }
    }

    private fun runShizukuProbe(live: Boolean) {
        pendingShizukuProbe = true
        pendingShizukuLiveProbe = live

        if (!Shizuku.pingBinder()) {
            statusView.text = "Shizuku binder unavailable; start Shizuku first"
            Log.i(TAG, "shizuku_probe_binder=unavailable")
            return
        }

        if (Shizuku.isPreV11()) {
            pendingShizukuProbe = false
            pendingShizukuLiveProbe = false
            statusView.text = "Shizuku pre-v11 is unsupported"
            Log.i(TAG, "shizuku_probe_version=unsupported_pre_v11")
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pendingShizukuProbe = false
                pendingShizukuLiveProbe = false
                statusView.text = "Shizuku permission denied; enable it in Shizuku"
                Log.i(TAG, "shizuku_probe_permission=rationale_required")
                return
            }
            statusView.text = "Requesting Shizuku permission…"
            Log.i(TAG, "shizuku_probe_permission=requested")
            Shizuku.requestPermission(REQUEST_SHIZUKU)
            return
        }

        pendingShizukuProbe = false
        pendingShizukuLiveProbe = false
        statusView.text = if (live) {
            "Running Shizuku UserService live parity probe…"
        } else {
            "Running Shizuku UserService off-call probe…"
        }
        Log.i(TAG, if (live) "shizuku_live_probe_start=true" else "shizuku_probe_start=true")

        val callback = ShizukuUserServiceProbe.Callback { result ->
            runOnUiThread {
                statusView.text = result
                Log.i(TAG, "shizuku_probe_result:\n$result")
            }
        }
        if (live) {
            ShizukuUserServiceProbe.runLive(this, LIVE_SHIZUKU_DURATION_MS, callback)
        } else {
            ShizukuUserServiceProbe.run(this, callback)
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
        const val TAG = "AiCallBridge"
        const val REQUEST_RECORD_AUDIO = 1001
        const val REQUEST_SHIZUKU = 1002
        const val REQUEST_IMPORT_GEMMA4_MODEL = 1003
        const val LIVE_SHIZUKU_DURATION_MS = 5_000
        const val EXTRA_RUN_CAPABILITY_PROBE = "run_probe"
    }
}
