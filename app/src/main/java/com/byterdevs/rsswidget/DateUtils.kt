package com.byterdevs.rsswidget

import android.text.format.DateFormat
import org.ocpsoft.prettytime.PrettyTime
import java.util.Calendar
import java.util.Date

object DateUtils {
    fun formatDate(date: Date?, dateFormat: String): String {
        if (date == null || dateFormat == "off") return ""
        return if (dateFormat == "absolute") {
            formatAsTodayOrFullDate(date)
        } else {
            PrettyTime().format(date)
        }
    }

    private fun formatAsTodayOrFullDate(date: Date): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }
        
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            "Today, " + DateFormat.format("h:mm a", date).toString()
        } else {
            DateFormat.format("MMM d, yyyy h:mm a", date).toString()
        }
    }
}
