package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** 日付ごとの動画グループ。date が null のものは「日付不明」 */
data class VideoGroup(
    val date: LocalDate?,
    val videos: List<VideoItem>,
) {
    val label: String
        get() = date?.format(LABEL_FORMAT) ?: "日付不明"

    companion object {
        private val LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}

/**
 * ファイル名先頭の `yyyyMMdd_` を日付としてパースし、日付降順でグルーピングする純粋ロジック。
 * 13 月・32 日など暦上不正な値は日付不明扱い。
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

    /**
     * 日付降順のグループ一覧を返す。日付不明グループは常に末尾。
     * グループ内はファイル名昇順。
     */
    fun group(items: List<VideoItem>): List<VideoGroup> {
        val (dated, undated) = items.partition { parseDate(it.name) != null }
        val groups = dated
            .groupBy { parseDate(it.name)!! }
            .toSortedMap(compareByDescending { it })
            .map { (date, videos) -> VideoGroup(date, videos.sortedBy { it.name }) }
        return if (undated.isEmpty()) {
            groups
        } else {
            groups + VideoGroup(date = null, videos = undated.sortedBy { it.name })
        }
    }
}
