package com.byterdevs.rsswidget.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "rss_items")
data class RssItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appWidgetId: Int,
    val title: String,
    val description: String,
    val link: String,
    val date: Long?, // Store as timestamp
    val source: String,
    val image: String?,
    val feedUrl: String
)
