package com.aklzo.videoplayer.data.source

import java.io.File

/**
 * 「動画ソース」の抽象化。
 * 現在は Google Drive 実装のみだが、将来ローカルフォルダ等を追加できるよう
 * 一覧取得・サムネイル取得・動画取得をインターフェースとして分離している。
 */
interface VideoSource {
    /**
     * 対象フォルダ群の配下(サブフォルダを再帰的に含む)の動画(.mp4)一覧を取得する。
     * 同一ファイルが複数フォルダから到達できても重複しない。
     */
    suspend fun listVideos(folders: List<VideoFolder>): List<VideoItem>

    /**
     * サムネイルを取得してローカルにキャッシュし、その File を返す。
     * サムネイルが存在しない・取得に失敗した場合は null。
     */
    suspend fun getThumbnail(item: VideoItem): File?

    /** 再生・キャッシュ保存に使う URI を返す(Drive は files.get?alt=media の URL) */
    fun playbackUri(item: VideoItem): String
}
