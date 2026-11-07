package pl.michalmatu.aicallbridge.ui

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.widget.ImageButton
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.view.ViewGroup

/** Presentation-only styles; never owns an AI action, a grant, or audio state. */
internal enum class UiActionTone { PRIMARY, SECONDARY, DANGER }

internal object UiDesign {
    val background = Color.rgb(247, 249, 253)
    val surface = Color.WHITE
    val ink = Color.rgb(27, 42, 64)
    val secondary = Color.rgb(80, 96, 116)
    val border = Color.rgb(211, 222, 235)
    val softAccent = Color.rgb(239, 245, 253)
    val primary = Color.rgb(39, 91, 157)
    val danger = Color.rgb(161, 36, 55)
    val dangerSurface = Color.rgb(255, 238, 241)
    const val GAP_SMALL = 8
    const val GAP_ACTION = 12
    const val GAP_SECTION = 24
    const val CONTENT_INSET = 20
    fun dp(context: Context, x: Int) = (context.resources.displayMetrics.density * x + 0.5f).toInt()
    fun surfaceDrawable(context: Context, fill: Int = surface, radiusDp: Int = 14) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            setStroke(dp(context, 1).coerceAtLeast(1), border)
        }
    fun touchSurface(context: Context, fill: Int = surface, radiusDp: Int = 14): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Color.rgb(225, 233, 246)),
            surfaceDrawable(context, fill, radiusDp), null)

    fun iconButton(context: Context, icon: Int, description: String, iconColor: Int = ink) =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(iconColor)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            minimumWidth = dp(context, 48)
            minimumHeight = dp(context, 48)
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            background = touchSurface(context, softAccent, 10)
            stateListAnimator = null
            elevation = 0f
        }

    fun styleAction(button: Button, tone: UiActionTone = UiActionTone.SECONDARY): Button =
        button.apply {
            val context = button.context
            isAllCaps = false
            textSize = 14f
            minimumHeight = dp(context, 48)
            minHeight = dp(context, 48)
            setPadding(dp(context, 14), dp(context, 10),
                dp(context, 14), dp(context, 10))
            val fill = when (tone) {
                UiActionTone.PRIMARY -> primary
                UiActionTone.DANGER -> dangerSurface
                UiActionTone.SECONDARY -> softAccent
            }
            val foreground = when (tone) {
                UiActionTone.PRIMARY -> Color.WHITE
                UiActionTone.DANGER -> danger
                UiActionTone.SECONDARY -> ink
            }
            setTextColor(foreground)
            background = touchSurface(context, fill, 12)
            stateListAnimator = null
            elevation = 0f
        }

    fun action(context: Context, text: String, tone: UiActionTone = UiActionTone.SECONDARY,
               onClick: () -> Unit): Button =
        styleAction(Button(context).apply {
            this.text = text
            setOnClickListener { onClick() }
        }, tone)

    fun card(context: Context, inset: Int = 16): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = surfaceDrawable(context, surface, 16)
            setPadding(dp(context, inset), dp(context, inset),
                dp(context, inset), dp(context, inset))
        }

    fun caption(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 13f
        setTextColor(secondary)
        setLineSpacing(dp(context, 2).toFloat(), 1f)
    }

    fun spaceAfter(context: Context, gap: Int = GAP_ACTION) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(context, gap) }

    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value
        styleAction(this)
        setOnClickListener { action() }
    }
    fun heading(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 20f
        setTextColor(ink)
        gravity = Gravity.CENTER_VERTICAL
    }
}
