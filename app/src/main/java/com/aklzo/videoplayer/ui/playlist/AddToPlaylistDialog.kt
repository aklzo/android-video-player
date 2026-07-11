package com.aklzo.videoplayer.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoListGrouper

/**
 * 動画カード長押しで表示する「プレイリストへ追加」ダイアログ。
 * 既存プレイリストの選択、または新規作成→追加ができる。
 */
@Composable
fun AddToPlaylistDialog(
    video: VideoItem,
    viewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating) {
        CreatePlaylistDialog(
            onCreate = { name ->
                viewModel.createPlaylist(name) { playlistId ->
                    viewModel.addToPlaylist(playlistId, video)
                }
                onDismiss()
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プレイリストに追加") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    VideoListGrouper.displayTitle(video.name),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.itemCount} 本") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.addToPlaylist(playlist.playlistId, video)
                            onDismiss()
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("新しいプレイリストを作成") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                    },
                    modifier = Modifier.clickable { creating = true },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
