package com.aklzo.videoplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.data.playlist.PlaylistDao
import com.aklzo.videoplayer.data.source.VideoItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailViewModel(
    private val dao: PlaylistDao,
    private val playlistId: Long,
    private val thumbsDir: File,
) : ViewModel() {

    val name: StateFlow<String?> =
        dao.observeName(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 追加順の動画一覧 */
    val videos: StateFlow<List<VideoItem>> =
        dao.observeItems(playlistId)
            .map { items ->
                items.map {
                    VideoItem(
                        id = it.fileId,
                        name = it.fileName,
                        sizeBytes = it.sizeBytes,
                        thumbnailFileId = null,
                        thumbnailUrl = null,
                        folderPath = it.folderPath,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeItem(fileId: String) {
        viewModelScope.launch { dao.removeItem(playlistId, fileId) }
    }

    /** ローカルにキャッシュ済みのサムネイルのみ使う */
    suspend fun localThumbnail(item: VideoItem): File? = withContext(Dispatchers.IO) {
        File(thumbsDir, "${item.id}.webp").takeIf { it.exists() && it.length() > 0 }
    }

    class Factory(
        private val dao: PlaylistDao,
        private val playlistId: Long,
        private val thumbsDir: File,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistDetailViewModel(dao, playlistId, thumbsDir) as T
    }
}
