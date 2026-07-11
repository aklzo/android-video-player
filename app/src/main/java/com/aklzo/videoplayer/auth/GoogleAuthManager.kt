package com.aklzo.videoplayer.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.aklzo.videoplayer.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

sealed interface AuthorizeOutcome {
    data class Success(val accessToken: String) : AuthorizeOutcome

    /** ユーザーの同意(アカウント選択・スコープ許可)が必要 */
    data class NeedsUserAction(val pendingIntent: PendingIntent) : AuthorizeOutcome
    data class Failure(val message: String) : AuthorizeOutcome
}

/**
 * AuthorizationClient による drive.readonly スコープの OAuth トークン管理。
 * Android 用 OAuth クライアント ID(パッケージ名 + SHA-1)で解決されるため
 * クライアント ID をコードに持たない。
 * local.properties に google.webClientId がある場合のみ、サインイン時に
 * Credential Manager でアカウント選択を先に行う(→ docs/design/decisions.md)。
 */
class GoogleAuthManager(private val context: Context) : TokenProvider {

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.readonly")
    private val client by lazy { Identity.getAuthorizationClient(context) }
    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    /** サイレント認証を試みる。未同意なら NeedsUserAction を返す */
    suspend fun authorize(account: Account? = null): AuthorizeOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(driveScope))
            .apply { if (account != null) setAccount(account) }
            .build()
        return try {
            val result = client.authorize(request).await()
            val pendingIntent = result.pendingIntent
            when {
                result.hasResolution() && pendingIntent != null ->
                    AuthorizeOutcome.NeedsUserAction(pendingIntent)
                result.accessToken != null -> {
                    cachedToken = result.accessToken
                    AuthorizeOutcome.Success(result.accessToken!!)
                }
                else -> AuthorizeOutcome.Failure("アクセストークンを取得できませんでした")
            }
        } catch (e: Exception) {
            Log.w(TAG, "authorize failed", e)
            AuthorizeOutcome.Failure(e.message ?: "認証中に不明なエラーが発生しました")
        }
    }

    /** 同意画面(PendingIntent)の結果からトークンを取り出す */
    fun handleAuthorizationResult(intent: Intent?): AuthorizeOutcome = try {
        val result = client.getAuthorizationResultFromIntent(intent)
        val token = result.accessToken
        if (token != null) {
            cachedToken = token
            AuthorizeOutcome.Success(token)
        } else {
            AuthorizeOutcome.Failure("アクセストークンを取得できませんでした")
        }
    } catch (e: Exception) {
        Log.w(TAG, "handleAuthorizationResult failed", e)
        AuthorizeOutcome.Failure("サインインがキャンセルされたか、失敗しました")
    }

    /**
     * google.webClientId 設定時のみ: Credential Manager のアカウント選択を表示し、
     * 選択されたアカウントを返す。未設定・失敗時は null(AuthorizationClient に任せる)。
     */
    suspend fun pickAccountViaCredentialManager(activity: Activity): Account? {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) return null
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request).credential
            val googleId = GoogleIdTokenCredential.createFrom(credential.data)
            Account(googleId.id, "com.google")
        } catch (e: Exception) {
            Log.w(TAG, "Credential Manager sign-in skipped", e)
            null
        }
    }

    /**
     * ストリーミング再生の open ごとに呼ばれる。キャッシュを使わず authorize() し直すことで
     * 長時間再生中のトークン失効を避ける(サイレントで完結し、UI は出ない)。
     */
    suspend fun freshAccessToken(): String =
        when (val outcome = authorize()) {
            is AuthorizeOutcome.Success -> outcome.accessToken
            else -> throw NeedsSignInException()
        }

    override suspend fun getAccessToken(): String {
        cachedToken?.let { return it }
        return mutex.withLock {
            cachedToken ?: when (val outcome = authorize()) {
                is AuthorizeOutcome.Success -> outcome.accessToken
                else -> throw NeedsSignInException()
            }
        }
    }

    override fun invalidateToken() {
        cachedToken = null
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
    }
}
