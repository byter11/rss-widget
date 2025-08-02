package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Parcelable
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.parcelize.Parcelize
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri
import org.ocpsoft.prettytime.PrettyTime

@ColorInt
fun Context.getColorResCompat(@AttrRes id: Int): Int {
    val resolvedAttr = TypedValue()
    this.theme.resolveAttribute(id, resolvedAttr, true)
    val colorRes = resolvedAttr.run { if (resourceId != 0) resourceId else data }
    return ContextCompat.getColor(this, colorRes)
}

class RssRemoteViewsFactory(private val context: Context, private val rssUrl: String?, private val maxItems: Int = 20, private val showDescription: Boolean = false) : RemoteViewsService.RemoteViewsFactory {
    private var items = mutableListOf<RssItem>()
    private var customTitle: String? = null
    private var showTitle: Boolean = false
    private var appWidgetId: Int = -1

    companion object {
        private val refreshLock = Any()
        @Volatile private var isRefreshing = false
    }
    override fun onCreate() {
    }

    fun loadRSS(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        var connection: HttpURLConnection? = null
        try {
            val feedUrl = URL(url)
            val input = SyndFeedInput()
            connection = feedUrl.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:48.0) Gecko/48.0 Firefox/48.0")
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 15000 // 15 seconds
            connection.connect()

            val feed = input.build(XmlReader(connection.inputStream))
            for (entry in feed.entries.take(maxItems)) {
                val title = entry.title ?: "No Title"
                val link = entry.link ?: ""
                val rawDescription = entry.description?.value ?: ""
                // Parse HTML to plain text and truncate to 100 chars with ellipses
                val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                val truncatedDescription = if (plainDescription.length > 100) plainDescription.take(500) + "..." else plainDescription
                val pubDate = entry.publishedDate?.let {
                    PrettyTime().format(it)
                } ?: ""
                items.add(RssItem(title, truncatedDescription, link, pubDate))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return items
    }

    override fun onDataSetChanged() {
        synchronized(refreshLock) {
            if (isRefreshing) {
                Log.d("RssRemoteViewsFactory", "Refresh already in progress, ignoring this request.")
                return
            }
            isRefreshing = true
        }
        try {
            val url = rssUrl ?: "https://hnrss.org/frontpage?link=comments&description=0"
            val loadedItems = loadRSS(url)
            items.clear()
            items.addAll(loadedItems)
            Log.d("RssRemoteViewsFactory", "Data loaded successfully. Item count: ${items.size}")
        } catch (e: Exception) {
            Log.e("RssRemoteViewsFactory", "Failed to load RSS feed", e)
            items.clear()
        } finally {
            isRefreshing = false
        }
    }
    
    fun setHeader(title: String?) {
        customTitle = title
        showTitle = !title.isNullOrEmpty()
    }
    fun setAppWidgetId(id: Int) { appWidgetId = id }

    override fun getCount(): Int {
        Log.d("RssRemoteViewsFactory", "getCount() called. Item count: ${items.size}")
        return items.size + if (showTitle) 1 else 0
    }

    override fun getViewTypeCount(): Int {
        // We have a header and a list item, so two view types.
        return 2
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (showTitle && position == 0) {
            val headerViews = RemoteViews(context.packageName, R.layout.widget_rss_header)
            headerViews.setTextViewText(R.id.widget_title, customTitle)
            return headerViews
        }
        val itemIndex = if (showTitle) position - 1 else position
        if (itemIndex >= items.size) {
            return getLoadingView();
        }
        val item = items[itemIndex]
        val views = RemoteViews(context.packageName, R.layout.widget_rss_item)
        views.setTextViewText(R.id.item_title, item.title)
        if(showDescription && item.description.isNotEmpty()) {
            views.setViewVisibility(R.id.item_description, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_description, item.description)
        } else {
            views.setViewVisibility(R.id.item_description, android.view.View.GONE)
        }
        views.setTextViewText(R.id.item_date, item.pubDate)
        markItemRead(views, item)

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

    fun markItemRead(views: RemoteViews, item: RssItem) {
        val configurationContext = getThemedContextForWidget(context)
        val colorSecondary = configurationContext.getColorResCompat(android.R.attr.colorSecondary)
        val colorTitle = configurationContext.getColorResCompat(android.R.attr.colorForeground)
        val colorDesc = configurationContext.getColorResCompat(android.R.attr.textColorPrimary)

        // Dim read items
        val isRead = ReadItemsStore.isRead(context, appWidgetId, item.link)
        if (isRead) {
            views.setTextColor(R.id.item_title, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_description, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_date, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
        } else {
            views.setTextColor(R.id.item_title, colorTitle)
            views.setTextColor(R.id.item_description, colorDesc)
            views.setTextColor(R.id.item_date, colorSecondary)
        }
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_rss_loading)
    }
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { items.clear() }

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

    @Parcelize
    data class RssItem(val title: String, val description: String, val link: String, val pubDate: String): Parcelable
}
