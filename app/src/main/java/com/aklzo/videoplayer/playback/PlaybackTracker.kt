package com.aklzo.videoplayer.playback

/**
 * 現在再生中(再生画面表示中)の fileId。
 * キャッシュ削除(LRU・全削除)から除外するために参照する。
 */
object PlaybackTracker {
    @Volatile
    var currentFileId: String? = null
}
