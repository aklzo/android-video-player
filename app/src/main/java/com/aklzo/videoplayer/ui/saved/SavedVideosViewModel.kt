package com.aklzo.videoplayer.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoGroup
import com.aklzo.videoplayer.domain.VideoListGrouper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class SavedVideosUiState(
    val groups: List<VideoGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * 保存済み(キャッシュ保存が完了した)動画の一覧。
 * Room のメタデータとローカルサムネイルだけで構築するため、オフラインでも表示できる。
 */
class SavedVideosViewModel(
    cacheRepository: VideoCacheRepository,
    private val thumbsDir: File,
) : ViewModel() {

    val uiState: StateFlow<SavedVideosUiState> = cacheRepository.cachedVideos
        .map { entities ->
            val items = entities.map {
                VideoItem(
                    id = it.fileId,
                    name = it.fileName,
                    sizeBytes = it.sizeBytes.takeIf { size -> size > 0 },
                    thumbnailFileId = null,
                    thumbnailUrl = null,
                    folderPath = it.folderPath,
                )
            }
            SavedVideosUiState(groups = VideoListGrouper.group(items), isLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SavedVideosUiState())

    /** ローカルにキャッシュ済みのサムネイルのみ使う(ネットワークへは行かない) */
    suspend fun localThumbnail(item: VideoItem): File? = withContext(Dispatchers.IO) {
        File(thumbsDir, "${item.id}.webp").takeIf { it.exists() && it.length() > 0 }
    }

    class Factory(
        private val cacheRepository: VideoCacheRepository,
        private val thumbsDir: File,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SavedVideosViewModel(cacheRepository, thumbsDir) as T
    }
}
