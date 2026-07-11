package com.aklzo.videoplayer

import android.content.Context
import com.aklzo.videoplayer.auth.GoogleAuthManager

/** 手書きの DI コンテナ(→ docs/design/decisions.md) */
class AppContainer(context: Context) {
    val authManager = GoogleAuthManager(context.applicationContext)
}
