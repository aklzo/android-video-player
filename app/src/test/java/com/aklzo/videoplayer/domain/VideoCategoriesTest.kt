package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCategoriesTest {

    private fun video(name: String, path: String?) =
        VideoItem(
            id = name,
            name = name,
            sizeBytes = null,
            thumbnailFileId = null,
            thumbnailUrl = null,
            folderPath = path,
        )

    @Test
    fun `分類キーは対象フォルダ直下のサブディレクトリ名`() {
        assertEquals(
            "streams",
            VideoCategories.categoryOf(video("a.mp4", "suzuminemo/streams/20260420_x")),
        )
        assertEquals(
            "shorts",
            VideoCategories.categoryOf(video("b.mp4", "suzuminemo/shorts")),
        )
    }

    @Test
    fun `対象フォルダ直下の動画は対象フォルダ名が分類キーになる`() {
        assertEquals("suzuminemo", VideoCategories.categoryOf(video("a.mp4", "suzuminemo")))
    }

    @Test
    fun `パス不明は分類なし`() {
        assertNull(VideoCategories.categoryOf(video("a.mp4", null)))
        assertNull(VideoCategories.categoryOf(video("a.mp4", "")))
    }

    @Test
    fun `分類一覧は重複なし名前順`() {
        val categories = VideoCategories.categories(
            listOf(
                video("a.mp4", "top/videos/x"),
                video("b.mp4", "top/shorts/y"),
                video("c.mp4", "top/videos/z"),
                video("d.mp4", "top/streams/w"),
                video("e.mp4", null),
            )
        )
        assertEquals(listOf("shorts", "streams", "videos"), categories)
    }

    @Test
    fun `フィルターは指定分類のみ残す`() {
        val items = listOf(
            video("a.mp4", "top/videos/x"),
            video("b.mp4", "top/shorts/y"),
        )
        assertEquals(listOf("a.mp4"), VideoCategories.filter(items, "videos").map { it.name })
        assertEquals(2, VideoCategories.filter(items, null).size)
    }
}
