package com.aklzo.videoplayer.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoListGrouper
import com.aklzo.videoplayer.ui.list.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    cachedVideoIds: Set<String>,
    onVideoClick: (VideoItem) -> Unit,
    onBack: () -> Unit,
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<VideoItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name ?: "プレイリスト") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (videos.isEmpty()) {
                Text(
                    "このプレイリストは空です。\n動画一覧・保存済みタブで動画を長押しすると追加できます",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // 追加順のフラットなグリッド(月ヘッダーなし)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(videos, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            isCached = video.id in cachedVideoIds,
                            thumbnailProvider = { viewModel.localThumbnail(it) },
                            onClick = { onVideoClick(video) },
                            onLongClick = { removeTarget = video },
                        )
                    }
                }
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("プレイリストから削除") },
            text = {
                Text("「${VideoListGrouper.displayTitle(target.name)}」をこのプレイリストから削除しますか?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeItem(target.id)
                    removeTarget = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("キャンセル") }
            },
        )
    }
}
