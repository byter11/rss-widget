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
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.DelicateCoroutinesApi

class RssWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        Log.d("RssWidgetProvider", "onUpdate: ids=${appWidgetIds.joinToString()}")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d("RssWidgetProvider", "onDeleted: ids=${appWidgetIds.joinToString()}")
        for (appWidgetId in appWidgetIds) {
            // Cancel workers
            WorkManager.getInstance(context).cancelUniqueWork("rss_widget_update_$appWidgetId")
            
            // Clear items from DB
            val db = com.byterdevs.rsswidget.room.RssDatabase.getInstance(context)
            val dao = db.rssItemDao()
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                dao.clearItemsForWidget(appWidgetId)
            }

            // Clear preferences
            context.deleteWidgetPrefs(appWidgetId)

            // Clear cached images
            RssWidgetUpdateWorker.clearAllImagesForWidget(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("RssWidgetProvider", "onReceive: action=${intent.action}")

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            Log.d("RssWidgetProvider", "onReceive: ACTION_REFRESH id=$appWidgetId")
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val workRequest = OneTimeWorkRequestBuilder<RssWidgetUpdateWorker>()
                    .addTag("rss_widget_manual_refresh_$appWidgetId")
                    .setInputData(
                        Data.Builder().putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId).putBoolean("hardRefresh", true).build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d("RssWidgetProvider", "onReceive: Enqueued worker for refresh")
            }
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            val prefs = context.getWidgetPrefs(appWidgetId)
            Log.d("RssWidgetProvider", "updateAppWidget: id=$appWidgetId, compact=${prefs.compactMode}, theme=${prefs.themeMode}")
            
            val layoutRes = if (prefs.compactMode) R.layout.widget_rss_compact else R.layout.widget_rss
            Log.d("RssWidgetProvider", "updateAppWidget: layout=${context.resources.getResourceEntryName(layoutRes)}")

            val views = setBgTransparency(
                context,
                RemoteViews(context.packageName, layoutRes),
                R.id.widget_rss,
                prefs.transparency,
                prefs.themeMode,
                prefs.compactMode
            )

            val showHeader = prefs.showHeaderBar
            views.setTextViewText(R.id.widget_title, prefs.title)

            Log.d("RssWidgetProvider", "updateAppWidget: lastUpdated=${prefs.lastUpdated}")
            if (showHeader && prefs.lastUpdated > 0) {
                val dateStr = DateUtils.formatDate(Date(prefs.lastUpdated), "absolute")
                Log.d("RssWidgetProvider", "updateAppWidget: dateStr=$dateStr")
                if (dateStr.isNotEmpty()) {
                    Log.d("RssWidgetProvider", "updateAppWidget: dateStr=$dateStr")
                    views.setTextViewText(R.id.widget_last_updated, "Updated $dateStr")
                    views.setViewVisibility(R.id.widget_last_updated, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_last_updated, android.view.View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_last_updated, android.view.View.GONE)
            }

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

            ThemeUtils.applyThemeToWidget(context, views, prefs.themeMode)

            Log.d("RssWidgetProvider", "updateAppWidget: updating widget")
            appWidgetManager.updateAppWidget(appWidgetId, views)

            Thread.sleep(150)

            Log.d("RssWidgetProvider", "updateAppWidget: notifying data change")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)

            Log.d("RssWidgetProvider", "updateAppWidget: enqueuing periodic update")
            enqueuePeriodicUpdate(context, appWidgetId)
        }

        private fun enqueuePeriodicUpdate(context: Context, appWidgetId: Int) {
            val prefs = context.getWidgetPrefs(appWidgetId)
            if (prefs.updateInterval == 0) {
                WorkManager.getInstance(context).cancelUniqueWork("rss_widget_update_$appWidgetId")
                return
            }

            val workRequest = PeriodicWorkRequestBuilder<RssWidgetUpdateWorker>(
                prefs.updateInterval.toLong(), TimeUnit.MINUTES
            ).addTag("rss_widget_update_$appWidgetId").setInputData(
                Data.Builder().putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rss_widget_update_$appWidgetId", ExistingPeriodicWorkPolicy.UPDATE, workRequest
            )
        }
    }

}
