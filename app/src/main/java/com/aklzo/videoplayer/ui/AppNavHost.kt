package com.aklzo.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aklzo.videoplayer.AppContainer
import com.aklzo.videoplayer.LoadingScreen
import com.aklzo.videoplayer.ui.folder.FolderPickerScreen
import com.aklzo.videoplayer.ui.folder.FolderPickerViewModel
import com.aklzo.videoplayer.ui.list.VideoListScreen
import com.aklzo.videoplayer.ui.list.VideoListViewModel

object Routes {
    const val FOLDER_PICKER = "folderPicker"
    const val VIDEO_LIST = "videoList"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{fileId}"

    fun player(fileId: String) = "player/$fileId"
}

/** サインイン済み後のナビゲーション */
@Composable
fun AppNavHost(
    container: AppContainer,
    onNeedsSignIn: () -> Unit,
) {
    val navController = rememberNavController()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    val current = settings ?: run {
        LoadingScreen()
        return
    }
    // 初回コンポジション時のフォルダ設定有無で開始画面を決める(以後は Nav 側で遷移)
    val startDestination = remember {
        if (current.folderId == null) Routes.FOLDER_PICKER else Routes.VIDEO_LIST
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.FOLDER_PICKER) {
            val viewModel: FolderPickerViewModel = viewModel(
                factory = FolderPickerViewModel.Factory(
                    container.driveApiClient,
                    container.settingsRepository,
                )
            )
            FolderPickerScreen(
                viewModel = viewModel,
                onFolderSelected = {
                    navController.navigate(Routes.VIDEO_LIST) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNeedsSignIn = onNeedsSignIn,
            )
        }
        composable(Routes.VIDEO_LIST) {
            val viewModel: VideoListViewModel = viewModel(
                factory = VideoListViewModel.Factory(
                    container.videoSource,
                    container.settingsRepository,
                )
            )
            val cachedIds by container.videoCacheRepository.cachedIds
                .collectAsStateWithLifecycle(initialValue = emptySet())
            VideoListScreen(
                viewModel = viewModel,
                cachedVideoIds = cachedIds,
                onVideoClick = { /* ダウンロード+再生は次のステップで実装 */ },
                onSettingsClick = { /* 設定画面は後のステップで実装 */ },
                onNeedsSignIn = onNeedsSignIn,
            )
        }
    }
}
