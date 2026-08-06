package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.content.pm.PackageManager
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import kotlinx.parcelize.Parcelize
import com.byterdevs.rsswidget.widget.WidgetThumbnailProvider
import com.byterdevs.rsswidget.room.RssDatabase
import java.io.File
import java.util.Date
import kotlinx.coroutines.*

@ColorInt
fun Context.getColorResCompat(@AttrRes id: Int): Int {
    val resolvedAttr = TypedValue()
    this.theme.resolveAttribute(id, resolvedAttr, true)
    val colorRes = resolvedAttr.run { if (resourceId != 0) resourceId else data }
    return ContextCompat.getColor(this, colorRes)
}

class RssRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var items = mutableListOf<RssItem>()
    private lateinit var prefs: WidgetPrefs
    private var error: Boolean = false
    private var isRefreshing = false
    private val refreshLock = Any()

    override fun onCreate() {
        prefs = context.getWidgetPrefs(appWidgetId)
    }

    fun loadItems() = runBlocking {
        val db = com.byterdevs.rsswidget.room.RssDatabase.getInstance(context)
        val dao = db.rssItemDao()
        try {
            val entities = dao.getItemsForWidget(appWidgetId)
            val loadedItems = entities.map {
                RssItem(
                    title = it.title,
                    description = it.description,
                    link = it.link,
                    date = it.date?.let { d -> Date(d) },
                    source = it.source,
                    image = it.image,
                    feedUrl = it.feedUrl
                )
            }
            withContext(Dispatchers.Main) {
                Log.d("RssRemoteViewsFactory", "Loaded ${loadedItems.size} items")
                items.clear()
                items.addAll(loadedItems)
            }
        } catch (e: Exception) {
            Log.e("RssRemoteViewsFactory", "Failed to load items from DB", e)
            withContext(Dispatchers.Main) {
                error = true
                items.clear()
                items.add(RssItem("Failed to load RSS feed", "Verify the URL and add the widget again.", ""))
            }
        } finally {
            isRefreshing = false
        }
    }

    override fun onDataSetChanged() {
        Log.d("RssRemoteViewsFactory", "onDataSetChanged start: id=$appWidgetId")
        synchronized(refreshLock) {
            if (isRefreshing) {
                Log.d("RssRemoteViewsFactory", "Refresh already in progress, ignoring this request.")
                return
            }
            isRefreshing = true
        }
        prefs = context.getWidgetPrefs(appWidgetId)
        Log.d("RssRemoteViewsFactory", "onDataSetChanged: prefs reloaded, compact=${prefs.compactMode}")
        error = false

        loadItems()
    }

    override fun getCount(): Int {
        Log.d("RssRemoteViewsFactory", "getCount: ${items.size}")
        return items.size
    }

    override fun getViewTypeCount(): Int = 2

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return getLoadingView()
        Log.d("RssRemoteViewsFactory", "getViewAt: pos=$position, compact=${prefs.compactMode}")
        val item = items[position]
        val layoutRes = if (prefs.compactMode) R.layout.widget_rss_item_compact else R.layout.widget_rss_item
        val views = RemoteViews(context.packageName, layoutRes)
        
        val themedContext = getThemedContextForWidget(context, prefs.themeMode)
        val colorTitle = themedContext.getColorResCompat(android.R.attr.colorForeground)
        val colorDesc = themedContext.getColorResCompat(android.R.attr.textColorPrimary)
        val colorSecondary = themedContext.getColorResCompat(android.R.attr.colorSecondary)
        val colorTextSecondary = themedContext.getColorResCompat(android.R.attr.textColorSecondary)

        views.setTextViewText(R.id.item_title, item.title)
        views.setTextColor(R.id.item_title, colorTitle)

        if((prefs.showDescription || error) && item.description.isNotEmpty()) {
            views.setViewVisibility(R.id.item_description, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_description, item.description)
            views.setTextColor(R.id.item_description, colorDesc)
        } else {
            views.setViewVisibility(R.id.item_description, android.view.View.GONE)
        }

        // Show image if available
        if (!item.image.isNullOrEmpty()) {
            views.setViewVisibility(R.id.item_image, android.view.View.VISIBLE)
            try {
                if (item.image.startsWith("content://")) {
                    views.setImageViewUri(R.id.item_image, item.image.toUri())
                } else {
                    val file = File(item.image)
                    if (file.exists()) {
                        val uri = WidgetThumbnailProvider.uriFor(context, file.name)
                        views.setImageViewUri(R.id.item_image, uri)
                    } else {
                        views.setViewVisibility(R.id.item_image, android.view.View.GONE)
                    }
                }
            } catch (e: Exception) {
                Log.e("RssRemoteViewsFactory", "Failed to set image URI for ${item.image}", e)
                views.setViewVisibility(R.id.item_image, android.view.View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.item_image, android.view.View.GONE)
        }

        val formattedDate = DateUtils.formatDate(item.date, prefs.dateFormat)
        if (formattedDate.isNotEmpty()) {
            views.setViewVisibility(R.id.item_date, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_date, formattedDate)
            views.setTextColor(R.id.item_date, colorSecondary)
        } else {
            views.setViewVisibility(R.id.item_date, android.view.View.GONE)
        }
        
        if (prefs.showSource && item.source.isNotEmpty()) {
            views.setViewVisibility(R.id.item_source, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_source, item.source)
            views.setTextColor(R.id.item_source, colorTextSecondary)
        } else {
            views.setViewVisibility(R.id.item_source, android.view.View.GONE)
        }

        val color = prefs.feedColors[item.feedUrl]
        if (color != null) {
            views.setViewVisibility(R.id.item_color_bar, android.view.View.VISIBLE)
            views.setInt(R.id.item_color_bar, "setBackgroundColor", color)
        } else {
            views.setViewVisibility(R.id.item_color_bar, android.view.View.GONE)
        }

        if(prefs.dimReadItems) {
            val isRead = ReadItemsStore.isRead(context, appWidgetId, item.link)
            if (isRead) {
                val dimColor = context.getColor(com.google.android.material.R.color.material_dynamic_neutral50)
                views.setTextColor(R.id.item_title, dimColor)
                views.setTextColor(R.id.item_description, dimColor)
                views.setTextColor(R.id.item_date, dimColor)
                views.setTextColor(R.id.item_source, dimColor)
            }
        }

        val fillInIntent = Intent()
        fillInIntent.data = item.link.toUri()
        fillInIntent.putExtra("EXTRA_LINK", item.link)
        fillInIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_description, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_date, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_rss_item, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews {
        return setBgTransparency(context, RemoteViews(context.packageName, R.layout.widget_rss_loading), R.id.widget_rss_loading, prefs.transparency, prefs.themeMode)
    }

    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {
        items.clear()
    }

    @Parcelize
    data class RssItem(
        val title: String,
        val description: String,
        val link: String,
        val date: Date? = null,
        val source: String = "",
        val image: String? = null,
        val feedUrl: String = ""
    ): Parcelable
}
