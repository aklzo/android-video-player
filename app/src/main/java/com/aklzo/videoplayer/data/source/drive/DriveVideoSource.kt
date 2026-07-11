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

    override suspend fun listVideos(folderId: String): List<VideoItem> =
        VideoCatalog.buildVideoItems(driveApi.listFiles(folderId))

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

    override suspend fun downloadVideo(
        item: VideoItem,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        driveApi.downloadFile(item.id, destination, onProgress)
    }
}
