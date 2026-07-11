package com.aklzo.videoplayer.data.source

import java.io.File

/**
 * 「動画ソース」の抽象化。
 * 現在は Google Drive 実装のみだが、将来ローカルフォルダ等を追加できるよう
 * 一覧取得・サムネイル取得・動画取得をインターフェースとして分離している。
 */
interface VideoSource {
    /** フォルダ内の動画(.mp4)一覧を取得する */
    suspend fun listVideos(folderId: String): List<VideoItem>

    /**
     * サムネイルを取得してローカルにキャッシュし、その File を返す。
     * サムネイルが存在しない・取得に失敗した場合は null。
     */
    suspend fun getThumbnail(item: VideoItem): File?

    /**
     * 動画本体を destination にダウンロードする。
     * onProgress は (受信済みバイト数, 総バイト数 or null)。
     * コルーチンのキャンセルで中断できる。
     */
    suspend fun downloadVideo(
        item: VideoItem,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    )
}
