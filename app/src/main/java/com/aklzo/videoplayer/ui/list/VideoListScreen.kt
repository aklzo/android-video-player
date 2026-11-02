package com.aklzo.videoplayer.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.ui.folder.ErrorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    cachedVideoIds: Set<String>,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ディレクトリフィルター(shorts / streams / videos 等)。分類が複数あるときのみ表示
            if (!state.isLoading && state.error == null && state.categories.size > 1) {
                CategoryFilterRow(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = { viewModel.selectCategory(it) },
                )
            }
            if (state.showingStaleList) {
                StaleListBanner(onRetry = { viewModel.refresh() })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> ErrorContent(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.groups.isEmpty() && state.selectedCategory != null -> Text(
                        "「${state.selectedCategory}」に動画がありません",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.groups.isEmpty() -> EmptyContent(
                        onRetry = { viewModel.refresh() },
                        onChangeFolder = onChangeFolder,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> VideoGrid(
                        groups = state.groups,
                        cachedVideoIds = cachedVideoIds,
                        thumbnailProvider = { viewModel.loadThumbnail(it) },
                        onVideoClick = onVideoClick,
                        onVideoLongClick = onVideoLongClick,
                    )
                }
                // 前回の一覧を表示したまま裏で最新を取得中。一覧の上に重ねてレイアウトをずらさない
                if (state.isRefreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun StaleListBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "最新の一覧を取得できませんでした(前回の一覧を表示中)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("再試行") }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("すべて") },
            )
        }
        items(categories, key = { it }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category) },
            )
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
