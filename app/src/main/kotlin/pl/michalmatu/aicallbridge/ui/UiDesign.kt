package pl.michalmatu.aicallbridge.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/** Presentation-only styles; never owns an AI action, a grant, or audio state. */
internal object UiDesign {
    val background = Color.rgb(247, 249, 253)
    val surface = Color.WHITE
    val ink = Color.rgb(27, 42, 64)
    val secondary = Color.rgb(80, 96, 116)
    val border = Color.rgb(211, 222, 235)
    val softAccent = Color.rgb(239, 245, 253)
    fun dp(context: Context, x: Int) = (context.resources.displayMetrics.density * x + 0.5f).toInt()
    fun surfaceDrawable(context: Context, fill: Int = surface, radiusDp: Int = 14) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            setStroke(dp(context, 1).coerceAtLeast(1), border)
        }
    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        minHeight = dp(context, 48)
        setTextColor(ink)
        background = surfaceDrawable(context, softAccent, 10)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        setOnClickListener { action() }
    }
    fun heading(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 20f
        setTextColor(ink)
        gravity = Gravity.CENTER_VERTICAL
    }
}
