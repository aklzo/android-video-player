package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.drive.DriveFile

/**
 * Drive のファイル一覧(再帰列挙の結果)から動画一覧を組み立てる純粋ロジック。
 * - `.mp4`(拡張子は大文字小文字を区別しない)のみを対象にする
 * - 「同じ親フォルダ内」の同名(拡張子違い)`.webp` をサムネイルとして紐付ける
 * - `.webp` がなければ Drive の thumbnailLink をフォールバックにする
 */
object VideoCatalog {

    fun buildVideoItems(files: List<DriveFile>): List<VideoItem> {
        val thumbnailsByKey = files
            .filter { it.name.endsWith(".webp", ignoreCase = true) }
            .associateBy { matchKey(it) }

        return files
            .filter { it.name.endsWith(".mp4", ignoreCase = true) }
            .map { video ->
                val thumbnail = thumbnailsByKey[matchKey(video)]
                VideoItem(
                    id = video.id,
                    name = video.name,
                    sizeBytes = video.sizeBytes,
                    thumbnailFileId = thumbnail?.id,
                    thumbnailUrl = video.thumbnailLink,
                )
            }
    }

    /** 親フォルダ + 拡張子を除いたファイル名。別ディレクトリの同名を誤マッチさせない */
    private fun matchKey(file: DriveFile): Pair<String, String> =
        (file.parents.firstOrNull() ?: "") to file.name.substringBeforeLast('.')
}
