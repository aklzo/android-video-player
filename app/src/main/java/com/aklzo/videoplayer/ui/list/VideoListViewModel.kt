package com.aklzo.videoplayer.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.domain.VideoGroup
import com.aklzo.videoplayer.domain.VideoListGrouper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VideoListUiState(
    val folderName: String = "",
    val groups: List<VideoGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val needsSignIn: Boolean = false,
)

class VideoListViewModel(
    private val videoSource: VideoSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState = _uiState.asStateFlow()

    // サムネイル取得の重複防止(fileId → 取得ジョブ)
    private val thumbnailJobs = mutableMapOf<String, Deferred<File?>>()
    private val thumbnailMutex = Mutex()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, needsSignIn = false)
        viewModelScope.launch {
            try {
                val current = settings.settings.first()
                val folderId = current.folderId
                    ?: throw IllegalStateException("フォルダが選択されていません")
                val videos = videoSource.listVideos(folderId)
                _uiState.value = _uiState.value.copy(
                    folderName = current.folderName ?: "",
                    groups = VideoListGrouper.group(videos),
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsSignInException) {
                _uiState.value = _uiState.value.copy(isLoading = false, needsSignIn = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "一覧の取得に失敗しました。接続を確認してください",
                )
            }
        }
    }

    /** サムネイルのローカルファイルを取得(なければダウンロード)。UI から都度呼ばれる */
    suspend fun loadThumbnail(item: VideoItem): File? {
        val job = thumbnailMutex.withLock {
            thumbnailJobs.getOrPut(item.id) {
                viewModelScope.async { videoSource.getThumbnail(item) }
            }
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    class Factory(
        private val videoSource: VideoSource,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoListViewModel(videoSource, settings) as T
    }
}
