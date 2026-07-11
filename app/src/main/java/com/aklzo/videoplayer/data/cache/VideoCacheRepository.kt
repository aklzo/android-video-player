package com.aklzo.videoplayer.data.cache

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.CacheEntry
import com.aklzo.videoplayer.domain.CacheEvictionPolicy
import com.aklzo.videoplayer.playback.PlaybackTracker
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * 動画キャッシュの管理。実体は Media3 SimpleCache(files/video_cache/)が持ち、
 * ここでは「完全に保存済みのファイル」のメタデータ(Room)と
 * バックグラウンド保存(CacheWriter)・LRU 削除を担当する。
 * キャッシュキーは Drive の fileId。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoCacheRepository(
    private val dao: CachedVideoDao,
    private val cache: Cache,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
) {

    data class CachingProgress(val bytesCached: Long, val totalBytes: Long?) {
        val fraction: Float? =
            totalBytes?.takeIf { it > 0 }
                ?.let { (bytesCached.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    }

    /** 一覧の保存済みインジケータ用(完全保存済みのみ) */
    val cachedIds: Flow<Set<String>> =
        dao.observeAll().map { list -> list.map { it.fileId }.toSet() }

    /** 進行中のバックグラウンド保存の進捗(fileId → 進捗) */
    private val _cachingProgress = MutableStateFlow<Map<String, CachingProgress>>(emptyMap())
    val cachingProgress = _cachingProgress.asStateFlow()

    /** 設定画面の使用量表示用。部分キャッシュも含む実バイト数を定期的に返す */
    val totalSizeBytes: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(cache.cacheSpace)
            delay(1500)
        }
    }.flowOn(Dispatchers.IO)

    private val jobs = ConcurrentHashMap<String, Job>()

    suspend fun isFullyCached(fileId: String): Boolean = dao.get(fileId) != null

    /**
     * 動画全体をバックグラウンドで保存する(再生と並行して呼ぶ)。
     * 再生が先に受信した範囲はキャッシュ共有され、未取得範囲だけを追加取得する。
     * すでに実行中・保存済みなら何もしない。
     */
    fun ensureFullyCached(item: VideoItem, uri: String) {
        if (jobs.containsKey(item.id)) return
        val job = appScope.launch(Dispatchers.IO) {
            try {
                if (dao.get(item.id) != null) return@launch
                var totalBytes: Long? = item.sizeBytes
                _cachingProgress.update { it + (item.id to CachingProgress(0, totalBytes)) }
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(uri))
                    .setKey(item.id)
                    .build()
                runInterruptible {
                    CacheWriter(
                        cacheDataSourceFactory.createDataSourceForDownloading(),
                        dataSpec,
                        /* temporaryBuffer= */ null,
                    ) { requestLength, bytesCached, _ ->
                        if (requestLength != C.LENGTH_UNSET.toLong()) totalBytes = requestLength
                        _cachingProgress.update {
                            it + (item.id to CachingProgress(bytesCached, totalBytes))
                        }
                    }.cache()
                }
                val now = System.currentTimeMillis()
                dao.upsert(
                    CachedVideoEntity(
                        fileId = item.id,
                        fileName = item.name,
                        sizeBytes = totalBytes ?: 0L,
                        downloadedAt = now,
                        lastPlayedAt = now,
                    )
                )
                evictWithSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "background caching failed: ${item.name}", e)
            } finally {
                jobs.remove(item.id)
                _cachingProgress.update { it - item.id }
            }
        }
        jobs[item.id] = job
    }

    /** 再生開始時に呼び、LRU の基準となる最終再生日時を更新する(保存済みの場合のみ) */
    suspend fun markPlayed(fileId: String) {
        dao.updateLastPlayed(fileId, System.currentTimeMillis())
    }

    /** 手動全削除。再生中・保存中のファイルは除外する */
    suspend fun deleteAll(excludeFileId: String? = null) {
        for (entity in dao.getAll()) {
            if (entity.fileId == excludeFileId) continue
            delete(entity.fileId)
        }
        // Room 管理外の部分キャッシュも削除する
        for (key in cache.keys.toList()) {
            if (key == excludeFileId || jobs.containsKey(key)) continue
            runCatching { cache.removeResource(key) }
        }
    }

    private suspend fun delete(fileId: String) {
        runCatching { cache.removeResource(fileId) }
        dao.delete(fileId)
    }

    /** 保持期間切れ+容量超過分を削除する(→ CacheEvictionPolicy) */
    suspend fun evictWithSettings() {
        val settings = settingsRepository.current()
        val toDelete = CacheEvictionPolicy.selectForEviction(
            entries = dao.getAll().map {
                CacheEntry(fileId = it.fileId, sizeBytes = it.sizeBytes, lastPlayedAt = it.lastPlayedAt)
            },
            limitBytes = settings.cacheLimitBytes,
            retentionDays = settings.retentionDays,
            nowMillis = System.currentTimeMillis(),
            playingFileId = PlaybackTracker.currentFileId,
        )
        toDelete.forEach { delete(it) }
    }

    /**
     * 起動時の整合性チェック: Room に「保存済み」とあるのに
     * キャッシュ実体が存在しない行を削除する(旧方式からの移行や手動削除の後始末)。
     */
    suspend fun reconcile() {
        val keys = cache.keys
        for (entity in dao.getAll()) {
            if (entity.fileId !in keys) dao.delete(entity.fileId)
        }
    }

    companion object {
        private const val TAG = "VideoCacheRepository"
    }
}
