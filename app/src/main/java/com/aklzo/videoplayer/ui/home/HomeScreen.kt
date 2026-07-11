package com.aklzo.videoplayer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aklzo.videoplayer.AppContainer
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.ui.list.VideoListScreen
import com.aklzo.videoplayer.ui.list.VideoListViewModel
import com.aklzo.videoplayer.ui.saved.SavedVideosScreen
import com.aklzo.videoplayer.ui.saved.SavedVideosViewModel

private enum class HomeTab(val label: String, val icon: ImageVector) {
    ALL("動画一覧", Icons.Default.VideoLibrary),
    SAVED("保存済み", Icons.Default.DownloadDone),
}

/** 下部タブで「動画一覧」(Drive)と「保存済み」(ローカル)を切り替えるホーム画面 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onVideoClick: (VideoItem) -> Unit,
    onSettingsClick: () -> Unit,
    onChangeFolder: () -> Unit,
    onNeedsSignIn: () -> Unit,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (HomeTab.entries[selectedTabIndex]) {
                HomeTab.ALL -> {
                    val listViewModel: VideoListViewModel = viewModel(
                        factory = VideoListViewModel.Factory(
                            container.videoSource,
                            container.settingsRepository,
                        )
                    )
                    val cachedIds by container.videoCacheRepository.cachedIds
                        .collectAsStateWithLifecycle(initialValue = emptySet())
                    VideoListScreen(
                        viewModel = listViewModel,
                        cachedVideoIds = cachedIds,
                        onVideoClick = onVideoClick,
                        onSettingsClick = onSettingsClick,
                        onChangeFolder = onChangeFolder,
                        onNeedsSignIn = onNeedsSignIn,
                    )
                }
                HomeTab.SAVED -> {
                    val savedViewModel: SavedVideosViewModel = viewModel(
                        factory = SavedVideosViewModel.Factory(
                            container.videoCacheRepository,
                            container.thumbsDir,
                        )
                    )
                    SavedVideosScreen(
                        viewModel = savedViewModel,
                        onVideoClick = onVideoClick,
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
        }
    }
}
