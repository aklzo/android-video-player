---
title: Google Cloud プロジェクトの削除と再作成
type: how-to
status: 現行
updated: 2026-11-03
audience: このアプリの Google Cloud プロジェクトを管理する開発者
summary: アプリ用の Google Cloud プロジェクトを削除する手順と、もう一度使うときに新しく作る手順。削除は gcloud CLI だけで完結し、作り直しでは OAuth 同意画面と Android 用 OAuth クライアントをコンソールで作る。プロジェクト ID や証明書のフィンガープリントのような環境ごとの値はこの文書に残さず、作業のときに調べる。
---

## 作業の前に

削除は `gcloud projects delete` だけで完結します。作り直すときは、プロジェクトの作成と Drive API の有効化は gcloud でできますが、OAuth 同意画面と Android 用 OAuth クライアント ID は Google Cloud コンソールで作る必要があります。

| 作業 | gcloud CLI | コンソール |
|---|---|---|
| プロジェクトの削除と復元 | できる | 不要 |
| プロジェクトの作成、課金の紐付け、API の有効化 | できる | 不要 |
| OAuth 同意画面(アプリ名、対象、テストユーザー、スコープ) | できない | 必要 |
| Android 用 OAuth クライアント ID(パッケージ名と SHA-1) | できない | 必要 |
: gcloud CLI でできる作業

> [!NOTE]
> gcloud 591.0.0 のヘルプで確かめました(2026-11-03)。`gcloud iam oauth-clients create` のクライアントの種類は `confidential-client` と `public-client` だけで、Android のパッケージ名や SHA-1 を指定するオプションがありません。`gcloud alpha iap oauth-brands` は IAP 用で、その API は 2026-03-19 に停止しています。

削除すると OAuth クライアントも消えるので、アプリはサインインできなくなります。認証に失敗するとサインイン画面から先へ進まないため、保存済みの動画も開けません(`app/src/main/java/com/aklzo/videoplayer/RootViewModel.kt`)。

削除したプロジェクトは 30 日間は元に戻せ、そのあと完全に削除されます。完全に削除されたあとは同じプロジェクト ID を使えないので、もう一度使うときは新しいプロジェクトを作ります。作り直しに必要な設定はリポジトリにある値と作業のときに調べる値だけなので、削除の前に控えておくものはありません。アプリのコードと `local.properties` は変えなくてよく、クライアント ID はパッケージ名と SHA-1 で解決されます([設計書](../design/design.md))。

## 作業で使う値の調べ方

| 値 | 調べ方 |
|---|---|
| プロジェクト ID | `gcloud projects list --filter="name:android-video-player" --format="value(projectId)"` |
| パッケージ名 | `com.aklzo.videoplayer`(`app/build.gradle.kts` の `applicationId`) |
| スコープ | `https://www.googleapis.com/auth/drive.readonly`([仕様書](../spec/spec.md)) |
| 署名の SHA-1 | 下のコマンド |
: 作業で使う値

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

アプリはデバッグ用の鍵(`~/.android/debug.keystore`)で署名しています。このファイルがない環境では、デバッグビルドのときに Android のビルドツールが新しく作ります。その場合は SHA-1 が変わるので、作り直すときは新しい SHA-1 を登録します。

> [!NOTE]
> 鍵が変わると、端末に入っているアプリへの上書きインストールは署名の違いで失敗します。いったんアンインストールすると、端末に保存した動画、プレイリスト、設定も消えます。

## プロジェクトの削除

> [!CAUTION]
> 削除するとアプリはすぐにサインインできなくなり、30 日が過ぎると元に戻せません。

### 1. 対象のプロジェクトを確かめる

gcloud の既定のプロジェクトは別のアプリのものになっていることがあるので、ID を必ず明示します。`PROJECT_ID` は、節「作業で使う値の調べ方」で調べたプロジェクト ID に置き換えてください。

```bash
gcloud projects describe PROJECT_ID --format="value(name,lifecycleState)"
```

> [!TIP]
> `android-video-player` と `ACTIVE` が表示されれば、対象は正しいです。

### 2. プロジェクトを削除する

確認を求められたら `y` と答えます。

```bash
gcloud projects delete PROJECT_ID
```

> [!TIP]
> 手順 1 のコマンドで `DELETE_REQUESTED` が表示されれば、削除の受付は完了です。30 日後に完全に削除されます。

## 30 日以内の復元

削除を取り消すと、元のプロジェクト ID と OAuth の設定がそのまま戻ります。課金は自動では戻らないので、課金を使う場合だけ紐付け直します。すべてのサービスが使えるようになるまで、最大で 36 時間かかることがあります。

```bash
gcloud projects undelete PROJECT_ID
# 課金を使う場合だけ。BILLING_ACCOUNT_ID は gcloud billing accounts list で確かめる
gcloud billing projects link PROJECT_ID --billing-account=BILLING_ACCOUNT_ID
```

## プロジェクトの新規作成

もう一度使うときに、新しいプロジェクトを作る手順です。`NEW_PROJECT_ID` は新しいプロジェクト ID に置き換えます(削除したプロジェクトの ID は使えません)。

### 1. プロジェクトを作る

```bash
gcloud projects create NEW_PROJECT_ID --name="android-video-player"
```

### 2. Drive API を有効にする

```bash
gcloud services enable drive.googleapis.com --project=NEW_PROJECT_ID
```

> [!NOTE]
> 推定: Drive API は課金を紐付けなくても有効にできると考えています。課金が必要というエラーが出たら、`gcloud billing projects link NEW_PROJECT_ID --billing-account=BILLING_ACCOUNT_ID` で紐付けてから、もう一度実行してください。

### 3. OAuth 同意画面を作る

コンソールの `https://console.cloud.google.com/apis/credentials/consent?project=NEW_PROJECT_ID` を開いて設定します。

- アプリ名は自由に決め、サポートメールには自分のアカウントを選ぶ
- 対象は外部向けにし、公開ステータスはテストのままにする
- テストユーザーに、アプリでサインインする Google アカウントを足す
- スコープに `https://www.googleapis.com/auth/drive.readonly` を足す

### 4. Android 用 OAuth クライアント ID を作る

同じコンソールで OAuth クライアント ID を作ります。アプリケーションの種類は Android にし、パッケージ名に `com.aklzo.videoplayer`、SHA-1 に節「作業で使う値の調べ方」で調べた値を入れてください。

> [!NOTE]
> 未確認: 古いプロジェクトが削除待ちの 30 日のあいだは、同じパッケージ名と SHA-1 のクライアントを新しいプロジェクトに作れない可能性があります。作れないときは、古いプロジェクトを復元して使うか、完全に削除されるまで待ってください。

### 5. アプリで確かめる

```bash
./gradlew installDebug
```

> [!TIP]
> アプリを起動してサインインし、フォルダを選んで動画の一覧が表示されれば、作り直しは完了です。

## 完了の確認

削除した場合は、`gcloud projects describe PROJECT_ID --format="value(lifecycleState)"` が `DELETE_REQUESTED` を表示すれば完了です。作り直した場合は、アプリでサインインでき、動画の一覧が表示されれば完了です。
