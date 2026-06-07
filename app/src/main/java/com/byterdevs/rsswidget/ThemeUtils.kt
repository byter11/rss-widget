package com.byterdevs.rsswidget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemeUtils {
    fun getThemedContextForWidget(context: Context, themeMode: ThemeMode): Context {
        val configuration = Configuration(context.resources.configuration)

        val uiMode = when (themeMode) {
            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
            ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }

        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiMode

        val configurationContext = context.createConfigurationContext(configuration)
        return ContextThemeWrapper(configurationContext, R.style.Theme_RSSWidget)
    }

    @ColorInt
    fun Context.getColorResCompat(@AttrRes id: Int): Int {
        val resolvedAttr = TypedValue()
        if (this.theme.resolveAttribute(id, resolvedAttr, true)) {
            return if (resolvedAttr.resourceId != 0) {
                ContextCompat.getColor(this, resolvedAttr.resourceId)
            } else {
                resolvedAttr.data
            }
        }
        return android.graphics.Color.BLACK
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

    fun setBgTransparency(context: Context, views: RemoteViews, viewId: Int, transparency: Float, themeMode: ThemeMode): RemoteViews {
        if (themeMode == ThemeMode.SYSTEM) return setBgTransparency(context, views, viewId, transparency)

        val themedContext = getThemedContextForWidget(context, themeMode)
        val backgroundColor = themedContext.getColorResCompat(android.R.attr.colorBackground)

        // We set the base drawable to a solid white shape (background_1_0 should be white)
        views.setInt(viewId, "setBackgroundResource", R.drawable.background_1_0)

        // Calculate alpha based on transparency (0-100)
        val alpha = (transparency * 2.55f).toInt().coerceIn(0, 255)
        // Combine background color with alpha
        val colorWithAlpha = (backgroundColor and 0x00FFFFFF) or (alpha shl 24)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(viewId, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(colorWithAlpha))
        } else {
            views.setInt(viewId, "setBackgroundColor", colorWithAlpha)
        }

        return views
    }

    /**
     * Applies theme colors to the main widget container and header.
     */
    fun applyThemeToWidget(context: Context, views: RemoteViews, themeMode: ThemeMode) {
        val themedContext = getThemedContextForWidget(context, themeMode)

        // Header Text
        val titleColor = themedContext.getColorResCompat(android.R.attr.textColorPrimary)
        views.setTextColor(R.id.widget_title, titleColor)

        // Divider
        val dividerColor = themedContext.getColorResCompat(android.R.attr.textColorSecondary)
        views.setInt(R.id.header_divider, "setBackgroundColor", dividerColor)

        // Icons
        val iconColor = themedContext.getColorResCompat(android.R.attr.textColorSecondary)
        views.setInt(R.id.btn_settings, "setColorFilter", iconColor)
        views.setInt(R.id.btn_refresh, "setColorFilter", iconColor)
    }
}