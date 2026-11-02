---
title: 設計書: Google Drive 動画プレイヤー
type: architecture
status: 現行
updated: 2026-11-03
summary: 単一 Activity の Android アプリで、サーバーを持たず、端末から Google Drive API を直接呼ぶ。モジュール構成、一覧の取得、画面遷移、認証、再生とキャッシュの設計をまとめる。構造を変えたら、この文書を更新する。
---

## モジュール構成

過度な抽象化を避けるため、Gradle モジュールは `app` の 1 つだけにし、パッケージで層を分ける。

```
com.aklzo.videoplayer
├── MainActivity.kt              … 単一 Activity(Compose のホスト)
├── VideoPlayerApp.kt            … Application(DI コンテナを生成する)
├── AppContainer.kt              … 手書きの DI コンテナ
├── RootViewModel.kt             … 認証状態の管理(サイレント認証、サインインへの誘導)
├── auth/                        … 認証
│   ├── GoogleAuthManager.kt     … AuthorizationClient のラッパー。トークンの取得と失効の処理
│   └── TokenProvider.kt         … アクセストークンを供給するインターフェース
├── data/
│   ├── source/                  … 動画ソースの抽象化(将来ローカルフォルダなどを足せるようにする)
│   │   ├── VideoSource.kt       … インターフェース(一覧、サムネイル、再生用 URI)
│   │   ├── VideoItem.kt         … ソースに依存しない動画のメタデータ
│   │   ├── VideoFolder.kt       … 対象フォルダ(ID と名前)
│   │   ├── VideoListCache.kt    … 最後に取得した一覧のディスクキャッシュ(すぐに表示するため)
│   │   └── drive/
│   │       ├── DriveApiClient.kt        … Drive REST API のインターフェースと DriveFile
│   │       ├── OkHttpDriveApiClient.kt  … DriveApiClient の OkHttp 実装
│   │       └── DriveVideoSource.kt      … VideoSource の Drive 実装
│   ├── cache/
│   │   ├── VideoCacheRepository.kt  … 保存、キャッシュの管理、LRU による削除
│   │   ├── CacheDatabase.kt         … Room のデータベース
│   │   ├── CachedVideoDao.kt
│   │   ├── CachedVideoEntity.kt     … fileId、最終再生日時、サイズなど
│   │   └── NetworkMonitor.kt        … 従量制ネットワークかどうかの判定
│   ├── playlist/                … プレイリスト(Room)
│   └── settings/
│       └── SettingsRepository.kt    … DataStore(対象フォルダ、容量上限、保持期間、モバイル回線の許可)
├── domain/                      … 純粋なロジック(すべてユニットテストの対象)
│   ├── VideoListGrouper.kt      … 日付の取り出し(^(\d{8})_)、月ごとのグループ、並べ替え
│   ├── VideoCatalog.kt          … .mp4 の抽出と .webp サムネイルの紐付け
│   ├── DriveFolderTree.kt       … 選択フォルダ配下の判定とフォルダパスの算出
│   ├── VideoCategories.kt       … ディレクトリフィルターの分類
│   └── CacheEvictionPolicy.kt   … 保持期間切れと容量超過(LRU)の削除判定
├── playback/
│   ├── PlaybackService.kt       … MediaSessionService(バックグラウンド再生と通知)
│   └── PlaybackTracker.kt       … 再生中の fileId の共有(削除の対象から外すため)
└── ui/
    ├── AppNavHost.kt            … Navigation Compose
    ├── signin/                  … サインイン画面
    ├── folder/                  … フォルダ選択画面(と ViewModel)
    ├── home/HomeScreen.kt       … 下部タブ(動画一覧/プレイリスト/保存済み)のホスト
    ├── list/                    … Drive 由来の全動画のグリッド。VideoGrid.kt は各タブで共有する
    ├── playlist/                … プレイリストの一覧、中身、追加ダイアログ
    ├── saved/                   … Room 由来の保存済み動画。オフラインでも表示できる
    ├── player/                  … 再生画面(すぐに再生し、保存の進捗を表示、MediaController で接続)
    └── settings/                … 設定画面
```

DI には Hilt を使わず、Application に手書きの `AppContainer` を置く。依存するオブジェクトが少ないためである。

## 動画ソースの抽象化

将来ローカルフォルダなど別のソースを足せるよう、リポジトリ層をインターフェースで分ける。

```kotlin
interface VideoSource {
    /** 対象フォルダ群の配下(サブフォルダ再帰)の動画一覧を取得 */
    suspend fun listVideos(folders: List<VideoFolder>): List<VideoItem>
    /** サムネイル画像を取得してローカルファイルに保存し、その File を返す */
    suspend fun getThumbnail(item: VideoItem): File?
    /** 再生・キャッシュ保存に使う URI を返す(Drive は files.get?alt=media の URL) */
    fun playbackUri(item: VideoItem): String
}
```

