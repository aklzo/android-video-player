package com.aklzo.videoplayer.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoFolder
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoListCache
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.domain.VideoCategories
import com.aklzo.videoplayer.domain.VideoGroup
import com.aklzo.videoplayer.domain.VideoListGrouper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoListUiState(
    val folderName: String = "",
    val groups: List<VideoGroup> = emptyList(),
    /** 表示できる一覧がまだない状態での読み込み中(全面スピナー) */
    val isLoading: Boolean = true,
    /** 一覧を表示したまま裏で最新を取得中(上部の細いプログレスバー) */
    val isRefreshing: Boolean = false,
    /** 一覧がない状態での取得失敗(全面エラー+再試行) */
    val error: String? = null,
    /** 最新の取得に失敗し、前回の一覧を表示している(バナー表示) */
    val showingStaleList: Boolean = false,
    val needsSignIn: Boolean = false,
    /** ディレクトリフィルターの選択肢(shorts / streams / videos 等)。2 件以上のとき表示 */
    val categories: List<String> = emptyList(),
    /** 選択中の分類。null は「すべて」 */
    val selectedCategory: String? = null,
)

class VideoListViewModel(
    private val videoSource: VideoSource,
    private val settings: SettingsRepository,
    private val listCache: VideoListCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState = _uiState.asStateFlow()

    /** フィルター適用前の全動画。null はまだ何も表示していない状態 */
    private var allVideos: List<VideoItem>? = null

    private var refreshJob: Job? = null

    /** 取得済みサムネイル(スクロールで戻ったときにディスク確認を省く) */
    private val thumbnailFiles = ConcurrentHashMap<String, File>()

    init {
        refresh()
    }

    /**
     * 最新の一覧を Drive から取得する。初回は前回保存した一覧があれば先に即表示し、
     * 取得中も表示を続ける(stale-while-revalidate)。
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val folders = settings.settings.first().folders
            val title = titleOf(folders)
            if (allVideos == null) {
                listCache.load(folders)?.takeIf { it.isNotEmpty() }?.let { showVideos(it) }
            }
            val hasList = allVideos != null
            _uiState.update {
                it.copy(
                    folderName = title,
                    isLoading = !hasList,
                    isRefreshing = hasList,
                    error = null,
                    showingStaleList = false,
                    needsSignIn = false,
                )
            }
            try {
                val videos = videoSource.listVideos(folders)
                showVideos(videos)
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                listCache.save(folders, videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsSignInException) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, needsSignIn = true) }
            } catch (e: Exception) {
                _uiState.update {
                    if (allVideos != null) {
                        // 表示中の一覧は残し、最新でないことだけ知らせる
                        it.copy(isRefreshing = false, showingStaleList = true)
                    } else {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "一覧の取得に失敗しました。接続を確認してください",
                        )
                    }
                }
            }
        }
    }

    private fun titleOf(folders: List<VideoFolder>): String = when {
        folders.isEmpty() -> "動画一覧"
        folders.size == 1 -> folders.first().name
        else -> "${folders.first().name} ほか${folders.size - 1}件"
    }

    /** 一覧を差し替える。分類・グルーピング(数千件の日付パース)はメインスレッド外で行う */
    private suspend fun showVideos(videos: List<VideoItem>) {
        val previousSelection = _uiState.value.selectedCategory
        val (categories, selected, groups) = withContext(Dispatchers.Default) {
            val categories = VideoCategories.categories(videos)
            // 再読み込みで選択中の分類が消えていたら「すべて」に戻す
            val selected = previousSelection?.takeIf { it in categories }
            Triple(categories, selected, VideoListGrouper.group(VideoCategories.filter(videos, selected)))
        }
        allVideos = videos
        _uiState.update {
            it.copy(
                groups = groups,
                categories = categories,
                selectedCategory = selected,
                isLoading = false,
            )
        }
    }

    /** ディレクトリフィルターの切り替え。null は「すべて」 */
    fun selectCategory(category: String?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                groups = VideoListGrouper.group(VideoCategories.filter(allVideos.orEmpty(), category)),
            )
        }
    }

    /**
     * サムネイルのローカルファイルを取得(なければダウンロード)。
     * 呼び出し元(カード)のスコープで実行するため、画面外にスクロールして
     * カードが破棄されると取得もキャンセルされる。
     */
    suspend fun loadThumbnail(item: VideoItem): File? {
        thumbnailFiles[item.id]?.let { return it }
        return videoSource.getThumbnail(item)?.also { thumbnailFiles[item.id] = it }
    }

    class Factory(
        private val videoSource: VideoSource,
        private val settings: SettingsRepository,
        private val listCache: VideoListCache,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoListViewModel(videoSource, settings, listCache) as T
    }
}
