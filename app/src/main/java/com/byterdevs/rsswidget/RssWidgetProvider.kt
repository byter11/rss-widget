package com.byterdevs.rsswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import androidx.core.net.toUri
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

class RssWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("RssWidgetProvider", "onReceive triggered with action: ${intent.action}")

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            Log.d("RssWidgetProvider", "appWidgetId: $appWidgetId")
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val workRequest = OneTimeWorkRequestBuilder<RssWidgetUpdateWorker>()
                    .addTag("rss_widget_manual_refresh_$appWidgetId")
                    .setInputData(
                        Data.Builder().putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId).putBoolean("hardRefresh", true).build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            val prefs = context.getWidgetPrefs(appWidgetId)
            val views = setBgTransparency(
                context,
                RemoteViews(context.packageName, R.layout.widget_rss),
                R.id.widget_rss,
                prefs.transparency,
                prefs.themeMode
            )

            val showHeader = prefs.showHeaderBar
            views.setTextViewText(R.id.widget_title, prefs.title)
            views.setViewVisibility(R.id.control_bar, if (showHeader) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(R.id.widget_title, if (showHeader) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(R.id.header_divider, if (showHeader) android.view.View.VISIBLE else android.view.View.GONE)

            val intent = Intent(context, RssRemoteViewsService::class.java)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.data = intent.toUri(Intent.URI_INTENT_SCHEME).toUri()
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // Set up click and refresh intents
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

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

            ThemeUtils.applyThemeToWidget(context, views, prefs.themeMode, prefs.transparency)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)

            enqueuePeriodicUpdate(context, appWidgetId)
        }

        private fun enqueuePeriodicUpdate(context: Context, appWidgetId: Int) {
            val prefs = context.getWidgetPrefs(appWidgetId)
            if (prefs.updateInterval == 0) {
                return
            }

            val workRequest = PeriodicWorkRequestBuilder<RssWidgetUpdateWorker>(
                prefs.updateInterval.toLong(), TimeUnit.MINUTES
            ).addTag("rss_widget_update_$appWidgetId").setInputData(
                Data.Builder().putInt("appWidgetId", appWidgetId).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rss_widget_update_$appWidgetId", ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
        }
    }

}
