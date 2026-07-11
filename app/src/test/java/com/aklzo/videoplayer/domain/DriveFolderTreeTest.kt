package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.drive.DriveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveFolderTreeTest {

    private fun folder(id: String, parent: String?) = DriveFile(
        id = id,
        name = id,
        mimeType = DriveFile.MIME_FOLDER,
        sizeBytes = null,
        thumbnailLink = null,
        parents = listOfNotNull(parent),
    )

    private fun file(name: String, parent: String) = DriveFile(
        id = name,
        name = name,
        mimeType = "video/mp4",
        sizeBytes = 1L,
        thumbnailLink = null,
        parents = listOf(parent),
    )

    @Test
    fun `直下・子・孫フォルダのファイルが対象になり、配下以外は除外される`() {
        // root ── top ── sub1 ── sub1a
        //     └ other
        val folders = listOf(
            folder("top", "root"),
            folder("sub1", "top"),
            folder("sub1a", "sub1"),
            folder("other", "root"),
        )
        val files = listOf(
            file("direct.mp4", "top"),
            file("child.mp4", "sub1"),
            file("grandchild.mp4", "sub1a"),
            file("outside.mp4", "other"),
            file("atRoot.mp4", "root"),
        )

        val result = DriveFolderTree.filterUnder(files, folders, "top")

        assertEquals(
            setOf("direct.mp4", "child.mp4", "grandchild.mp4"),
            result.map { it.name }.toSet(),
        )
    }

    @Test
    fun `ルートを指定するとすべてのファイルが対象になる`() {
        val folders = listOf(folder("top", "root"), folder("sub", "top"))
        val files = listOf(file("a.mp4", "root"), file("b.mp4", "sub"))

        val result = DriveFolderTree.filterUnder(files, folders, "root")

        assertEquals(2, result.size)
    }

    @Test
    fun `親チェーンに循環があっても停止する`() {
        val folders = listOf(folder("a", "b"), folder("b", "a")) // 循環
        val files = listOf(file("v.mp4", "a"))

        val result = DriveFolderTree.filterUnder(files, folders, "unrelated")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `親情報がないファイルは除外される`() {
        val orphan = DriveFile("x", "x.mp4", "video/mp4", 1L, null, parents = emptyList())

        val result = DriveFolderTree.filterUnder(listOf(orphan), emptyList(), "top")

        assertTrue(result.isEmpty())
    }
}
