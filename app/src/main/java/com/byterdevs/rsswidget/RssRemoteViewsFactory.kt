package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.getColorResCompat
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import com.byterdevs.rsswidget.ThemeMode
import com.byterdevs.rsswidget.room.RssDatabase
import com.byterdevs.rsswidget.room.RssItemDao
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ocpsoft.prettytime.PrettyTime
import java.util.*

class RssRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<RssItem>()
    private lateinit var prefs: WidgetPrefs

    private var error = false

    override fun onCreate() {
        prefs = context.getWidgetPrefs(appWidgetId)
    }

    override fun onDataSetChanged() {
        prefs = context.getWidgetPrefs(appWidgetId)
        error = false
        items.clear()

        val db = RssDatabase.getInstance(context)
        val dao = db.rssItemDao()

        loadItems(dao)
    }

    private fun loadItems(dao: RssItemDao) = runBlocking {
        try {
            val entities = dao.getItemsForWidget(appWidgetId)

            val loaded = entities.map {
                RssItem(
                    title = it.title,
                    description = it.description,
                    link = it.link,
                    date = it.date?.let { d -> Date(d) },
                    source = it.source,
                    image = it.image
                )
            }

            withContext(Dispatchers.Main) {
                items.addAll(loaded)
            }

        } catch (e: Exception) {
            Log.e("RssFactory", "DB load failed", e)

            error = true
            items.clear()
            items.add(
                RssItem(
                    title = "Failed to load feed",
                    description = "Check RSS source or re-add widget",
                    link = ""
                )
            )
        }
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getViewTypeCount(): Int = 1

    override fun getLoadingView(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_rss_loading)

        val themedContext = getThemedContextForWidget(context, prefs.themeMode)
        val colorForeground = themedContext.getColorResCompat(android.R.attr.colorForeground)

        views.setTextColor(R.id.loading_text, colorForeground)

        return setBgTransparency(
            context,
            views,
            R.id.widget_rss_loading,
            prefs.transparency,
            prefs.themeMode
        )
    }

    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        items.clear()
    }

    override fun getViewAt(position: Int): RemoteViews {

        val themedContext = getThemedContextForWidget(context, prefs.themeMode)
        val foreground = themedContext.getColorResCompat(android.R.attr.colorForeground)
        val secondary = themedContext.getColorResCompat(android.R.attr.textColorSecondary)

        val item = items[position]

        val views = RemoteViews(context.packageName, R.layout.widget_rss_item)

        // RESPONSIVE SIZING
        val isCompact = prefs.compactMode
        val titleSize = if (isCompact) 14f else 17f
        val descSize = if (isCompact) 11f else 12f
        val metaSize = if (isCompact) 9f else 11f
        val horizontalPadding = if (isCompact) 10 else 16
        val verticalPadding = if (isCompact) 4 else 10

        views.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
        views.setTextViewTextSize(R.id.item_description, TypedValue.COMPLEX_UNIT_SP, descSize)
        views.setTextViewTextSize(R.id.item_source, TypedValue.COMPLEX_UNIT_SP, metaSize)
        views.setTextViewTextSize(R.id.item_date, TypedValue.COMPLEX_UNIT_SP, metaSize)

        views.setViewPadding(R.id.widget_rss_item, horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        // TEXT
        views.setTextViewText(R.id.item_title, item.title)
        views.setTextViewText(R.id.item_description, item.description)
        views.setTextViewText(R.id.item_date, formatDate(item.date))
        views.setTextViewText(R.id.item_source, item.source)

        // COLORS (single source of truth)
        if (prefs.dimReadItems && ReadItemsStore.isRead(context, appWidgetId, item.link)) {
            val dimColor = (foreground and 0x00FFFFFF) or 0x66000000 // ~40% alpha
            views.setTextColor(R.id.item_title, dimColor)
            views.setTextColor(R.id.item_description, dimColor)
            views.setTextColor(R.id.item_date, dimColor)
            views.setTextColor(R.id.item_source, dimColor)
            views.setInt(R.id.dot_divider, "setBackgroundColor", dimColor)
        } else {
            views.setTextColor(R.id.item_title, foreground)
            views.setTextColor(R.id.item_description, secondary)
            views.setTextColor(R.id.item_date, secondary)
            views.setTextColor(R.id.item_source, secondary)
            views.setInt(R.id.dot_divider, "setBackgroundColor", secondary)
        }

        // VISIBILITY
        views.setViewVisibility(
            R.id.item_description,
            if (prefs.showDescription && item.description.isNotEmpty())
                android.view.View.VISIBLE
            else
                android.view.View.GONE
        )

        val showSource = prefs.showSource && item.source.isNotEmpty()
        views.setViewVisibility(R.id.item_source, if (showSource) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.dot_divider, if (showSource) android.view.View.VISIBLE else android.view.View.GONE)

        // Adjust metadata container spacing in compact mode
        val metaTopPadding = if (isCompact) 4 else 8
        views.setViewPadding(R.id.meta_container, 0, metaTopPadding, 0, 0)

        // IMAGE (safe handling)
        if (prefs.showImages && !item.image.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(item.image)
                views.setViewVisibility(R.id.item_image, android.view.View.VISIBLE)
                views.setImageViewUri(R.id.item_image, uri)
            } catch (e: Exception) {
                views.setViewVisibility(R.id.item_image, android.view.View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.item_image, android.view.View.GONE)
        }

        // CLICK HANDLER
        val fillInIntent = Intent().apply {
            data = item.link.toUri()
            putExtra("EXTRA_LINK", item.link)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        views.setOnClickFillInIntent(R.id.widget_rss_item, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_description, fillInIntent)

        return views
    }

    private fun formatDate(date: Date?): String {
        if (date == null) return ""

        return if (prefs.dateFormat == "absolute") {
            PrettyTime().format(date)
        } else {
            PrettyTime().format(date)
        }
    }

    @Parcelize
    data class RssItem(
        val title: String,
        val description: String,
        val link: String,
        val date: Date? = null,
        val source: String = "",
        val image: String? = null
    ) : Parcelable
}