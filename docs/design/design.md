# 設計書: Google Drive 動画プレイヤー

## モジュール構成(単一 app モジュール内のパッケージ構成)

過度な抽象化を避けるため Gradle モジュールは `app` 1 つとし、パッケージで層を分ける。

```
com.aklzo.videoplayer
├── MainActivity.kt              … 単一 Activity(Compose ホスト)
├── VideoPlayerApp.kt            … Application(DI コンテナの生成)
├── auth/                        … 認証
│   ├── GoogleAuthManager.kt     … AuthorizationClient ラッパ。トークン取得・失効処理
│   └── TokenProvider.kt         … アクセストークン供給インターフェース
├── data/
│   ├── source/                  … 「動画ソース」抽象化(将来ローカルフォルダ等を追加可能)
│   │   ├── VideoSource.kt       … インターフェース(一覧/サムネイル/再生用データ取得)
│   │   ├── VideoItem.kt         … ソース非依存の動画メタデータモデル
│   │   └── drive/
│   │       ├── DriveApiClient.kt    … Drive REST API 呼び出し(インターフェース + OkHttp 実装)
│   │       └── DriveVideoSource.kt  … VideoSource の Drive 実装
│   ├── cache/
│   │   ├── VideoCacheRepository.kt  … ダウンロード・キャッシュ管理・LRU 削除
│   │   ├── CacheDatabase.kt         … Room Database
│   │   ├── CachedVideoDao.kt
│   │   └── CachedVideoEntity.kt     … fileId / 最終再生日時 / サイズ / パス
│   └── settings/
│       └── SettingsRepository.kt    … DataStore(フォルダID、容量上限、保持期間、Wi-Fi設定)
├── domain/                      … 純粋ロジック(すべてユニットテスト対象)
│   ├── VideoListGrouper.kt      … 日付パース(^(\d{8})_)・グルーピング・ソート
│   ├── VideoCatalog.kt          … .mp4 抽出と .webp サムネイルの紐付け
│   └── CacheEvictionPolicy.kt   … 保持期間切れ+容量超過(LRU)の削除判定
├── playback/
│   ├── PlaybackService.kt       … MediaSessionService(バックグラウンド再生・通知)
│   └── PlaybackTracker.kt       … 再生中 fileId の共有(削除除外用)
├── RootViewModel.kt             … 認証状態の管理(サイレント認証→サインイン誘導)
├── AppContainer.kt              … 手書き DI コンテナ
└── ui/
    ├── AppNavHost.kt            … Navigation Compose
    ├── signin/SignInScreen.kt
    ├── folder/FolderPickerScreen.kt (+ ViewModel)
    ├── list/VideoListScreen.kt      (+ ViewModel)
    ├── player/PlayerScreen.kt       (+ ViewModel)… DL 進捗/キャンセル/MediaController 接続
    └── settings/SettingsScreen.kt   (+ ViewModel)
```

DI は Hilt を使わず、Application に手書きの `AppContainer` を置く(依存が少ないため)。

## 「動画ソース」抽象化

将来ローカルフォルダなど別ソースを追加できるよう、リポジトリ層をインターフェースで分離する。

```kotlin
interface VideoSource {
    /** フォルダ内の動画一覧(サムネイル情報込み)を取得 */
    suspend fun listVideos(folderId: String): List<VideoItem>
    /** サムネイル画像を取得してローカルファイルに保存し、その File を返す */
    suspend fun fetchThumbnail(item: VideoItem): File?
    /** 動画本体を destination にダウンロードする(進捗コールバック付き) */
    suspend fun downloadVideo(item: VideoItem, destination: File, onProgress: (Long, Long?) -> Unit)
}
```

- `VideoItem` は `id / name / sizeBytes / thumbnailRef` を持つソース非依存モデル。
- Drive 実装 `DriveVideoSource` は `DriveApiClient`(インターフェース)に依存し、
  ユニットテストでは `DriveApiClient` をモックする。
- フォルダブラウズ(`listFolders`)は Drive 固有機能のため `DriveApiClient` 側に置く。

## 画面遷移

```
起動
 │
 ├─ 未サインイン ──────────► SignInScreen ──サインイン成功──┐
 │                                                        ▼
 ├─ サインイン済み・フォルダ未選択 ──────────────► FolderPickerScreen
 │                                                        │ フォルダ決定
 ├─ サインイン済み・フォルダ選択済み ──┐                    ▼
 │                                   └──────────► VideoListScreen ◄─┐
 │                                                 │        │       │
 │                                          タップ(DL→)再生  設定    │戻る
 │                                                 ▼        ▼       │
 │                                           PlayerScreen  SettingsScreen
 │                                                          │ フォルダ変更
 │                                                          └► FolderPickerScreen
```

