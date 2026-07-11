package com.aklzo.videoplayer.data.source.drive

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveVideoSourceTest {

    /** ドライブ全体をメモリ上で模した DriveApiClient のフェイク */
    private class FakeDriveApiClient(
        private val allFolders: List<DriveFile>,
        private val allMedia: List<DriveFile>,
        private val rootId: String = "rootId",
    ) : DriveApiClient {
        override suspend fun listFolders(folderIds: List<String>): List<DriveFile> =
            allFolders.filter { it.parents.firstOrNull() in folderIds }

        override suspend fun listFiles(folderIds: List<String>): List<DriveFile> =
            allMedia.filter { it.parents.firstOrNull() in folderIds }

        override suspend fun listAllFolders(): List<DriveFile> = allFolders

        override suspend fun listAllMediaFiles(): List<DriveFile> = allMedia

        override suspend fun resolveRootFolderId(): String = rootId

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
    fun `サブフォルダ配下の動画も列挙され、同ディレクトリの webp が紐付く`() = runTest {
        // top ── sub1 ── sub1-1
        //    └ sub2
        val api = FakeDriveApiClient(
            allFolders = listOf(
                folder("top", "rootId"),
                folder("sub1", "top"),
                folder("sub1-1", "sub1"),
                folder("sub2", "top"),
                folder("unrelated", "rootId"),
            ),
            allMedia = listOf(
                mp4("20260101_a.mp4", "sub1"),
                webp("20260101_a.webp", "sub1"),
                mp4("20260102_b.mp4", "sub1-1"),
                mp4("20260103_c.mp4", "sub2"),
                mp4("20260104_x.mp4", "unrelated"),
            ),
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
    fun `root エイリアスは実 ID に解決されて全動画が対象になる`() = runTest {
        val api = FakeDriveApiClient(
            allFolders = listOf(folder("top", "rootId")),
            allMedia = listOf(mp4("v.mp4", "top"), mp4("w.mp4", "rootId")),
        )
        val source = DriveVideoSource(api, thumbsDir = File("/tmp/unused"))

        val videos = source.listVideos("root")

        assertEquals(setOf("v.mp4", "w.mp4"), videos.map { it.name }.toSet())
    }
}
