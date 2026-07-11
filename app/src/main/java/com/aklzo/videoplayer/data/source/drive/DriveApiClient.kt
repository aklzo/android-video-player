package com.aklzo.videoplayer.data.source.drive

import java.io.File

/** Drive 上のファイル/フォルダのメタデータ */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val thumbnailLink: String?,
) {
    val isFolder: Boolean get() = mimeType == MIME_FOLDER

    companion object {
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
    }
}

/**
 * Drive REST API(drive.readonly スコープ)の呼び出し。
 * ユニットテストではこのインターフェースをモックする。
 */
interface DriveApiClient {
    /** 指定フォルダ直下のサブフォルダ一覧。ルートは folderId = "root" */
    suspend fun listFolders(folderId: String): List<DriveFile>

    /** 指定フォルダ直下のファイル一覧(フォルダ以外すべて。ページングは内部で処理) */
    suspend fun listFiles(folderId: String): List<DriveFile>

    /**
     * files.get?alt=media でファイルを destination にダウンロードする。
     * onProgress は (受信済みバイト数, 総バイト数 or null) で随時呼ばれる。
     * コルーチンのキャンセルで中断でき、その場合 destination は削除される。
     */
    suspend fun downloadFile(
        fileId: String,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    )
}