## 使用ライブラリ

| 用途 | ライブラリ | 備考 |
|---|---|---|
| UI | Jetpack Compose (BOM) + Material3 | |
| ナビゲーション | navigation-compose | |
| プレイヤー | Media3 ExoPlayer / media3-session / media3-ui | 通知・ロック画面は MediaSessionService 任せ |
| 認証 | play-services-auth (Identity.AuthorizationClient) + credentials | |
| HTTP | OkHttp | Drive REST を直接叩く |
| JSON | kotlinx-serialization-json | files.list レスポンスのパース |
| 画像 | Coil (coil-compose) | webp 対応 |
| 永続化 | Room(キャッシュメタデータ)、DataStore Preferences(設定) | |
| テスト | JUnit4 + kotlinx-coroutines-test | |

Google Drive 公式 Java クライアント(google-api-services-drive)は依存が重く
Android では推奨されないため使わず、必要な 3 エンドポイント
(`files.list` / `files.get?alt=media` / サムネイル URL)を OkHttp で直接呼ぶ。

## 認証フロー

```
起動
 │
 ▼
AuthorizationClient.authorize(scopes=[drive.readonly])   … UI なしで試行
 │
 ├─ 成功(既に許可済み)→ accessToken 取得 → サイレントにメイン画面へ
 │
 └─ pendingIntent あり(未許可 or 要再同意)
     │  ※初回はここに入る → SignInScreen に「Google でサインイン」ボタン表示
     ▼
   ボタン押下 → PendingIntent 起動(アカウント選択+同意画面)
     │
     ├─ 許可 → accessToken 取得 → フォルダ選択 or 一覧へ
     └─ 拒否 → SignInScreen にエラーとリトライ導線

API 呼び出しで 401 → トークン破棄 → authorize() 再試行
  → サイレント成功なら透過的にリトライ、失敗なら SignInScreen へ誘導
```

- AuthorizationClient は Google Cloud Console に登録済みの
  「Android 用 OAuth クライアント ID(パッケージ名 + SHA-1)」で動作するため、
  クライアント ID をコードに埋め込む必要がない。
- Credential Manager(GetGoogleIdOption)による ID トークン取得は **Web アプリケーション用
  クライアント ID(serverClientId)が必須**。本アプリはサーバーを持たず ID トークンも
  不要なため、`local.properties` に `google.webClientId` が設定されている場合のみ
  Credential Manager でアカウント確認を行い、未設定なら AuthorizationClient のみで完結する。
  (→ docs/design/decisions.md 参照)

## 設定値の注入(秘密情報の扱い)

- `local.properties`(.gitignore 済み)に設定値を書き、`app/build.gradle.kts` が読んで
  `BuildConfig` フィールドとして注入する。
  - `google.webClientId`(任意。未設定なら空文字)
- OAuth の Android クライアント ID はパッケージ名 + SHA-1 で解決されるためアプリ内に持たない。

## キャッシュ設計

- 保存先: `context.filesDir/video_cache/<fileId>.mp4`
  サムネイル: `context.cacheDir/thumbs/<fileId>.webp`
- Room `cached_videos` テーブル: `fileId(PK) / fileName / sizeBytes / lastPlayedAt / downloadedAt`
- LRU 削除判定(純粋ロジック `CacheEvictionPolicy` としてユニットテスト対象):
  1. 起動時: `lastPlayedAt` が保持期間より古いものを削除
  2. 合計サイズ > 上限: `lastPlayedAt` 昇順に、上限を下回るまで削除
  3. 再生中の fileId は常に除外
- ダウンロードは `<fileId>.mp4.part` に書き、完了後 rename(部分ファイルをキャッシュ扱いしない)。

## ダウンロードのネットワーク制約

- ダウンロード開始前に `ConnectivityManager` で判定:
  モバイル回線許可 OFF かつ非 Wi-Fi(≒従量制ネットワーク)ならダイアログでブロック。
- 判定には `NetworkCapabilities.NET_CAPABILITY_NOT_METERED` を用いる
  (テザリング等も含め「従量制かどうか」で判断するのが実態に合うため)。
