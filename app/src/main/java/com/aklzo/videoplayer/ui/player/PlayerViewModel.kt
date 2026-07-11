package com.aklzo.videoplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.cache.NetworkMonitor
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.playback.PlaybackTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Preparing : PlayerUiState

    /** ネットワーク制約で再生(ストリーミング)できない */
    data class Blocked(val offline: Boolean) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val uri: String) : PlayerUiState
}

class PlayerViewModel(
    private val item: VideoItem,
    private val videoSource: VideoSource,
    private val cacheRepository: VideoCacheRepository,
    private val networkMonitor: NetworkMonitor,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
    val uiState = _uiState.asStateFlow()

    val video: VideoItem get() = item

    /** この動画のバックグラウンド保存進捗(保存中でなければ null) */
    val cachingProgress: StateFlow<VideoCacheRepository.CachingProgress?> =
        cacheRepository.cachingProgress
            .map { it[item.id] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    /** 保存(キャッシュ)が完了しているか。概要欄の保存状態表示に使う */
    val isSaved: StateFlow<Boolean> =
        cacheRepository.cachedIds
            .map { item.id in it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), false)

    init {
        start()
    }

    fun start() {
        _uiState.value = PlayerUiState.Preparing
        viewModelScope.launch {
            try {
                val fullyCached = cacheRepository.isFullyCached(item.id)
                if (!fullyCached) {
                    when (networkMonitor.checkDownloadAllowed(settings.current().allowMetered)) {
                        is NetworkMonitor.DownloadPermission.Offline -> {
                            _uiState.value = PlayerUiState.Blocked(offline = true)
                            return@launch
                        }
                        is NetworkMonitor.DownloadPermission.MeteredBlocked -> {
                            _uiState.value = PlayerUiState.Blocked(offline = false)
                            return@launch
                        }
                        is NetworkMonitor.DownloadPermission.Allowed -> {
                            // 再生と並行して全体をバックグラウンド保存する
                            cacheRepository.ensureFullyCached(item, videoSource.playbackUri(item))
                        }
                    }
                }
                cacheRepository.markPlayed(item)
                PlaybackTracker.currentFileId = item.id
                _uiState.value = PlayerUiState.Ready(videoSource.playbackUri(item))
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsSignInException) {
                _uiState.value = PlayerUiState.Error("認証が切れています。一覧に戻って再サインインしてください")
            } catch (e: Exception) {
                _uiState.value =
                    PlayerUiState.Error(e.message ?: "再生の準備に失敗しました。接続を確認してください")
            }
        }
    }

    override fun onCleared() {
        if (PlaybackTracker.currentFileId == item.id) {
            PlaybackTracker.currentFileId = null
        }
    }

    class Factory(
        private val item: VideoItem,
        private val videoSource: VideoSource,
        private val cacheRepository: VideoCacheRepository,
        private val networkMonitor: NetworkMonitor,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(item, videoSource, cacheRepository, networkMonitor, settings) as T
    }
}
