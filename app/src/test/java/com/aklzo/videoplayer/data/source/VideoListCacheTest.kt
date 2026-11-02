package com.aklzo.videoplayer.data.source

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoListCacheTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val folders = listOf(VideoFolder("f1", "suzuminemo"), VideoFolder("f2", "その他"))

    private val videos = listOf(
        VideoItem(
            id = "v1",
            name = "20260101_a.mp4",
            sizeBytes = 3_000_000_000L,
            thumbnailFileId = "t1",
            thumbnailUrl = null,
            folderPath = "suzuminemo/streams/20260101_a",
        ),
        VideoItem(
            id = "v2",
            name = "日付なし.mp4",
            sizeBytes = null,
            thumbnailFileId = null,
            thumbnailUrl = "https://example.com/thumb",
            folderPath = null,
        ),
    )

    private fun cacheFile() = File(tempDir.root, "video_list.json")

    @Test
    fun `保存した一覧を同じ対象フォルダで読み戻せる`() = runTest {
        val cache = VideoListCache(cacheFile())
        cache.save(folders, videos)

        assertEquals(videos, cache.load(folders))
    }

    @Test
    fun `対象フォルダの並び順が違っても同じ集合なら使う`() = runTest {
        val cache = VideoListCache(cacheFile())
        cache.save(folders, videos)

        assertEquals(videos, cache.load(folders.reversed()))
    }

    @Test
    fun `対象フォルダが変わったら使わない`() = runTest {
        val cache = VideoListCache(cacheFile())
        cache.save(folders, videos)

        assertNull(cache.load(listOf(VideoFolder("f1", "suzuminemo"))))
        assertNull(cache.load(folders + VideoFolder("f3", "追加")))
    }

    @Test
    fun `キャッシュがない・壊れている場合は null`() = runTest {
        val cache = VideoListCache(cacheFile())
        assertNull(cache.load(folders))

        cacheFile().writeText("{ broken")
        assertNull(cache.load(folders))
    }

    @Test
    fun `上書き保存で最新の一覧に置き換わり、一時ファイルは残らない`() = runTest {
        val cache = VideoListCache(cacheFile())
        cache.save(folders, videos)
        cache.save(folders, videos.take(1))

        assertEquals(videos.take(1), cache.load(folders))
        assertEquals(listOf("video_list.json"), tempDir.root.list()!!.toList())
    }
}
