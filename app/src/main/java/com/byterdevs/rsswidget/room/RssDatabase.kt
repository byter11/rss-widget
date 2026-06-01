package com.byterdevs.rsswidget.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RssItemEntity::class, RssSourceEntity::class], version = 3)
abstract class RssDatabase : RoomDatabase() {
    abstract fun rssItemDao(): RssItemDao
    abstract fun rssSourceDao(): RssSourceDao

    companion object {
        @Volatile
        private var INSTANCE: RssDatabase? = null

        fun getInstance(context: Context): RssDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RssDatabase::class.java,
                    "rss_widget_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
