package com.aklzo.videoplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.data.playlist.PlaylistDao
import com.aklzo.videoplayer.data.playlist.PlaylistEntity
import com.aklzo.videoplayer.data.playlist.PlaylistItemEntity
import com.aklzo.videoplayer.data.playlist.PlaylistWithCount
import com.aklzo.videoplayer.data.source.VideoItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** プレイリスト一覧タブと「プレイリストへ追加」ダイアログで共用する */
class PlaylistsViewModel(private val dao: PlaylistDao) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> =
        dao.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String, then: (playlistId: Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = dao.insertPlaylist(
                PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis())
            )
            then(id)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { dao.deletePlaylist(playlistId) }
    }

    fun addToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            dao.addItem(
                PlaylistItemEntity(
                    playlistId = playlistId,
                    fileId = video.id,
                    fileName = video.name,
                    sizeBytes = video.sizeBytes,
                    folderPath = video.folderPath.orEmpty(),
                    addedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    class Factory(private val dao: PlaylistDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistsViewModel(dao) as T
    }
}
