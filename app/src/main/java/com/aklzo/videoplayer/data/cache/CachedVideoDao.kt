package com.aklzo.videoplayer.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedVideoDao {
    @Query("SELECT * FROM cached_videos")
    fun observeAll(): Flow<List<CachedVideoEntity>>

    @Query("SELECT * FROM cached_videos")
    suspend fun getAll(): List<CachedVideoEntity>

    @Query("SELECT * FROM cached_videos WHERE fileId = :fileId")
    suspend fun get(fileId: String): CachedVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedVideoEntity)

    @Query("UPDATE cached_videos SET lastPlayedAt = :playedAt WHERE fileId = :fileId")
    suspend fun updateLastPlayed(fileId: String, playedAt: Long)

    @Query("DELETE FROM cached_videos WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cached_videos")
    suspend fun totalSize(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cached_videos")
    fun observeTotalSize(): Flow<Long>
}
