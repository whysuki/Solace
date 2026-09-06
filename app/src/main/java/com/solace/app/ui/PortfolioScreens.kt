package com.solace.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.solace.app.data.MediaFilter
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaType
import com.solace.app.data.portfolio.PortfolioSummary
import com.solace.app.data.portfolio.WorkSummary
import kotlinx.coroutines.delay

/**
 * 作品集 Tab 根组合：作品集列表 → 作品网格。
 * 作品浏览（全屏播放器 / 空作品页）与「新建作品」流程由 [SolaceApp] 以覆盖层叠加，保证超出底部导航。
 */
@Composable
internal fun PortfolioTab(viewModel: PortfolioViewModel, onCreateWork: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val error = ui.error
    when {
        error != null -> PortfolioErrorScreen(message = error, onRetry = viewModel::load)
        ui.selectedPortfolio == null -> PortfolioListScreen(ui = ui, viewModel = viewModel)
        else -> WorksScreen(ui = ui, viewModel = viewModel, onCreateWork = onCreateWork)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioListScreen(ui: PortfolioUiState, viewModel: PortfolioViewModel) {
    val portfolios = ui.portfolios
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PortfolioSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<PortfolioSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("作品集") },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建作品集")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading && portfolios.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            portfolios.isEmpty() -> EmptyHint(padding, "还没有作品集\n点击右下角 + 新建")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(portfolios, key = { it.id }) { p ->
                    PortfolioRow(
                        portfolio = p,
                        onClick = { viewModel.openPortfolio(p) },
                        onRename = { renameTarget = p },
                        onDelete = { deleteTarget = p },
                    )
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建作品集",
            placeholder = "作品集名称",
            onConfirm = { name ->
                showCreate = false
                viewModel.createPortfolio(name)
            },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { p ->
        TextInputDialog(
            title = "重命名作品集",
            initial = p.name,
            onConfirm = { name ->
                renameTarget = null
                viewModel.renamePortfolio(p.id, name)
            },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { p ->
        ConfirmDialog(
            title = "删除作品集",
            text = "删除「${p.name}」及其中的 ${p.workCount} 个作品、${p.itemCount} 个素材文件？此操作不可恢复。",
            onConfirm = {
                deleteTarget = null
                viewModel.deletePortfolio(p.id)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun PortfolioRow(
    portfolio: PortfolioSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(cover = portfolio.cover, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = portfolio.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "${portfolio.workCount} 个作品 · ${portfolio.itemCount} 个素材",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorksScreen(ui: PortfolioUiState, viewModel: PortfolioViewModel, onCreateWork: () -> Unit) {
    val portfolio = ui.selectedPortfolio ?: return
    val works = ui.works
    var renameTarget by remember { mutableStateOf<WorkSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(portfolio.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = viewModel::backFromPortfolio) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateWork) {
                        Icon(Icons.Filled.Add, contentDescription = "新建作品")
                    }
                },
            )
        },
    ) { padding ->
        if (works.isEmpty()) {
            EmptyHint(padding, "还没有作品\n点击右下角 + 新建")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(works, key = { it.id }) { w ->
                    WorkCard(
                        work = w,
                        onClick = { viewModel.openWork(w) },
                        onRename = { renameTarget = w },
                        onDelete = { deleteTarget = w },
                    )
                }
            }
        }
    }

    renameTarget?.let { w ->
        TextInputDialog(
            title = "重命名作品",
            initial = w.title,
            onConfirm = { name ->
                renameTarget = null
                viewModel.renameWork(w.id, name)
            },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { w ->
        ConfirmDialog(
            title = "删除作品",
            text = "删除「${w.title}」及其 ${w.itemCount} 个素材文件？此操作不可恢复。",
            onConfirm = {
                deleteTarget = null
                viewModel.deleteWork(w.id)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private enum class CreateStep { PICK, TITLE }

/**
 * 「新建作品」两步全屏弹层（仿抖音作品创作：选素材 → 命名发布）。
 * 第 1 步从最近媒体多选素材（含全部/图片/视频筛选与滚动分页），
 * 第 2 步命名并「发布」：创建作品 + 按选择顺序批量复制素材。
 * 由 [SolaceApp] 以覆盖层叠加，超出底部导航。
 */
@Composable
internal fun CreateWorkFlow(
    ui: PortfolioUiState,
    viewModel: PortfolioViewModel,
    onDismiss: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(CreateStep.PICK) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selectedItems = ui.pickerItems.filter { it.key in selectedKeys }

    // 选择页返回 = 关闭流程；命名页返回 = 回到选择页（TitleStep 内处理）。
    BackHandler(enabled = step == CreateStep.PICK, onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize()) {
        if (step == CreateStep.PICK) {
            PickStep(
                ui = ui,
                selectedKeys = selectedKeys,
                onToggle = { key ->
                    selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                },
                onClearSelection = { selectedKeys = emptyList() },
                onNext = { step = CreateStep.TITLE },
                onClose = onDismiss,
                viewModel = viewModel,
            )
        } else {
            TitleStep(
                title = title,
                onTitleChange = { title = it },
                selectedItems = selectedItems,
                onBack = { step = CreateStep.PICK },
                onClose = onDismiss,
                onPublish = {
                    onDismiss()
                    viewModel.createWorkWithItems(title.trim(), selectedItems)
                },
            )
        }
    }
}

@Composable
private fun PickStep(
    ui: PortfolioUiState,
    selectedKeys: List<String>,
    onToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    viewModel: PortfolioViewModel,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("创建作品", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "第 1 步 · 选择素材",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedKeys.isNotEmpty()) {
                TextButton(onClick = onClearSelection) {
                    Text("取消选中", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(MediaFilter.ALL to "全部", MediaFilter.IMAGE to "图片", MediaFilter.VIDEO to "视频").forEach { (filter, label) ->
                FilterChip(
                    selected = ui.pickerFilter == filter,
                    onClick = { viewModel.setPickerFilter(filter) },
                    label = { Text(label) },
                )
            }
        }

        val gridState = rememberLazyGridState()
        LaunchedEffect(ui.pickerFilter) { gridState.scrollToItem(0) }
        val shouldLoadMore by remember {
            derivedStateOf {
                val layout = gridState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layout.totalItemsCount - 6
            }
        }
        LaunchedEffect(shouldLoadMore, ui.pickerLoading, ui.pickerEndReached) {
            if (shouldLoadMore && !ui.pickerLoading && !ui.pickerEndReached) {
                viewModel.loadMorePicker()
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                ui.pickerItems.isEmpty() && ui.pickerLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                ui.pickerItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有可选择的媒体文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(ui.pickerItems, key = { it.key }) { item ->
                        PickerCell(
                            item = item,
                            orderIndex = selectedKeys.indexOf(item.key),
                            onToggle = { onToggle(item.key) },
                        )
                    }
                    if (ui.pickerLoading) {
                        item(key = "pickerLoading") {
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
        }

        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = onNext,
                enabled = selectedKeys.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("下一步（${selectedKeys.size}）")
            }
        }
    }
}

@Composable
private fun TitleStep(
    title: String,
    onTitleChange: (String) -> Unit,
    selectedItems: List<MediaItem>,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPublish: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("新建作品", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "第 2 步 · 命名发布",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPublish, enabled = title.isNotBlank()) {
                Text("发布")
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            label = { Text("作品名称") },
            placeholder = { Text("给作品起个名字") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        // 预览：在作品名称下方横向分页切换已选素材（图片按比例显示，视频为缩略图+角标）。
        // 黑底与圆角裁剪统一在整块容器（而非每页），滑动时四角一致，避免圆角-直角跳动。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            val pagerState = rememberPagerState(pageCount = { selectedItems.size })
            // 当前正在预览播放的视频素材 key；点击播放后才挂载播放器，翻页即卸载。
            var playingKey by remember { mutableStateOf<String?>(null) }

            // 翻页后若当前页不是正在播放的素材，停止预览播放（卸载并释放播放器）。
            LaunchedEffect(pagerState.currentPage) {
                val current = selectedItems.getOrNull(pagerState.currentPage)
                if (playingKey != null && playingKey != current?.key) playingKey = null
            }

            if (selectedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有已选素材", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = selectedItems[page]
                    WorkPreviewPage(
                        item = item,
                        playing = playingKey == item.key,
                        onPlay = { playingKey = item.key },
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${selectedItems.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 预览单页：容器黑底/圆角由外层容器统一裁剪；视频未播放时显示画面帧 + 播放角标（整页可点），
 * 已进入播放态则替换为内联播放器（[InlineVideoPlayer]）。
 */
@Composable
private fun WorkPreviewPage(
    item: MediaItem,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    if (item.type == MediaType.VIDEO && playing) {
        InlineVideoPlayer(item = item)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (item.type == MediaType.VIDEO) Modifier.clickable(onClick = onPlay) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MediaThumbnail(item = item, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (item.type == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = formatDuration(item.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(shadow = overlayTextShadow),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * 预览内联视频播放器：点击播放键开始播放；播放中点击画面切换底部控制条
 * （进度条 + 暂停按钮）显隐，进度条可点/拖动 seek；暂停/播完后显示居中的
 * 播放/重播按钮，点击画面恢复播放。页面卸载或离开后台时释放/暂停，规则与
 * [FullScreenPlayer] 视频页一致。
 */
@Composable
private fun InlineVideoPlayer(item: MediaItem) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // 底部控制条（进度条 + 暂停）显隐：播放中点击画面切换，暂停/重播后隐藏。
    var showControls by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 播放中每 250ms 刷新进度位置；拖动期间以拖动值为准，不被播放位置覆盖。
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            if (!dragging) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    val durationMs = (player.duration.takeIf { it > 0 } ?: item.durationMs).coerceAtLeast(0L)
    val displayPosition = if (dragging) dragValue else positionMs
    val fraction = if (durationMs > 0) {
        (displayPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // 画面点击：播放中=切换底部控制条显隐；未播放/播完=开始/重播（控制条保持隐藏）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    when {
                        player.playbackState == Player.STATE_ENDED -> {
                            player.seekTo(0)
                            player.play()
                            showControls = false
                        }
                        player.isPlaying -> showControls = !showControls
                        else -> {
                            player.play()
                            showControls = false
                        }
                    }
                },
        )
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (ended) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                    contentDescription = if (ended) "重播" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        if (isPlaying && showControls) {
            // 底部控制条：白底胶囊（与全屏播放器控制条同风格），行内为暂停按钮 + 时间轴。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(50), clip = false)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable(onClick = { player.pause() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "暂停",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Scrubber(
                    fraction = fraction,
                    onFractionChange = { f ->
                        dragging = true
                        dragValue = (f * durationMs).toLong()
                    },
                    onFractionChangeFinished = {
                        player.seekTo(dragValue)
                        dragging = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = formatDuration(durationMs),
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PickerCell(item: MediaItem, orderIndex: Int, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle),
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
        val badge = if (orderIndex >= 0) {
            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .border(1.5.dp, Color.White, CircleShape)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .then(badge),
            contentAlignment = Alignment.Center,
        ) {
            if (orderIndex >= 0) {
                Text(
                    text = "${orderIndex + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun WorkCard(
    work: WorkSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (work.cover != null) {
            MediaThumbnail(item = work.cover, modifier = Modifier.fillMaxSize())
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
                text = work.title,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = overlayTextShadow),
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${work.itemCount} 个素材",
                style = MaterialTheme.typography.bodySmall.copy(shadow = overlayTextShadow),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

/** 作品内素材全部缺失（或为空）时的全屏空态页，由 SolaceApp 叠加显示。 */
@Composable
internal fun WorkEmptyScreen(title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "作品内暂无可用素材",
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text = "返回",
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CoverThumb(cover: MediaItem?, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (cover != null) {
            MediaThumbnail(item = cover, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun EmptyHint(padding: PaddingValues, text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PortfolioErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "出错了：$message", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.size(16.dp))
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

@Composable
private fun TextInputDialog(
    title: String,
    initial: String = "",
    placeholder: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
