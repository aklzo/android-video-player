package com.aklzo.videoplayer.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoGroup
import com.aklzo.videoplayer.domain.VideoListGrouper
import java.io.File
import java.time.format.DateTimeFormatter

/** 月ヘッダー付きのサムネイルグリッド(YouTube 風)。各タブで共有する */
@Composable
fun VideoGrid(
    groups: List<VideoGroup>,
    cachedVideoIds: Set<String>,
    thumbnailProvider: suspend (VideoItem) -> File?,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: ((VideoItem) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        groups.forEach { group ->
            item(key = "header_${group.label}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(group.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    isCached = video.id in cachedVideoIds,
                    thumbnailProvider = thumbnailProvider,
                    onClick = { onVideoClick(video) },
                    onLongClick = onVideoLongClick?.let { handler -> { handler(video) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoItem,
    isCached: Boolean,
    thumbnailProvider: suspend (VideoItem) -> File?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        val thumbnailFile by produceState<File?>(initialValue = null, video.id) {
            value = thumbnailProvider(video)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailFile != null) {
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
            if (isCached) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "保存済み",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            VideoListGrouper.displayTitle(video.name),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val date = VideoListGrouper.parseDate(video.name)
            Text(
                listOfNotNull(
                    date?.format(CARD_DATE_FORMAT),
                    video.sizeBytes?.let { formatSize(it) },
                ).joinToString("・"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isCached) {
                Text(
                    " ・保存済み",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val CARD_DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日")

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
