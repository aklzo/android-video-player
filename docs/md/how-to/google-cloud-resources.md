---
title: Google Cloud のリソースの確認と整理
type: how-to
status: 現行
updated: 2026-11-03
audience: このアプリの Google Cloud プロジェクトを管理する開発者
summary: このアプリが Google Cloud で使うのは Drive API と OAuth の設定だけで、Cloud Run などの実行環境は使っていません。この手順書では gcloud CLI を入れて実際のリソースを確かめ、アプリに要らないものだけを止めます。
---

## 作業の前に

このアプリはサーバーを持たず、端末から Drive API を直接呼びます(`app/src/main/java/com/aklzo/videoplayer/data/source/drive/OkHttpDriveApiClient.kt`)。リポジトリには Dockerfile や Cloud Build の設定、`run.app` の URL がなく、git の履歴にも出てきません(2026-11-03 に確認)。Cloud Run のサービスが見つかった場合は、このアプリとは別のものです。

アプリが使う Google Cloud の設定は次の表のとおりです。消すとアプリが動かなくなるものがあるので、整理の前に役割を確かめてください。

| リソース | アプリでの用途 | 消したときの影響 |
|---|---|---|
| Drive API の有効化 | 動画一覧の取得と再生 | 一覧の取得も再生もできなくなる |
| OAuth 同意画面(テストモード) | サインイン時の同意画面とテストユーザーの登録 | サインインできなくなる |
| Android 用 OAuth クライアント ID | パッケージ名 `com.aklzo.videoplayer` と署名の SHA-1 でアプリを識別する | サインインできなくなる |
| Web 用 OAuth クライアント ID(任意) | Credential Manager のアカウント選択。`local.properties` でコメントアウトしており、今は使っていない | 今は影響しない |
: アプリが使う Google Cloud の設定(README.md のセットアップ手順から)

> [!NOTE]
> 表はリポジトリのコードと README.md から読み取った内容です。2026-11-03 に gcloud で確かめたところ、アプリ用のプロジェクト(名前は android-video-player)には、表のほかにリソースはありませんでした。Cloud Run は別のアプリのプロジェクトにあり、このアプリとは関係しません。

この PC の WSL(Ubuntu 24.04)には、2026-11-03 の時点で gcloud CLI 591.0.0 が apt で入っています(`/usr/bin/gcloud`)。

## gcloud CLI のインストール

Ubuntu では apt で入れる方法をおすすめします。`sudo` が必要ですが、PATH を設定する必要がなく、以降の更新も `apt` でできます。`sudo` を使えない場合は、次の節「sudo を使わないインストール」の手順で入れてください。

### 1. 必要なパッケージを入れる

```bash
sudo apt-get update
sudo apt-get install ca-certificates gnupg curl
```

### 2. Google Cloud の署名鍵を登録する

```bash
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg
```

### 3. パッケージの取得元を追加する

> [!WARNING]
> このコマンドはファイルに行を追記します。2 回実行すると同じ取得元が二重に登録されるので、1 回だけ実行してください。

```bash
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
```

### 4. gcloud CLI を入れる

```bash
sudo apt-get update && sudo apt-get install google-cloud-cli
```

> [!TIP]
> `gcloud --version` でバージョンが表示されれば、インストールは終わりです。

## sudo を使わないインストール

アーカイブをホームディレクトリに展開して入れます。x86_64 版には Python が同梱されているので、別に用意する必要はありません。

### 1. アーカイブをダウンロードして展開する

```bash
cd ~
curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-linux-x86_64.tar.gz
tar -xf google-cloud-cli-linux-x86_64.tar.gz
```

### 2. インストールスクリプトを実行する

PATH への追加、コマンドの補完、利用統計の送信を順に聞かれます。PATH への追加には `Y` と答えてください。

```bash
./google-cloud-sdk/install.sh
```

### 3. 新しい端末を開く

PATH の変更は新しく開いた端末から有効になります。

> [!TIP]
> 新しい端末で `gcloud --version` がバージョンを表示すれば、インストールは終わりです。

## サインインとプロジェクトの選択

### 1. 初期設定を始める

WSL ではブラウザが自動で開かないことがあるため、`--console-only` を付けてコマンドラインで認可します。表示された URL を Windows のブラウザで開き、アプリを登録した Google アカウントで許可してから、表示されたコードを端末に貼り付けてください。続けてプロジェクトを選ぶよう聞かれたら、OAuth クライアント ID を作ったプロジェクトを選びます。

```bash
gcloud init --console-only
```

### 2. 設定を確かめる

