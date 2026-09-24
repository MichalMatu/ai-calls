package pl.michalmatu.aicallbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelDownloader
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelImporter
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelDownloadPresentation
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelInstallResult
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelOperationGate
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelOperationState
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var runtimePreferences: CallRuntimePreferences
    private lateinit var selectedAudioMode: CallAudioMode
    private lateinit var selectedTextLlmProvider: TextLlmProvider
    private lateinit var textLlmProviderSpinner: Spinner
    private lateinit var modelImporter: AndroidGemma4ModelImporter
    private lateinit var modelDownloader: AndroidGemma4ModelDownloader
    private val modelOperationGate = Gemma4ModelOperationGate()
    private val modelDownloadPresentation = Gemma4ModelDownloadPresentation()
    private lateinit var modelImportButton: Button
    private lateinit var modelDownloadButton: Button
    private lateinit var modelDownloadCancelButton: Button
    private lateinit var modelDownloadProgress: ProgressBar
    private lateinit var modelDownloadSourceView: TextView
    private val modelImportExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aicall-gemma4-model-io").apply { isDaemon = true }
    }
    private lateinit var developerProbes: MainActivityDeveloperProbes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtimePreferences = CallRuntimePreferences(this)
        modelImporter = AndroidGemma4ModelImporter(this)
        modelDownloader = AndroidGemma4ModelDownloader(this)
        val initialSelection = runtimePreferences.load()
        selectedAudioMode = initialSelection.audioMode
        selectedTextLlmProvider = initialSelection.textLlmProvider

        statusView = TextView(this).apply {
            text = runtimeSelectionSummary()
            textSize = 15f
            setTextIsSelectable(true)
        }
        developerProbes = MainActivityDeveloperProbes(this, statusView)

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
        modelDownloadSourceView = TextView(this).apply {
            text = modelDownloadPresentation.sourceSummary
            textSize = 13f
        }
        modelDownloadButton = Button(this).apply {
            text = modelDownloadPresentation.startButtonLabel
            setOnClickListener { showGemma4DownloadConfirmation() }
        }
        modelDownloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        modelDownloadCancelButton = Button(this).apply {
            text = "Cancel Gemma 4 download"
            isEnabled = false
            setOnClickListener { cancelGemma4Download() }
        }

        val requestMicButton = Button(this).apply {
            text = "Grant microphone permission"
            setOnClickListener { requestMicrophonePermissionIfNeeded() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(TextView(this@MainActivity).apply {
                text = "AI Calls"
                textSize = 24f
            })
            addView(TextView(this@MainActivity).apply { text = "Audio mode" })
            addView(audioModeSpinner)
            addView(TextView(this@MainActivity).apply { text = "LLM provider (text mode)" })
            addView(textLlmProviderSpinner)
            addView(modelImportButton)
            addView(modelDownloadSourceView)
            addView(modelDownloadButton)
            addView(modelDownloadProgress)
            addView(modelDownloadCancelButton)
            addView(requestMicButton)
            developerProbes.addControls(this)
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

        developerProbes.attach()

        if (intent.getBooleanExtra(EXTRA_RUN_CAPABILITY_PROBE, false)) {
            developerProbes.runCapabilityProbe()
        }
    }

    override fun onDestroy() {
        modelDownloader.cancel()
        modelImportExecutor.shutdownNow()
        if (::developerProbes.isInitialized) developerProbes.close()
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
        if (!modelOperationGate.tryBeginImport()) {
            statusView.text = modelOperationBusyText()
            return
        }
        refreshModelOperationControls()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT_GEMMA4_MODEL)
        } catch (error: Throwable) {
            modelOperationGate.finishImport()
            refreshModelOperationControls()
            statusView.text = "Gemma 4 model import failed: picker_${error.javaClass.simpleName}"
        }
    }

    private fun showGemma4DownloadConfirmation() {
        if (modelOperationGate.state() != Gemma4ModelOperationState.IDLE) {
            statusView.text = modelOperationBusyText()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download Gemma 4 model")
            .setMessage(modelDownloadPresentation.confirmationMessage)
            .setPositiveButton(modelDownloadPresentation.confirmButtonLabel) { _, _ ->
                startGemma4Download()
            }
            .setNegativeButton(modelDownloadPresentation.cancelButtonLabel, null)
            .show()
    }

    private fun startGemma4Download() {
        if (!modelOperationGate.tryBeginDownload()) {
            statusView.text = modelOperationBusyText()
            return
        }
        modelDownloadProgress.progress = 0
        modelDownloadProgress.isIndeterminate = false
        refreshModelOperationControls()
        statusView.text = "Starting reviewed Gemma 4 download…"
        modelImportExecutor.execute {
            var lastUiBytes = -MODEL_DOWNLOAD_PROGRESS_STEP_BYTES
            val result = modelDownloader.download { progress ->
                val expected = progress.expectedBytes
                val shouldPublish =
                    progress.bytesRead - lastUiBytes >= MODEL_DOWNLOAD_PROGRESS_STEP_BYTES ||
                        (expected != null && progress.bytesRead >= expected)
                if (shouldPublish) {
                    lastUiBytes = progress.bytesRead
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        modelDownloadProgress.isIndeterminate = expected == null
                        if (expected != null) {
                            modelDownloadProgress.progress = modelDownloadPresentation.progressPercent(progress)
                        }
                        statusView.text = modelDownloadPresentation.progressText(progress)
                    }
                }
            }
            modelOperationGate.finishDownload()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                refreshModelOperationControls()
                statusView.text = when (result) {
                    is Gemma4ModelInstallResult.Success ->
                        "Gemma 4 model ready: ${result.modelId}; ${result.bytesWritten} bytes; sha256=${result.sha256}"

                    is Gemma4ModelInstallResult.Failure -> when (result.reason) {
                        "model_download_cancelled" -> "Gemma 4 model download cancelled"
                        else -> "Gemma 4 model download failed: ${result.reason}"
                    }
                }
            }
        }
    }

    private fun cancelGemma4Download() {
        if (modelDownloader.cancel()) {
            modelDownloadCancelButton.isEnabled = false
            statusView.text = "Cancelling Gemma 4 model download…"
        }
    }

    private fun refreshModelOperationControls() {
        when (modelOperationGate.state()) {
            Gemma4ModelOperationState.IDLE -> {
                modelImportButton.isEnabled = true
                modelDownloadButton.isEnabled = true
                modelDownloadCancelButton.isEnabled = false
                modelDownloadProgress.visibility = View.GONE
            }

            Gemma4ModelOperationState.IMPORTING -> {
                modelImportButton.isEnabled = false
                modelDownloadButton.isEnabled = false
                modelDownloadCancelButton.isEnabled = false
                modelDownloadProgress.visibility = View.GONE
            }

            Gemma4ModelOperationState.DOWNLOADING -> {
                modelImportButton.isEnabled = false
                modelDownloadButton.isEnabled = false
                modelDownloadCancelButton.isEnabled = true
                modelDownloadProgress.visibility = View.VISIBLE
            }
        }
    }

    private fun modelOperationBusyText(): String =
        "Gemma 4 model operation already running: ${modelOperationGate.state().name.lowercase()}"

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_GEMMA4_MODEL) return
        if (resultCode != RESULT_OK) {
            modelOperationGate.finishImport()
            refreshModelOperationControls()
            statusView.text = "Gemma 4 model import cancelled"
            return
        }
        val uri = data?.data
        if (uri == null) {
            modelOperationGate.finishImport()
            refreshModelOperationControls()
            statusView.text = "Gemma 4 model import failed: model_source_missing"
            return
        }

        refreshModelOperationControls()
        statusView.text = "Importing Gemma 4 model and verifying SHA-256…"
        modelImportExecutor.execute {
            val result = modelImporter.import(uri)
            modelOperationGate.finishImport()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                refreshModelOperationControls()
                statusView.text = when (result) {
                    is Gemma4ModelInstallResult.Success ->
                        "Gemma 4 model ready: ${result.modelId}; ${result.bytesWritten} bytes; sha256=${result.sha256}"

                    is Gemma4ModelInstallResult.Failure ->
                        "Gemma 4 model import failed: ${result.reason}"
                }
            }
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
        const val MODEL_DOWNLOAD_PROGRESS_STEP_BYTES = 16L * 1024L * 1024L
        const val REQUEST_RECORD_AUDIO = 1001
        const val REQUEST_IMPORT_GEMMA4_MODEL = 1003
        const val EXTRA_RUN_CAPABILITY_PROBE = "run_probe"
    }
}
