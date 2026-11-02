package com.aklzo.videoplayer.data.source.drive

import android.os.SystemClock
import android.util.Log
import com.aklzo.videoplayer.auth.TokenProvider
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 401 (トークン失効) を表す内部例外。リトライ判定に使う */
private class UnauthorizedException : IOException("401 Unauthorized")

class OkHttpDriveApiClient(
    private val tokenProvider: TokenProvider,
    private val httpClient: OkHttpClient,
) : DriveApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileListResponse(
        val nextPageToken: String? = null,
        val files: List<DriveFileDto> = emptyList(),
    )

    @Serializable
    private data class DriveFileDto(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: String? = null,
        val thumbnailLink: String? = null,
        val parents: List<String> = emptyList(),
    ) {
        fun toModel() = DriveFile(id, name, mimeType, size?.toLongOrNull(), thumbnailLink, parents)
    }

    override suspend fun listFolders(folderIds: List<String>): List<DriveFile> =
        listInParents(folderIds, "mimeType = '${DriveFile.MIME_FOLDER}'")

    override suspend fun listFiles(folderIds: List<String>): List<DriveFile> =
        listInParents(folderIds, "mimeType != '${DriveFile.MIME_FOLDER}'")

    /** 親フォルダを最大 [PARENTS_PER_QUERY] 件ずつ OR でまとめて files.list する */
    private suspend fun listInParents(folderIds: List<String>, mimeCondition: String): List<DriveFile> =
        folderIds.chunked(PARENTS_PER_QUERY).flatMap { chunk ->
            val parentsCondition =
                chunk.joinToString(" or ") { "'${escapeQuery(it)}' in parents" }
            listAll(
                "($parentsCondition) and $mimeCondition and trashed = false",
                fields = FILE_FIELDS,
                orderBy = "name", // フォルダ選択画面は API の並び順をそのまま表示する
            )
        }

    // 一括取得の 2 つは並び順をクライアント側(VideoListGrouper)で決めるため orderBy を付けず、
    // fields も用途に必要な分だけにしてサーバー側の処理とレスポンスを減らす

    override suspend fun listAllFolders(): List<DriveFile> =
        listAll("mimeType = '${DriveFile.MIME_FOLDER}' and trashed = false", fields = FOLDER_FIELDS)

    override suspend fun listAllMediaFiles(): List<DriveFile> =
        listAll(
            "(mimeType = 'video/mp4' or mimeType = 'image/webp') and trashed = false",
            fields = FILE_FIELDS,
        )

    @Serializable
    private data class FileIdResponse(val id: String)

    override suspend fun resolveRootFolderId(): String {
        val body = withAuthRetry { token ->
            executeCall(
                buildRequest("https://www.googleapis.com/drive/v3/files/root?fields=id", token)
            ).use { response ->
                checkResponse(response)
                withContext(Dispatchers.IO) { response.body!!.string() }
            }
        }
        return json.decodeFromString<FileIdResponse>(body).id
    }

    private suspend fun listAll(
        query: String,
        fields: String,
        orderBy: String? = null,
    ): List<DriveFile> {
        val startedAt = SystemClock.elapsedRealtime()
        val results = mutableListOf<DriveFile>()
        var pages = 0
        var pageToken: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files($fields)")
                .addQueryParameter("pageSize", "1000")
                .apply { orderBy?.let { addQueryParameter("orderBy", it) } }
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = withAuthRetry { token ->
                executeCall(buildRequest(url.toString(), token)).use { response ->
                    checkResponse(response)
                    // 1 ページ数百 KB になるため、読み込みとパースはメインスレッド外で行う
                    withContext(Dispatchers.IO) {
                        json.decodeFromString<FileListResponse>(response.body!!.string())
                    }
                }
            }
            results += page.files.map { it.toModel() }
            pages++
            pageToken = page.nextPageToken
        } while (pageToken != null)
        // 一覧が遅いときの切り分け用(adb logcat -s DriveApi)
        Log.d(
            TAG,
            "files.list ${results.size} 件 / $pages ページ / " +
                "${SystemClock.elapsedRealtime() - startedAt}ms: $query",
        )
        return results
    }

    override suspend fun downloadFile(
        fileId: String,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        downloadToFile("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", destination, onProgress)
    }

    override suspend fun downloadUrl(url: String, destination: File) {
        downloadToFile(url, destination) { _, _ -> }
    }

    private suspend fun downloadToFile(
        url: String,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        withAuthRetry { token ->
            executeCall(buildRequest(url, token)).use { response ->
                checkResponse(response)
                withContext(Dispatchers.IO) {
                    try {
                        val body = response.body!!
                        val total = body.contentLength().takeIf { it >= 0 }
                        destination.parentFile?.mkdirs()
                        body.byteStream().use { input ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                var read = 0L
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    onProgress(read, total)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        destination.delete()
                        throw e
                    }
                }
            }
        }
    }

    private fun buildRequest(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

    /** 401 のときはトークンを破棄して 1 回だけリトライする */
    private suspend fun <T> withAuthRetry(block: suspend (token: String) -> T): T {
        val token = tokenProvider.getAccessToken()
        return try {
            block(token)
        } catch (e: UnauthorizedException) {
            tokenProvider.invalidateToken()
            block(tokenProvider.getAccessToken())
        }
    }

    private fun checkResponse(response: Response) {
        if (response.code == 401) throw UnauthorizedException()
        if (!response.isSuccessful) {
            throw IOException("Drive API エラー: HTTP ${response.code}")
        }
    }

    private suspend fun executeCall(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response) { _, _, _ -> response.close() }
                }
            })
        }

    private fun escapeQuery(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        private const val TAG = "DriveApi"
        private const val PARENTS_PER_QUERY = 15

        /** フォルダのツリー判定に必要な最小限(DriveFileDto の必須項目+parents) */
        private const val FOLDER_FIELDS = "id,name,mimeType,parents"
        private const val FILE_FIELDS = "id,name,mimeType,size,thumbnailLink,parents"
    }
}
