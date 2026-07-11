package com.aklzo.videoplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheEvictionPolicyTest {

    private val now = 1_800_000_000_000L // 適当な基準時刻
    private val day = 24L * 60 * 60 * 1000

    private fun entry(id: String, size: Long, lastPlayedDaysAgo: Long) =
        CacheEntry(fileId = id, sizeBytes = size, lastPlayedAt = now - lastPlayedDaysAgo * day)

    @Test
    fun `上限以内かつ期限内なら何も削除しない`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(entry("a", 100, 1), entry("b", 100, 2)),
            limitBytes = 1000,
            retentionDays = 30,
            nowMillis = now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `容量超過時は最終再生が古い順に上限を下回るまで削除する`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(
                entry("newest", 400, 1),
                entry("oldest", 400, 10),
                entry("middle", 400, 5),
            ),
            limitBytes = 800,
            retentionDays = null,
            nowMillis = now,
        )
        assertEquals(listOf("oldest"), result)
    }

    @Test
    fun `大きく超過していれば複数削除する`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(
                entry("a", 400, 3),
                entry("b", 400, 2),
                entry("c", 400, 1),
            ),
            limitBytes = 400,
            retentionDays = null,
            nowMillis = now,
        )
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `保持期間を超えたものは容量に関係なく削除する`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(entry("old", 100, 31), entry("recent", 100, 29)),
            limitBytes = 1000,
            retentionDays = 30,
            nowMillis = now,
        )
        assertEquals(listOf("old"), result)
    }

    @Test
    fun `保持期間が無期限なら期限切れ削除はしない`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(entry("veryOld", 100, 365)),
            limitBytes = 1000,
            retentionDays = null,
            nowMillis = now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `再生中のファイルは期限切れでも削除しない`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(entry("playing", 100, 100)),
            limitBytes = 1000,
            retentionDays = 30,
            nowMillis = now,
            playingFileId = "playing",
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `再生中のファイルは LRU 削除からも除外され、次に古いものが削除される`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(
                entry("playing", 400, 10), // 最古だが再生中
                entry("second", 400, 5),
                entry("newest", 400, 1),
            ),
            limitBytes = 800,
            retentionDays = null,
            nowMillis = now,
            playingFileId = "playing",
        )
        assertEquals(listOf("second"), result)
    }

    @Test
    fun `期限切れ削除で上限を下回れば LRU 削除はしない`() {
        val result = CacheEvictionPolicy.selectForEviction(
            entries = listOf(
                entry("expired", 600, 40),
                entry("recent", 400, 1),
            ),
            limitBytes = 500,
            retentionDays = 30,
            nowMillis = now,
        )
        assertEquals(listOf("expired"), result)
    }
}
