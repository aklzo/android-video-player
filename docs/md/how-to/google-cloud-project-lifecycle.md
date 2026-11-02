---
title: Google Cloud プロジェクトの削除と再作成
type: how-to
status: 現行
updated: 2026-11-03
audience: このアプリの Google Cloud プロジェクトを管理する開発者
summary: アプリ用の Google Cloud プロジェクト android-video-player-502112 を削除する手順と、あとで同じ構成に作り直す手順。削除は gcloud CLI だけで完結するが、作り直しでは OAuth 同意画面と Android 用 OAuth クライアントをコンソールで作る必要がある。
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

削除したプロジェクトは 30 日間は元に戻せ、そのあと完全に削除されます。完全に削除されたあとは、プロジェクト ID `android-video-player-502112` を再び使えません。作り直すときは新しい ID でプロジェクトを作ります。アプリのコードと `local.properties` は変えなくてよく、クライアント ID はパッケージ名と SHA-1 で解決されます([設計書](../design/design.md))。

## 削除前の構成

2026-11-03 に gcloud とリポジトリで確かめた構成です。作り直すときは、この表のとおりに設定します。

| 項目 | 値 |
|---|---|
| プロジェクト ID | `android-video-player-502112`(名前 android-video-player、番号 206236835316、2026-07-11 作成) |
| アプリが使う API | `drive.googleapis.com`。ほかに有効な API は、新しいプロジェクトで最初から有効になっているもの |
| ほかのリソース | サービスアカウント、Cloud Storage のバケット、Cloud Run、BigQuery のデータセットはない |
| 権限 | owner はアプリの開発者のアカウントだけ |
| 課金 | 請求先アカウントに紐付いている |
| OAuth 同意画面 | 外部向けのテストモード。スコープは `https://www.googleapis.com/auth/drive.readonly`。テストユーザーはアプリでサインインする Google アカウント |
| Android 用 OAuth クライアント | パッケージ名 `com.aklzo.videoplayer`、SHA-1 `80:AD:AB:FE:2E:F9:E9:3A:84:57:03:7F:9E:AA:99:DF:0B:1D:34:8A` |
| 署名に使うキーストア | `~/.android/debug.keystore`(`app/build.gradle.kts` に signingConfigs はなく、デバッグ用の鍵で署名する。有効期限 2053-11-26) |
| Web 用 OAuth クライアント | 使っていない(`local.properties` の `google.webClientId` はコメントアウト) |
: 削除前の構成(2026-11-03 時点)

## 削除前の控え

### 1. コンソールの設定を控える

OAuth 同意画面とクライアントの設定は gcloud では読み出せません。コンソールの OAuth 同意画面(`https://console.cloud.google.com/apis/credentials/consent?project=android-video-player-502112`)を開き、アプリ名、サポートメール、テストユーザー、スコープ、Android クライアントの名前とパッケージ名と SHA-1 を確かめてください。上の表と違うところがあれば、この文書の表を直してからプロジェクトを削除します。

### 2. デバッグ用のキーストアを保管する

SHA-1 は `~/.android/debug.keystore` から決まります。PC を替えたりファイルを消したりすると SHA-1 が変わり、作り直すときに新しい SHA-1 を登録することになります。同じ SHA-1 を使い続けたい場合は、このファイルをリポジトリの外に保管してください。

いまの SHA-1 は次のコマンドで確かめられます。

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

## プロジェクトの削除

> [!CAUTION]
> 削除するとアプリはすぐにサインインできなくなり、30 日が過ぎると元に戻せません。作業の前に、節「削除前の控え」を済ませてください。

### 1. 対象のプロジェクトを確かめる

gcloud の既定のプロジェクトは別のアプリのものになっていることがあるので、ID を必ず明示します。

```bash
gcloud projects describe android-video-player-502112 --format="value(name,lifecycleState)"
```

> [!TIP]
> `android-video-player` と `ACTIVE` が表示されれば、対象は正しいです。

### 2. プロジェクトを削除する

確認を求められたら `y` と答えます。

```bash
gcloud projects delete android-video-player-502112
```

> [!TIP]
> 手順 1 のコマンドで `DELETE_REQUESTED` が表示されれば、削除の受付は完了です。30 日後に完全に削除されます。

## 30 日以内の復元

削除を取り消すと、元のプロジェクト ID と OAuth の設定がそのまま戻ります。課金は自動では戻らないので、課金を使う場合だけ紐付け直します。すべてのサービスが使えるようになるまで、最大で 36 時間かかることがあります。

```bash
gcloud projects undelete android-video-player-502112
# 課金を使う場合だけ。BILLING_ACCOUNT_ID は gcloud billing accounts list で確かめる
gcloud billing projects link android-video-player-502112 --billing-account=BILLING_ACCOUNT_ID
```

## プロジェクトの作り直し

完全に削除されたあとで、同じ構成に作り直す手順です。`NEW_PROJECT_ID` は新しいプロジェクト ID に置き換えます(元の ID は使えません)。

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

コンソールの `https://console.cloud.google.com/apis/credentials/consent?project=NEW_PROJECT_ID` を開き、節「削除前の構成」の表のとおりに設定します。

- 対象は外部向けにし、公開ステータスはテストのままにする
- テストユーザーに、アプリでサインインする Google アカウントを足す
- スコープに `https://www.googleapis.com/auth/drive.readonly` を足す

### 4. Android 用 OAuth クライアント ID を作る

同じコンソールで OAuth クライアント ID を作ります。アプリケーションの種類は Android にし、パッケージ名に `com.aklzo.videoplayer`、SHA-1 にキーストアの SHA-1 を入れてください。

> [!NOTE]
> 未確認: 古いプロジェクトが削除待ちの 30 日のあいだは、同じパッケージ名と SHA-1 のクライアントを新しいプロジェクトに作れない可能性があります。作れないときは、古いプロジェクトを復元して使うか、完全に削除されるまで待ってください。

### 5. アプリで確かめる

```bash
./gradlew installDebug
```

> [!TIP]
> アプリを起動してサインインし、フォルダを選んで動画の一覧が表示されれば、作り直しは完了です。

## 完了の確認

削除した場合は、`gcloud projects describe android-video-player-502112 --format="value(lifecycleState)"` が `DELETE_REQUESTED` を表示すれば完了です。作り直した場合は、アプリでサインインでき、動画の一覧が表示されれば完了です。
