package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.telecom.TelecomManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

class ConversationHistoryActivity : UiChromeActivity(AppTab.HISTORY) {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var history: RecordedConversationHistory
    private var playback: MediaPlayer? = null
    private var playbackStatus: TextView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        history = RecordedConversationHistory(File(filesDir, CallSessionEvidenceRecorder.ROOT))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32) }
        setContentView(ScrollView(this).apply { addView(content) })
        showList()
    }
    private fun showList() {
        stopPlayback()
        playbackStatus = null
        content.removeAllViews()
        content.addView(TextView(this).apply { text = "Historia rozmów"; textSize = 22f })
        content.addView(TextView(this).apply { text = "Sesje z włączonym lokalnym zapisem rozmowy. Wyczyszczenie widoku czatu na żywo nie usuwa archiwum." })
        io.execute {
            val result = runCatching { history.list() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { sessions ->
                    if (sessions.isEmpty()) content.addView(TextView(this).apply { text = "Brak zapisanych rozmów" })
                    sessions.forEach { session -> content.addView(Button(this).apply {
                        val direction = when (session.direction) {
                            ConversationDirection.OUTGOING -> "Wychodząca"; ConversationDirection.INCOMING -> "Przychodząca"; ConversationDirection.UNSPECIFIED -> "Rozmowa"
                        }
                        text = "$direction • ${DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs))} • ${session.lines.size} wypowiedzi"
                        setOnClickListener { showSession(session.id) }
                    }) }
                }.onFailure { failure -> content.addView(TextView(this).apply { text = "Błąd odczytu historii: ${failure.javaClass.simpleName}" }) }
            }
        }
    }
    private fun showSession(id: String) {
        io.execute {
            val result = runCatching { history.load(id) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                content.removeAllViews()
                content.addView(Button(this).apply { text = "Wszystkie rozmowy"; setOnClickListener { showList() } })
                result.onSuccess { session ->
                    if (session.invalidRecords > 0) content.addView(TextView(this).apply { text = "Niepełne dane archiwum: ${session.invalidRecords} uszkodzonych wpisów" })
                    playbackStatus = TextView(this).apply {
                        text = "Dostępne fragmenty audio: " + session.audioFiles.size +
                            ". Nie jest to nagranie całej sesji."
                    }
                    content.addView(playbackStatus)
                    session.lines.forEach { line ->
                        content.addView(conversationMessageView(this, line, ongoing = !session.complete))
                        session.audioFor(line)?.let { name ->
                            content.addView(Button(this).apply {
                                text = "▶ Odtwórz fragment"
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                    val linked = session.lines.mapNotNull(session::audioFor).toSet()
                    val unlinked = session.audioFiles.values.filterNot { it in linked }
                    if (unlinked.isNotEmpty()) {
                        content.addView(TextView(this).apply {
                            text = "Inne dostępne segmenty, w tym krótkie potwierdzenia AI:"
                        })
                        unlinked.forEach { name ->
                            content.addView(Button(this).apply {
                                text = "▶ Odtwórz dodatkowy segment"
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                }.onFailure { failure -> content.addView(TextView(this).apply { text = "Błąd odczytu: ${failure.javaClass.simpleName}" }) }
            }
        }
    }
    private fun playFragment(id: String, fileName: String) {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            playbackStatus?.text = "Do odsłuchu potrzebna jest zgoda Androida na odczyt stanu telefonu."
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            playbackStatus?.text = "Odsłuch możliwy tylko poza połączeniem telefonicznym."
            return
        }
        val file = history.resolveAudioFile(id, fileName)
        if (file == null) {
            playbackStatus?.text = "Nie znaleziono bezpiecznego lokalnego fragmentu WAV."
            return
        }
        runCatching {
            stopPlayback()
            val player = MediaPlayer()
            playback = player
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                if (playback === it) {
                    playback = null
                    playbackStatus?.text = "Odsłuch fragmentu zakończony."
                }
            }
            player.prepare()
            player.start()
            playbackStatus?.text = "Odtwarzanie zapisanego fragmentu audio."
        }.onFailure {
            stopPlayback()
            playbackStatus?.text = "Nie można odtworzyć fragmentu audio."
        }
    }
    private fun stopPlayback() {
        runCatching { playback?.release() }
        playback = null
    }
    override fun onPause() { stopPlayback(); super.onPause() }
    override fun onDestroy() { stopPlayback(); io.shutdown(); super.onDestroy() }
}
