package com.aklzo.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            // 次のステップで実装
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("動画一覧(次のステップで実装)\nフォルダ: ${current.folderName}")
            }
        }
    }
}
