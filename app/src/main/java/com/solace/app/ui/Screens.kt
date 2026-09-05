package com.solace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.solace.app.data.FolderSort
import com.solace.app.data.MediaFilter
import com.solace.app.data.MediaFolder
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaType
import java.util.concurrent.TimeUnit

@Composable
fun SolaceApp(
    access: MediaAccess,
    onOpenSettings: () -> Unit = {},
    viewModel: MediaViewModel = viewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var playerIndex by remember { mutableStateOf<Int?>(null) }

    // 仅在首次进入或权限级别变化时重载；从设置页返回（access 未变）不重扫 MediaStore。
    LaunchedEffect(access) {
        viewModel.load()
    }

    when {
        state.foldersError != null -> ErrorScreen(
            message = state.foldersError,
            onRetry = viewModel::load,
        )
        state.loadingFolders && state.folders.isEmpty() -> LoadingScreen()
        state.currentFolder != null -> {
            MediaListScreen(
                folder = state.currentFolder,
                items = state.folderItems,
                loadingMore = state.loadingMore,
                endReached = state.endReached,
                sort = state.sort,
                filter = state.filter,
                onLoadMore = viewModel::loadMore,
                onBack = viewModel::back,
                onSortSelected = viewModel::setSort,
                onFilterSelected = viewModel::setFilter,
                onItemClick = { playerIndex = it },
            )
            val index = playerIndex
            if (index != null) {
                FullScreenPlayer(
                    items = state.folderItems,
                    initialIndex = index,
                    onLoadMore = viewModel::loadMore,
                    onClose = { playerIndex = null },
                )
            }
        }
        else -> FolderListScreen(
            folders = state.folders,
            gridView = state.folderGridView,
            onToggleLayout = viewModel::toggleFolderLayout,
            onFolderClick = viewModel::openFolder,
            onRetry = viewModel::load,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "出错了：$message", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "重试",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderListScreen(
    folders: List<MediaFolder>,
    gridView: Boolean,
    onToggleLayout: () -> Unit,
    onFolderClick: (MediaFolder) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件夹") },
                actions = {
                    IconButton(onClick = onToggleLayout) {
                        Icon(
                            imageVector = if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (gridView) "切换为列表样式" else "切换为网格样式",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有找到媒体文件")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "刷新",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                }
            }
        } else if (gridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(folders, key = { it.bucketId }) { folder ->
                    FolderCard(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(folders, key = { it.bucketId }) { folder ->
                    FolderRow(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        }
    }
}

private val overlayTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.8f),
    offset = Offset(1f, 1f),
    blurRadius = 4f,
)

private fun mediaCountText(folder: MediaFolder): String = buildString {
    if (folder.imageCount > 0) append("${folder.imageCount} 张图片")
    if (folder.videoCount > 0) {
        if (isNotEmpty()) append(" · ")
        append("${folder.videoCount} 个视频")
    }
}

@Composable
private fun FolderRow(folder: MediaFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val thumb = folder.thumbnail
            if (thumb != null) {
                MediaThumbnail(
                    item = thumb,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mediaCountText(folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        val thumb = folder.thumbnail
        if (thumb != null) {
            MediaThumbnail(
                item = thumb,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = overlayTextShadow),
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaCountText(folder),
                style = MaterialTheme.typography.bodySmall.copy(shadow = overlayTextShadow),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaListScreen(
    folder: MediaFolder,
    items: List<MediaItem>,
    loadingMore: Boolean,
    endReached: Boolean,
    sort: FolderSort,
    filter: MediaFilter,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onSortSelected: (FolderSort) -> Unit,
    onFilterSelected: (MediaFilter) -> Unit,
    onItemClick: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    MediaMoreMenu(
                        sort = sort,
                        filter = filter,
                        onSortSelected = onSortSelected,
                        onFilterSelected = onFilterSelected,
                    )
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layout.totalItemsCount - 6
            }
        }

        LaunchedEffect(shouldLoadMore, loadingMore, endReached) {
            if (shouldLoadMore && !loadingMore && !endReached) {
                onLoadMore()
            }
        }

        when {
            items.isEmpty() && loadingMore -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("这个文件夹是空的")
            }
            else -> MediaGrid(
                listState = listState,
                items = items,
                loadingMore = loadingMore,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun MediaGrid(
    listState: LazyGridState,
    items: List<MediaItem>,
    loadingMore: Boolean,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            MediaCell(item = item, onClick = { onItemClick(index) })
        }
        if (loadingMore) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaCell(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())
        if (item.type == MediaType.VIDEO) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(shadow = overlayTextShadow),
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = item.uri,
        contentDescription = item.name,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    ) {
        if (painter.state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.type == MediaType.VIDEO) Icons.Filled.PlayArrow else Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds)
    val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)
    return "%d:%02d".format(minutes, seconds)
}

private enum class MoreSection { SORT, FILTER }

@Composable
private fun MediaMoreMenu(
    sort: FolderSort,
    filter: MediaFilter,
    onSortSelected: (FolderSort) -> Unit,
    onFilterSelected: (MediaFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<MoreSection?>(null) }

    Box {
        IconButton(onClick = { expanded = true; section = null }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                section = null
            },
        ) {
            when (section) {
                null -> {
                    DropdownMenuItem(
                        text = { Text("排序") },
                        onClick = { section = MoreSection.SORT },
                    )
                    DropdownMenuItem(
                        text = { Text("展示内容") },
                        onClick = { section = MoreSection.FILTER },
                    )
                }
                MoreSection.SORT -> {
                    MenuHeader("排序")
                    CheckMenuItem("时间", FolderSort.TIME, sort) {
                        expanded = false
                        section = null
                        onSortSelected(FolderSort.TIME)
                    }
                    CheckMenuItem("名称", FolderSort.NAME, sort) {
                        expanded = false
                        section = null
                        onSortSelected(FolderSort.NAME)
                    }
                    CheckMenuItem("大小", FolderSort.SIZE, sort) {
                        expanded = false
                        section = null
                        onSortSelected(FolderSort.SIZE)
                    }
                }
                MoreSection.FILTER -> {
                    MenuHeader("展示内容")
                    CheckMenuItem("全部", MediaFilter.ALL, filter) {
                        expanded = false
                        section = null
                        onFilterSelected(MediaFilter.ALL)
                    }
                    CheckMenuItem("图片", MediaFilter.IMAGE, filter) {
                        expanded = false
                        section = null
                        onFilterSelected(MediaFilter.IMAGE)
                    }
                    CheckMenuItem("视频", MediaFilter.VIDEO, filter) {
                        expanded = false
                        section = null
                        onFilterSelected(MediaFilter.VIDEO)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun <T> CheckMenuItem(
    label: String,
    value: T,
    selectedValue: T,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = if (value == selectedValue) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}
