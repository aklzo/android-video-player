package com.aklzo.videoplayer.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CachedVideoEntity::class], version = 2, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cachedVideoDao(): CachedVideoDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_videos ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun create(context: Context): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
