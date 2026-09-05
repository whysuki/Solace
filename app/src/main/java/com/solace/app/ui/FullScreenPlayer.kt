package com.solace.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 顶部返回圆圈、文件名胶囊、位置圆圈三者的统一高度。 */
private val topBarControlSize = 40.dp

/** 音量面板滑杆内容高度。 */
private val panelContentHeight = 160.dp

/**
 * 全屏大图/视频播放器：横向分页左右滑动切换当前文件夹内的对象。
 * 图片用 Coil 按屏幕尺寸解码；视频用 ExoPlayer，支持时间轴/倍速/音量。
 * 打开方式见 [SolaceApp]：从网格点击某一张，以该下标作为起始页。
 */
@Composable
fun FullScreenPlayer(
    items: List<MediaItem>,
    initialIndex: Int,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
) {
    val startIndex = initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var controlsVisible by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage)

    // 滑动接近列表末尾时继续分页加载，保证能一直往后滑。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page >= items.size - 3) onLoadMore()
        }
    }

    // 翻页时收起文件详情，避免详情停留在已经滑走的对象上。
    LaunchedEffect(pagerState.settledPage) {
        showDetails = false
    }

    BackHandler(onBack = onClose)

    // 显示导航/控制按钮时切为白色背景，隐藏时回黑色沉浸背景。
    Box(modifier = Modifier.fillMaxSize().background(if (controlsVisible) Color.White else Color.Black)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.key ?: it.toString() },
        ) { page ->
            val item = items[page]
            val isActive = page == pagerState.settledPage
            if (item.type == MediaType.VIDEO) {
                VideoPage(
                    item = item,
                    isActive = isActive,
                    controlsVisible = controlsVisible,
                    onToggle = { controlsVisible = !controlsVisible },
                )
            } else {
                ImagePage(
                    item = item,
                    onToggle = { controlsVisible = !controlsVisible },
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopBar(
                title = currentItem?.name?.let(::stripExtension) ?: "",
                position = "${pagerState.currentPage + 1}/${items.size}",
                onClose = onClose,
                onShowDetails = { showDetails = true },
                modifier = Modifier.statusBarsPadding(),
            )
        }

        if (showDetails) {
            currentItem?.let {
                MediaDetailOverlay(
                    item = it,
                    onClose = { showDetails = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    position: String,
    onClose: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 返回圆圈、文件名胶囊、位置圆圈三者等高（topBarControlSize），分居左/中/右。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // 左侧返回圆圈
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(topBarControlSize)
                .shadow(4.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.9f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.Black,
                modifier = Modifier.size(20.dp),
            )
        }
        // 居中文件名胶囊（与两侧圆点等高）；点击显示文件详情。
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = topBarControlSize + 24.dp)
                .height(topBarControlSize)
                .shadow(4.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.9f))
                .clickable(onClick = onShowDetails)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = Color.Black,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 右侧位置圆圈，显示 当前/总数
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(topBarControlSize)
                .sizeIn(minWidth = topBarControlSize)
                .shadow(4.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.9f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = position,
                color = Color.Black,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ImagePage(
    item: MediaItem,
    onToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize().clickable(onClick = onToggle),
            contentScale = ContentScale.Fit,
        ) {
            if (painter.state is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VideoPage(
    item: MediaItem,
    isActive: Boolean,
    controlsVisible: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember { mutableStateOf(false) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
        }
    }

    // 仅页面卸载时释放播放器；进入/离开后台只暂停不自动播放。
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

    // 离开当前页即暂停；进入时不自动播放（先显示缩略图，由 started 控制）。
    LaunchedEffect(isActive) {
        if (!isActive) player.pause()
    }

    val togglePlay: () -> Unit = {
        if (!started) {
            started = true
            player.play()
        } else if (player.playbackState == ExoPlayer.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
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

        if (started) {
            // 已开始播放：点击空白处切换控件显隐。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onToggle),
            )
        } else {
            // 未开始：铺系统缩略图作海报，点击即开始播放。
            VideoPoster(
                item = item,
                onPlay = {
                    togglePlay()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (controlsVisible) {
            VideoControls(
                player = player,
                durationMs = item.durationMs,
                onTogglePlay = {
                    togglePlay()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun VideoPoster(
    item: MediaItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 显式以缩略图尺寸请求，走系统缩略图缓存，避免按视频原图解码失败。
    val request = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(512, 512).build()
    }
    Box(
        modifier = modifier.clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        ) {
            if (painter.state is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

private enum class PlayerPanel { SPEED, VOLUME }

@Composable
private fun VideoControls(
    player: ExoPlayer,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }

    val duration = (player.duration.takeIf { it > 0 } ?: durationMs).coerceAtLeast(0L)
    val displayPosition = if (dragging) dragValue else position
    val fraction = if (duration > 0) displayPosition.toFloat() / duration.toFloat() else 0f

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(250)
        }
    }

    val playIcon = when {
        player.playbackState == ExoPlayer.STATE_ENDED -> Icons.Filled.Replay
        isPlaying -> Icons.Filled.Pause
        else -> Icons.Filled.PlayArrow
    }

    // 控制条为独立的胶囊浮动面板；展开的倍速/音量/进度面板在其上方单独成卡，
    // 避免把胶囊(50%圆角)拉高后圆角过度导致内容被裁。
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 只有拖动时间轴时才显示进度数字，向上展开。
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            ControlSurface(modifier = Modifier.padding(bottom = 8.dp)) {
                ProgressPanel(
                    text = "${formatPlayerDuration(dragValue)} / ${formatPlayerDuration(duration)}",
                )
            }
        }
        // 倍速/音量详情：锚定在控制条上方，向上展开。
        // 倍速面板：窄卡，右侧内缩对齐到「倍速数字」按钮上方（end=音量按钮宽48+内边距16）。
        AnimatedVisibility(
            visible = panel == PlayerPanel.SPEED,
            modifier = Modifier.align(Alignment.End),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            ControlSurface(
                modifier = Modifier.padding(end = 64.dp, bottom = 8.dp).width(100.dp),
            ) {
                SpeedOptions(
                    current = speed,
                    onSpeedSelected = {
                        speed = it
                        player.playbackParameters = PlaybackParameters(it)
                        panel = null
                    },
                )
            }
        }
        // 音量面板：竖向滑杆，右侧对齐到「音量」按钮上方。
        AnimatedVisibility(
            visible = panel == PlayerPanel.VOLUME,
            modifier = Modifier.align(Alignment.End),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            ControlSurface(modifier = Modifier.padding(end = 16.dp, bottom = 8.dp)) {
                VolumeSlider(
                    volume = volume,
                    onVolumeSelected = {
                        volume = it
                        player.volume = it
                    },
                    onVolumeChangedFinished = {
                        panel = null
                    },
                )
            }
        }

        // 单行：播放按钮 + 时间轴（紧邻播放键右侧）+ 倍速 + 音量。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = playIcon,
                    contentDescription = "播放/暂停",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Scrubber(
                fraction = fraction.coerceIn(0f, 1f),
                onFractionChange = { f ->
                    dragging = true
                    dragValue = (f * duration).toLong()
                },
                onFractionChangeFinished = {
                    player.seekTo(dragValue)
                    dragging = false
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(onClick = {
                panel = if (panel == PlayerPanel.SPEED) null else PlayerPanel.SPEED
            }) {
                Text(text = "${formatSpeed(speed)}x", color = Color.Black)
            }
            IconButton(onClick = {
                panel = if (panel == PlayerPanel.VOLUME) null else PlayerPanel.VOLUME
            }) {
                Icon(
                    imageVector = if (volume > 0f) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = if (volume > 0f) "静音" else "取消静音",
                    tint = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun SpeedOptions(
    current: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    // 倍速选项竖向排列、平均分配在固定高度内。本地把最小交互尺寸压到 0，
    // 让六个选项均匀铺满卡高，避免 material 默认 48dp/项把卡片顶得过高。
    // 高度比音量面板略大，给选项更多点击空间。
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp),
        ) {
            speeds.forEach { s ->
                TextButton(
                    onClick = { onSpeedSelected(s) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = "${formatSpeed(s)}x",
                        color = if (s == current) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(alpha = 0.7f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeSlider(
    volume: Float,
    onVolumeSelected: (Float) -> Unit,
    onVolumeChangedFinished: () -> Unit,
) {
    // 竖向音量：只有竖向滑杆（从下往上滑动调大），高度与倍速面板一致。
    VerticalScrubber(
        fraction = volume,
        onFractionChange = onVolumeSelected,
        onFractionChangeFinished = onVolumeChangedFinished,
        modifier = Modifier.height(panelContentHeight),
    )
}

/** 控制条上方展开面板的载体：扁圆角卡片，白底 + 阴影，避免被胶囊的 50% 圆角过度裁剪。 */
@Composable
private fun ControlSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun ProgressPanel(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.Black,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * 自绘导轨：细轨道 + 填充段 + 圆形滑块，无边上的"竖线"。
 * [fraction] 为 0..1 归一化进度；[onFractionChange] 拖动/点击时连续回调，
 * [onFractionChangeFinished] 点击或拖动结束时回调一次（可空）。
 */
@Composable
private fun Scrubber(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFractionChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onFractionChange(f)
                    onFractionChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onFractionChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onFractionChangeFinished?.invoke() },
                    onDragCancel = { onFractionChangeFinished?.invoke() },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { 14.dp.toPx() }
        // 轨道底
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.12f)),
        )
        // 已播放/已设音量：填充段
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f)),
        )
        // 圆形滑块
        Box(
            modifier = Modifier
                .offset { IntOffset((fraction * (trackWidthPx - thumbSizePx)).roundToInt(), 0) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.Black),
        )
    }
}

/**
 * 竖向自绘导轨：底部为 0、顶部为 1，用于竖向音量滑杆（从下往上滑动调大）。
 * [onFractionChangeFinished] 点击或拖动结束时回调一次（可空）。
 */
@Composable
private fun VerticalScrubber(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFractionChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .width(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onFractionChange(f)
                    onFractionChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onFractionChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onFractionChangeFinished?.invoke() },
                    onDragCancel = { onFractionChangeFinished?.invoke() },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbSizePx = with(density) { 14.dp.toPx() }
        // 轨道底（竖线）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.12f)),
        )
        // 已设置音量：从底部向上填充
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight(fraction)
                .width(3.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f)),
        )
        // 圆形滑块：fraction 越大越靠上
        Box(
            modifier = Modifier
                .offset { IntOffset(0, -(fraction * (trackHeightPx - thumbSizePx)).roundToInt()) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.Black),
        )
    }
}

private fun formatPlayerDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds)
    val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)
    return "%d:%02d".format(minutes, seconds)
}

private fun formatSpeed(speed: Float): String =
    "%.2f".format(speed).trimEnd('0').trimEnd('.')

/**
 * 文件详情浮层：点击顶部文件名胶囊后弹出。
 * 半透明遮罩 + 居中白卡，展示文件名 / 路径 / 大小 / 拍摄时间；点遮罩或右上角关闭。
 */
@Composable
private fun MediaDetailOverlay(
    item: MediaItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                // 拦截卡片内点击，避免误触遮罩关闭。
                .clickable(onClick = {})
                .padding(horizontal = 36.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "文件详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.Black,
                    )
                }
            }
            HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
            DetailRow(label = "文件名", value = item.name)
            DetailRow(label = "文件路径", value = item.path)
            DetailRow(label = "文件大小", value = formatFileSize(item.size))
            DetailRow(label = "拍摄时间", value = formatDateTaken(item.dateTaken))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value.ifBlank { "无" },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 去掉文件扩展名（只对最后一个点之后的结尾，如 a.jpg → a；隐藏文件 .nomedia 保持不变）。 */
private fun stripExtension(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) else name
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "未知"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "%.1f GB".format(bytes / gb)
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        bytes >= kb -> "%.0f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}

private fun formatDateTaken(epochMs: Long): String {
    if (epochMs <= 0) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}
