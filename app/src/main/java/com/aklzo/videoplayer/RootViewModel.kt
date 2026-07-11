package com.aklzo.videoplayer

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aklzo.videoplayer.auth.AuthorizeOutcome
import com.aklzo.videoplayer.auth.GoogleAuthManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RootState {
    data object Loading : RootState
    data class SignIn(val errorMessage: String?) : RootState
    data object SignedIn : RootState
}

/** アプリ全体の認証状態を管理する */
class RootViewModel(private val auth: GoogleAuthManager) : ViewModel() {

    private val _state = MutableStateFlow<RootState>(RootState.Loading)
    val state = _state.asStateFlow()

    /** 同意画面の PendingIntent を Activity に起動してもらうためのイベント */
    private val _resolutionRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val resolutionRequests = _resolutionRequests.asSharedFlow()

    private var pendingResolution: PendingIntent? = null

    init {
        silentSignIn()
    }

    /** 起動時・再試行時のサイレント認証 */
    fun silentSignIn() {
        _state.value = RootState.Loading
        viewModelScope.launch {
            when (val outcome = auth.authorize()) {
                is AuthorizeOutcome.Success -> _state.value = RootState.SignedIn
                is AuthorizeOutcome.NeedsUserAction -> {
                    pendingResolution = outcome.pendingIntent
                    _state.value = RootState.SignIn(errorMessage = null)
                }
                is AuthorizeOutcome.Failure -> _state.value = RootState.SignIn(outcome.message)
            }
        }
    }

    /** サインインボタン押下 */
    fun onSignInClick(activity: Activity) {
        viewModelScope.launch {
            // webClientId 設定時のみ Credential Manager でアカウント選択(未設定なら null)
            val account = auth.pickAccountViaCredentialManager(activity)
            if (account != null) {
                when (val outcome = auth.authorize(account)) {
                    is AuthorizeOutcome.Success -> {
                        _state.value = RootState.SignedIn
                        return@launch
                    }
                    is AuthorizeOutcome.NeedsUserAction -> pendingResolution = outcome.pendingIntent
                    is AuthorizeOutcome.Failure -> {
                        _state.value = RootState.SignIn(outcome.message)
                        return@launch
                    }
                }
            }
            val resolution = pendingResolution
            if (resolution != null) {
                _resolutionRequests.emit(resolution)
            } else {
                silentSignIn()
            }
        }
    }

    /** 同意画面から戻ってきた結果 */
    fun onAuthorizationResult(intent: Intent?) {
        pendingResolution = null
        when (val outcome = auth.handleAuthorizationResult(intent)) {
            is AuthorizeOutcome.Success -> _state.value = RootState.SignedIn
            is AuthorizeOutcome.Failure -> {
                _state.value = RootState.SignIn(outcome.message)
                // 再度サイレント認証を試みて PendingIntent を取り直す
                viewModelScope.launch {
                    val retry = auth.authorize()
                    if (retry is AuthorizeOutcome.NeedsUserAction) {
                        pendingResolution = retry.pendingIntent
                    }
                }
            }
            is AuthorizeOutcome.NeedsUserAction -> Unit // ここには来ない
        }
    }

    /** API 層が NeedsSignInException を投げた際に UI から呼ぶ */
    fun onSessionExpired() {
        auth.invalidateToken()
        silentSignIn()
    }

    class Factory(private val auth: GoogleAuthManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RootViewModel(auth) as T
    }
}
