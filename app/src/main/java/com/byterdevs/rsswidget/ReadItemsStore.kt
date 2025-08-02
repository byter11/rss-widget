package com.byterdevs.rsswidget

import android.content.Context
import android.util.Log

object ReadItemsStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences("read_items", Context.MODE_PRIVATE)

    fun markRead(context: Context, appWidgetId: Int, link: String) {
        val key = "read_$appWidgetId"
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val set = prefs.getStringSet(key, mutableSetOf()) ?: mutableSetOf()
        set.add(link)
        prefs.edit().putStringSet(key, set).apply()
        // Store timestamp for each link
        val timeKey = "read_time_${appWidgetId}_$link"
        prefs.edit().putLong(timeKey, now).apply()
        Log.d("ReadItemsStore", "Marked item as read: $link")
        prune(context, appWidgetId)
    }

    fun isRead(context: Context, appWidgetId: Int, link: String): Boolean {
        val key = "read_$appWidgetId"
        val set = prefs(context).getStringSet(key, mutableSetOf()) ?: mutableSetOf()
        return set.contains(link)
    }

    fun prune(context: Context, appWidgetId: Int) {
        val key = "read_$appWidgetId"
        val prefs = prefs(context)
        val set = prefs.getStringSet(key, mutableSetOf()) ?: mutableSetOf()
        val now = System.currentTimeMillis()
        val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L
        val toRemove = mutableSetOf<String>()
        for (link in set) {
            val timeKey = "read_time_${appWidgetId}_$link"
            val readTime = prefs.getLong(timeKey, 0L)
            if (readTime == 0L || now - readTime > twoDaysMillis) {
                toRemove.add(link)
                prefs.edit().remove(timeKey).apply()
            }
        }
        if (toRemove.isNotEmpty()) {
            set.removeAll(toRemove)
            prefs.edit().putStringSet(key, set).apply()
        }
    }
}
