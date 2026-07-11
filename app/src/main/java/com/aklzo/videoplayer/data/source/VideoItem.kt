package com.aklzo.videoplayer.data.source

/** ソース非依存の動画メタデータ */
data class VideoItem(
    val id: String,
    /** ファイル名(拡張子含む) 例: 20260315_タイトル.mp4 */
    val name: String,
    val sizeBytes: Long?,
    /** 同名 .webp サムネイルのファイル ID(存在する場合) */
    val thumbnailFileId: String?,
    /** フォールバック用サムネイル URL(Drive の thumbnailLink 等) */
    val thumbnailUrl: String?,
    /** 選択フォルダからの相対フォルダパス(直下は空文字、不明は null)。概要欄に表示する */
    val folderPath: String? = null,
)
