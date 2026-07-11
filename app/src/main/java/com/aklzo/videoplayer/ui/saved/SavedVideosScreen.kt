package com.aklzo.videoplayer.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.ui.list.VideoGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedVideosScreen(
    viewModel: SavedVideosViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("保存済み動画") },
                actions = {
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
                state.groups.isEmpty() -> Text(
                    "保存済みの動画はありません。\n動画を最後まで保存するとここに表示され、\nオフラインでも再生できます",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> VideoGrid(
                    groups = state.groups,
                    // 保存済みタブでは全件が保存済みなのでマークは省略する
                    cachedVideoIds = emptySet(),
                    thumbnailProvider = { viewModel.localThumbnail(it) },
                    onVideoClick = onVideoClick,
                )
            }
        }
    }
}
