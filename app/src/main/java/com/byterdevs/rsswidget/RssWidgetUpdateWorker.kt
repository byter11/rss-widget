package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.byterdevs.rsswidget.room.RssDatabase
import com.byterdevs.rsswidget.room.RssItemDao
import com.byterdevs.rsswidget.room.RssItemEntity
import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID


class RssWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val appWidgetId = inputData.getInt("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return Result.failure()
        }

        val hardRefresh = inputData.getBoolean("hardRefresh", false)

        val prefs = applicationContext.getWidgetPrefs(appWidgetId)
        
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val db = RssDatabase.getInstance(applicationContext)
        val itemDao = db.rssItemDao()
        val sourceDao = db.rssSourceDao()

        val sources = runBlocking { sourceDao.getSourcesForWidget(appWidgetId).filter { it.isEnabled } }
        
        if (sources.isEmpty() && prefs.url == null) {
            return Result.failure()
        }

        if (hardRefresh) {
            Log.d("RssWidgetUpdateWorker", "Refresh request received")
            runBlocking { itemDao.clearItemsForWidget(appWidgetId) }
            RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }

        // Migration: if prefs.url is present, add it to sources and clear it?
        // For now, just handle both.
        val allItems = mutableListOf<RssItemEntity>()
        val imgPrefix = UUID.randomUUID().toString()

        sources.forEach { source ->
            try {
                val fetched = fetchRssItems(appWidgetId, source.url, prefs, imgPrefix)
                allItems.addAll(fetched)
            } catch (e: Exception) {
                Log.e("RssWidgetUpdateWorker", "Failed to fetch ${source.url}: $e")
            }
        }

        // Also handle the legacy single URL from prefs if present
        if (prefs.url != null) {
            try {
                val fetched = fetchRssItems(appWidgetId, prefs.url, prefs, imgPrefix)
                allItems.addAll(fetched)
            } catch (e: Exception) {}
        }

        if (allItems.isNotEmpty()) {
            // Sort all by date desc
            allItems.sortByDescending { it.date ?: 0L }
            
            runBlocking {
                itemDao.clearItemsForWidget(appWidgetId)
                clearStaleImages(applicationContext, appWidgetId, imgPrefix)
                itemDao.insertAll(allItems.take(prefs.maxItems))
            }
            Log.i("RssWidgetUpdateWorker", "Loaded ${allItems.size} articles for widget $appWidgetId")
        }

        RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        return Result.success()
    }

    private fun fetchRssItems(appWidgetId: Int, rssUrl: String, prefs: WidgetPrefs, imgPrefix: String): List<RssItemEntity> {
        val feedUrl = URL(rssUrl)
        val input = SyndFeedInput()
        val connection = feedUrl.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:48.0) Gecko/48.0 Firefox/48.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 15000

        return try {
            connection.connect()
            val feed = input.build(XmlReader(connection.inputStream))
            val feedTitle = feed.title?.trim() ?: ""
            
            feed.entries.map { entry ->
                val title = entry.title ?: "No Title"
                val link = entry.link ?: ""
                val rawDescription = entry.description?.value 
                    ?: entry.contents.firstOrNull()?.value 
                    ?: ""
                val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                val description = if (prefs.descriptionLength > 0 && plainDescription.length > prefs.descriptionLength)
                    plainDescription.take(prefs.descriptionLength) + "..."
                else plainDescription
                
                val source = when {
                    rssUrl.contains("reddit.com", ignoreCase = true) -> {
                        val subreddit = rssUrl.substringAfter("/r/").substringBefore("/").trim()
                        if (subreddit.isNotEmpty() && subreddit != rssUrl) "Reddit /r/$subreddit" else "Reddit"
                    }
                    feedTitle.isNotEmpty() -> feedTitle
                    else -> {
                        try {
                            val host = URL(link).host
                            if (host.startsWith("www.")) host.substring(4) else host
                        } catch (e: Exception) { "" }
                    }
                }

                val localImageUri = if (prefs.showImages) getImageUrl(entry)?.let {
                    getLocalImageUri(applicationContext, appWidgetId, imgPrefix, it)
                } else null

                RssItemEntity(
                    appWidgetId = appWidgetId,
                    title = title,
                    description = description,
                    link = link,
                    date = entry.publishedDate?.time,
                    source = source,
                    image = localImageUri
                )
            }
        } catch (e: Exception) {
            Log.e("RssWidgetUpdateWorker", "Error fetching $rssUrl: $e")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun clearStaleImages(context: Context, appWidgetId: Int, prefix: String) {
        for (f in context.cacheDir.listFiles()!!) {
            // Don't delete images for other widgets
            if (!f.name.startsWith("$appWidgetId")) {
                continue
            }

            // Delete stale images without the current prefix
            if (!f.getName().startsWith("${appWidgetId}_${prefix}")) {
                f.delete()
            }
        }
    }

    private fun getLocalImageUri(context: Context, appWidgetId: Int, prefix: String, imageUrl: String?): String? {
        if (imageUrl == null || !imageUrl.startsWith("http")) return null
        val cachedFile = downloadAndCacheImage(context, appWidgetId, prefix, imageUrl)
        return cachedFile?.let {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    it
                )
                uri.toString()
            } catch (e: Exception) {
                null
            }
        }
    }

    // Helper function to download and cache image
    private fun downloadAndCacheImage(context: Context, appWidgetId: Int, prefix: String, url: String): java.io.File? {
        return try {
            val cacheDir = context.cacheDir
            val fileName = "${appWidgetId}_${prefix}_${url.hashCode()}.jpg"
            val file = java.io.File(cacheDir, fileName)
            if (!file.exists()) {
                val connection = URL(url).openConnection()
                connection.connect()
                val input = connection.getInputStream()
                val output = file.outputStream()
                input.copyTo(output)
                input.close()
                output.close()
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun getImageUrl(entry: SyndEntry): String? {
        return bestMediaContent(entry)
            ?: bestThumbnail(entry)
            ?: enclosureImage(entry)
    }

    private fun bestMediaContent(entry: SyndEntry): String? {
        var bestWidth = -1
        var imageUrl: String? = null
        val mediaModule = entry.getModule(MediaEntryModule.URI) as MediaEntryModule?
        if (mediaModule == null) {
            return null
        }

        for (mediaContent in mediaModule.mediaContents) {
            (mediaContent.reference as? UrlReference)?.url.let {
                if (bestWidth < mediaContent.width) {
                    imageUrl = it.toString()
                    bestWidth = mediaContent.width
                }
            }
        }

        return imageUrl
    }

    private fun bestThumbnail(entry: SyndEntry): String? {
        var bestWidth = -1
        var imageUrl: String? = null
        val mediaModule = entry.getModule(MediaEntryModule.URI) as MediaEntryModule?
        if (mediaModule == null) {
            return null
        }

        for (thumbnail in mediaModule.metadata.thumbnail) {
            if (bestWidth < thumbnail.width) {
                imageUrl = thumbnail.url.toString()
                bestWidth = thumbnail.width
            }
        }

        return imageUrl
    }

    private fun enclosureImage(entry: SyndEntry): String? {
        for (enclosure in entry.enclosures) {
            if (enclosure.url?.startsWith("http") == true && enclosure.type.startsWith("image/")) {
                return enclosure.url
            }
        }
        return null
    }

}
