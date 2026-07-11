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
        parent: String = "dir1",
    ) = DriveFile(
        id = id,
        name = name,
        mimeType = mimeType,
        sizeBytes = 100L,
        thumbnailLink = thumbnailLink,
        parents = listOf(parent),
    )

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
    fun `同じ親フォルダの同名 webp がサムネイルとして紐付く`() {
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
    fun `別の親フォルダにある同名 webp は紐付かない`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("a.mp4", id = "v1", parent = "dir1"),
                file("a.webp", id = "t1", mimeType = "image/webp", parent = "dir2"),
            )
        )
        assertNull(items[0].thumbnailFileId)
    }

    @Test
    fun `サブフォルダごとに正しく紐付く`() {
        val items = VideoCatalog.buildVideoItems(
            listOf(
                file("a.mp4", id = "v1", parent = "dir1"),
                file("a.webp", id = "t1", mimeType = "image/webp", parent = "dir1"),
                file("a.mp4", id = "v2", parent = "dir2"),
                file("a.webp", id = "t2", mimeType = "image/webp", parent = "dir2"),
            )
        )
        assertEquals("t1", items.first { it.id == "v1" }.thumbnailFileId)
        assertEquals("t2", items.first { it.id == "v2" }.thumbnailFileId)
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
