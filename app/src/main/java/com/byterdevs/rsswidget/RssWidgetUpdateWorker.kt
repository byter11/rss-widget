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
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID


class RssWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val appWidgetId = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        Log.d("RssWidgetUpdateWorker", "doWork: appWidgetId=$appWidgetId")
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("RssWidgetUpdateWorker", "Invalid appWidgetId received in worker")
            return Result.failure()
        }
        val hardRefresh = inputData.getBoolean("hardRefresh", false)
        Log.d("RssWidgetUpdateWorker", "doWork: hardRefresh=$hardRefresh")

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val db = RssDatabase.getInstance(applicationContext)
        val dao = db.rssItemDao()

        if (hardRefresh) {
            Log.d("RssWidgetUpdateWorker", "doWork: clearing items for widget $appWidgetId")
            runBlocking { dao.clearItemsForWidget(appWidgetId) }
            RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }

        updateRssFeed(appWidgetId, dao)
        Log.d("RssWidgetUpdateWorker", "doWork: calling updateAppWidget after feed fetch")
        RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        return Result.success()
    }

    private fun fetchRssItems(appWidgetId: Int, rssUrl: String): List<RssItemEntity> {
        val prefs = applicationContext.getWidgetPrefs(appWidgetId)

        return try {
            val feed = fetchFeedWithRetry(rssUrl)
            val feedTitle = feed.title?.trim() ?: ""

            feed.entries.map { entry ->
                val title = entry.title ?: "No Title"
                val link = entry.link ?: ""
                val rawDescription = entry.description?.value
                    ?: entry.contents.firstOrNull()?.value
                    ?: ""
                val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .replace("\uFFFC", "")
                    .replace("\n", " ")
                    .trim()
                val description = if (prefs.descriptionLength > 0 && plainDescription.length > prefs.descriptionLength)
                    plainDescription.take(prefs.descriptionLength) + "..."
                else plainDescription

                val source = try {
                    val host = URL(link).host
                    if (host.startsWith("www.")) host.substring(4) else host
                } catch (e: Exception) { feedTitle }

                val localImageUri = if (prefs.showImages) getImageUrl(entry)?.let {
                    getLocalImageUri(applicationContext, appWidgetId, it)
                } else null

                RssItemEntity(
                    appWidgetId = appWidgetId,
                    title = title,
                    description = description,
                    link = link,
                    date = entry.publishedDate?.time,
                    source = source,
                    image = localImageUri,
                    feedUrl = rssUrl
                )
            }
        } catch (e: Exception) {
            Log.e("RssWidgetUpdateWorker", "Error fetching $rssUrl: $e")
            emptyList()
        }
    }

    private fun fetchFeedWithRetry(
        rssUrl: String,
        maxAttempts: Int = 3
    ): SyndFeed {
        var lastError: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return fetchFeedOnce(rssUrl)
            } catch (e: Exception) {
                lastError = e
                val retryable = e is SocketException ||
                        e is SocketTimeoutException ||
                        (e is IOException && e.message?.contains("EOF") == true)

                if (!retryable || attempt == maxAttempts - 1) {
                    throw e
                }
                Log.w("RssWidgetUpdateWorker", "Retrying $rssUrl after ${e.javaClass.simpleName} (attempt ${attempt + 1})")
                Thread.sleep(500L * (attempt + 1)) // simple backoff: 500ms, 1000ms...
            }
        }
        throw lastError ?: IllegalStateException("Unreachable")
    }

    private fun fetchFeedOnce(rssUrl: String): SyndFeed {
        val feedUrl = URL(rssUrl)
        var connection = feedUrl.openConnection() as HttpURLConnection
        configureConnection(connection)
        connection.connect()

        // Follow redirects manually — some feeds 301/302 before serving RSS
        var redirects = 0
        while (connection.responseCode in intArrayOf(301, 302, 303, 307, 308) && redirects < 5) {
            val newUrl = connection.getHeaderField("Location")
                ?: throw IOException("Redirect with no Location header")
            connection.disconnect()
            connection = URL(newUrl).openConnection() as HttpURLConnection
            configureConnection(connection)
            connection.connect()
            redirects++
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()?.take(200)
            connection.disconnect()
            throw IOException("HTTP $code for $rssUrl: $errorBody")
        }

        return try {
            SyndFeedInput().build(XmlReader(connection.inputStream))
        } finally {
            connection.disconnect()
        }
    }

    private fun configureConnection(connection: HttpURLConnection) {
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        connection.setRequestProperty("Connection", "close") // avoid stale keep-alive socket reuse
        connection.instanceFollowRedirects = false // handled manually above
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
    }
    private fun clearStaleImages(context: Context, appWidgetId: Int) {
        for (f in context.cacheDir.listFiles()!!) {
            // Don't delete images for other widgets
            if (!f.name.startsWith("$appWidgetId")) {
                continue
            }

            // Delete stale images without the current prefix
            if (!f.getName().startsWith("${appWidgetId}_${id.toString()}")) {
                f.delete()
            }
        }
    }

    private fun getLocalImageUri(context: Context, appWidgetId: Int, imageUrl: String?): String? {
        if (imageUrl == null || !imageUrl.startsWith("http")) return null
        val cachedFile = downloadAndCacheImage(context, appWidgetId, imageUrl)
        return cachedFile?.absolutePath
    }

    // Helper function to download and cache image
    private fun downloadAndCacheImage(context: Context, appWidgetId: Int, url: String): java.io.File? {
        return try {
            val cacheDir = context.cacheDir
            val fileName = "${appWidgetId}_${id.toString()}_${url.hashCode()}.jpg"
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
        var bestWidth: Int? = null
        var imageUrl: String? = null
        val mediaModule = entry.getModule(MediaEntryModule.URI) as MediaEntryModule?
        if (mediaModule == null) {
            return null
        }

        for (mediaContent in mediaModule.mediaContents) {
            (mediaContent.reference as? UrlReference)?.url.let {
                if (mediaContent.width != null || bestWidth == null || bestWidth < mediaContent.width) {
                    imageUrl = it.toString()
                    bestWidth = mediaContent.width
                }
            }
        }

        return imageUrl
    }

    private fun bestThumbnail(entry: SyndEntry): String? {
        var bestWidth: Int? = null
        var imageUrl: String? = null
        val mediaModule = entry.getModule(MediaEntryModule.URI) as MediaEntryModule?
        if (mediaModule == null) {
            return null
        }

        for (thumbnail in mediaModule.metadata.thumbnail) {
            if (thumbnail.width == null || bestWidth == null || bestWidth < thumbnail.width) {
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

    fun updateRssFeed(appWidgetId: Int, dao: RssItemDao) = runBlocking {
        val prefs = applicationContext.getWidgetPrefs(appWidgetId)

        val entities = mutableListOf<RssItemEntity>()
        prefs.urls.forEach { url ->
            entities.addAll(fetchRssItems(appWidgetId, url))
        }

        entities.sortByDescending { it.date ?: 0L }

        if (entities.isNotEmpty()) {
            dao.clearItemsForWidget(appWidgetId)
            clearStaleImages(applicationContext, appWidgetId)
            dao.insertAll(entities)
            
            val now = System.currentTimeMillis()
            Log.d("RssWidgetUpdateWorker", "Updating lastUpdated to $now for widget $appWidgetId")
            // Re-read prefs to avoid overwriting other settings (like compactMode) changed during the network fetch
            val latestPrefs = applicationContext.getWidgetPrefs(appWidgetId)
            val updatedPrefs = latestPrefs.copy(lastUpdated = now)
            applicationContext.setWidgetPrefs(appWidgetId, updatedPrefs)

            Log.i("RssWidgetUpdateWorker", "Loaded ${entities.size} articles for widget $appWidgetId")
        } else {
            Log.w("RssWidgetUpdateWorker", "No entities fetched for widget $appWidgetId")
        }
    }
}
