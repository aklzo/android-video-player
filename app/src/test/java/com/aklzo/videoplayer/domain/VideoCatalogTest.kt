package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.drive.DriveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCatalogTest {

    private fun file(
        name: String,
        id: String = name,
        mimeType: String = "video/mp4",
        thumbnailLink: String? = null,
    ) = DriveFile(id = id, name = name, mimeType = mimeType, sizeBytes = 100L, thumbnailLink = thumbnailLink)

    @Test
    fun `mp4 のみが動画として列挙される`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("20260315_a.mp4"),
                file("20260315_a.webp", mimeType = "image/webp"),
                file("メモ.txt", mimeType = "text/plain"),
            )
        )
        assertEquals(listOf("20260315_a.mp4"), items.map { it.name })
    }

    @Test
    fun `拡張子の大文字小文字は区別しない`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("a.MP4"),
                file("a.WEBP", mimeType = "image/webp"),
            )
        )
        assertEquals(1, items.size)
        assertEquals("a.WEBP", items[0].thumbnailFileId)
    }

    @Test
    fun `同名 webp がサムネイルとして紐付く`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("20260315_a.mp4", id = "v1", thumbnailLink = "https://example.com/t1"),
                file("20260315_a.webp", id = "t1", mimeType = "image/webp"),
                file("20260316_b.mp4", id = "v2"),
            )
        )
        val a = items.first { it.id == "v1" }
        assertEquals("t1", a.thumbnailFileId)
        val b = items.first { it.id == "v2" }
        assertNull(b.thumbnailFileId)
    }

    @Test
    fun `webp がない場合は thumbnailLink がフォールバックになる`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(file("a.mp4", thumbnailLink = "https://example.com/thumb"))
        )
        assertNull(items[0].thumbnailFileId)
        assertEquals("https://example.com/thumb", items[0].thumbnailUrl)
    }

    @Test
    fun `別名の webp は紐付かない`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("a.mp4"),
                file("b.webp", mimeType = "image/webp"),
            )
        )
        assertNull(items[0].thumbnailFileId)
    }
}
