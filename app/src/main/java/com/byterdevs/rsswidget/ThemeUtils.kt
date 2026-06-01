package com.byterdevs.rsswidget

import android.content.Context
import android.content.res.Configuration
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

    fun setBgTransparency(context: Context, views: RemoteViews, viewId: Int, transparency: Float, themeMode: ThemeMode): RemoteViews {
        val themedContext = getThemedContextForWidget(context, themeMode)
        val backgroundColor = themedContext.getColorResCompat(android.R.attr.colorBackground)
        
        // We set the base drawable to a solid white shape (background_1_0 should be white)
        views.setInt(viewId, "setBackgroundResource", R.drawable.background_1_0)
        
        // Calculate alpha based on transparency (0-100)
        val alpha = (transparency * 2.55f).toInt().coerceIn(0, 255)
        // Combine background color with alpha
        val colorWithAlpha = (backgroundColor and 0x00FFFFFF) or (alpha shl 24)
        
        views.setColorStateList(viewId, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(colorWithAlpha))
        return views
    }
}
