package com.byterdevs.rsswidget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object ThemeUtils {
    fun getThemedContextForWidget(context: Context): Context {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        val newConfig = Configuration(context.resources.configuration)
        newConfig.uiMode = if (isNightMode) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val configurationContext = context.applicationContext.createConfigurationContext(newConfig)
        return ContextThemeWrapper(configurationContext, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
    }

    fun getBackgroundResource(transparency: Float): Int {
        return when (transparency) {
            in 0.0 .. 0.0 -> R.drawable.background_0_0
            in 0.0..0.1 -> R.drawable.background_0_1
            in 0.1..0.2 -> R.drawable.background_0_2
            in 0.2..0.3 -> R.drawable.background_0_3
            in 0.3..0.4 -> R.drawable.background_0_4
            in 0.4..0.5 -> R.drawable.background_0_5
            in 0.5..0.6 -> R.drawable.background_0_6
            in 0.6..0.7 -> R.drawable.background_0_7
            in 0.7..0.8 -> R.drawable.background_0_8
            in 0.8..0.9 -> R.drawable.background_0_9
            in 0.9..1.0 -> R.drawable.background_1_0
            else -> R.drawable.background_1_0
        }
    }
    fun setBgTransparency(context: Context, views: RemoteViews, viewName: Int, transparency: Float): RemoteViews {
        views.setInt(viewName, "setBackgroundResource", getBackgroundResource(transparency/100))
        return views
    }
}