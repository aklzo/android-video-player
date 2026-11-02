package com.aklzo.videoplayer.data.source.drive

import com.aklzo.videoplayer.data.source.VideoFolder
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import com.aklzo.videoplayer.domain.DriveFolderTree
import com.aklzo.videoplayer.domain.VideoCatalog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
     * 一括取得の 2 つ(と "root" の実 ID 解決)は互いに独立なので並列に投げる。
     * 同一ファイルは fileId で重複除去(先勝ち)し、フォルダパスは「対象フォルダ名/相対パス」。
     */
    override suspend fun listVideos(folders: List<VideoFolder>): List<VideoItem> {
        if (folders.isEmpty()) return emptyList()
        val (allFolders, allMedia, rootId) = coroutineScope {
            val foldersJob = async { driveApi.listAllFolders() }
            val mediaJob = async { driveApi.listAllMediaFiles() }
            val rootIdJob = if (folders.any { it.id == "root" }) {
                async { driveApi.resolveRootFolderId() }
            } else {
                null
            }
            Triple(foldersJob.await(), mediaJob.await(), rootIdJob?.await())
        }

        // 数千件のツリー判定・サムネイル紐付けは CPU 処理なのでメインスレッド外で行う
        return withContext(Dispatchers.Default) {
            val merged = LinkedHashMap<String, VideoItem>()
            for (folder in folders) {
                val targetId = if (folder.id == "root") checkNotNull(rootId) else folder.id
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
            merged.values.toList()
        }
    }

    override suspend fun getThumbnail(item: VideoItem): File? {
        val cached = File(thumbsDir, "${item.id}.webp")
        if (withContext(Dispatchers.IO) { cached.exists() && cached.length() > 0 }) return cached
        // ダウンロード途中のファイルを他の読み手(同時取得・保存済みタブ)に見せないよう、
        // 一時ファイルに落としてから rename する。同時に取得しても壊れない
        val temp = File(thumbsDir, "${item.id}.${UUID.randomUUID()}.part")
        return try {
            when {
                item.thumbnailFileId != null -> driveApi.downloadFile(item.thumbnailFileId, temp)
                item.thumbnailUrl != null -> driveApi.downloadUrl(item.thumbnailUrl, temp)
                else -> return null
            }
            withContext(Dispatchers.IO) {
                if (temp.renameTo(cached) || cached.exists()) cached else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            temp.delete() // rename 済みなら何もしない。キャンセル・失敗時の後始末
        }
    }

    override fun playbackUri(item: VideoItem): String =
        "https://www.googleapis.com/drive/v3/files/${item.id}?alt=media"
}
