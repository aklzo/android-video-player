package com.aklzo.videoplayer

import android.app.Application
import kotlinx.coroutines.launch

class VideoPlayerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 起動時のキャッシュ掃除(整合性チェック→保持期間切れ+容量超過分の削除)
        container.appScope.launch {
            container.videoCacheRepository.reconcile()
            container.videoCacheRepository.evictWithSettings()
        }
    }
}
