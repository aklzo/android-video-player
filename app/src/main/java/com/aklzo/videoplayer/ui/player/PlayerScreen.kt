package com.aklzo.videoplayer.ui.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoListGrouper
import com.aklzo.videoplayer.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import java.time.format.DateTimeFormatter

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is PlayerUiState.Preparing -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("準備中…") }

        is PlayerUiState.Blocked -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (s.offline) {
                    "オフラインです。接続を確認してください\n(保存済みの動画はオフラインでも再生できます)"
                } else {
                    "モバイル回線では再生(ダウンロード)しない設定です。\nWi-Fi に接続するか、設定で「モバイル回線でもダウンロード」を有効にしてください"
                },
                textAlign = TextAlign.Center,
            )
            Button(onClick = { viewModel.start() }, Modifier.padding(top = 24.dp)) { Text("再試行") }
            OutlinedButton(onClick = onBack, Modifier.padding(top = 8.dp)) { Text("戻る") }
        }

        is PlayerUiState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Button(onClick = { viewModel.start() }, Modifier.padding(top = 24.dp)) { Text("再試行") }
            OutlinedButton(onClick = onBack, Modifier.padding(top = 8.dp)) { Text("戻る") }
        }

        is PlayerUiState.Ready -> {
            val cachingProgress by viewModel.cachingProgress.collectAsStateWithLifecycle()
            val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
            PlayerWithDetails(
                uri = s.uri,
                video = viewModel.video,
                isSaved = isSaved,
                cachingProgress = cachingProgress,
            )
        }
    }
}

/**
 * YouTube 風レイアウト: 上部に動画プレイヤー、下部に概要欄(メタ情報)。
 * プレイヤーの全画面ボタンで全画面(概要欄・システムバー非表示)に切り替える。
 * この画面を離れる(dispose)と再生を停止する。ホーム画面・画面オフでは
 * dispose されないため、バックグラウンド再生は継続する。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerWithDetails(
    uri: String,
    video: VideoItem,
    isSaved: Boolean,
    cachingProgress: VideoCacheRepository.CachingProgress?,
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    // Android 13+ は通知表示に POST_NOTIFICATIONS 許可が必要
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 全画面中のみシステムバーを隠す
    val view = LocalView.current
    DisposableEffect(isFullscreen) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(uri) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .setMediaId(video.id)
                .setCustomCacheKey(video.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(VideoListGrouper.displayTitle(video.name))
                        .build()
                )
                .build()
            c.setMediaItem(mediaItem)
            c.prepare()
            c.play()
            controller = c
        }, MoreExecutors.directExecutor())

        onDispose {
            val c = controller
            if (c != null) {
                c.stop()
                c.clearMediaItems()
                c.release()
            } else {
                MediaController.releaseFuture(future)
            }
            controller = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            }.background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        keepScreenOn = true
                    }
                },
                update = { playerView ->
                    playerView.player = controller
                    playerView.setFullscreenButtonClickListener { fullscreen ->
                        isFullscreen = fullscreen
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!isFullscreen) {
            VideoDetails(
                video = video,
                isSaved = isSaved,
                cachingProgress = cachingProgress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 概要欄: タイトル・日付・保存状態・フォルダパス等のメタ情報 */
@Composable
private fun VideoDetails(
    video: VideoItem,
    isSaved: Boolean,
    cachingProgress: VideoCacheRepository.CachingProgress?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            VideoListGrouper.displayTitle(video.name),
            style = MaterialTheme.typography.titleMedium,
        )

        val date = VideoListGrouper.parseDate(video.name)
        Text(
            listOfNotNull(
                date?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                video.sizeBytes?.let { formatSize(it) },
            ).joinToString("・"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        // 保存状態
        when {
            isSaved -> Text(
                "✓ このデバイスに保存済み(オフライン再生可)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            cachingProgress != null -> Column(Modifier.padding(top = 6.dp)) {
                val fraction = cachingProgress.fraction
                Text(
                    if (fraction != null) "保存中 ${(fraction * 100).toInt()}%" else "保存中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            else -> Text(
                "未保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        MetaRow("フォルダ", video.folderPath?.let { if (it.isEmpty()) "(選択フォルダ直下)" else it } ?: "不明")
        MetaRow("ファイル名", video.name)
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
