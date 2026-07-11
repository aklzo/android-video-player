package com.aklzo.videoplayer.data.source.drive

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
            listAll("($parentsCondition) and $mimeCondition and trashed = false")
        }

    private suspend fun listAll(query: String): List<DriveFile> {
        val results = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,thumbnailLink,parents)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("orderBy", "name")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = withAuthRetry { token ->
                executeCall(buildRequest(url.toString(), token)).use { response ->
                    checkResponse(response)
                    withContext(Dispatchers.IO) { response.body!!.string() }
                }
            }
            val page = json.decodeFromString<FileListResponse>(body)
            results += page.files.map { it.toModel() }
            pageToken = page.nextPageToken
        } while (pageToken != null)
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
        private const val PARENTS_PER_QUERY = 15
    }
}
