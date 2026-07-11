package com.aklzo.videoplayer.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.playlist.PlaylistWithCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("プレイリスト") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "プレイリストを作成")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (playlists.isEmpty()) {
                Text(
                    "プレイリストがありません。\n右下の + で作成するか、\n動画を長押しして追加できます",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(playlists, key = { it.playlistId }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.itemCount} 本") },
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                            },
                            trailingContent = {
                                IconButton(onClick = { deleteTarget = playlist }) {
                                    Icon(Icons.Default.Delete, contentDescription = "${playlist.name} を削除")
                                }
                            },
                            modifier = Modifier.clickable { onOpenPlaylist(playlist.playlistId) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("プレイリストを削除") },
            text = { Text("「${target.name}」を削除しますか?(動画本体・キャッシュは削除されません)") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.playlistId)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいプレイリスト") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("作成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
