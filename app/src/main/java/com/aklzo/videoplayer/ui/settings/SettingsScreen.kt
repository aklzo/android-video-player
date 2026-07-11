package com.aklzo.videoplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onChangeFolder: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = null)
    val cacheSize by viewModel.cacheSizeBytes.collectAsStateWithLifecycle(initialValue = 0L)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("対象フォルダ")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current.folderName ?: "未選択",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onChangeFolder) { Text("変更") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("キャッシュ容量上限")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppSettings.CACHE_LIMIT_CHOICES_GB.forEachIndexed { index, gb ->
                    SegmentedButton(
                        selected = current.cacheLimitGb == gb,
                        onClick = { viewModel.setCacheLimitGb(gb) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppSettings.CACHE_LIMIT_CHOICES_GB.size,
                        ),
                    ) { Text("${gb}GB") }
                }
            }

            SectionTitle("キャッシュ保持期間", Modifier.padding(top = 16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppSettings.RETENTION_CHOICES_DAYS.forEachIndexed { index, days ->
                    SegmentedButton(
                        selected = current.retentionDays == days,
                        onClick = { viewModel.setRetentionDays(days) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppSettings.RETENTION_CHOICES_DAYS.size,
                        ),
                    ) { Text(days?.let { "${it}日" } ?: "無期限") }
                }
            }
            Text(
                "最終再生からこの期間を過ぎた動画は起動時に削除されます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("通信")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("モバイル回線でもダウンロード", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "オフの場合は Wi-Fi(非従量制)接続時のみダウンロードします",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = current.allowMetered,
                    onCheckedChange = { viewModel.setAllowMetered(it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("キャッシュ使用量")
            Text(
                formatSize(cacheSize),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = { showDeleteConfirm = true },
                enabled = cacheSize > 0,
            ) { Text("キャッシュを全削除") }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("キャッシュを全削除") },
            text = { Text("ダウンロード済みの動画をすべて削除します。再生中の動画は削除されません。よろしいですか?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllCache()
                    showDeleteConfirm = false
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
