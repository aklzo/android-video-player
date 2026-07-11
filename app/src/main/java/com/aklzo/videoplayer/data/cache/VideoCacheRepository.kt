package com.aklzo.videoplayer.data.cache

import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.data.source.VideoSource
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 動画キャッシュ(files/video_cache/)とそのメタデータ(Room)の管理。
 * ダウンロードは <fileId>.mp4.part に書き、完了後に <fileId>.mp4 へ rename する。
 */
class VideoCacheRepository(
    private val dao: CachedVideoDao,
    private val videoSource: VideoSource,
    private val cacheDir: File,
) {

    /** 一覧のキャッシュ済みインジケータ用 */
    val cachedIds: Flow<Set<String>> =
        dao.observeAll().map { list -> list.map { it.fileId }.toSet() }

    /** 設定画面のキャッシュ使用量表示用 */
    val totalSizeBytes: Flow<Long> = dao.observeTotalSize()

    fun fileFor(fileId: String): File = File(cacheDir, "$fileId.mp4")

    /** 有効なキャッシュファイルを返す。なければ null */
    suspend fun cachedFile(fileId: String): File? {
        val entity = dao.get(fileId) ?: return null
        val file = fileFor(fileId)
        if (!file.exists()) {
            // 実体が消えている場合はメタデータを掃除する
            dao.delete(entity.fileId)
            return null
        }
        return file
    }

    /**
     * 動画をダウンロードしてキャッシュに登録し、ファイルを返す。
     * すでにキャッシュ済みなら即座に返す。キャンセル時は部分ファイルを残さない。
     */
    suspend fun download(
        item: VideoItem,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): File {
        cachedFile(item.id)?.let { return it }

        val destination = fileFor(item.id)
        val partFile = File(cacheDir, "${item.id}.mp4.part")
        try {
            videoSource.downloadVideo(item, partFile, onProgress)
            if (!partFile.renameTo(destination)) {
                throw java.io.IOException("キャッシュファイルの作成に失敗しました")
            }
        } finally {
            partFile.delete()
        }

        val now = System.currentTimeMillis()
        dao.upsert(
            CachedVideoEntity(
                fileId = item.id,
                fileName = item.name,
                sizeBytes = destination.length(),
                downloadedAt = now,
                lastPlayedAt = now,
            )
        )
        return destination
    }

    /** 再生開始時に呼び、LRU の基準となる最終再生日時を更新する */
    suspend fun markPlayed(fileId: String) {
        dao.updateLastPlayed(fileId, System.currentTimeMillis())
    }

    /** 手動全削除。再生中のファイルは除外する */
    suspend fun deleteAll(excludeFileId: String? = null) {
        for (entity in dao.getAll()) {
            if (entity.fileId == excludeFileId) continue
            fileFor(entity.fileId).delete()
            dao.delete(entity.fileId)
        }
    }

    suspend fun delete(fileId: String) {
        fileFor(fileId).delete()
        dao.delete(fileId)
    }

    suspend fun entries(): List<CachedVideoEntity> = dao.getAll()
}
