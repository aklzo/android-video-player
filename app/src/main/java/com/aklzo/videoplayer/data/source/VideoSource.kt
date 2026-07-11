package com.aklzo.videoplayer.data.source

import java.io.File

/**
 * 「動画ソース」の抽象化。
 * 現在は Google Drive 実装のみだが、将来ローカルフォルダ等を追加できるよう
 * 一覧取得・サムネイル取得・動画取得をインターフェースとして分離している。
 */
interface VideoSource {
    /** フォルダ配下(サブフォルダを再帰的に含む)の動画(.mp4)一覧を取得する */
    suspend fun listVideos(folderId: String): List<VideoItem>

    /**
     * サムネイルを取得してローカルにキャッシュし、その File を返す。
     * サムネイルが存在しない・取得に失敗した場合は null。
     */
    suspend fun getThumbnail(item: VideoItem): File?

    /** 再生・キャッシュ保存に使う URI を返す(Drive は files.get?alt=media の URL) */
    fun playbackUri(item: VideoItem): String
}
