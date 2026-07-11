package com.aklzo.videoplayer.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.NeedsSignInException
import com.aklzo.videoplayer.data.settings.SettingsRepository
import com.aklzo.videoplayer.data.source.drive.DriveApiClient
import com.aklzo.videoplayer.data.source.drive.DriveFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FolderCrumb(val id: String, val name: String)

data class FolderPickerUiState(
    /** 現在位置までのパス。先頭は必ずマイドライブ */
    val path: List<FolderCrumb> = listOf(FolderCrumb("root", "マイドライブ")),
    val folders: List<DriveFile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val needsSignIn: Boolean = false,
) {
    val current: FolderCrumb get() = path.last()
    val canGoUp: Boolean get() = path.size > 1
}

class FolderPickerViewModel(
    private val driveApi: DriveApiClient,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderPickerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadCurrent()
    }

    fun open(folder: DriveFile) {
        _uiState.value = _uiState.value.copy(
            path = _uiState.value.path + FolderCrumb(folder.id, folder.name)
        )
        loadCurrent()
    }

    fun goUp() {
        val state = _uiState.value
        if (!state.canGoUp) return
        _uiState.value = state.copy(path = state.path.dropLast(1))
        loadCurrent()
    }

    fun retry() = loadCurrent()

    private fun loadCurrent() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, needsSignIn = false)
        viewModelScope.launch {
            try {
                val folders = driveApi.listFolders(_uiState.value.current.id)
                _uiState.value = _uiState.value.copy(isLoading = false, folders = folders)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsSignInException) {
                _uiState.value = _uiState.value.copy(isLoading = false, needsSignIn = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "フォルダ一覧の取得に失敗しました",
                )
            }
        }
    }

    /** 現在のフォルダを対象フォルダとして保存する */
    fun selectCurrent(onDone: () -> Unit) {
        val current = _uiState.value.current
        viewModelScope.launch {
            settings.setFolder(current.id, current.name)
            onDone()
        }
    }

    class Factory(
        private val driveApi: DriveApiClient,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FolderPickerViewModel(driveApi, settings) as T
    }
}
