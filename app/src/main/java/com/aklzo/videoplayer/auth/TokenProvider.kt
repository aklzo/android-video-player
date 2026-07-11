package com.aklzo.videoplayer.auth

/** API 呼び出しが 401 を受けた際などに投げ、再サインイン UI への誘導に使う */
class NeedsSignInException : Exception("再サインインが必要です")

/** Drive API 呼び出し側にアクセストークンを供給する */
interface TokenProvider {
    /**
     * 有効なアクセストークンを返す。
     * サイレント認証で取得できない場合は [NeedsSignInException] を投げる。
     */
    suspend fun getAccessToken(): String

    /** 401 応答を受けた際に呼び、キャッシュ済みトークンを破棄する */
    fun invalidateToken()
}
