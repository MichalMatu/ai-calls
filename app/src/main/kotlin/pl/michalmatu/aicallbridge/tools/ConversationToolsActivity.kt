package pl.michalmatu.aicallbridge.tools

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.*
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

class ConversationToolsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val refresh = object : Runnable {
        override fun run() { status.text = ConversationToolUi.latestText(); handler.postDelayed(this, 500L) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ConversationToolSettingsStore(this)
        val enabled = store.enabled()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(UiDesign.dp(this@ConversationToolsActivity, 28), UiDesign.dp(this@ConversationToolsActivity, 36), UiDesign.dp(this@ConversationToolsActivity, 28), UiDesign.dp(this@ConversationToolsActivity, 28)) }
        content.addView(TextView(this).apply { text = "Narzędzia AI"; textSize = 22f })
        content.addView(TextView(this).apply { text = "Narzędzia są wspólne dla modeli. Włączenie możliwości nie zmienia zakresu zadania ani kontroli połączenia. Wyłączenie zatrzymuje nowe wykonania także podczas rozmowy." })
        val choices = ConversationToolId.entries.associateWith { tool ->
            CheckBox(this).apply { text = tool.title + "\n" + tool.description; isChecked = tool in enabled; content.addView(this) }
        }
        val feedback = TextView(this)
        content.addView(Button(this).apply {
            text = "Zapisz ustawienia narzędzi"
            setOnClickListener {
                runCatching { store.save(choices.filterValues { it.isChecked }.keys) }
                    .onSuccess { feedback.text = "Zapisano ustawienia narzędzi." }
                    .onFailure { feedback.text = "Nie zapisano ustawień narzędzi." }
            }
        })
        content.addView(feedback)
        status = TextView(this).apply { textSize = 16f }; content.addView(status)
        content.addView(TextView(this).apply { text = "Żądanie zakończenia jest wykonywane po odtworzeniu pożegnania, wyłącznie dla rozmowy nadal obsługiwanej przez AI. Wynik i etapy znajdziesz w czacie oraz prywatnej historii rozmowy." })
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
