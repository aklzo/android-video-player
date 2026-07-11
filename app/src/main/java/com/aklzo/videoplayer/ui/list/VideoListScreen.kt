package com.aklzo.videoplayer.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.ui.folder.ErrorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    cachedVideoIds: Set<String>,
    onVideoClick: (VideoItem) -> Unit,
    onSettingsClick: () -> Unit,
    onChangeFolder: () -> Unit,
    onNeedsSignIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.needsSignIn) {
        onNeedsSignIn()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.folderName.ifEmpty { "動画一覧" }) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorContent(
                    message = state.error!!,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.align(Alignment.Center),
                )
                state.groups.isEmpty() -> EmptyContent(
                    onRetry = { viewModel.refresh() },
                    onChangeFolder = onChangeFolder,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> VideoGrid(
                    state = state,
                    cachedVideoIds = cachedVideoIds,
                    viewModel = viewModel,
                    onVideoClick = onVideoClick,
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(
    onRetry: () -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "このフォルダ配下に動画(.mp4)が見つかりません",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onChangeFolder) { Text("フォルダを変更") }
        OutlinedButton(onClick = onRetry) { Text("再読み込み") }
    }
}

/** YouTube 風のサムネイルグリッド。日付ヘッダーは全幅で挟む */
@Composable
private fun VideoGrid(
    state: VideoListUiState,
    cachedVideoIds: Set<String>,
    viewModel: VideoListViewModel,
    onVideoClick: (VideoItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        state.groups.forEach { group ->
            item(key = "header_${group.label}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(group.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    isCached = video.id in cachedVideoIds,
                    viewModel = viewModel,
                    onClick = { onVideoClick(video) },
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    isCached: Boolean,
    viewModel: VideoListViewModel,
    onClick: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onClick)) {
        val thumbnailFile by produceState<java.io.File?>(initialValue = null, video.id) {
            value = viewModel.loadThumbnail(video)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailFile != null) {
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
            if (isCached) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "保存済み",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            video.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            video.sizeBytes?.let {
                Text(
                    formatSize(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCached) {
                Text(
                    " ・保存済み",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
