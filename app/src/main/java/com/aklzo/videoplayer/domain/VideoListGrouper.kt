package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** 月ごとの動画グループ。month が null のものは「日付不明」 */
data class VideoGroup(
    val month: YearMonth?,
    val videos: List<VideoItem>,
) {
    val label: String
        get() = month?.format(LABEL_FORMAT) ?: "日付不明"

    companion object {
        private val LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM")
    }
}

/**
 * ファイル名先頭の `yyyyMMdd_` を日付としてパースし、月ごと・日付降順でグルーピングする
 * 純粋ロジック。13 月・32 日など暦上不正な値は日付不明扱い。
 */
object VideoListGrouper {

    private val DATE_PREFIX = Regex("^(\\d{8})_")
    private val DATE_FORMAT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)

    /** ファイル名から日付を取り出す。パース不能・不正な日付は null */
    fun parseDate(fileName: String): LocalDate? {
        val match = DATE_PREFIX.find(fileName) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], DATE_FORMAT)
        } catch (e: Exception) {
            null
        }
    }

    /** 一覧・概要欄の表示タイトル: 日付プレフィックスと拡張子を除いたファイル名 */
    fun displayTitle(fileName: String): String {
        val withoutDate = DATE_PREFIX.replace(fileName, "")
        val withoutExt = withoutDate.substringBeforeLast('.')
        return withoutExt.ifEmpty { withoutDate }
    }

    /**
     * 月降順のグループ一覧を返す。日付不明グループは常に末尾。
     * グループ内は日付降順(= 日付プレフィックス付きファイル名の降順)。
     */
    fun group(items: List<VideoItem>): List<VideoGroup> {
        val (dated, undated) = items.partition { parseDate(it.name) != null }
        val groups = dated
            .groupBy { YearMonth.from(parseDate(it.name)!!) }
            .toSortedMap(compareByDescending { it })
            .map { (month, videos) -> VideoGroup(month, videos.sortedByDescending { it.name }) }
        return if (undated.isEmpty()) {
            groups
        } else {
            groups + VideoGroup(month = null, videos = undated.sortedBy { it.name })
        }
    }
}
