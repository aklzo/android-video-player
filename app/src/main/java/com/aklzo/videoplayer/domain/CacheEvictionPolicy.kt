package com.aklzo.videoplayer.domain

/** 削除判定に必要な最小限のキャッシュ情報 */
data class CacheEntry(
    val fileId: String,
    val sizeBytes: Long,
    /** epoch millis */
    val lastPlayedAt: Long,
)

/**
 * キャッシュ削除判定の純粋ロジック。
 * 1. 最終再生から保持期間を超えたものを削除(retentionDays = null は無期限)
 * 2. 合計サイズが上限を超えていれば、最終再生日時の古い順に上限を下回るまで削除
 * 再生中のファイル(playingFileId)は常に削除対象から除外する。
 */
object CacheEvictionPolicy {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun selectForEviction(
        entries: List<CacheEntry>,
        limitBytes: Long,
        retentionDays: Int?,
        nowMillis: Long,
        playingFileId: String? = null,
    ): List<String> {
        val evictable = entries.filter { it.fileId != playingFileId }

        // 1. 保持期間切れ
        val expired = if (retentionDays != null) {
            val threshold = nowMillis - retentionDays * DAY_MILLIS
            evictable.filter { it.lastPlayedAt < threshold }
        } else {
            emptyList()
        }
        val expiredIds = expired.map { it.fileId }.toSet()

        // 2. 容量超過分を LRU で削除(再生中ファイルのサイズも合計には含める)
        val remaining = entries.filter { it.fileId !in expiredIds }
        var totalSize = remaining.sumOf { it.sizeBytes }
        val lruEvicted = mutableListOf<String>()
        if (totalSize > limitBytes) {
            val candidates = remaining
                .filter { it.fileId != playingFileId }
                .sortedBy { it.lastPlayedAt }
            for (entry in candidates) {
                if (totalSize <= limitBytes) break
                lruEvicted += entry.fileId
                totalSize -= entry.sizeBytes
            }
        }

        return expiredIds.toList() + lruEvicted
    }
}
