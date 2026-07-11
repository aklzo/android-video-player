package com.aklzo.videoplayer.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** ダウンロード可否のネットワーク判定(→ docs/design/decisions.md「NOT_METERED で行う」) */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    sealed interface DownloadPermission {
        data object Allowed : DownloadPermission
        data object Offline : DownloadPermission

        /** 従量制ネットワーク接続中で、設定によりブロックされている */
        data object MeteredBlocked : DownloadPermission
    }

    fun checkDownloadAllowed(allowMetered: Boolean): DownloadPermission {
        val network = connectivityManager.activeNetwork
            ?: return DownloadPermission.Offline
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return DownloadPermission.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return DownloadPermission.Offline
        }
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return if (unmetered || allowMetered) {
            DownloadPermission.Allowed
        } else {
            DownloadPermission.MeteredBlocked
        }
    }
}
