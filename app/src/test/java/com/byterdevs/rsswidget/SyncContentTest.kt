package com.byterdevs.rsswidget

import org.junit.Assert.*
import org.junit.Test

class SyncContentTest {

    private fun createPrefs(
        activeStart: Int,
        activeEnd: Int,
        filterMode: ReadFilterMode = ReadFilterMode.SHOW
    ): WidgetPrefs {
        return WidgetPrefs(
            urls = setOf("https://example.com/rss"),
            title = "Test",
            maxItems = 20,
            showDescription = true,
            showImages = true,
            descriptionLength = -1,
            transparency = 100f,
            fontScale = 1.0f,
            showSource = true,
            dateFormat = "relative",
            updateInterval = 30,
            dimReadItems = false,
            readerType = ReaderType.INTERNAL,
            showHeaderBar = true,
            themeMode = ThemeMode.SYSTEM,
            compactMode = false,
            lastUpdated = 0L,
            feedColors = emptyMap(),
            readFilterMode = filterMode,
            activeHoursStart = activeStart,
            activeHoursEnd = activeEnd
        )
    }

    @Test
    fun testReadFilterModeCycle() {
        var mode = ReadFilterMode.SHOW
        mode = when (mode) {
            ReadFilterMode.SHOW -> ReadFilterMode.DIM
            ReadFilterMode.DIM -> ReadFilterMode.HIDE
            ReadFilterMode.HIDE -> ReadFilterMode.SHOW
        }
        assertEquals(ReadFilterMode.DIM, mode)

        mode = when (mode) {
            ReadFilterMode.SHOW -> ReadFilterMode.DIM
            ReadFilterMode.DIM -> ReadFilterMode.HIDE
            ReadFilterMode.HIDE -> ReadFilterMode.SHOW
        }
        assertEquals(ReadFilterMode.HIDE, mode)

        mode = when (mode) {
            ReadFilterMode.SHOW -> ReadFilterMode.DIM
            ReadFilterMode.DIM -> ReadFilterMode.HIDE
            ReadFilterMode.HIDE -> ReadFilterMode.SHOW
        }
        assertEquals(ReadFilterMode.SHOW, mode)
    }

    @Test
    fun testActiveHoursAlwaysActive() {
        val prefs = createPrefs(-1, -1)
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 0))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 12))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 23))
    }

    @Test
    fun testActiveHoursStandardRange() {
        // Active from 8:00 to 20:00 (8 AM - 8 PM)
        val prefs = createPrefs(8, 20)
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 8))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 14))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 20))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 7))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 21))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 0))
    }

    @Test
    fun testActiveHoursOvernightRange() {
        // Active from 22:00 to 6:00 (10 PM to 6 AM)
        val prefs = createPrefs(22, 6)
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 22))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 23))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 0))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 5))
        assertTrue(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 6))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 7))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 12))
        assertFalse(RssWidgetUpdateWorker.isWithinActiveHours(prefs, 21))
    }

    @Test
    fun testSanitizeImageUrlDirect() {
        val direct = "https://example.com/images/photo.jpg"
        assertEquals(direct, RssWidgetUpdateWorker.sanitizeImageUrl(direct))
    }

    @Test
    fun testSanitizeImageUrlTrackingRedirect() {
        val tracked = "https://tracker.adservice.com/click?dest=https%3A%2F%2Fcdn.news.com%2Fbanner.png&user=123"
        assertEquals("https://cdn.news.com/banner.png", RssWidgetUpdateWorker.sanitizeImageUrl(tracked))

        val trackedParam = "https://redirect.com/proxy?url=https%3A%2F%2Fexample.org%2Ffeed_image.webp"
        assertEquals("https://example.org/feed_image.webp", RssWidgetUpdateWorker.sanitizeImageUrl(trackedParam))
    }

    @Test
    fun testSanitizeImageUrlInvalid() {
        assertNull(RssWidgetUpdateWorker.sanitizeImageUrl(null))
        assertNull(RssWidgetUpdateWorker.sanitizeImageUrl(""))
        assertNull(RssWidgetUpdateWorker.sanitizeImageUrl("ftp://invalid.com/pic.jpg"))
    }
}
