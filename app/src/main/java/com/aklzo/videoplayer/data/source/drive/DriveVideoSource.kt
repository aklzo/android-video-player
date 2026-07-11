package com.aklzo.videoplayer.data.source.drive

import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
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
     * 選択フォルダ配下を BFS で再帰的に辿り、全ファイルから動画一覧を組み立てる。
     * 深さ・フォルダ数に上限を設け、循環(ショートカット等)は訪問済み集合で防ぐ。
     */
    override suspend fun listVideos(folderId: String): List<VideoItem> {
        val allFiles = mutableListOf<DriveFile>()
        val visited = mutableSetOf(folderId)
        var frontier = listOf(folderId)
        var depth = 0
        while (frontier.isNotEmpty() && depth < MAX_DEPTH && visited.size <= MAX_FOLDERS) {
            allFiles += driveApi.listFiles(frontier)
            frontier = driveApi.listFolders(frontier)
                .map { it.id }
                .filter { visited.add(it) }
            depth++
        }
        return VideoCatalog.buildVideoItems(allFiles)
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

    override suspend fun downloadVideo(
        item: VideoItem,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        driveApi.downloadFile(item.id, destination, onProgress)
    }

    companion object {
        private const val MAX_DEPTH = 10
        private const val MAX_FOLDERS = 1000
    }
}
