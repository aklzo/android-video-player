# android-video-player

Google Drive 上の動画(.mp4)をサムネイル付きグリッドから選んで再生する、個人用 Android アプリ。
タップで即ストリーミング再生を開始し、再生と並行して動画全体を端末に保存する
(二重ダウンロードなし)。保存済みの動画はオフラインでも再生できる。

## 主な機能

- **Google 認証**: AuthorizationClient による `drive.readonly` スコープの OAuth。
  クライアント ID をコードに持たない(パッケージ名+SHA-1 で解決)。初回以降はサイレント認証
- **対象フォルダ**: Drive をブラウズして複数フォルダを選択・追加・削除。
  サブフォルダは再帰的に列挙(1,000 超のサブフォルダでも数リクエスト)
- **一覧**: YouTube 風グリッド。月ごと(`yyyy/MM`)にグルーピング、
  上位ディレクトリ名(shorts / streams / videos 等)によるフィルターチップ、
  同名 `.webp` サムネイル(なければ Drive の thumbnailLink)
- **再生**: Media3 (ExoPlayer)。横型は上部プレイヤー+概要欄(日付・フォルダパス・
  サイズ・保存状態)、縦型(shorts)は全画面+ボトムシート詳細。
  ストリーミングで受信したデータはそのままキャッシュに書かれ、
  CacheWriter が残りをバックグラウンドで取得して全体を保存する
- **バックグラウンド再生**: MediaSessionService。通知(再生/一時停止/停止)・ロック画面対応
- **キャッシュ管理**: Room でメタデータ管理。容量上限(5〜50GB)と保持期間(7日〜無期限)
  による LRU 削除。再生中のファイルは削除対象外。設定画面に使用量表示・全削除
- **プレイリスト**: カード長押しで追加。オフラインでも表示可能(メタデータ非正規化)
- **通信量配慮**: 既定では非従量制ネットワーク(Wi-Fi 等)のみダウンロード。設定で変更可

## 技術スタック

| 領域 | 採用 |
|---|---|
| UI | Kotlin / Jetpack Compose (Material3) / Navigation Compose |
| 再生 | Media3 (ExoPlayer / MediaSessionService / SimpleCache + CacheDataSource + CacheWriter) |
| 認証 | Play Services AuthorizationClient(+ 任意で Credential Manager) |
| Drive API | OkHttp + kotlinx-serialization(公式クライアント不使用、files.list / alt=media 直叩き) |
| 永続化 | Room(キャッシュ・プレイリスト)/ DataStore Preferences(設定) |
| 画像 | Coil |
| DI | 手書きコンテナ(AppContainer) |

minSdk 26 / targetSdk 34 / JDK 17 / Gradle 8.9(バージョンカタログ管理)

## セットアップ

1. **Google Cloud Console**(済みであればスキップ)
   - Drive API を有効化
   - OAuth 同意画面(テストモードで可)に `drive.readonly` スコープとテストユーザーを登録
   - Android 用 OAuth クライアント ID を作成し、パッケージ名 `com.aklzo.videoplayer` と
     ビルドに使う keystore の SHA-1 を登録(デバッグは `~/.android/debug.keystore`)
2. **local.properties**(git 管理外)
   ```properties
   sdk.dir=/path/to/android-sdk
   # 任意。設定すると Credential Manager のアカウント選択が有効になる
   #google.webClientId=xxxxx.apps.googleusercontent.com
   ```
3. **ビルド・インストール**
   ```bash
   ./gradlew testDebugUnitTest   # ユニットテスト
   ./gradlew installDebug        # 実機へインストール(adb 接続)
   ```

## ワイヤレスデバッグへの再接続(使用頻度高)

端末を持ち出す等で Wi-Fi が切れると接続は解除され、**ワイヤレスデバッグの
ポート番号は有効化し直すたびに変わる**。アプリを更新するときは以下の手順で再接続する。

1. 端末側でポートを確認する:
   **設定 → 開発者向けオプション → ワイヤレスデバッグ**(タップして開く)
   → 画面に表示される「**IP アドレスとポート**」を見る(例: `192.168.11.6:39703`)
2. PC 側で接続する:
   ```bash
   adb connect 192.168.11.6:39703   # ← 手順1で確認した IP:ポート
   adb devices                       # 「device」と出れば接続完了
   ```
3. あとは通常どおり `./gradlew installDebug` で更新できる。

補足:

- `connected to ...` と出ても `adb devices` で `offline` の場合は、端末の
  ワイヤレスデバッグを一度 OFF→ON してからやり直す
- ポートを画面で見ずに探したい場合は mDNS で発見できることがある:
  ```bash
  adb mdns services   # _adb-tls-connect._tcp の IP:ポートに adb connect する
  ```
- 「このデバイスをペア設定する」を求められるのは初回(または認証情報が消えたとき)のみ。
  その場合は端末の「ペア設定コード によるペア設定」に表示される IP:ポート(接続用とは別)へ
  `adb pair <IP:ポート>` を実行し、6 桁コードを入力してから手順 2 を行う
- IP アドレス自体も Wi-Fi ネットワークが変わると変化する点に注意

## プロジェクト構成

```
app/src/main/java/com/aklzo/videoplayer/
├── auth/       認証(AuthorizationClient ラッパ、TokenProvider)
├── data/
│   ├── source/    「動画ソース」抽象化(VideoSource)+ Drive 実装
│   ├── cache/     ストリーミングキャッシュ・Room・LRU・ネットワーク判定
│   ├── playlist/  プレイリスト(Room)
│   └── settings/  設定(DataStore)
├── domain/     純粋ロジック(月グルーピング・サムネ紐付け・ツリー判定・LRU 判定・分類)
├── playback/   MediaSessionService(バックグラウンド再生)
└── ui/         画面(ホームタブ・一覧・保存済み・プレイリスト・再生・設定・フォルダ選択)
```

## ドキュメント(docs/)

`docs/md/` の Markdown が原本、`docs/html/` はそこから生成した閲覧用のミラー。
人が読むときは `docs/html/index.html` をブラウザで開く。

- `docs/md/spec/` — 機能仕様
- `docs/md/design/` — 設計書(モジュール構成・認証フロー・キャッシュ設計)と
  技術判断の記録(decisions.md)。実装中の判断はすべてここに理由付きで残している
- `docs/md/how-to/` — 手順書(Google Cloud のリソースの確認と整理など)
- `docs/md/troubleshooting/` — ハマった問題と解決方法
- `docs/md/knowledge/` — 実装全体の人間向け解説

Markdown を直したら `python3 scripts/build_docs.py` で HTML を再生成する
(書き方と手順は `docs/md/index.md`)。

## 注意事項

- 個人利用前提。OAuth 同意画面はテストモード運用(`drive.readonly` は制限付きスコープの
  ため、本番モード化には Google の検証が必要)
- 書き込み系 Drive API は使用しない(読み取り専用)
