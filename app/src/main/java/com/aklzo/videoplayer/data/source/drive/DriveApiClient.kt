package com.aklzo.videoplayer.data.source.drive

import java.io.File

/** Drive 上のファイル/フォルダのメタデータ */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val thumbnailLink: String?,
    /** 親フォルダ ID(通常 1 件)。サムネイル紐付けの「同じディレクトリ」判定に使う */
    val parents: List<String> = emptyList(),
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
    /**
     * 指定フォルダ群の直下にあるサブフォルダ一覧。ルートは "root"。
     * 複数フォルダは 1 クエリにまとめて問い合わせる(再帰列挙の効率化)。
     */
    suspend fun listFolders(folderIds: List<String>): List<DriveFile>

    /** 指定フォルダ群の直下にあるファイル一覧(フォルダ以外すべて。ページングは内部で処理) */
    suspend fun listFiles(folderIds: List<String>): List<DriveFile>

    /** ドライブ全体のフォルダ一覧(選択フォルダ配下の判定用ツリー構築に使う) */
    suspend fun listAllFolders(): List<DriveFile>

    /** ドライブ全体の動画(video/mp4)とサムネイル(image/webp)の一覧 */
    suspend fun listAllMediaFiles(): List<DriveFile>

    /** マイドライブのルートフォルダの実 ID を返す("root" エイリアスの解決) */
    suspend fun resolveRootFolderId(): String

    /**
     * files.get?alt=media でファイルを destination にダウンロードする(サムネイル用)。
     * コルーチンのキャンセルで中断でき、その場合 destination は削除される。
     */
    suspend fun downloadFile(
        fileId: String,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    )

    /** 任意 URL(thumbnailLink 等)を認証付きでダウンロードする */
    suspend fun downloadUrl(url: String, destination: File)
}
