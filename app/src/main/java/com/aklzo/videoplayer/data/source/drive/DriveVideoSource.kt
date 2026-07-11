package com.aklzo.videoplayer.data.source.drive

import com.aklzo.videoplayer.data.source.VideoFolder
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.domain.DriveFolderTree
import com.aklzo.videoplayer.domain.VideoCatalog
import java.io.File

/**
 * VideoSource の Google Drive 実装。
 * サムネイルは thumbsDir(cacheDir/thumbs)に <videoId>.webp としてキャッシュする。
 */
class DriveVideoSource(
    private val driveApi: DriveApiClient,
    private val thumbsDir: File,
) : VideoSource {

    /**
     * 対象フォルダ群の配下(サブフォルダ再帰)の動画一覧を取得する。
     * フォルダごとに問い合わせるのではなく、全フォルダ+全動画/webp を一括取得して
     * クライアント側で親子関係を辿る(対象フォルダ数・サブフォルダ数に関係なく数リクエスト)。
     * 同一ファイルは fileId で重複除去(先勝ち)し、フォルダパスは「対象フォルダ名/相対パス」。
     */
    override suspend fun listVideos(folders: List<VideoFolder>): List<VideoItem> {
        if (folders.isEmpty()) return emptyList()
        val allFolders = driveApi.listAllFolders()
        val allMedia = driveApi.listAllMediaFiles()

        val merged = LinkedHashMap<String, VideoItem>()
        var resolvedRootId: String? = null
        for (folder in folders) {
            val targetId = if (folder.id == "root") {
                resolvedRootId ?: driveApi.resolveRootFolderId().also { resolvedRootId = it }
            } else {
                folder.id
            }
            val underTarget = DriveFolderTree.filterUnder(allMedia, allFolders, targetId)
            val pathOf = DriveFolderTree.pathResolver(allFolders, targetId)
            val items = VideoCatalog.buildVideoItems(underTarget) { file ->
                when (val relative = pathOf(file.parents.firstOrNull())) {
                    null -> folder.name
                    "" -> folder.name
                    else -> "${folder.name}/$relative"
                }
            }
            for (item in items) {
                merged.putIfAbsent(item.id, item)
            }
        }
        return merged.values.toList()
    }

    override suspend fun getThumbnail(item: VideoItem): File? {
        val cached = File(thumbsDir, "${item.id}.webp")
        if (cached.exists() && cached.length() > 0) return cached
        return try {
            when {
                item.thumbnailFileId != null -> {
                    driveApi.downloadFile(item.thumbnailFileId, cached)
                    cached
                }
                item.thumbnailUrl != null -> {
                    driveApi.downloadUrl(item.thumbnailUrl, cached)
                    cached
                }
                else -> null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun playbackUri(item: VideoItem): String =
        "https://www.googleapis.com/drive/v3/files/${item.id}?alt=media"
}
