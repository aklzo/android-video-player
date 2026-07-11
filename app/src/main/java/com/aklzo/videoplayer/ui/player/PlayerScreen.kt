package com.aklzo.videoplayer.ui.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aklzo.videoplayer.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is PlayerUiState.Preparing -> CenteredMessage("準備中…")

        is PlayerUiState.Downloading -> DownloadingContent(
            name = viewModel.videoName,
            state = s,
            onCancel = {
                viewModel.cancelDownload()
                onBack()
            },
        )

        is PlayerUiState.Blocked -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (s.offline) {
                    "オフラインです。接続を確認してください"
                } else {
                    "モバイル回線ではダウンロードしない設定です。\nWi-Fi に接続するか、設定で「モバイル回線でもダウンロード」を有効にしてください"
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

        is PlayerUiState.Ready -> VideoPlayer(file = s.file, title = viewModel.videoName)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { Text(text) }
}

@Composable
private fun DownloadingContent(
    name: String,
    state: PlayerUiState.Downloading,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        val progress = state.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            Text(
                "${(progress * 100).toInt()}% (${formatMb(state.bytesRead)} / ${formatMb(state.totalBytes!!)})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 24.dp))
            Text(
                formatMb(state.bytesRead),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedButton(onClick = onCancel, Modifier.padding(top = 24.dp)) { Text("キャンセル") }
    }
}

private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

/**
 * MediaController 経由で PlaybackService の ExoPlayer に接続して再生する。
 * この画面を離れる(dispose)と再生を停止する。ホーム画面・画面オフでは
 * dispose されないため、バックグラウンド再生は継続する。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(file: File, title: String) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

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

    // 全画面(システムバー非表示)
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(file) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaId(file.nameWithoutExtension)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
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

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                keepScreenOn = true
            }
        },
        update = { playerView -> playerView.player = controller },
        modifier = Modifier.fillMaxSize(),
    )
}
