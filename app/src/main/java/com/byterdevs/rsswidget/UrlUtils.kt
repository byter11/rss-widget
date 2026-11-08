package com.byterdevs.rsswidget

object UrlUtils {
    /**
     * Ensures an RSS feed URL specifies a protocol.
     * If no protocol (http:// or https://) is specified, automatically prepends "https://".
     */
    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
}
