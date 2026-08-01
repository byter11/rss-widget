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
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import kotlinx.parcelize.Parcelize
import android.graphics.BitmapFactory
import com.byterdevs.rsswidget.room.RssDatabase
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

    companion object {
        private val refreshLock = Any()
        @Volatile private var isRefreshing = false
    }
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
                    image = it.image
                )
            }
            withContext(Dispatchers.Main) {
                items.addAll(loadedItems)
            }
        } catch (e: Exception) {
            Log.e("RssRemoteViewsFactory", "Failed to load items from DB", e)
            withContext(Dispatchers.Main) {
                error = true
                items.add(RssItem("Failed to load RSS feed", "Verify the URL and add the widget again.", ""))
            }
        } finally {
            isRefreshing = false
        }
    }

    override fun onDataSetChanged() {
        synchronized(refreshLock) {
            if (isRefreshing) {
                Log.d("RssRemoteViewsFactory", "Refresh already in progress, ignoring this request.")
                return
            }
            isRefreshing = true
        }
        prefs = context.getWidgetPrefs(appWidgetId)
        error = false
        items.clear()

        loadItems()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getViewTypeCount(): Int = 2

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val layoutRes = if (prefs.compactMode) R.layout.widget_rss_item_compact else R.layout.widget_rss_item
        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.item_title, item.title)
        if((prefs.showDescription || error) && item.description.isNotEmpty()) {
            views.setViewVisibility(R.id.item_description, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_description, item.description)
        } else {
            views.setViewVisibility(R.id.item_description, android.view.View.GONE)
        }
        // Show image if available
        if (item.image != null && item.image.isNotEmpty()) {
            views.setViewVisibility(R.id.item_image, android.view.View.VISIBLE)
            try {
                val bitmap = if (item.image.startsWith("content://")) {
                    context.contentResolver.openInputStream(item.image.toUri())?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } else {
                    BitmapFactory.decodeFile(item.image)
                }

                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.item_image, bitmap)
                } else {
                    views.setViewVisibility(R.id.item_image, android.view.View.GONE)
                }
            } catch (e: Exception) {
                Log.e("RssRemoteViewsFactory", "Failed to load image bitmap", e)
                views.setViewVisibility(R.id.item_image, android.view.View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.item_image, android.view.View.GONE)
        }
        val formattedDate = DateUtils.formatDate(item.date, prefs.dateFormat)
        if (formattedDate.isNotEmpty()) {
            views.setViewVisibility(R.id.item_date, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_date, formattedDate)
        } else {
            views.setViewVisibility(R.id.item_date, android.view.View.GONE)
        }
        if (prefs.showSource && item.source.isNotEmpty()) {
            views.setViewVisibility(R.id.item_source, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_source, item.source)
        } else {
            views.setViewVisibility(R.id.item_source, android.view.View.GONE)
        }

        if (prefs.showColorBar) {
            views.setViewVisibility(R.id.item_color_bar, android.view.View.VISIBLE)
            val colors = intArrayOf(
                0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
                0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
                0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
                0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt()
            )
            val colorIndex = Math.abs(item.link.hashCode()) % colors.size
            views.setInt(R.id.item_color_bar, "setBackgroundColor", colors[colorIndex])
        } else {
            views.setViewVisibility(R.id.item_color_bar, android.view.View.GONE)
        }

        if(prefs.dimReadItems) {
            markItemRead(views, item)
        }

        val fillInIntent = Intent()
        fillInIntent.data = item.link.toUri()
        fillInIntent.putExtra("EXTRA_LINK", item.link)
        fillInIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_description, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_date, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_rss_item, fillInIntent)

        val themedContext = getThemedContextForWidget(context, prefs.themeMode)

        val colorTitle = themedContext.getColorResCompat(android.R.attr.colorForeground)
        val colorDesc = themedContext.getColorResCompat(android.R.attr.textColorPrimary)
        val colorSecondary = themedContext.getColorResCompat(android.R.attr.colorSecondary)
        val colorTextSecondary = themedContext.getColorResCompat(android.R.attr.textColorSecondary)

        views.setTextColor(R.id.item_title, colorTitle)
        views.setTextColor(R.id.item_description, colorDesc)
        views.setTextColor(R.id.item_date, colorSecondary)
        views.setTextColor(R.id.item_source, colorTextSecondary)
        return views
    }

    fun markItemRead(views: RemoteViews, item: RssItem) {
        val configurationContext = getThemedContextForWidget(context, prefs.themeMode)
        val colorSecondary = configurationContext.getColorResCompat(android.R.attr.colorSecondary)
        val colorTextSecondary = configurationContext.getColorResCompat(android.R.attr.textColorSecondary)
        val colorTitle = configurationContext.getColorResCompat(android.R.attr.colorForeground)
        val colorDesc = configurationContext.getColorResCompat(android.R.attr.textColorPrimary)

        // Dim read items
        val isRead = ReadItemsStore.isRead(context, appWidgetId, item.link)
        if (isRead) {
            views.setTextColor(R.id.item_title, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_description, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_date, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_source, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
        } else {
            views.setTextColor(R.id.item_title, colorTitle)
            views.setTextColor(R.id.item_description, colorDesc)
            views.setTextColor(R.id.item_date, colorSecondary)
            views.setTextColor(R.id.item_source, colorTextSecondary)
        }
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
        val image: String? = null
    ): Parcelable
}
