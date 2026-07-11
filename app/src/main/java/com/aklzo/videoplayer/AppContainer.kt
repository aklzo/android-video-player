package com.aklzo.videoplayer

import android.content.Context
import com.aklzo.videoplayer.auth.GoogleAuthManager
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.drive.DriveApiClient
import com.aklzo.videoplayer.data.source.drive.OkHttpDriveApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 手書きの DI コンテナ(→ docs/design/decisions.md) */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val authManager = GoogleAuthManager(appContext)

    val settingsRepository = SettingsRepository(appContext)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val driveApiClient: DriveApiClient = OkHttpDriveApiClient(authManager, okHttpClient)
}
