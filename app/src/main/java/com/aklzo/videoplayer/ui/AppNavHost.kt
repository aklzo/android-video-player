package com.aklzo.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aklzo.videoplayer.AppContainer
import com.aklzo.videoplayer.LoadingScreen
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.ui.folder.FolderPickerScreen
import com.aklzo.videoplayer.ui.folder.FolderPickerViewModel
import com.aklzo.videoplayer.ui.list.VideoListScreen
import com.aklzo.videoplayer.ui.list.VideoListViewModel
import com.aklzo.videoplayer.ui.player.PlayerScreen
import com.aklzo.videoplayer.ui.player.PlayerViewModel
import com.aklzo.videoplayer.ui.settings.SettingsScreen
import com.aklzo.videoplayer.ui.settings.SettingsViewModel
import java.net.URLEncoder

object Routes {
    const val FOLDER_PICKER = "folderPicker"
    const val VIDEO_LIST = "videoList"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{fileId}/{name}?size={size}"

    fun player(item: VideoItem): String {
        val encodedName = URLEncoder.encode(item.name, Charsets.UTF_8.name())
        return "player/${item.id}/$encodedName?size=${item.sizeBytes ?: -1L}"
    }
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
                onVideoClick = { item -> navController.navigate(Routes.player(item)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onChangeFolder = { navController.navigate(Routes.FOLDER_PICKER) },
                onNeedsSignIn = onNeedsSignIn,
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    container.settingsRepository,
                    container.videoCacheRepository,
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onChangeFolder = { navController.navigate(Routes.FOLDER_PICKER) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("size") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val item = VideoItem(
                id = args.getString("fileId")!!,
                name = args.getString("name")!!,
                sizeBytes = args.getLong("size").takeIf { it >= 0 },
                thumbnailFileId = null,
                thumbnailUrl = null,
            )
            val viewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(
                    item = item,
                    videoSource = container.videoSource,
                    cacheRepository = container.videoCacheRepository,
                    networkMonitor = container.networkMonitor,
                    settings = container.settingsRepository,
                )
            )
            PlayerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
