package com.aklzo.videoplayer.data.playlist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * プレイリスト内の動画。オフラインでも表示・再生開始できるよう、
 * 動画のメタデータを非正規化して保持する(→ docs/md/design/decisions.md)。
 */
@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "fileId"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long?,
    val folderPath: String = "",
    val addedAt: Long,
)

/** プレイリスト一覧表示用(件数付き) */
data class PlaylistWithCount(
    val playlistId: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)
