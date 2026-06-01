package com.byterdevs.rsswidget.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RssSourceDao {
    @Query("SELECT * FROM rss_sources WHERE appWidgetId = :appWidgetId")
    suspend fun getSourcesForWidget(appWidgetId: Int): List<RssSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: RssSourceEntity)

    @Update
    suspend fun update(source: RssSourceEntity)

    @Delete
    suspend fun delete(source: RssSourceEntity)

    @Query("DELETE FROM rss_sources WHERE appWidgetId = :appWidgetId")
    suspend fun deleteSourcesForWidget(appWidgetId: Int)
}
