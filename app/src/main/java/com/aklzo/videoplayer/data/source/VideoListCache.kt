package com.aklzo.videoplayer.data.source

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 最後に取得した動画一覧のディスクキャッシュ(JSON 1 ファイル)。
 * 一覧画面の初回表示でこれを即表示し、裏で Drive から最新を取得して差し替える
 * (stale-while-revalidate。→ docs/design/design.md)。
 * 対象フォルダ(ID の集合)が保存時と異なる場合は使わない。
 */
class VideoListCache(private val file: File) {

    @Serializable
    private data class Snapshot(
        val folderIds: List<String>,
        val videos: List<VideoItem>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 対象フォルダが一致するキャッシュがあれば返す。ない・壊れている・フォルダ不一致なら null */
    suspend fun load(folders: List<VideoFolder>): List<VideoItem>? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            snapshot.videos.takeIf { snapshot.folderIds.toSet() == folders.map { it.id }.toSet() }
        } catch (e: Exception) {
            // 形式変更・破損時は使わない(次の取得成功時に上書きされる)
            null
        }
    }

    /** 一覧を保存する。書き込み途中のファイルを読ませないよう一時ファイル → rename で置き換える */
    suspend fun save(folders: List<VideoFolder>, videos: List<VideoItem>) {
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "${file.name}.tmp")
            try {
                file.parentFile?.mkdirs()
                temp.writeText(json.encodeToString(Snapshot(folders.map { it.id }, videos)))
                if (!temp.renameTo(file)) temp.delete()
            } catch (e: IOException) {
                // キャッシュ保存の失敗は一覧表示に影響させない
                temp.delete()
            }
        }
    }
}