- `VideoItem` は `id / name / sizeBytes / thumbnailFileId / thumbnailUrl / folderPath` を持ち、ソースに依存しない。
- Drive の実装 `DriveVideoSource` はインターフェース `DriveApiClient` に依存し、ユニットテストでは `DriveApiClient` のフェイクを使う。
- フォルダのブラウズ(`listFolders`)は Drive に固有の機能なので、`DriveApiClient` の側に置く。

### 再帰列挙

- ドライブ全体のフォルダ一覧(`mimeType = folder`)と動画・サムネイルの一覧(`mimeType = video/mp4 or image/webp`)を `files.list` でまとめて取得し、クライアントの側で parents を辿って選択フォルダの配下に絞る(`DriveFolderTree`)。フォルダを階層ごとに問い合わせる方式は、1 階層に 1,216 フォルダある実データで使えなくなったため、やめた(→ [技術判断の記録](decisions.md))。
- 2 種類の一括取得と、"root" の実 ID の解決は並列に投げる。並び順はクライアントの側(`VideoListGrouper`)で決めるので `orderBy` は付けず、`fields` も用途ごとに最小限にする(フォルダ一覧は id、name、mimeType、parents だけ)。
- JSON のパース、ツリーの判定、グループ分けはメインスレッドの外(IO / Default)で行う。
- 循環や深すぎる階層は、辿るホップ数の上限(100)で打ち切る。
- `.webp` を紐付けるのは、同じ親フォルダにある同名のファイルだけにする(別のディレクトリの同名ファイルと取り違えないため)。

### 一覧のディスクキャッシュ(stale-while-revalidate)

- 最後に取得した一覧(`List<VideoItem>`)を、対象フォルダの ID の集合と一緒に `cacheDir/video_list.json` に保存する(`VideoListCache`)。
- 一覧画面を最初に表示するとき、対象フォルダが一致するキャッシュがあればすぐに表示し、裏で Drive から最新を取得して差し替える(取得中は上部に細いプログレスバーを出す)。
- 裏での取得に失敗した場合はキャッシュの表示を続け、「前回の一覧を表示中」のバナーを出す。キャッシュがない場合は、これまでどおり全面のスピナーかエラーを表示する。
- `thumbnailUrl`(Drive の thumbnailLink)は数時間で失効するため、キャッシュにある値ではサムネイルを取得できないことがある。最新の一覧に差し替わると VideoItem が変わるので、サムネイルを取り直す。

### サムネイルの取得

- 取得はカードのコンポジション(`produceState`)のスコープで行い、画面外へスクロールして破棄されたカードの取得はキャンセルする。高速にスクロールしたとき、画面外のダウンロードが表示中のサムネイルを待たせないようにするためである。
- `cacheDir/thumbs/<fileId>.webp` には一時ファイルに書いてから rename で置き、書き込み途中のファイルをほかの処理が読まないようにする。
- OkHttp の同一ホストへの同時リクエストの上限を 16 に上げる(既定は 5)。サムネイルと一覧の取得が同じ www.googleapis.com に集中するためである。

## 画面遷移

サインインの判定は `RootViewModel` が行い、サインイン後の遷移は `AppNavHost` が受け持つ。

```
起動
 │
 ├─ 未サインイン ─────────────► SignInScreen ── サインイン成功 ──┐
 │                                                              ▼
 ├─ サインイン済み・フォルダ未選択 ─────────────────► FolderPickerScreen
 │                                                              │ フォルダを決定
 └─ サインイン済み・フォルダ選択済み ──────────────► HomeScreen(下部タブ)
                                                     ├ 動画一覧     VideoListScreen
                                                     ├ プレイリスト PlaylistsScreen ──► PlaylistDetailScreen
                                                     └ 保存済み     SavedVideosScreen

HomeScreen / PlaylistDetailScreen ── カードをタップ ──► PlayerScreen
HomeScreen ── 設定 ──► SettingsScreen ── フォルダを変更 ──► FolderPickerScreen
```

## 使用ライブラリ

| 用途 | ライブラリ | 備考 |
|---|---|---|
| UI | Jetpack Compose (BOM) + Material3 | |
| ナビゲーション | navigation-compose | |
| プレイヤー | Media3 ExoPlayer / media3-session / media3-ui | 通知とロック画面は MediaSessionService に任せる |
| 認証 | play-services-auth (Identity.AuthorizationClient) + credentials | |
| HTTP | OkHttp | Drive の REST API を直接呼ぶ |
| JSON | kotlinx-serialization-json | files.list のレスポンスと一覧のキャッシュ |
| 画像 | Coil (coil-compose) | webp に対応 |
| 永続化 | Room(キャッシュのメタデータ、プレイリスト)、DataStore Preferences(設定) | |
| テスト | JUnit4 + kotlinx-coroutines-test | |
: 使用ライブラリ

