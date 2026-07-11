package com.aklzo.videoplayer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.aklzo.videoplayer.auth.GoogleAuthManager
import com.aklzo.videoplayer.data.cache.CacheDatabase
import com.aklzo.videoplayer.data.cache.NetworkMonitor
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.data.source.drive.DriveApiClient
import com.aklzo.videoplayer.data.source.drive.DriveVideoSource
import com.aklzo.videoplayer.data.source.drive.OkHttpDriveApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** 手書きの DI コンテナ(→ docs/design/decisions.md) */
@androidx.annotation.OptIn(UnstableApi::class)
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authManager = GoogleAuthManager(appContext)

    val settingsRepository = SettingsRepository(appContext)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val driveApiClient: DriveApiClient = OkHttpDriveApiClient(authManager, okHttpClient)

    val videoSource: VideoSource = DriveVideoSource(
        driveApi = driveApiClient,
        thumbsDir = File(appContext.cacheDir, "thumbs"),
    )

    private val cacheDatabase = CacheDatabase.create(appContext)

    /** Media3 SimpleCache(files/video_cache/)。プロセス内で唯一のインスタンス */
    private val mediaCache: SimpleCache = run {
        val dir = File(appContext.filesDir, "video_cache")
        // 旧ダウンロード方式(<fileId>.mp4 の平置き)からの移行: 残骸を消してから初期化する
        dir.listFiles()
            ?.filter { it.name.endsWith(".mp4") || it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        // 容量上限は起動時の設定値で固定(変更はアプリ再起動後に反映。
        // 完全保存済みファイルには CacheEvictionPolicy が即時適用される)
        val limitBytes = runBlocking { settingsRepository.current().cacheLimitBytes }
        SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(limitBytes),
            StandaloneDatabaseProvider(appContext),
        )
    }

    /** 再生・保存の open ごとに Authorization ヘッダを注入する DataSource */
    val cacheDataSourceFactory: CacheDataSource.Factory = run {
        val upstream = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        ) { dataSpec ->
            if (dataSpec.uri.host?.endsWith("googleapis.com") == true) {
                val token = runBlocking { authManager.freshAccessToken() }
                dataSpec.withAdditionalHeaders(mapOf("Authorization" to "Bearer $token"))
            } else {
                dataSpec
            }
        }
        CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
    }

    val videoCacheRepository = VideoCacheRepository(
        dao = cacheDatabase.cachedVideoDao(),
        cache = mediaCache,
        cacheDataSourceFactory = cacheDataSourceFactory,
        settingsRepository = settingsRepository,
        appScope = appScope,
    )

    val networkMonitor = NetworkMonitor(appContext)
}
