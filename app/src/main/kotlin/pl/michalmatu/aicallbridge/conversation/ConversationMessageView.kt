package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed

import android.widget.LinearLayout.LayoutParams

    internal fun conversationMessageView(
        context: Context,
        line: ConversationChatFeed.Line,
        ongoing: Boolean = true,
        onAudioTap: (() -> Unit)? = null,
        audioTag: String? = null,
        audioHint: String? = null,
    ): View {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
        val isAgent = line.speaker == ConversationChatFeed.Speaker.ASSISTANT
        val label = if (line.speaker == ConversationChatFeed.Speaker.TOOL) "NARZĘDZIE • TURA "
        else if (!isAgent) "ROZMÓWCA / STT • TURA " else when (line.responseKind) {
            ConversationChatFeed.ResponseKind.MODEL_DIRECT -> "AI / MODEL • TURA "
            ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED -> "AI / SKRYPT • TURA "
            ConversationChatFeed.ResponseKind.UNSPECIFIED -> "AI • TURA "
        }
        val time = DateFormat.format("HH:mm:ss", line.atEpochMs).toString()
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (onAudioTap != null) {
                tag = audioTag
                isClickable = true
                isFocusable = true
                contentDescription = line.text + ". " + audioHint.orEmpty()
                setOnClickListener { onAudioTap() }
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(if (isAgent) Color.rgb(29, 76, 140) else Color.rgb(40, 52, 69))
            }
            setPadding(dp(12), dp(9), dp(12), dp(10))
            addView(TextView(context).apply {
                text = (if (line.turn == 0 && isAgent) "AI / POWITANIE" else label + line.turn) + " • " + time
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(185, 207, 234))
            })
            addView(TextView(context).apply {
                text = line.text
                textSize = 14f
                setTextColor(Color.WHITE)
                setTextIsSelectable(onAudioTap == null)
                setPadding(0, dp(4), 0, 0)
            })
            if (isAgent) addView(TextView(context).apply {
                text = line.txStatus ?: if (ongoing) "Odpowiedź wygenerowana • TTS/TX w toku" else "Brak potwierdzenia wysłania"
                textSize = 11f
                setTextColor(Color.rgb(191, 229, 252))
                setPadding(0, dp(5), 0, 0)
            })
            if (onAudioTap != null) addView(TextView(context).apply {
                tag = audioTag?.let { it + ":hint" }
                text = audioHint.orEmpty()
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(206, 233, 255))
                setPadding(0, dp(8), 0, dp(1))
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
