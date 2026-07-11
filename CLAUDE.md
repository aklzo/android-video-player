# プロジェクト概要
Google Drive 上の動画(.mp4)をサムネイル(.webp)付き一覧から再生する Android アプリ。
動画はアプリ専用ストレージに一時ダウンロードして再生し、キャッシュとして LRU 管理する。
バックグラウンド再生対応。Kotlin + Jetpack Compose + Jetpack Media3 + Google Drive API。
リポジトリ: android-video-player / パッケージ: com.aklzo.videoplayer

# 開発ルール
- テスト可能な小さい単位で実装し、機能追加ごとにビルドが通る状態を保つ
- 機能・修正の意味のある単位ごとに git commit を切る(ビルドが通る状態でコミットする)
- コミット後は git push でリモートに反映する(SSH は ssh-agent 設定済みでパスフレーズ不要)
- git push --force / --force-with-lease は禁止。push が失敗した場合は原因を docs/troubleshooting/ に記録し、履歴操作はせず人間に報告する
- 可読性・保守性を優先し、過度な抽象化はしない
- UI は Jetpack Compose、プレイヤーは Media3 (ExoPlayer + MediaSessionService)
- Drive アクセスは drive.readonly スコープの範囲に限定する。書き込み系 API は使用しない
- OAuth クライアント ID などの秘密情報はコミットしない(local.properties 等で管理し .gitignore に含める)
- 実装前に docs/design/ の設計書を確認し、乖離する場合は先に設計書を更新する
- 実装中の不明点・判断は作業を止めず、docs/design/decisions.md に判断理由を記録して進める

# よく使うコマンド
- ビルド+インストール: ./gradlew installDebug
- 起動: adb shell am start -n com.aklzo.videoplayer/.MainActivity
- ログ: adb logcat --pid=$(adb shell pidof -s com.aklzo.videoplayer)
- クラッシュ確認: adb logcat -b crash -d
- スクリーンショット: adb exec-out screencap -p > /tmp/screen.png
- キャッシュ確認: adb shell run-as com.aklzo.videoplayer ls -la files/video_cache/

# ドキュメント構成(docs/)
- docs/spec/       仕様書。機能要件を追加・変更したとき更新する
- docs/design/     設計書・技術判断の記録。実装前に参照、構造変更時に更新
- docs/troubleshooting/  詰まった問題と解決方法。ビルドエラーや環境問題を解決したら追記
- docs/knowledge/  人間向け解説ドキュメント(HTML)。主要機能の実装完了時に生成・更新

