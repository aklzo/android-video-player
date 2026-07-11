package com.aklzo.videoplayer.data.source.drive

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
     * 選択フォルダ配下(サブフォルダ再帰)の動画一覧を取得する。
     * フォルダごとに問い合わせるのではなく、全フォルダ+全動画/webp を一括取得して
     * クライアント側で親子関係を辿る(サブフォルダが 1000 個以上でも数リクエストで済む)。
     */
    override suspend fun listVideos(folderId: String): List<VideoItem> {
        val targetId = if (folderId == "root") driveApi.resolveRootFolderId() else folderId
        val folders = driveApi.listAllFolders()
        val media = driveApi.listAllMediaFiles()
        val underTarget = DriveFolderTree.filterUnder(media, folders, targetId)
        val pathOf = DriveFolderTree.pathResolver(folders, targetId)
        return VideoCatalog.buildVideoItems(underTarget) { pathOf(it.parents.firstOrNull()) }
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
