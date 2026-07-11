package com.aklzo.videoplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.settings.AppSettings
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.playback.PlaybackTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: VideoCacheRepository,
) : ViewModel() {

    val settings: Flow<AppSettings> = settingsRepository.settings
    val cacheSizeBytes: Flow<Long> = cacheRepository.totalSizeBytes

    fun removeFolder(folderId: String) {
        viewModelScope.launch { settingsRepository.removeFolder(folderId) }
    }

    fun setCacheLimitGb(gb: Int) {
        viewModelScope.launch {
            settingsRepository.setCacheLimitGb(gb)
            evictWithCurrentSettings()
        }
    }

    fun setRetentionDays(days: Int?) {
        viewModelScope.launch {
            settingsRepository.setRetentionDays(days)
            evictWithCurrentSettings()
        }
    }

    fun setAllowMetered(allow: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowMetered(allow) }
    }

    fun deleteAllCache() {
        viewModelScope.launch {
            cacheRepository.deleteAll(excludeFileId = PlaybackTracker.currentFileId)
        }
    }

    private suspend fun evictWithCurrentSettings() {
        cacheRepository.evictWithSettings()
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val cacheRepository: VideoCacheRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, cacheRepository) as T
    }
}
