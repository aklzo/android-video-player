package com.aklzo.videoplayer.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    viewModel: FolderPickerViewModel,
    onFolderSelected: () -> Unit,
    onNeedsSignIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.needsSignIn) {
        onNeedsSignIn()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.current.name) },
                navigationIcon = {
                    if (state.canGoUp) {
                        IconButton(onClick = { viewModel.goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上のフォルダへ")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.selectCurrent(onFolderSelected) },
                enabled = !state.isLoading && state.error == null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("このフォルダを追加")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorContent(
                    message = state.error!!,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )
                state.folders.isEmpty() -> Text(
                    "サブフォルダはありません",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn {
                    items(state.folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            modifier = Modifier.clickable { viewModel.open(folder) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("再試行") }
    }
}
