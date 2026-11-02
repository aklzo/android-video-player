---
title: ルート引数の空白が「+」になる問題
type: how-to
status: 解決済み
updated: 2026-11-03
summary: Navigation Compose のルート引数で渡したファイル名の空白が「+」で表示された。URLEncoder を Uri.encode に替えて解決した。
---

## 症状

ファイル名をルート引数(`player/{fileId}/{name}`)で再生画面に渡したところ、名前に含まれる空白がすべて `+` で表示された。さらにその名前が Room(保存済みのメタデータ)にそのまま保存され、保存済みタブの表示にも `+` が入った。

## 原因

エンコードに `URLEncoder.encode(name, "UTF-8")` を使っていた。これは HTML フォーム用のエンコード(`application/x-www-form-urlencoded`)で、空白を `%20` ではなく `+` に変換する。一方、Navigation Compose のデコードは `Uri.decode` に相当し、`%XX` は元に戻すが、`+` を空白には戻さない。

## 解決方法

`android.net.Uri.encode(name)` を使う。空白は `%20` になり、`+` には変換されない。

```kotlin
// NG: 空白が + になり、デコードで戻らない
URLEncoder.encode(item.name, "UTF-8")
// OK
Uri.encode(item.name)
```

## 再発防止と後始末

- Room に保存済みのおかしな名前は、再生時にルート引数の最新の値で上書きする自己修復の処理(`VideoCacheRepository.markPlayed(item)`)を入れて解消した。
- ルート引数に任意の文字列を載せるときは、必ず `Uri.encode` を使う。
