package com.aklzo.videoplayer.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aklzo.videoplayer.data.source.VideoFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** 対象フォルダ(複数)。空なら未選択 */
    val folders: List<VideoFolder>,
    /** キャッシュ容量上限 (GB) */
    val cacheLimitGb: Int,
    /** キャッシュ保持期間 (日)。null は無期限 */
    val retentionDays: Int?,
    /** モバイル回線(従量制ネットワーク)でのダウンロードを許可するか */
    val allowMetered: Boolean,
) {
    val cacheLimitBytes: Long get() = cacheLimitGb * 1_073_741_824L

    companion object {
        val CACHE_LIMIT_CHOICES_GB = listOf(5, 10, 20, 50)

        /** 保持期間の選択肢 (日)。null = 無期限 */
        val RETENTION_CHOICES_DAYS = listOf<Int?>(7, 30, 90, null)
    }
}

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        /** VideoFolder のリスト(JSON) */
        val FOLDERS = stringPreferencesKey("folders_json")

        // 旧: 単一フォルダ設定(FOLDERS への移行元として読み取りのみ)
        val LEGACY_FOLDER_ID = stringPreferencesKey("folder_id")
        val LEGACY_FOLDER_NAME = stringPreferencesKey("folder_name")

        val CACHE_LIMIT_GB = intPreferencesKey("cache_limit_gb")

        // 無期限は -1 で保存する
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ALLOW_METERED = booleanPreferencesKey("allow_metered")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val folders = prefs[Keys.FOLDERS]?.let { raw ->
            try {
                json.decodeFromString<List<VideoFolder>>(raw)
            } catch (e: Exception) {
                Log.w("SettingsRepository", "folders_json の読込に失敗", e)
                emptyList()
            }
        } ?: legacyFolder(prefs[Keys.LEGACY_FOLDER_ID], prefs[Keys.LEGACY_FOLDER_NAME])

        AppSettings(
            folders = folders,
            cacheLimitGb = prefs[Keys.CACHE_LIMIT_GB] ?: 10,
            retentionDays = (prefs[Keys.RETENTION_DAYS] ?: 30).takeIf { it > 0 },
            allowMetered = prefs[Keys.ALLOW_METERED] ?: false,
        )
    }

    private fun legacyFolder(id: String?, name: String?): List<VideoFolder> =
        if (id != null) listOf(VideoFolder(id, name ?: "選択フォルダ")) else emptyList()

    suspend fun current(): AppSettings = settings.first()

    /** フォルダを追加する(同じ ID があれば名前を更新するのみ) */
    suspend fun addFolder(folder: VideoFolder) {
        val updated = current().folders.filterNot { it.id == folder.id } + folder
        writeFolders(updated)
    }

    suspend fun removeFolder(folderId: String) {
        writeFolders(current().folders.filterNot { it.id == folderId })
    }

    private suspend fun writeFolders(folders: List<VideoFolder>) {
        context.dataStore.edit {
            it[Keys.FOLDERS] = json.encodeToString(folders)
            it.remove(Keys.LEGACY_FOLDER_ID)
            it.remove(Keys.LEGACY_FOLDER_NAME)
        }
    }

    suspend fun setCacheLimitGb(gb: Int) {
        context.dataStore.edit { it[Keys.CACHE_LIMIT_GB] = gb }
    }

    suspend fun setRetentionDays(days: Int?) {
        context.dataStore.edit { it[Keys.RETENTION_DAYS] = days ?: -1 }
    }

    suspend fun setAllowMetered(allow: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_METERED] = allow }
    }
}
