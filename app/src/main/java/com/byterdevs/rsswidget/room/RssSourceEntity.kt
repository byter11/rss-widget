package com.byterdevs.rsswidget.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rss_sources")
data class RssSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appWidgetId: Int,
    val url: String,
    val isEnabled: Boolean = true
)
