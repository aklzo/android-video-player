package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.drive.DriveFile

/**
 * Drive のファイル一覧から動画一覧を組み立てる純粋ロジック。
 * - `.mp4`(拡張子は大文字小文字を区別しない)のみを対象にする
 * - 同名(拡張子違い)の `.webp` があればサムネイルとして紐付ける
 * - `.webp` がなければ Drive の thumbnailLink をフォールバックにする
 */
object VideoCatalog {

    fun buildVideoItems(files: List<DriveFile>): List<VideoItem> {
        val thumbnailsByBaseName = files
            .filter { it.name.endsWith(".webp", ignoreCase = true) }
            .associateBy { baseName(it.name) }

        return files
            .filter { it.name.endsWith(".mp4", ignoreCase = true) }
            .map { video ->
                val thumbnail = thumbnailsByBaseName[baseName(video.name)]
                VideoItem(
                    id = video.id,
                    name = video.name,
                    sizeBytes = video.sizeBytes,
                    thumbnailFileId = thumbnail?.id,
                    thumbnailUrl = video.thumbnailLink,
                )
            }
    }

    private fun baseName(fileName: String): String = fileName.substringBeforeLast('.')
}
