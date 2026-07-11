package com.aklzo.videoplayer.data.source.drive

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveVideoSourceTest {

    /** フォルダ階層をメモリ上で模した DriveApiClient のフェイク */
    private class FakeDriveApiClient(
        /** parentId -> 直下のファイル/フォルダ */
        private val tree: Map<String, List<DriveFile>>,
    ) : DriveApiClient {
        var listCallCount = 0
            private set

        override suspend fun listFolders(folderIds: List<String>): List<DriveFile> {
            listCallCount++
            return folderIds.flatMap { id -> tree[id].orEmpty().filter { it.isFolder } }
        }

        override suspend fun listFiles(folderIds: List<String>): List<DriveFile> {
            listCallCount++
            return folderIds.flatMap { id -> tree[id].orEmpty().filterNot { it.isFolder } }
        }

        override suspend fun downloadFile(
            fileId: String,
            destination: File,
            onProgress: (Long, Long?) -> Unit,
        ) = throw UnsupportedOperationException()

        override suspend fun downloadUrl(url: String, destination: File) =
            throw UnsupportedOperationException()
    }

    private fun folder(id: String, parent: String) =
        DriveFile(id, id, DriveFile.MIME_FOLDER, null, null, listOf(parent))

    private fun mp4(name: String, parent: String) =
        DriveFile(name, name, "video/mp4", 10L, null, listOf(parent))

    private fun webp(name: String, parent: String) =
        DriveFile(name, name, "image/webp", 1L, null, listOf(parent))

    @Test
    fun `サブフォルダ配下の動画も再帰的に列挙され、同ディレクトリの webp が紐付く`() = runTest {
        val api = FakeDriveApiClient(
            tree = mapOf(
                "top" to listOf(folder("sub1", "top"), folder("sub2", "top")),
                "sub1" to listOf(
                    mp4("20260101_a.mp4", "sub1"),
                    webp("20260101_a.webp", "sub1"),
                    folder("sub1-1", "sub1"),
                ),
                "sub1-1" to listOf(mp4("20260102_b.mp4", "sub1-1")),
                "sub2" to listOf(mp4("20260103_c.mp4", "sub2")),
            )
        )
        val source = DriveVideoSource(api, thumbsDir = File("/tmp/unused"))

        val videos = source.listVideos("top")

        assertEquals(
            setOf("20260101_a.mp4", "20260102_b.mp4", "20260103_c.mp4"),
            videos.map { it.name }.toSet(),
        )
        assertEquals(
            "20260101_a.webp",
            videos.first { it.name == "20260101_a.mp4" }.thumbnailFileId,
        )
    }

    @Test
    fun `フォルダの循環があっても停止する`() = runTest {
        val api = FakeDriveApiClient(
            tree = mapOf(
                "a" to listOf(folder("b", "a"), mp4("v1.mp4", "a")),
                "b" to listOf(folder("a", "b")), // a → b → a の循環
            )
        )
        val source = DriveVideoSource(api, thumbsDir = File("/tmp/unused"))

        val videos = source.listVideos("a")

        assertEquals(listOf("v1.mp4"), videos.map { it.name })
    }
}
