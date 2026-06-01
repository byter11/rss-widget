package com.byterdevs.rsswidget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.getColorResCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RssWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {

            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {

                val workRequest = OneTimeWorkRequestBuilder<RssWidgetUpdateWorker>()
                    .setInputData(
                        Data.Builder()
                            .putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            .putBoolean("hardRefresh", true)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    companion object {

        @SuppressLint("RemoteViewLayout")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            val prefs = context.getWidgetPrefs(appWidgetId)

            val views = RemoteViews(context.packageName, R.layout.widget_rss)

            val themedContext = getThemedContextForWidget(context, prefs.themeMode)
            val colorForeground = themedContext.getColorResCompat(android.R.attr.colorForeground)

            // Background transparency
            ThemeUtils.setBgTransparency(
                context,
                views,
                R.id.widget_rss,
                prefs.transparency,
                prefs.themeMode
            )

            // RESPONSIVE HEADER
            if (prefs.compactMode) {
                views.setViewPadding(R.id.control_bar, 12, 4, 8, 2)
                views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            } else {
                views.setViewPadding(R.id.control_bar, 16, 8, 12, 4)
                views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            }

            // ----------------------------
            // STATIC UI
            // ----------------------------
            if (prefs.showHeaderBar) {
                views.setViewVisibility(R.id.control_bar, View.VISIBLE)
                views.setViewVisibility(R.id.header_divider, View.VISIBLE)

                if (prefs.showTitle) {
                    views.setViewVisibility(R.id.widget_title, View.VISIBLE)
                    val title = if (prefs.customTitle.isNullOrEmpty()) {
                        context.getString(R.string.app_label)
                    } else {
                        prefs.customTitle
                    }
                    views.setTextViewText(R.id.widget_title, title)
                    views.setTextColor(R.id.widget_title, colorForeground)
                } else {
                    views.setViewVisibility(R.id.widget_title, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.control_bar, View.GONE)
                views.setViewVisibility(R.id.header_divider, View.GONE)
            }

            views.setTextColor(R.id.empty_text, colorForeground)
            
            // Set divider color with alpha (subtle line)
            val dividerColor = (colorForeground and 0x00FFFFFF) or 0x26000000 // ~15% alpha
            views.setInt(R.id.header_divider, "setBackgroundColor", dividerColor)

            // Buttons with subtle transparency
            views.setInt(R.id.btn_refresh, "setColorFilter", colorForeground)
            views.setInt(R.id.btn_settings, "setColorFilter", colorForeground)
            
            // Apply alpha programmatically for maximum stability
            views.setInt(R.id.btn_refresh, "setAlpha", 180)
            views.setInt(R.id.btn_settings, "setAlpha", 180)

            views.setViewVisibility(
                R.id.btn_refresh,
                if (prefs.showRefreshButton) View.VISIBLE else View.GONE
            )

            // ----------------------------
            // FIXED REMOTE ADAPTER (IMPORTANT)
            // ----------------------------
            val intent = Intent(context, RssRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

                // CRITICAL: prevents widget list caching bugs
                data = Uri.parse("rss://widget/$appWidgetId/${System.currentTimeMillis()}")
            }

            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // ----------------------------
            // CLICK HANDLING
            // ----------------------------
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)

            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

            // ----------------------------
            // REFRESH BUTTON
            // ----------------------------
            val refreshIntent = Intent(context, RssWidgetProvider::class.java).apply {
                action = "com.byterdevs.rsswidget.ACTION_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            // ----------------------------
            // SETTINGS BUTTON
            // ----------------------------
            val settingsIntent = Intent(context, RssWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 20000,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent)

            // ----------------------------
            // APPLY UPDATE
            // ----------------------------
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // FORCE LIST REFRESH
            appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetId,
                R.id.widget_list
            )

            enqueuePeriodicUpdate(context, appWidgetId)
        }

        private fun enqueuePeriodicUpdate(context: Context, appWidgetId: Int) {

            val prefs = context.getWidgetPrefs(appWidgetId)

            if (prefs.updateInterval == 0) return

            val workRequest = PeriodicWorkRequestBuilder<RssWidgetUpdateWorker>(
                prefs.updateInterval.toLong(),
                TimeUnit.MINUTES
            )
                .setInputData(
                    Data.Builder()
                        .putInt("appWidgetId", appWidgetId)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rss_widget_update_$appWidgetId",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}