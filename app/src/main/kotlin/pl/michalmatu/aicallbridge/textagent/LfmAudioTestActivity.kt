package pl.michalmatu.aicallbridge.textagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.debug.UsbDebugBridge
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** On-device, English-only LFM off-call voice test. Does not mutate the Call profile. */
class LfmAudioTestActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean()
    private var nativeProcess: Process? = null
    private var audioRecorder: AudioRecord? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var state: android.widget.TextView
    private lateinit var gemma: Button
    private lateinit var lfm: Button
    private lateinit var pick: Button
    private lateinit var test: Button
    private lateinit var stop: Button
    private var running = false
    private val prefs get() = LfmAudioTestAssets.preferences(this)
    private val mode get() = prefs.getString(LfmAudioTestAssets.MODE_KEY, "gemma") ?: "gemma"

    override fun toolbarTitle() = "Test modeli audio"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        content.addView(UiDesign.heading(this, "Wybierz model do testu"),
            UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            "Test bez połączenia. Aktywny model telefoniczny i jego ustawienia pozostają bez zmian."),
            UiDesign.spaceAfter(this, 16))
        val switch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gemma = UiDesign.action(this, "Gemma E2B") {
            if (!running) select("gemma")
        }.apply { tag = "audio-lab-gemma" }
        lfm = UiDesign.action(this, "LFM2.5 Audio") {
            if (!running) select("lfm")
        }.apply { tag = "audio-lab-lfm" }
        switch.addView(gemma, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        switch.addView(lfm, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(switch, UiDesign.spaceAfter(this, 16))
        val card = UiDesign.card(this)
        state = UiDesign.caption(this, "").apply { tag = "audio-lab-model-status" }
        card.addView(state, UiDesign.spaceAfter(this, 12))
        pick = UiDesign.action(this, "Wskaż folder z 4 plikami GGUF") { chooseFolder() }
            .apply { tag = "audio-lab-pick-folder" }
        card.addView(pick, UiDesign.spaceAfter(this, 8))
        test = UiDesign.action(this, "Testuj", UiActionTone.PRIMARY) { onTestClick() }
            .apply { tag = "audio-lab-test" }
        card.addView(test, UiDesign.spaceAfter(this, 8))
        stop = UiDesign.action(this, "Zatrzymaj test", UiActionTone.DANGER) { cancelTest() }
            .apply { tag = "audio-lab-stop"; visibility = View.GONE }
        card.addView(stop)
        content.addView(card)
        setContentView(ScrollView(this).apply { addView(content) })
        refresh()
    }

    private fun select(value: String) {
        prefs.edit().putString(LfmAudioTestAssets.MODE_KEY, value).apply()
        refresh()
    }

    private fun refresh(message: String? = null) {
        if (isFinishing || isDestroyed) return
        gemma.isEnabled = !running && mode != "gemma"
        lfm.isEnabled = !running && mode != "lfm"
        pick.visibility = if (mode == "lfm") View.VISIBLE else View.GONE
        pick.isEnabled = !running
        val installed = LfmAudioTestAssets.selected(this) != null
        val runner = LfmAudioTestAssets.runnerReady(this)
        state.text = message ?: when {
            mode == "gemma" -> "Gemma E2B: użyj dotychczasowego laboratorium rozmów."
            !installed -> "LFM2.5: wybierz folder w Pobrane. Model zostanie jednorazowo zweryfikowany i skopiowany do aplikacji."
            !runner -> "LFM2.5: model zapisany. Brakuje zgodnego silnika audio w tej wersji aplikacji."
            else -> "LFM2.5 Audio Q4: gotowy do prywatnego testu mowy po angielsku."
        }
        test.text = if (mode == "gemma") "Testuj Gemmę" else "Testuj LFM (6 s nagrania)"
        test.isEnabled = !running && (mode == "gemma" || (installed && runner))
        stop.visibility = if (running) View.VISIBLE else View.GONE
    }

    private fun safelyIdle(): Boolean = runCatching {
        val snap = UsbDebugBridge.snapshot(this)
        snap.optBoolean("call_idle", false) &&
            snap.optBoolean("campaign_service_inactive", false) &&
            !snap.optBoolean("pending_retry", true)
    }.getOrDefault(false)

    private fun chooseFolder() {
        if (!safelyIdle()) { refresh("Telefon musi być bezczynny, bez aktywnej kampanii."); return }
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, PICK_FOLDER)
        } catch (_: Exception) { refresh("Systemowy wybór folderu jest niedostępny.") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (!safelyIdle()) { refresh("Telefon jest zajęty; import przerwany."); return }
        running = true
        cancelled.set(false)
        refresh("Weryfikacja i import plików LFM2.5…")
        worker.execute {
            val result = runCatching { LfmAudioTestAssets.importTree(this, uri) { percent ->
                runOnUiThread { if (running) refresh("Import LFM2.5: " + percent + "%") }
            }}.exceptionOrNull()
            runOnUiThread {
                running = false
                refresh(if (result == null) "LFM2.5: import poprawny. Możesz rozpocząć test."
                else "Nie udało się zaimportować modelu (" +
                    (result.message?.take(80) ?: "błąd plików") + ").")
            }
        }
    }

    private fun onTestClick() {
        if (running) return
        if (!safelyIdle()) { refresh("Test jest dostępny tylko poza połączeniem."); return }
        if (mode == "gemma") {
            startActivity(Intent(this, ConversationSkillLabActivity::class.java))
            return
        }
        if (LfmAudioTestAssets.selected(this) == null || !LfmAudioTestAssets.runnerReady(this)) {
            refresh("Najpierw wskaż zgodny model i zainstaluj silnik testowy.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            return
        }
        startLfm()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            onTestClick()
    }

    private fun startLfm() {
        val bundle = LfmAudioTestAssets.selected(this) ?: return
        running = true; cancelled.set(false)
        refresh("Nagrywanie. Mów po angielsku przez 6 sekund…")
        worker.execute {
            val input = File(cacheDir, "lfm-private-input.wav")
            val output = File(cacheDir, "lfm-private-output.wav")
            val logs = File(cacheDir, "lfm-private-runner.log")
            try {
                output.delete(); logs.delete()
                recordWav(input)
                if (cancelled.get()) return@execute
                runOnUiThread { refresh("LFM przetwarza głos lokalnie…") }
                val cli = LfmAudioTestAssets.runnerFile(this)
                val args = listOf(cli.absolutePath,
                    "-m", File(bundle, LfmAudioTestAssets.parts[0].name).absolutePath,
                    "-mm", File(bundle, LfmAudioTestAssets.parts[1].name).absolutePath,
                    "-mv", File(bundle, LfmAudioTestAssets.parts[2].name).absolutePath,
                    "--tts-speaker-file", File(bundle, LfmAudioTestAssets.parts[3].name).absolutePath,
                    "-sys", "Respond with interleaved text and audio.",
                    "--audio", input.absolutePath, "--output", output.absolutePath)
                val started = System.nanoTime()
                val process = ProcessBuilder(args)
                    .directory(File(applicationInfo.nativeLibraryDir))
                    .redirectErrorStream(true)
                    .redirectOutput(logs)
                    .apply { environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir }
                    .start()
                nativeProcess = process
                val finished = process.waitFor(210, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                if (cancelled.get()) return@execute
                val valid = finished && process.exitValue() == 0 &&
                    output.isFile && output.length() > 44L &&
                    output.inputStream().use { ByteArray(4).also { header ->
                        it.read(header)
                    }.contentEquals(byteArrayOf(82, 73, 70, 70)) }
                runOnUiThread {
                    if (valid) play(output, elapsed)
                    else refresh("LFM nie wygenerował poprawnego WAV. Próbę zakończono bez zmian Gemmy.")
                }
            } catch (_: Exception) {
                runOnUiThread { if (!cancelled.get()) refresh("Test LFM zakończył się błędem silnika lub nagrywania.") }
            } finally {
                nativeProcess = null
                input.delete(); logs.delete()
                runOnUiThread {
                    running = false
                    if (cancelled.get()) refresh("Test zatrzymany.")
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun recordWav(target: File) {
        val rate = 16000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(rate, channel, format)
        require(min > 0)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, rate, channel, format,
            maxOf(min * 2, 8192))
        audioRecorder = recorder
        val bytes = ByteArrayOutputStream(rate * 12)
        val buffer = ByteArray(4096)
        try {
            recorder.startRecording()
            require(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val until = System.nanoTime() + 6_000_000_000L
            while (!cancelled.get() && System.nanoTime() < until) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n > 0) bytes.write(buffer, 0, n)
                else if (n < 0) error("microphone_capture_failed")
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            audioRecorder = null
        }
        val audio = bytes.toByteArray()
        require(audio.size > 32000 && !cancelled.get())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(audio.size + 36)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16).putShort(1).putShort(1)
        header.putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)).putInt(audio.size)
        target.outputStream().use { it.write(header.array()); it.write(audio) }
    }

    private fun play(file: File, elapsed: Long) {
        mediaPlayer?.release()
        val media = MediaPlayer()
        mediaPlayer = media
        runCatching {
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener {
                media.release(); if (mediaPlayer === media) mediaPlayer = null
                file.delete()
            }
            media.prepare()
            media.start()
            refresh("LFM: odtwarzanie odpowiedzi. Inferencja: " + elapsed / 1000.0 + " s.")
        }.onFailure {
            media.release(); mediaPlayer = null
            file.delete()
            refresh("Nie udało się odtworzyć odpowiedzi modelu.")
        }
    }

    private fun cancelTest() {
        cancelled.set(true)
        runCatching { audioRecorder?.stop() }
        nativeProcess?.destroyForcibly()
        mediaPlayer?.release(); mediaPlayer = null
        refresh("Zatrzymuję test…")
    }

    override fun onPause() {
        cancelTest()
        super.onPause()
    }

    override fun onDestroy() {
        cancelTest()
        worker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val PICK_FOLDER = 93
        const val AUDIO_PERMISSION = 94
    }
}
