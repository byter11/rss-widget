package com.byterdevs.rsswidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import androidx.core.text.HtmlCompat
import kotlinx.parcelize.Parcelize
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class RssRemoteViewsFactory(private val context: Context, private val rssUrl: String?, private val maxItems: Int = 20, private val showDescription: Boolean = false) : RemoteViewsService.RemoteViewsFactory {
    private var items = mutableListOf<RssItem>()
    companion object {
        private val refreshLock = Any()
        @Volatile private var isRefreshing = false
    }
    override fun onCreate() {
    }

    fun loadRSS(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        try {
            val feedUrl = URL(url)
            val input = SyndFeedInput()
            val feed = input.build(XmlReader(feedUrl))
            for (entry in feed.entries.take(maxItems)) {
                val title = entry.title ?: "No Title"
                val link = entry.link ?: ""
                val rawDescription = entry.description?.value ?: ""
                // Parse HTML to plain text and truncate to 100 chars with ellipses
                val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                val truncatedDescription = if (plainDescription.length > 100) plainDescription.take(500) + "..." else plainDescription
                val pubDate = entry.publishedDate?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it)
                } ?: ""
                items.add(RssItem(title, truncatedDescription, link, pubDate))
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
    
    override fun getCount(): Int {
        Log.d("RssRemoteViewsFactory", "getCount() called. Item count: ${items.size}")
        return items.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= getCount()) {
            return getLoadingView();
        }
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_rss_item)
        views.setTextViewText(R.id.item_title, item.title)
        if(showDescription && item.description.isNotEmpty()) {
            views.setViewVisibility(R.id.item_description, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_description, item.description)
        } else {
            views.setViewVisibility(R.id.item_description, android.view.View.GONE)
        }
        views.setTextViewText(R.id.item_date, item.pubDate)
        val fillInIntent = Intent()
        fillInIntent.data = Uri.parse(item.link)
        fillInIntent.putExtra("EXTRA_LINK", item.link)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_description, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_date, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_rss_item, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_rss_loading)
    }
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { items.clear() }

    @Parcelize
    data class RssItem(val title: String, val description: String, val link: String, val pubDate: String): Parcelable
}
