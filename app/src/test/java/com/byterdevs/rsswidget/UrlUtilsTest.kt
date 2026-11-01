package com.byterdevs.rsswidget

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun sanitizeUrl_prependsHttpsWhenProtocolMissing() {
        assertEquals("https://example.com/feed.xml", UrlUtils.sanitizeUrl("example.com/feed.xml"))
        assertEquals("https://news.ycombinator.com/rss", UrlUtils.sanitizeUrl("news.ycombinator.com/rss"))
    }

    @Test
    fun sanitizeUrl_preservesExistingHttpAndHttps() {
        assertEquals("http://insecure.org/rss", UrlUtils.sanitizeUrl("http://insecure.org/rss"))
        assertEquals("https://secure.org/rss", UrlUtils.sanitizeUrl("https://secure.org/rss"))
        assertEquals("HTTPS://secure.org/rss", UrlUtils.sanitizeUrl("HTTPS://secure.org/rss"))
    }

    @Test
    fun sanitizeUrl_trimsWhitespace() {
        assertEquals("https://example.com/feed", UrlUtils.sanitizeUrl("   example.com/feed   "))
        assertEquals("https://example.com/feed", UrlUtils.sanitizeUrl("   https://example.com/feed   "))
    }

    @Test
    fun sanitizeUrl_handlesEmptyString() {
        assertEquals("", UrlUtils.sanitizeUrl(""))
        assertEquals("", UrlUtils.sanitizeUrl("   "))
    }
}
