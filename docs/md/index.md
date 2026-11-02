---
title: android-video-player のドキュメント
type: service-docs
status: 現行
updated: 2026-11-03
summary: 仕様、設計、技術判断の記録、手順書、解説の一覧と、文書の更新のしかた。docs/md/ の Markdown が原本で、docs/html/ はそこから生成した人向けの閲覧用ミラーである。
---

## 文書の一覧

| 文書 | 内容 | 更新するとき |
|---|---|---|
| [仕様書](spec/spec.md) | 機能の要件 | 機能を足したり変えたりしたとき |
| [設計書](design/design.md) | モジュール構成、認証、一覧の取得、再生とキャッシュの設計 | 構造を変えたとき |
| [技術判断の記録](design/decisions.md) | 実装中に迷った点の判断と理由 | 判断をしたとき(新しいものを上に足す) |
| [実装の解説](knowledge/overview.md) | 実装全体を人向けに説明した文書 | 主要な機能の実装を終えたとき |
| [Google Cloud のリソースの確認と整理](how-to/google-cloud-resources.md) | gcloud CLI の導入と、Google Cloud のリソースの確認と整理の手順 | Google Cloud の構成を変えたとき |
| [Gradle がない環境での Wrapper の生成](troubleshooting/gradle-wrapper-bootstrap.md) | Gradle を入れずに Gradle Wrapper を作る手順 | 同じ問題で手順が変わったとき |
| [ルート引数の空白が「+」になる問題](troubleshooting/navigation-url-encoding.md) | Navigation Compose のルート引数のエンコードの直し方 | 同じ問題で手順が変わったとき |
: docs/md/ の文書(HTML では、リンクは対応するページを開く)

ビルドのエラーや環境の問題を解決したら、手順を docs/md/troubleshooting/ に足す。

## md と html の役割

docs/md/ の Markdown が原本で、生成 AI はこれを読んで作業し、変更もここに書く。docs/html/ は人が確かめるための閲覧用ミラーで、`scripts/build_docs.py` が docs/md/ から生成する。HTML は直接編集しない。直接直しても、次の生成で上書きされる。

HTML の見た目は document-design-system の field-notes テーマで、CSS は `scripts/docs-shell.html` に書き出してある。プラグインがない環境でも再生成できる。

## 文書を更新する手順

1. docs/md/ の Markdown を編集する。文書を足したときは、この文書の「文書の一覧」の表にも足す。
2. `python3 scripts/build_docs.py` を実行して HTML を生成する。
3. `python3 scripts/build_docs.py --check` を実行し、何も表示されずに終わることを確かめる。
4. Markdown と HTML を同じコミットに入れる。

> [!TIP]
> `--check` は、HTML が原本と一致しないときと、一覧の表に載っていない文書があるときに、その内容を表示して終了コード 1 で終わる。

## 書き方

生成スクリプトは次の書き方だけに対応している。対応していない書き方は HTML に正しく出ないので使わない。

### front matter

文書の先頭に `---` で囲んで書く。

| キー | 必須 | 内容 |
|---|---|---|
| `title` | 必須 | 文書の題 |
| `type` | 必須 | `requirements`、`architecture`、`adr`、`how-to`、`explanation`、`service-docs` のどれか。ほかの値なら `pattern` と `label` も書く |
| `status` | 必須 | 文書の状態(現行、草案など) |
| `updated` | 必須 | 最後に内容を更新した日(YYYY-MM-DD) |
| `summary` | 必須 | 文書の要点を 1〜2 文で。HTML では題の下に出る |
| `audience` | 任意 | 想定する読み手 |
| `pattern` / `label` | 任意 | document-design-system の pattern と、題の上に出す種類の名前 |
: front matter のキー

### 本文の記法

| 書きたいもの | 記法 | 補足 |
|---|---|---|
| 節 | `## 見出し` | HTML では番号付きの節になる。題は front matter に書き、`#` は使わない |
| 小見出し | `###`、`####` | |
| 強調・コード・リンク | `**太字**`、`` `コード` ``、`[文字](パス)` | 別の .md へのリンクは HTML で .html へのリンクになる |
| 箇条書き | `- ` か `1. ` | 2 文字以上の字下げで入れ子にできる。項目のあいだに空行を入れない |
| 表 | GitHub 形式の表 | 表の直後の行に `: キャプション` を書くと番号付きのキャプションになる |
| コード | ` ``` ` で囲み、言語名を付ける | 言語名は HTML でラベルになる |
| 要点 | `> [!IMPORTANT]` | 技術判断の記録(`adr`)では「判断」の欄になる |
| 根拠・注記 | `> [!NOTE]` | 推定や未確認の事項は、ここに「推定:」と書いて区別する |
| 結果の確認 | `> [!TIP]` | 手順書と解説では手順の結果、それ以外では要点として出る |
| 危険な操作 | `> [!WARNING]`、`> [!CAUTION]` | 元に戻せない操作の前に置く |
| 手順書の手順 | `### 1. 手順の名前` | `how-to` では番号付きの手順の欄にまとまる |
: 生成スクリプトが対応する記法

画像、斜体、HTML の直書き、箇条書きの中のコードブロック、Mermaid の図には対応していない。図は ` ``` ` で囲んだテキストで描く。