```bash
gcloud config list
```

> [!TIP]
> `account` にサインインしたアカウント、`project` に選んだプロジェクトが表示されれば完了です。

## リソースの確認

> [!NOTE]
> この節のコマンドは、2026-11-03 に実行して確かめました。次の節の削除と変更のコマンドは gcloud のリファレンスに沿って書いたもので、実行していません。エラーになったときは `gcloud <コマンド> --help` で引数を確かめてください。

以下の `PROJECT_ID` は次の手順 1 で確かめたプロジェクト ID に置き換えて実行します。

### 1. プロジェクトの一覧を見る

```bash
gcloud projects list
```

Cloud Run のサービスは別のプロジェクトにある可能性があります。表示されたプロジェクトそれぞれについて、手順 2 から 4 を繰り返してください。

### 2. 有効な API を見る

```bash
gcloud services list --enabled --project PROJECT_ID
```

アプリ用のプロジェクトなら `drive.googleapis.com` が含まれています。

### 3. Cloud Run のサービスを探す

```bash
gcloud run services list --project PROJECT_ID
```

Cloud Run API を有効にするか聞かれたら `N` と答えてください。API が無効なプロジェクトには Cloud Run のサービスもありません。サービスが見つかったら、サービス名とリージョンを控えます。

### 4. 課金の状態を見る

```bash
gcloud billing projects describe PROJECT_ID
```

`billingEnabled: true` と表示されたら、そのプロジェクトは請求先アカウントに紐付いています。実際にかかった金額は Google Cloud コンソールの「お支払い」の「レポート」で確かめられます。

### 5. OAuth クライアント ID をコンソールで見る

OAuth クライアント ID は gcloud では一覧できないため、Google Cloud コンソールの「API とサービス」の「認証情報」で確かめます。パッケージ名が `com.aklzo.videoplayer` の Android クライアントがあれば、そのプロジェクトがアプリ用です。

## 要らないリソースの整理

見つかったリソースは次の表の基準で残すか止めるかを決めます。

| 見つかったもの | アプリへの影響 | 対応 |
|---|---|---|
| Cloud Run のサービスとそのコンテナイメージ | ない(アプリは使っていない) | 要らなければ削除する |
| アプリ用プロジェクトの Drive API と OAuth の設定 | 消すとサインイン、一覧、再生ができなくなる | アプリを使うあいだは残す |
| アプリ用プロジェクトの課金の紐付け | ないと考えている(下の注記) | 外してもよい |
: 見つかったリソースの扱い

> [!CAUTION]
> Cloud Run のサービスとコンテナイメージは、削除すると元に戻せません。削除したプロジェクトは 30 日後に完全に消え、それまでは戻せますが、そのあいだもアプリはサインインできません。削除の前に、名前とプロジェクト ID が正しいかを確かめてください。

### 1. Cloud Run のサービスを削除する

```bash
gcloud run services delete SERVICE_NAME --region REGION --project PROJECT_ID
```

### 2. コンテナイメージの置き場所を削除する

ソースからデプロイしたサービスのイメージは Artifact Registry に残り、保管の料金がかかることがあります。一覧を見て、要らないリポジトリだけを削除してください。

```bash
gcloud artifacts repositories list --project PROJECT_ID
gcloud artifacts repositories delete REPOSITORY --location LOCATION --project PROJECT_ID
```

### 3. 課金の紐付けを外す

アプリ用のプロジェクトに課金を残したくない場合に行います。紐付けを外すと、そのプロジェクトで課金が必要なリソースは止まります。

```bash
gcloud billing projects unlink PROJECT_ID
```

> [!NOTE]
> 推定: Drive API と OAuth は課金なしで使えるため、紐付けを外してもアプリには影響しないと考えています。外したあとで、アプリのサインインと一覧の表示を確かめてください。

### 4. プロジェクトを削除する

アプリをもう使わない場合だけ行います。控えておく設定と作り直しの手順は「[Google Cloud プロジェクトの削除と再作成](google-cloud-project-lifecycle.md)」にあります。削除したプロジェクトは 30 日後に完全に消えます。それまでは `gcloud projects undelete PROJECT_ID` で戻せます。

```bash
gcloud projects delete PROJECT_ID
```

## 完了の確認

次の 3 点を確かめたら整理は終わりです。

- 残すと決めたプロジェクト以外で、`gcloud run services list` がサービスを表示しない
- `gcloud billing projects describe PROJECT_ID` の `billingEnabled` が決めた状態になっている
- アプリでサインインでき、動画一覧が表示される
