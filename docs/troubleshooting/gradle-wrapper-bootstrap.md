# Gradle 未インストール環境での Wrapper 生成

## 状況
WSL2 の CLI 環境に Gradle がインストールされておらず、`gradle wrapper` で
Gradle Wrapper を生成できなかった(新規プロジェクトのため既存の gradlew も無い)。

## 解決方法
1. Gradle 配布物を一時ディレクトリにダウンロードして展開する
   ```bash
   curl -sL -o gradle-8.9-bin.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
   unzip -q gradle-8.9-bin.zip
   ```
2. 展開した gradle でプロジェクトに Wrapper を生成する
   ```bash
   ./gradle-8.9/bin/gradle wrapper --gradle-version 8.9
   ```
3. 以降は `./gradlew` を使う。一時ディレクトリの gradle は不要。

## 補足
- `gradle/wrapper/gradle-wrapper.jar` はリポジトリにコミットする(Wrapper の流儀)。
- JDK 17 + AGP 8.5.2 + Gradle 8.9 の組み合わせで動作確認済み。
