package pl.michalmatu.aicallbridge

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.developerrelay.LiveCallChatFeed

/**
 * Owner-only, read-only conversation dock shared by main and campaign screens.
 * All live text stays in app-process memory; the UI polls only while attached.
 * Never starts, ends or modifies a call, a grant or evidence recording.
 */
internal class LiveCallChatPanel(context: Context) : LinearLayout(context) {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var renderedRevision = -1L
    private var expanded = true

    private val statusLabel = TextView(context).apply {
        text = "Brak aktywnej rozmowy"
        textSize = 12f
        setTextColor(Color.rgb(196, 205, 221))
        setPadding(dp(14), 0, dp(14), dp(9))
    }

    private val messages = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }

    private val messageScroll = ScrollView(context).apply {
        isFillViewport = true
        addView(messages, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(184))
    }

    private val toggle = Button(context).apply {
        text = "▾"
        textSize = 16f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setOnClickListener {
            expanded = !expanded
            messageScroll.visibility = if (expanded) View.VISIBLE else View.GONE
            text = if (expanded) "▾" else "▸"
            contentDescription = if (expanded) "Zwiń czat" else "Rozwiń czat"
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            render(LiveCallChatFeed.snapshot())
            refreshHandler.postDelayed(this, 450L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(14, 23, 39))
        elevation = dp(4).toFloat()
        contentDescription = "Prywatny podgląd rozmowy na żywo"

        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(6), 0)
            addView(TextView(context).apply {
                text = "ROZMOWA NA ŻYWO"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Wyczyść widok"
                textSize = 11f
                isAllCaps = false
                setOnClickListener { LiveCallChatFeed.clearVisibleHistory() }
                contentDescription = "Wyczyść lokalny widok czatu bez przerywania rozmowy"
            })
            addView(toggle)
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statusLabel)
        addView(messageScroll)
        render(LiveCallChatFeed.snapshot())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshHandler.removeCallbacks(refresh)
        refreshHandler.post(refresh)
    }

    override fun onDetachedFromWindow() {
        refreshHandler.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun render(snapshot: LiveCallChatFeed.Snapshot) {
        if (snapshot.revision == renderedRevision) return
        renderedRevision = snapshot.revision
        statusLabel.text = (if (snapshot.active) "● " else "○ ") + snapshot.status

        val previousY = messageScroll.scrollY
        val atBottom = messages.height <= messageScroll.height ||
            previousY + messageScroll.height >= messages.height - dp(36)

        messages.removeAllViews()
        if (snapshot.lines.isEmpty()) {
            messages.addView(TextView(context).apply {
                text = if (snapshot.active) {
                    "Nasłuch trwa. Rozpoznane wypowiedzi i tekst Gemmy pojawią się tutaj."
                } else {
                    "Otwórz rozmowę. Tutaj zobaczysz STT rozmówcy, odpowiedzi AI i status TX."
                }
                textSize = 13f
                setTextColor(Color.rgb(170, 185, 203))
                setPadding(dp(5), dp(12), dp(5), dp(10))
            })
        } else {
            snapshot.lines.forEach { line -> messages.addView(makeMessage(line)) }
        }
        messageScroll.post {
            if (atBottom) messageScroll.fullScroll(View.FOCUS_DOWN)
            else messageScroll.scrollTo(0, previousY)
        }
    }

    private fun makeMessage(line: LiveCallChatFeed.Line): View {
        val isAgent = line.speaker != LiveCallChatFeed.Speaker.OFFICE
        val label = when (line.speaker) {
            LiveCallChatFeed.Speaker.OFFICE -> "BIURO OBSŁUGI / STT • TURA "
            LiveCallChatFeed.Speaker.GEMMA -> "GEMMA RAW • TURA "
            LiveCallChatFeed.Speaker.SCRIPT -> "AI SKRYPT + GEMMA • TURA "
        }
        val bubble = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(if (isAgent) Color.rgb(29, 76, 140) else Color.rgb(40, 52, 69))
            }
            setPadding(dp(12), dp(9), dp(12), dp(10))
            addView(TextView(context).apply {
                text = label + line.turn
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(185, 207, 234))
            })
            addView(TextView(context).apply {
                text = line.text
                textSize = 14f
                setTextColor(Color.WHITE)
                setTextIsSelectable(true)
                setPadding(0, dp(4), 0, 0)
            })
            if (isAgent) addView(TextView(context).apply {
                text = line.txStatus ?: "Odpowiedź wygenerowana • TTS/TX w toku"
                textSize = 11f
                setTextColor(Color.rgb(191, 229, 252))
                setPadding(0, dp(5), 0, 0)
            })
        }
        return LinearLayout(context).apply {
            gravity = if (isAgent) Gravity.END else Gravity.START
            setPadding(if (isAgent) dp(28) else 0, dp(3),
                if (isAgent) 0 else dp(28), dp(3))
            addView(bubble, LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
