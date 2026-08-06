package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.text.HtmlCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.byterdevs.rsswidget.room.RssDatabase
import com.byterdevs.rsswidget.room.RssItemDao
import com.byterdevs.rsswidget.room.RssItemEntity
import com.byterdevs.rsswidget.widget.WidgetThumbnailProvider
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

    private fun fetchRssItems(appWidgetId: Int, rssUrl: String, prefs: WidgetPrefs): List<RssItemEntity> {
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

                val imageUrl = getImageUrl(entry)

                RssItemEntity(
                    appWidgetId = appWidgetId,
                    title = title,
                    description = description,
                    link = link,
                    date = entry.publishedDate?.time,
                    source = source,
                    image = imageUrl, // Store remote URL temporarily
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

    private fun configureConnection(connection: HttpURLConnection, followRedirects: Boolean = false) {
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "image/*, application/rss+xml, application/xml, text/xml, */*")
        connection.setRequestProperty("Connection", "close")
        connection.instanceFollowRedirects = followRedirects
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
    }

    companion object {
        fun clearAllImagesForWidget(context: Context, appWidgetId: Int) {
            val files = WidgetThumbnailProvider.cacheDir(context).listFiles() ?: return
            Log.d("RssWidgetUpdateWorker", "clearAllImagesForWidget: deleting all cached images for widget $appWidgetId")
            for (f in files) {
                if (f.name.startsWith("img_$appWidgetId") || f.name.startsWith("${appWidgetId}_")) {
                    f.delete()
                }
            }
        }
    }

    private fun clearStaleImages(context: Context, appWidgetId: Int) {
        val files = WidgetThumbnailProvider.cacheDir(context).listFiles() ?: return
        val now = System.currentTimeMillis()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L
        
        Log.d("RssWidgetUpdateWorker", "clearStaleImages: checking cache for widget $appWidgetId")
        for (f in files) {
            // Delete images for this widget that haven't been accessed in a week
            if (f.name.startsWith("img_$appWidgetId") || f.name.startsWith("${appWidgetId}_")) {
                if (now - f.lastModified() > oneWeek) {
                    Log.d("RssWidgetUpdateWorker", "Deleting old image: ${f.name}")
                    f.delete()
                }
            }
        }
    }

    private fun getLocalImageUri(context: Context, appWidgetId: Int, imageUrl: String?): String? {
        if (imageUrl == null || !imageUrl.startsWith("http")) return null
        val cachedFile = downloadAndCacheImage(context, appWidgetId, imageUrl)
        return cachedFile?.absolutePath
    }

    // Helper function to download and cache image with retries and stable naming
    private fun downloadAndCacheImage(context: Context, appWidgetId: Int, url: String): java.io.File? {
        Log.d("RssWidgetUpdateWorker", "downloadAndCacheImage: url=$url")
        var lastError: Exception? = null
        // Stable filename using URL hash, not worker ID, to avoid unnecessary re-downloads
        val fileName = "img_${appWidgetId}_${url.hashCode()}.jpg"
        val file = java.io.File(WidgetThumbnailProvider.cacheDir(context), fileName)

        if (file.exists() && file.length() > 0) {
            Log.d("RssWidgetUpdateWorker", "Image already cached: $fileName")
            return file
        }

        repeat(3) { attempt ->
            try {
                Log.d("RssWidgetUpdateWorker", "Downloading image (attempt ${attempt + 1}) to $fileName")
                val connection = URL(url).openConnection() as HttpURLConnection
                configureConnection(connection, followRedirects = true)
                connection.connect()

                val code = connection.responseCode
                if (code !in 200..299) {
                    val msg = "HTTP $code for $url"
                    connection.disconnect()
                    throw IOException(msg)
                }

                val tempFile = java.io.File(WidgetThumbnailProvider.cacheDir(context), "${fileName}.tmp")
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()

                // Downsample and save to final location
                downsampleAndSave(tempFile, file)
                tempFile.delete()

                Log.d("RssWidgetUpdateWorker", "Image downloaded and downsampled: ${file.length()} bytes")
                return file
            } catch (e: Exception) {
                lastError = e
                Log.w("RssWidgetUpdateWorker", "Failed attempt ${attempt + 1} for $url: ${e.message}")
                if (attempt < 2) Thread.sleep(1000L * (attempt + 1))
            }
        }
        Log.e("RssWidgetUpdateWorker", "Final failure downloading image $url: ${lastError?.message}")
        return null
    }

    private fun downsampleAndSave(sourceFile: java.io.File, destFile: java.io.File) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            // Target max dimension of 800px for widget images
            options.inSampleSize = calculateInSampleSize(options, 800, 800)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            if (bitmap != null) {
                destFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
            } else {
                sourceFile.renameTo(destFile)
            }
        } catch (e: Exception) {
            Log.e("RssWidgetUpdateWorker", "Error downsampling image", e)
            sourceFile.renameTo(destFile)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

        val allEntities = mutableListOf<RssItemEntity>()
        prefs.urls.forEach { url ->
            val items = fetchRssItems(appWidgetId, url, prefs)
            allEntities.addAll(items)
            Log.d("RssWidgetUpdateWorker", "Loaded ${items.size} articles from $url")
        }

        allEntities.sortByDescending { it.date ?: 0L }

        // Apply maxItems limit before downloading images
        val limitedEntities = allEntities.take(prefs.maxItems)

        Log.d("RssWidgetUpdateWorker", "Limiting articles to ${limitedEntities.size}")

        if (limitedEntities.isNotEmpty()) {
            // Now download images only for the items we are keeping
            val finalEntities = limitedEntities.map { entity ->
                val localImageUri = if (prefs.showImages && entity.image != null) {
                    Log.d("RssWidgetUpdateWorker", "Downloading image for: ${entity.title.take(20)}")
                    getLocalImageUri(applicationContext, appWidgetId, entity.image)
                } else null
                
                entity.copy(image = localImageUri)
            }

            dao.clearItemsForWidget(appWidgetId)
            clearStaleImages(applicationContext, appWidgetId)
            dao.insertAll(finalEntities)
            
            val now = System.currentTimeMillis()
            Log.d("RssWidgetUpdateWorker", "Updating lastUpdated to $now for widget $appWidgetId")
            // Re-read prefs to avoid overwriting other settings (like compactMode) changed during the network fetch
            val latestPrefs = applicationContext.getWidgetPrefs(appWidgetId)
            val updatedPrefs = latestPrefs.copy(lastUpdated = now)
            applicationContext.setWidgetPrefs(appWidgetId, updatedPrefs)

            Log.i("RssWidgetUpdateWorker", "Loaded ${finalEntities.size} articles for widget $appWidgetId")
        } else {
            Log.w("RssWidgetUpdateWorker", "No entities fetched for widget $appWidgetId")
        }
    }
}
