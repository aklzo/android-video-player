# Navigation Compose のルート引数で空白が「+」になる

## 症状
ファイル名をルート引数(`player/{fileId}/{name}`)で再生画面に渡したところ、
名前に含まれる空白がすべて `+` として表示された。さらにその名前が Room
(保存済みメタデータ)にそのまま保存され、保存済みタブの表示も `+` 入りになった。

## 原因
エンコードに `URLEncoder.encode(name, "UTF-8")` を使っていた。
これは HTML フォーム用のエンコード(`application/x-www-form-urlencoded`)で、
空白を `%20` ではなく `+` に変換する。一方 Navigation Compose のデコードは
`Uri.decode` 相当で、`%XX` は復元するが `+` を空白には戻さない。

## 解決方法
`android.net.Uri.encode(name)` を使う(空白は `%20`、`+` 変換なし)。

```kotlin
// NG: 空白が + になり、デコードで戻らない
URLEncoder.encode(item.name, "UTF-8")
// OK
Uri.encode(item.name)
```

## 再発防止・後始末
- Room に保存済みの汚れた名前は、再生時にルート引数の最新値で上書きする
  自己修復処理(`VideoCacheRepository.markPlayed(item)`)を入れて解消した。
- ルート引数に任意文字列を載せる際は必ず `Uri.encode` を使う。
