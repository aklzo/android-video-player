package com.aklzo.videoplayer.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.domain.VideoCategories
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
    /** ディレクトリフィルターの選択肢(shorts / streams / videos 等)。2 件以上のとき表示 */
    val categories: List<String> = emptyList(),
    /** 選択中の分類。null は「すべて」 */
    val selectedCategory: String? = null,
)

class VideoListViewModel(
    private val videoSource: VideoSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState = _uiState.asStateFlow()

    /** フィルター適用前の全動画 */
    private var allVideos: List<VideoItem> = emptyList()

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
                val folders = current.folders
                val videos = videoSource.listVideos(folders)
                val title = when {
                    folders.isEmpty() -> "動画一覧"
                    folders.size == 1 -> folders.first().name
                    else -> "${folders.first().name} ほか${folders.size - 1}件"
                }
                allVideos = videos
                val categories = VideoCategories.categories(videos)
                // 再読み込みで選択中の分類が消えていたら「すべて」に戻す
                val selected = _uiState.value.selectedCategory?.takeIf { it in categories }
                _uiState.value = _uiState.value.copy(
                    folderName = title,
                    groups = VideoListGrouper.group(VideoCategories.filter(videos, selected)),
                    categories = categories,
                    selectedCategory = selected,
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

    /** ディレクトリフィルターの切り替え。null は「すべて」 */
    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            groups = VideoListGrouper.group(VideoCategories.filter(allVideos, category)),
        )
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
