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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.aklzo.videoplayer.data.cache.VideoCacheRepository
import com.aklzo.videoplayer.data.source.VideoItem
import com.aklzo.videoplayer.domain.VideoCategories
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
                onBack = onBack,
            )
        }
    }
}

/**
 * 横型動画: YouTube 風レイアウト(上部プレイヤー+下部概要欄。全画面ボタンあり)。
 * 縦型動画(shorts): 画面全体に表示し、概要欄は「詳細」ボタンからボトムシートで開く。
 * 縦型判定は shorts ディレクトリを初期値とし、実際の映像サイズで確定する。
 * この画面を離れる(dispose)と再生を停止する。ホーム画面・画面オフでは
 * dispose されないため、バックグラウンド再生は継続する。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerWithDetails(
    uri: String,
    video: VideoItem,
    isSaved: Boolean,
    cachingProgress: VideoCacheRepository.CachingProgress?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showDetailsSheet by rememberSaveable { mutableStateOf(false) }

    // 縦型判定: shorts ディレクトリなら初期値 true、映像サイズが分かり次第確定
    val initialVertical = remember { VideoCategories.categoryOf(video) == "shorts" }
    var detectedVertical by remember { mutableStateOf<Boolean?>(null) }
    val isVertical = detectedVertical ?: initialVertical
    val immersive = isFullscreen || isVertical

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

    // 全画面・縦型(shorts)表示中のみシステムバーを隠す
    val view = LocalView.current
    DisposableEffect(immersive) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive) {
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
            c.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        detectedVertical = videoSize.height > videoSize.width
                    }
                }
            })
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
            modifier = if (immersive) {
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
                    if (isVertical) {
                        // 縦型は常に全画面のため全画面ボタンは出さない
                        playerView.setFullscreenButtonClickListener(
                            null as PlayerView.FullscreenButtonClickListener?
                        )
                    } else {
                        playerView.setFullscreenButtonClickListener { fullscreen ->
                            isFullscreen = fullscreen
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 一覧へ戻るボタン(常時表示)
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "一覧に戻る",
                    tint = Color.White,
                )
            }

            // 縦型(shorts): タイトルと「詳細」を下部にオーバーレイ
            if (isVertical) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    Text(
                        VideoListGrouper.displayTitle(video.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { showDetailsSheet = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text("詳細")
                    }
                }
            }
        }
        if (!immersive) {
            VideoDetails(
                video = video,
                isSaved = isSaved,
                cachingProgress = cachingProgress,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 縦型動画の概要欄(横型と同じ形式)をボトムシートで表示
    if (showDetailsSheet) {
        ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }) {
            VideoDetails(
                video = video,
                isSaved = isSaved,
                cachingProgress = cachingProgress,
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
