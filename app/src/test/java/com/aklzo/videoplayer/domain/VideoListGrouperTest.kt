package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.VideoItem
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoListGrouperTest {

    private fun video(name: String) =
        VideoItem(id = name, name = name, sizeBytes = null, thumbnailFileId = null, thumbnailUrl = null)

    // --- parseDate ---

    @Test
    fun `正常な日付をパースできる`() {
        assertEquals(LocalDate.of(2026, 3, 15), VideoListGrouper.parseDate("20260315_タイトル.mp4"))
    }

    @Test
    fun `うるう年の2月29日は有効`() {
        assertEquals(LocalDate.of(2024, 2, 29), VideoListGrouper.parseDate("20240229_a.mp4"))
    }

    @Test
    fun `非うるう年の2月29日は不正`() {
        assertNull(VideoListGrouper.parseDate("20230229_a.mp4"))
    }

    @Test
    fun `13月は不正`() {
        assertNull(VideoListGrouper.parseDate("20261301_a.mp4"))
    }

    @Test
    fun `32日は不正`() {
        assertNull(VideoListGrouper.parseDate("20260132_a.mp4"))
    }

    @Test
    fun `アンダースコアがなければパース不能`() {
        assertNull(VideoListGrouper.parseDate("20260315タイトル.mp4"))
    }

    @Test
    fun `日付プレフィックスがなければパース不能`() {
        assertNull(VideoListGrouper.parseDate("タイトル.mp4"))
        assertNull(VideoListGrouper.parseDate("2026_a.mp4"))
    }

    @Test
    fun `先頭以外の日付は対象外`() {
        assertNull(VideoListGrouper.parseDate("x20260315_a.mp4"))
    }

    // --- displayTitle ---

    @Test
    fun `表示タイトルは日付プレフィックスと拡張子を除く`() {
        assertEquals("タイトル", VideoListGrouper.displayTitle("20260315_タイトル.mp4"))
    }

    @Test
    fun `日付プレフィックスがなければ拡張子のみ除く`() {
        assertEquals("タイトル", VideoListGrouper.displayTitle("タイトル.mp4"))
    }

    @Test
    fun `タイトル中のドットは最後の拡張子だけ除かれる`() {
        assertEquals("Ver2.0紹介", VideoListGrouper.displayTitle("20260315_Ver2.0紹介.mp4"))
    }

    // --- group(月ごと) ---

    @Test
    fun `月降順にグルーピングされ、グループ内は日付降順`() {
        val groups = VideoListGrouper.group(
            listOf(
                video("20260101_a.mp4"),
                video("20260315_b.mp4"),
                video("20260320_a.mp4"),
                video("20251231_c.mp4"),
            )
        )
        assertEquals(
            listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 1), YearMonth.of(2025, 12)),
            groups.map { it.month },
        )
        // 同月内は日付降順(ファイル名降順)
        assertEquals(listOf("20260320_a.mp4", "20260315_b.mp4"), groups[0].videos.map { it.name })
    }

    @Test
    fun `パース不能なファイルは日付不明グループとして末尾に置かれる`() {
        val groups = VideoListGrouper.group(
            listOf(
                video("メモ.mp4"),
                video("20260315_a.mp4"),
                video("20261301_不正日付.mp4"),
            )
        )
        assertEquals(2, groups.size)
        assertEquals(YearMonth.of(2026, 3), groups[0].month)
        assertNull(groups[1].month)
        assertEquals("日付不明", groups[1].label)
        assertEquals(
            listOf("20261301_不正日付.mp4", "メモ.mp4"),
            groups[1].videos.map { it.name },
        )
    }

    @Test
    fun `空リストなら空のグループ一覧`() {
        assertEquals(emptyList<VideoGroup>(), VideoListGrouper.group(emptyList()))
    }

    @Test
    fun `ラベルは yyyy スラッシュ MM 形式`() {
        val groups = VideoListGrouper.group(listOf(video("20260305_a.mp4")))
        assertEquals("2026/03", groups[0].label)
    }
}
