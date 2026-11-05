package pl.michalmatu.aicallbridge.conversation

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

class ConversationHistoryActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var history: RecordedConversationHistory
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        history = RecordedConversationHistory(File(filesDir, CallSessionEvidenceRecorder.ROOT))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32) }
        setContentView(ScrollView(this).apply { addView(content) })
        showList()
    }
    private fun showList() {
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
                    session.lines.forEach { content.addView(conversationMessageView(this, it, ongoing = !session.complete)) }
                }.onFailure { failure -> content.addView(TextView(this).apply { text = "Błąd odczytu: ${failure.javaClass.simpleName}" }) }
            }
        }
    }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}
