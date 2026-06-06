package com.byterdevs.rsswidget.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RssItemEntity::class], version = 2)
abstract class RssDatabase : RoomDatabase() {
    abstract fun rssItemDao(): RssItemDao

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
