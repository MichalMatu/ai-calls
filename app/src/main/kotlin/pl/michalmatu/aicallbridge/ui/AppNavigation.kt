package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity

/** One stable navigation contract. Presentation never starts a call or changes authorization. */
internal enum class AppTab(val title: Int, val image: Int) {
    HOME(R.string.ui_tab_home, R.drawable.ic_aicalls_home),
    HISTORY(R.string.ui_tab_history, R.drawable.ic_aicalls_history),
    LAB(R.string.ui_tab_lab, R.drawable.ic_aicalls_lab),
    SETTINGS(R.string.ui_tab_settings, R.drawable.ic_aicalls_settings),
}

internal object AppNavigation {
    fun open(activity: Activity, tab: AppTab) {
        val destination: Class<out Activity> = when (tab) {
            AppTab.HOME -> MainActivity::class.java
            AppTab.HISTORY -> ConversationHistoryActivity::class.java
            AppTab.LAB -> ConversationSkillLabActivity::class.java
            AppTab.SETTINGS -> SettingsHubActivity::class.java
        }
        if (activity.javaClass == destination) return
        activity.startActivity(Intent(activity, destination).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    fun bar(activity: Activity, active: AppTab) = LinearLayout(activity).apply {
        tag = "app-bottom-navigation"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(UiDesign.surface)
        elevation = UiDesign.dp(activity, 4).toFloat()
        val density = activity.resources.displayMetrics.density
        setPadding(UiDesign.dp(activity, 6), UiDesign.dp(activity, 4),
            UiDesign.dp(activity, 6), UiDesign.dp(activity, 4))
        for (tab in AppTab.entries) {
            val selected = tab == active
            val labelColor = if (selected) Color.rgb(22, 83, 160) else UiDesign.secondary
            val item = LinearLayout(activity).apply {
                tag = "app-tab:" + tab.name.lowercase()
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = UiDesign.dp(activity, 60)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = activity.getString(tab.title)
                setOnClickListener { open(activity, tab) }
                background = if (selected) GradientDrawable().apply {
                    setColor(UiDesign.softAccent)
                    cornerRadius = UiDesign.dp(activity, 16).toFloat()
                } else null
            }
            val icon = ImageView(activity).apply {
                setImageResource(tab.image)
                setColorFilter(labelColor)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            item.addView(icon, LinearLayout.LayoutParams(
                UiDesign.dp(activity, 24), UiDesign.dp(activity, 24)))
            item.addView(TextView(activity).apply {
                text = activity.getString(tab.title)
                textSize = 11f
                setTextColor(labelColor)
                gravity = Gravity.CENTER
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiDesign.dp(activity, 3) })
            addView(item, LinearLayout.LayoutParams(
                0, UiDesign.dp(activity, 62), 1f
            ).apply { marginStart = UiDesign.dp(activity, 2); marginEnd = UiDesign.dp(activity, 2) })
        }
    }

    fun install(activity: Activity, body: View, active: AppTab) {
        val root = LinearLayout(activity).apply {
            tag = "app-screen-shell"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiDesign.background)
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(0, bars.top, 0, bars.bottom)
                } else {
                    view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                }
                insets
            }
            addView(body, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar(activity, active))
        }
        activity.window.statusBarColor = UiDesign.background
        activity.window.navigationBarColor = UiDesign.surface
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        activity.setContentView(root)
    }
}
