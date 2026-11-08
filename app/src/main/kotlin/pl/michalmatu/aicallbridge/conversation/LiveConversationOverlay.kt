package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Transient owner-only overlay; no call, grant, recording or persistent transcript side effects. */
internal class LiveConversationOverlay(context: Context, underlying: View) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var opened = false
    private var wasActive = false
    private var lastSeenLines = 0
    private var unread = false
    private fun dp(value: Int) = UiDesign.dp(context, value)

    private val scrim = View(context).apply {
        tag = "conversation-floating-scrim"
        setBackgroundColor(0x660E1727)
        visibility = View.GONE
        setOnClickListener { close() }
    }
    private val panel = ConversationChatPanel(context, initiallyExpanded = true,
        fillAvailableHeight = true, onClose = ::close).apply {
        tag = "conversation-floating-panel"
        visibility = View.GONE
        elevation = dp(14).toFloat()
    }
    private val bubble = ImageButton(context).apply {
        tag = "conversation-floating-button"
        contentDescription = context.getString(R.string.ux10_chat_open)
        setImageResource(R.drawable.ic_aicalls_chat_bubble)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(15), dp(15), dp(15), dp(15))
        minimumHeight = dp(56)
        minimumWidth = dp(56)
        background = UiDesign.touchSurface(context, UiDesign.primary, 28)
        elevation = dp(9).toFloat()
        visibility = View.GONE
        setOnClickListener { open() }
    }
    private val dot = View(context).apply {
        tag = "conversation-floating-unread"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(UiDesign.danger)
            setStroke(dp(2), Color.WHITE)
        }
        visibility = View.GONE
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val pulse = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            refresh()
            handler.postDelayed(this, 450L)
        }
    }
    init {
        clipChildren = false
        clipToPadding = false
        addView(underlying, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, dp(400), Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        addView(bubble, LayoutParams(dp(56), dp(56), Gravity.RIGHT or Gravity.BOTTOM).apply {
            rightMargin = dp(18)
            bottomMargin = dp(18)
        })
        addView(dot, LayoutParams(dp(13), dp(13), Gravity.RIGHT or Gravity.BOTTOM).apply {
            rightMargin = dp(17)
            bottomMargin = dp(61)
        })
        refresh()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) (panel.layoutParams as LayoutParams).also {
            it.height = (h * 0.84f).toInt().coerceAtLeast(dp(220))
            panel.layoutParams = it
        }
    }
    private fun open() {
        if (bubble.visibility != View.VISIBLE) return
        opened = true
        unread = false
        lastSeenLines = ConversationChatFeed.snapshot().lines.size
        refresh()
    }
    private fun close() {
        opened = false
        refresh()
    }
    private fun refresh() {
        val snapshot = ConversationChatFeed.snapshot()
        val available = snapshot.active && snapshot.lines.isNotEmpty()
        if (!available) {
            opened = false
            unread = false
            lastSeenLines = 0
        } else {
            if (!wasActive) lastSeenLines = 0
            if (snapshot.lines.size > lastSeenLines && !opened) unread = true
            if (opened) lastSeenLines = snapshot.lines.size
        }
        wasActive = snapshot.active
        scrim.visibility = if (available && opened) View.VISIBLE else View.GONE
        panel.visibility = if (available && opened) View.VISIBLE else View.GONE
        bubble.visibility = if (available && !opened) View.VISIBLE else View.GONE
        dot.visibility = if (available && !opened && unread) View.VISIBLE else View.GONE
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(pulse)
        handler.post(pulse)
    }
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(pulse)
        super.onDetachedFromWindow()
    }
}
