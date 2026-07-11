package com.aklzo.videoplayer.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aklzo.videoplayer.data.playlist.PlaylistDao
import com.aklzo.videoplayer.data.playlist.PlaylistEntity
import com.aklzo.videoplayer.data.playlist.PlaylistItemEntity

@Database(
    entities = [CachedVideoEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cachedVideoDao(): CachedVideoDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_videos ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        playlistId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_items (
                        playlistId INTEGER NOT NULL,
                        fileId TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        sizeBytes INTEGER,
                        folderPath TEXT NOT NULL DEFAULT '',
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, fileId)
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
