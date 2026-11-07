package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed

/**
 * Owner-only, read-only conversation dock shared by main and campaign screens.
 * All live text stays in app-process memory; the UI polls only while attached.
 * Never starts, ends or modifies a call, a grant or evidence recording.
 */
internal class ConversationChatPanel(context: Context, initiallyExpanded: Boolean = true) : LinearLayout(context) {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var renderedRevision = -1L
    private var expanded = initiallyExpanded

    private val statusLabel = TextView(context).apply {
        text = context.getString(R.string.live_chat_inactive)
        textSize = 12f
        setTextColor(UiDesign.secondary)
        setPadding(dp(14), 0, dp(14), dp(9))
    }

    private val messages = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }

    private val messageScroll = ScrollView(context).apply {
        tag = "live-chat-scroll"
        visibility = if (expanded) View.VISIBLE else View.GONE
        isFillViewport = true
        addView(messages, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(184))
    }

    private val clearButton = Button(context).apply {
        text = context.getString(R.string.live_chat_clear)
        textSize = 11f
        isAllCaps = false
        visibility = if (expanded) View.VISIBLE else View.GONE
        setOnClickListener { ConversationChatFeed.clearVisibleHistory() }
        contentDescription = context.getString(R.string.live_chat_clear_description)
    }

    private val toggle = ImageButton(context).apply {
        tag = "live-chat-toggle"
        contentDescription = context.getString(if (expanded) R.string.live_chat_collapse else R.string.live_chat_expand)
        setImageResource(if (expanded) R.drawable.ic_aicalls_chevron_up else R.drawable.ic_aicalls_chevron_down)
        imageTintList = ColorStateList.valueOf(UiDesign.ink)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        stateListAnimator = null
        elevation = 0f
        background = UiDesign.touchSurface(context, UiDesign.softAccent, 10)
        setOnClickListener {
            expanded = !expanded
            messageScroll.visibility = if (expanded) View.VISIBLE else View.GONE
            clearButton.visibility = if (expanded) View.VISIBLE else View.GONE
            statusLabel.visibility = if (expanded || ConversationChatFeed.snapshot().active) View.VISIBLE else View.GONE
            setImageResource(if (expanded) R.drawable.ic_aicalls_chevron_up else R.drawable.ic_aicalls_chevron_down)
            contentDescription = context.getString(if (expanded) R.string.live_chat_collapse else R.string.live_chat_expand)
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            render(ConversationChatFeed.snapshot())
            refreshHandler.postDelayed(this, 450L)
        }
    }

    init {
        orientation = VERTICAL
        tag = "live-chat-dock"
        background = UiDesign.surfaceDrawable(context, UiDesign.surface, 16)
        elevation = 0f
        contentDescription = context.getString(R.string.live_chat_private_preview)

        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(TextView(context).apply {
                text = context.getString(R.string.live_chat_title)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiDesign.ink)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle, LayoutParams(dp(48), dp(48)))
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), 0, dp(16), 0)
            addView(clearButton)
        })
        addView(statusLabel)
        statusLabel.visibility = if (expanded) View.VISIBLE else View.GONE
        addView(messageScroll)
        render(ConversationChatFeed.snapshot())
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

    private fun render(snapshot: ConversationChatFeed.Snapshot) {
        if (snapshot.revision == renderedRevision) return
        renderedRevision = snapshot.revision
        statusLabel.text = (if (snapshot.active) "● " else "○ ") + snapshot.status
        statusLabel.visibility = if (expanded || snapshot.active) View.VISIBLE else View.GONE

        val previousY = messageScroll.scrollY
        val atBottom = messages.height <= messageScroll.height ||
            previousY + messageScroll.height >= messages.height - dp(36)

        messages.removeAllViews()
        if (snapshot.lines.isEmpty()) {
            messages.addView(TextView(context).apply {
                text = if (snapshot.active) {
                    context.getString(R.string.live_chat_listening)
                } else {
                    context.getString(R.string.live_chat_hint)
                }
                textSize = 13f
                setTextColor(UiDesign.secondary)
                setPadding(dp(5), dp(12), dp(5), dp(10))
            })
        } else {
            snapshot.lines.forEach { line -> messages.addView(conversationMessageView(context, line, snapshot.active)) }
        }
        messageScroll.post {
            if (atBottom) messageScroll.fullScroll(View.FOCUS_DOWN)
            else messageScroll.scrollTo(0, previousY)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
