package com.aklzo.videoplayer.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_videos")
data class CachedVideoEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    /** epoch millis */
    val downloadedAt: Long,
    /** epoch millis。未再生ならダウンロード日時と同じ */
    val lastPlayedAt: Long,
)
