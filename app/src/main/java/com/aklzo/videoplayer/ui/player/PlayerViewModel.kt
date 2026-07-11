package com.aklzo.videoplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.cache.NetworkMonitor
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.playback.PlaybackTracker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Preparing : PlayerUiState
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : PlayerUiState {
        val progress: Float? =
            totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    }

    /** ネットワーク制約でダウンロードできない */
    data class Blocked(val offline: Boolean) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val file: File) : PlayerUiState
}

class PlayerViewModel(
    private val item: VideoItem,
    private val cacheRepository: VideoCacheRepository,
    private val networkMonitor: NetworkMonitor,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
    val uiState = _uiState.asStateFlow()

    val videoName: String get() = item.name

    private var downloadJob: Job? = null

    init {
        start()
    }

    fun start() {
        downloadJob?.cancel()
        _uiState.value = PlayerUiState.Preparing
        downloadJob = viewModelScope.launch {
            try {
                val cached = cacheRepository.cachedFile(item.id)
                if (cached != null) {
                    onReady(cached)
                    return@launch
                }
                when (networkMonitor.checkDownloadAllowed(settings.current().allowMetered)) {
                    is NetworkMonitor.DownloadPermission.Offline ->
                        _uiState.value = PlayerUiState.Blocked(offline = true)
                    is NetworkMonitor.DownloadPermission.MeteredBlocked ->
                        _uiState.value = PlayerUiState.Blocked(offline = false)
                    is NetworkMonitor.DownloadPermission.Allowed -> {
                        _uiState.value = PlayerUiState.Downloading(0, item.sizeBytes)
                        val file = cacheRepository.download(item) { read, total ->
                            _uiState.value = PlayerUiState.Downloading(read, total ?: item.sizeBytes)
                        }
                        onReady(file)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsSignInException) {
                _uiState.value = PlayerUiState.Error("認証が切れています。一覧に戻って再サインインしてください")
            } catch (e: Exception) {
                _uiState.value =
                    PlayerUiState.Error(e.message ?: "ダウンロードに失敗しました。接続を確認してください")
            }
        }
    }

    private suspend fun onReady(file: File) {
        cacheRepository.markPlayed(item.id)
        PlaybackTracker.currentFileId = item.id
        _uiState.value = PlayerUiState.Ready(file)
        // ダウンロードで上限を超えていたら古いものから削除する
        val current = settings.current()
        cacheRepository.evict(
            limitBytes = current.cacheLimitBytes,
            retentionDays = current.retentionDays,
            playingFileId = item.id,
        )
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    override fun onCleared() {
        if (PlaybackTracker.currentFileId == item.id) {
            PlaybackTracker.currentFileId = null
        }
    }

    class Factory(
        private val item: VideoItem,
        private val cacheRepository: VideoCacheRepository,
        private val networkMonitor: NetworkMonitor,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(item, cacheRepository, networkMonitor, settings) as T
    }
}
