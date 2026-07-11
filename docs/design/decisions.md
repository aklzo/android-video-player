# 技術判断の記録(decisions)

実装中に迷った点と、その判断・理由を記録する。新しいものを上に追記する。

---

## 2026-07-11: Credential Manager の扱い

**判断**: 認証の主経路は AuthorizationClient とし、Credential Manager(GetGoogleIdOption)は
`local.properties` に `google.webClientId` がある場合のみ使う。

**理由**: Credential Manager で Google アカウントの ID トークンを得るには
Web アプリケーション用クライアント ID(serverClientId)が必須だが、
Google Cloud Console には Android 用クライアント ID のみ登録済みと聞いている。
一方 AuthorizationClient.authorize() はパッケージ名+SHA-1 で解決されるため追加設定不要で、
アカウント選択 UI・同意画面・アクセストークン取得・サイレント再認証のすべてを満たす。
本アプリはバックエンドを持たず ID トークンの用途がないため、機能要件は
AuthorizationClient のみで完全に満たせる。Web クライアント ID が用意されたら
`local.properties` に設定するだけで Credential Manager 経由のサインイン UI が有効になる。

## 2026-07-11: Drive API クライアントを自作(OkHttp 直叩き)

**判断**: google-api-services-drive(公式 Java クライアント)を使わず、
OkHttp + kotlinx-serialization で `files.list` / `files.get?alt=media` を直接呼ぶ。

**理由**: 公式クライアントは Apache HTTP 等の重い推移的依存を持ち Android と相性が悪い。
必要なエンドポイントは 2〜3 個だけで、インターフェース(`DriveApiClient`)を切って
モック可能にする方針とも合う。

## 2026-07-11: DI は Hilt を使わず手書きコンテナ

**判断**: Application に `AppContainer` を手書きする。

**理由**: 依存オブジェクトが 10 個未満で画面も 5 つ。Hilt の導入コスト
(kapt/ksp 設定・学習コスト)に見合わない。CLAUDE.md の「過度な抽象化はしない」に従う。

## 2026-07-11: 「Wi-Fi のみ」判定は NOT_METERED で行う

**判断**: 設定名は「モバイル回線でもダウンロード」だが、実際の判定は
`NET_CAPABILITY_NOT_METERED`(非従量制かどうか)で行う。

**理由**: ユーザーが守りたいのは通信量。Wi-Fi テザリング(従量制)を Wi-Fi 扱いで
許可してしまうより、従量制判定のほうが意図に合う。
