---
title: Gradle がない環境での Wrapper の生成
type: how-to
status: 解決済み
updated: 2026-11-03
summary: WSL2 に Gradle が入っておらず、gradle wrapper で Gradle Wrapper を作れなかった。Gradle の配布物を一時的に展開し、それで Wrapper を生成して解決した。
---

## 状況

WSL2 の CLI 環境に Gradle がインストールされておらず、`gradle wrapper` で Gradle Wrapper を生成できなかった。新しいプロジェクトだったため、使える gradlew もなかった。

## 解決方法

### 1. Gradle の配布物をダウンロードして展開する

一時ディレクトリで実行する。

```bash
curl -sL -o gradle-8.9-bin.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q gradle-8.9-bin.zip
```

### 2. 展開した gradle で Wrapper を生成する

```bash
./gradle-8.9/bin/gradle wrapper --gradle-version 8.9
```

### 3. 以降は gradlew を使う

これからは `./gradlew` を使う。一時ディレクトリの gradle はもう要らない。

## 補足

- `gradle/wrapper/gradle-wrapper.jar` はリポジトリにコミットする(Wrapper の慣例)。
- JDK 17、AGP 8.5.2、Gradle 8.9 の組み合わせで動くことを確認済み。
