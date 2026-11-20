package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.R
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
import android.widget.ImageView
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
        bodyTag: String? = null,
        headerOverride: String? = null,
    ): View {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
        val isAgent = line.speaker == ConversationChatFeed.Speaker.ASSISTANT
        val label = if (line.speaker == ConversationChatFeed.Speaker.TOOL) R.string.chat_turn_tool
        else if (!isAgent) R.string.chat_turn_counterpart else when (line.responseKind) {
            ConversationChatFeed.ResponseKind.MODEL_DIRECT -> R.string.chat_turn_model
            ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED -> R.string.chat_turn_script
            ConversationChatFeed.ResponseKind.UNSPECIFIED -> R.string.chat_turn_assistant
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
            val header = TextView(context).apply {
                text = headerOverride ?: ((if (line.turn == 0 && isAgent) context.getString(R.string.chat_turn_welcome)
                    else context.getString(label, line.turn)) + " • " + time)
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(185, 207, 234))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            if (onAudioTap != null) {
                // Header icon shares the first line; the entire bubble stays the
                // large, accessible tap target. No extra instruction row.
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(header, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    addView(ImageView(context).apply {
                        tag = audioTag?.let { it + ":hint" }
                        setImageResource(android.R.drawable.ic_media_play)
                        setColorFilter(Color.rgb(206, 233, 255))
                        contentDescription = audioHint
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(dp(20), dp(20)).apply {
                        marginStart = dp(8)
                    })
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            } else addView(header)
            addView(TextView(context).apply {
                tag = bodyTag ?: audioTag?.let { it + ":body" }
                text = line.text
                textSize = 14f
                setTextColor(Color.WHITE)
                setTextIsSelectable(onAudioTap == null)
                setPadding(0, dp(4), 0, 0)
            })
            if (isAgent) addView(TextView(context).apply {
                text = line.txStatus ?: context.getString(if (ongoing) R.string.chat_tx_pending else R.string.chat_tx_unconfirmed)
                textSize = 11f
                setTextColor(Color.rgb(191, 229, 252))
                setPadding(0, dp(5), 0, 0)
            })
            // Playback is indicated in the header; no footer or shifting text.
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