Google Drive の公式 Java クライアント(google-api-services-drive)は依存が重く Android に向かないため使わない。必要な 3 つのエンドポイント(`files.list`、`files.get?alt=media`、サムネイルの URL)を OkHttp で直接呼ぶ。

## 認証フロー

```
起動
 │
 ▼
AuthorizationClient.authorize(scopes=[drive.readonly])   … UI なしで試す
 │
 ├─ 成功(許可済み)→ accessToken を取得 → サイレントにメイン画面へ
 │
 └─ pendingIntent あり(未許可、または再同意が必要)
     │  ※初回はここに入る → SignInScreen に「Google でサインイン」ボタンを表示
     ▼
   ボタンを押す → PendingIntent を起動(アカウント選択と同意画面)
     │
     ├─ 許可 → accessToken を取得 → フォルダ選択か一覧へ
     └─ 拒否 → SignInScreen にエラーと再試行の導線

API 呼び出しが 401 → トークンを破棄 → authorize() をやり直す
  → サイレントに成功すれば透過的に再試行、失敗すれば SignInScreen へ誘導
```

- AuthorizationClient は Google Cloud Console に登録した「Android 用 OAuth クライアント ID(パッケージ名 + SHA-1)」で動くため、クライアント ID をコードに書く必要がない。
- Credential Manager(GetGoogleIdOption)で ID トークンを得るには、Web アプリケーション用のクライアント ID(serverClientId)が必須である。このアプリはサーバーを持たず ID トークンも要らないため、`local.properties` に `google.webClientId` がある場合だけ Credential Manager でアカウントを確認し、ない場合は AuthorizationClient だけで完結させる(→ [技術判断の記録](decisions.md))。
- Google Cloud 側の設定と、その確認・整理の手順は [Google Cloud のリソースの確認と整理](../how-to/google-cloud-resources.md) にある。

## 設定値の注入と秘密情報

- `local.properties`(.gitignore で除外済み)に設定値を書き、`app/build.gradle.kts` が読み込んで `BuildConfig` のフィールドとして注入する。
  - `google.webClientId`(任意。未設定なら空文字)
- OAuth の Android クライアント ID はパッケージ名 + SHA-1 で解決されるため、アプリの中には持たない。

## 再生とキャッシュ

タップしてから再生が始まるまで待たせないため、Media3 のキャッシュの仕組みを使って、ストリーミング再生とバックグラウンド保存を同時に行う。

```
ExoPlayer ──► CacheDataSource ──┬─ キャッシュにある → SimpleCache(files/video_cache/)から読む
                                └─ ない → 認証付き HTTP(alt=media)から読み、読んだ分を書き込む
バックグラウンド保存: CacheWriter が同じ SimpleCache のキー(fileId)の未取得の範囲だけを追加で取得
```

- 再生で受信したバイト列はそのままキャッシュに書かれるため、再生と保存で同じデータを二度取得しない。
- 認証は、`ResolvingDataSource` が open のたびに Authorization ヘッダーを付けて行う。
- 「キャッシュ済み(全体の保存が完了)」は Room の `cached_videos`(`fileId(PK) / fileName / sizeBytes / lastPlayedAt / downloadedAt`)で管理する。行があれば、完全にダウンロード済みである。
- サムネイルは `context.cacheDir/thumbs/<fileId>.webp` に置く。
- LRU による削除の判定(純粋なロジックの `CacheEvictionPolicy`。ユニットテストの対象)は、完全に保存済みのファイル(Room の行)に対して行う。
  1. 起動時に、`lastPlayedAt` が保持期間より古いものを削除する
  2. 合計サイズが上限を超えたら、`lastPlayedAt` の古い順に、上限を下回るまで削除する
  3. 再生中の fileId は常に除く
- 部分キャッシュ(視聴の途中でやめた残り)は、SimpleCache の `LeastRecentlyUsedCacheEvictor`(上限は設定値)が自動で管理する。完全に保存したファイルと部分キャッシュを、別々の仕組みで管理する二段構えである。

## ダウンロードのネットワーク制約

- ダウンロードを始める前に `ConnectivityManager` で判定し、モバイル回線の許可が OFF で Wi-Fi でない(ほぼ従量制のネットワーク)ときは、ダイアログでブロックする。
- 判定には `NetworkCapabilities.NET_CAPABILITY_NOT_METERED` を使う。テザリングなども含めて「従量制かどうか」で判断するほうが、実際の使い方に合うためである。
