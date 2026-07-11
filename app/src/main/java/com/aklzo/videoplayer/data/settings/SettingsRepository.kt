package com.aklzo.videoplayer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val folderId: String?,
    val folderName: String?,
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

    private object Keys {
        val FOLDER_ID = stringPreferencesKey("folder_id")
        val FOLDER_NAME = stringPreferencesKey("folder_name")
        val CACHE_LIMIT_GB = intPreferencesKey("cache_limit_gb")

        // 無期限は -1 で保存する
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ALLOW_METERED = booleanPreferencesKey("allow_metered")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            folderId = prefs[Keys.FOLDER_ID],
            folderName = prefs[Keys.FOLDER_NAME],
            cacheLimitGb = prefs[Keys.CACHE_LIMIT_GB] ?: 10,
            retentionDays = (prefs[Keys.RETENTION_DAYS] ?: 30).takeIf { it > 0 },
            allowMetered = prefs[Keys.ALLOW_METERED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setFolder(folderId: String, folderName: String) {
        context.dataStore.edit {
            it[Keys.FOLDER_ID] = folderId
            it[Keys.FOLDER_NAME] = folderName
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
