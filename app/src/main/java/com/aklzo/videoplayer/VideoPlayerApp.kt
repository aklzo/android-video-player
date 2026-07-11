package com.aklzo.videoplayer

import android.app.Application
import com.aklzo.videoplayer.playback.PlaybackTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoPlayerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 起動時のキャッシュ掃除(保持期間切れ+容量超過分)
        applicationScope.launch {
            val settings = container.settingsRepository.current()
            container.videoCacheRepository.evict(
                limitBytes = settings.cacheLimitBytes,
                retentionDays = settings.retentionDays,
                playingFileId = PlaybackTracker.currentFileId,
            )
        }
    }
}
