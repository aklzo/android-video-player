package com.aklzo.videoplayer.data.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.playlistId AS playlistId, p.name AS name, p.createdAt AS createdAt,
               COUNT(i.fileId) AS itemCount
        FROM playlists p
        LEFT JOIN playlist_items i ON p.playlistId = i.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT name FROM playlists WHERE playlistId = :playlistId")
    fun observeName(playlistId: Long): Flow<String?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND fileId = :fileId")
    suspend fun removeItem(playlistId: Long, fileId: String)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistRow(playlistId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsOf(playlistId: Long)

    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        deleteItemsOf(playlistId)
        deletePlaylistRow(playlistId)
    }
}
