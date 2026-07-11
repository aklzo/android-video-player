package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem

/**
 * ディレクトリフィルターの分類ロジック。
 * フォルダパスは「対象フォルダ名/サブディレクトリ/…」形式(VideoItem.folderPath)。
 * 分類キーは対象フォルダ直下のサブディレクトリ名(例: shorts / streams / videos)。
 * 対象フォルダ直下に直接置かれた動画は対象フォルダ名そのものを分類キーとする。
 */
object VideoCategories {

    fun categoryOf(item: VideoItem): String? {
        val segments = item.folderPath
            ?.split('/')
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return when {
            segments.isEmpty() -> null
            segments.size == 1 -> segments[0] // 対象フォルダ直下
            else -> segments[1]               // 上位サブディレクトリ名
        }
    }

    /** 一覧に存在する分類キーを名前順で返す */
    fun categories(items: List<VideoItem>): List<String> =
        items.mapNotNull { categoryOf(it) }.distinct().sorted()

    /** category が null なら全件、指定があればその分類のみ */
    fun filter(items: List<VideoItem>, category: String?): List<VideoItem> =
        if (category == null) items else items.filter { categoryOf(it) == category }
}
