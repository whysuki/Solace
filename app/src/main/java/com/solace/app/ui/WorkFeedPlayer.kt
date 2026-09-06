package com.solace.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.solace.app.data.MediaItem
import com.solace.app.data.MediaType
import com.solace.app.data.portfolio.WorkSummary
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 作品详情 feed（抖音式，见 AGENTS.md §8）：外层纵向分页切换同一作品集内的作品，
 * 每页内部横向分页切换该作品的素材（顺序 = 素材 sortOrder）。
 *
 * 交互规则：
 * - 底部 chrome 常驻（不随点击隐藏）：作品名称 + 视频进度条（拖动/点按 seek、按住显示时长）
 *   / 多素材图片位置横线；视频滑到当前页自动播放（循环）。
 * - 点击画面：视频 = 切换播放/暂停，图片 = 仅切换返回箭头显隐；返回箭头进入时隐藏、
 *   点击后显示，纵向滑动落定复位隐藏（与自动播放配对）。
 */
@Composable
internal fun WorkFeedPlayer(
    ui: PortfolioUiState,
    viewModel: PortfolioViewModel,
) {
    val works = ui.works
    if (works.isEmpty()) return
    val initialPage = works.indexOfFirst { it.id == ui.selectedWork?.id }
        .coerceAtLeast(0)
        .coerceAtMost(works.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { works.size }
    // 返回箭头显隐（进入默认隐藏）：点击画面切换；视频点击同时切换播放/暂停。
    // 作品名称 + 底部进度条/横线常驻显示，不随点击隐藏。
    var backVisible by remember { mutableStateOf(false) }

    // 滑动落定：更新锚点作品（驱动关闭判定/刷新）并预载相邻作品素材；箭头复位隐藏（与自动播放配对）。
    LaunchedEffect(pagerState.settledPage) {
        works.getOrNull(pagerState.settledPage)?.let { viewModel.onFeedSettled(it.id) }
        backVisible = false
    }

    BackHandler(onBack = viewModel::backFromWork)

    // 沉浸黑底：与全屏播放器一致，不叠加系统栏 inset，chrome 自行 padding。
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { works.getOrNull(it)?.id ?: it },
        ) { page ->
            val work = works[page]
            WorkFeedPage(
                work = work,
                workItems = ui.workItems[work.id],
                isPageActive = page == pagerState.settledPage,
                onToggleBack = { backVisible = !backVisible },
            )
        }

        // 返回箭头：仅在点击画面后显示（左上角白色箭头，无胶囊底），其余 chrome 常驻。
        AnimatedVisibility(
            visible = backVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            FeedBackArrow(onClose = viewModel::backFromWork)
        }
    }
}

/** 单作品页：素材未加载显示圈；空作品显示空态；有素材则内层横向分页 + 底部常驻进度条/位置横线。 */
@Composable
private fun WorkFeedPage(
    work: WorkSummary,
    workItems: WorkItemsState?,
    isPageActive: Boolean,
    onToggleBack: () -> Unit,
) {
    when {
        workItems == null || workItems.loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        workItems.items.isEmpty() -> {
            // 空态页只是 feed 的一页，不打断纵向滑动（无返回按钮/BackHandler）。
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = work.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "作品内暂无可用素材",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        else -> {
            val items = workItems.items
            val innerPagerState = rememberPagerState(pageCount = { items.size })
            val settledItem = items[innerPagerState.settledPage.coerceAtMost(items.lastIndex)]
            // 当前内层页播放器暴露给底部进度条（仅落定页设置）。
            var videoHolder by remember { mutableStateOf<ExoPlayer?>(null) }
            // 内容永远全屏铺底，底部 chrome 悬浮叠加——不能占布局空间（否则切换进度/清空态时
            // 媒体高度变化，ContentScale.Fit 的画面重新居中而上下偏移）。
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = innerPagerState,
                    beyondViewportPageCount = 1,
                    key = { items.getOrNull(it)?.key ?: it.toString() },
                ) { page ->
                    val item = items[page]
                    val innerActive = page == innerPagerState.settledPage
                    if (item.type == MediaType.VIDEO) {
                        FeedVideoPage(
                            item = item,
                            isActive = isPageActive && innerActive,
                            onToggleBack = onToggleBack,
                            onHolder = { videoHolder = it },
                        )
                    } else {
                        ImagePage(item = item, onToggle = onToggleBack)
                    }
                }
                // 底部常驻（悬浮叠加，不占布局空间）：作品名称 + 固定高度的指示槽
                // （视频进度条 / 图片位置横线）。指示槽恒为 48dp，作品名固定在其上方。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp),
                ) {
                    // 作品名称：左对齐单行省略，位置固定（下方恒为 8dp 间距 + 48dp 指示槽）。
                    Text(
                        text = work.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(shadow = overlayTextShadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (settledItem.type == MediaType.VIDEO) {
                        FeedVideoProgressBar(
                            holder = videoHolder,
                            fallbackDurationMs = settledItem.durationMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    } else {
                        // 图片：多素材显示位置横线，单素材槽留空——两者都保持同一底部位置。
                        Box(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (items.size > 1) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FeedPositionLines(
                                        index = innerPagerState.settledPage,
                                        count = items.size,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * feed 视频页：ExoPlayer + 自动播放（循环）+ 点击画面切换播放/暂停并同步切换返回箭头显隐。
 * 无控制条/倍速/音量（按需求移除）；进度条由页面底部 [FeedVideoProgressBar] 渲染。
 */
@Composable
private fun FeedVideoPage(
    item: MediaItem,
    isActive: Boolean,
    onToggleBack: () -> Unit,
    onHolder: (ExoPlayer?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember { mutableStateOf(false) }
    // 是否在前台：ON_STOP 暂停，ON_START 时自动播放页恢复。
    var appVisible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    // 播放/暂停状态（中心播放按钮显隐依据）。
    var isPlaying by remember { mutableStateOf(false) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            // 作品 feed 循环播放：播完自动从头继续，无播完定格态。
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
        }
    }

    // 同步 isPlaying：暂停态在中心显示播放按钮，恢复播放后隐藏。
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 仅页面卸载时释放播放器；进入/离开后台只暂停、回前台由下方 effect 恢复。
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appVisible = true
                Lifecycle.Event.ON_STOP -> {
                    appVisible = false
                    player.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    // 抖音式：滑到当前页且前台自动播放（播完回退重播）；滑走/后台暂停。
    LaunchedEffect(isActive, appVisible) {
        if (isActive && appVisible) {
            started = true
            if (player.playbackState == ExoPlayer.STATE_ENDED) player.seekTo(0)
            player.play()
        } else if (!isActive) {
            player.pause()
        }
    }

    // 当前页的播放器交给外层（底部进度条使用）；离开当前页即解除。
    LaunchedEffect(isActive) {
        if (isActive) onHolder(player) else onHolder(null)
    }

    val togglePlayPause: () -> Unit = {
        if (!started) {
            started = true
            player.play()
        } else if (player.playbackState == ExoPlayer.STATE_ENDED) {
            // 播完：清空态点击 = 回到片头暂停，再点恢复播放。
            player.seekTo(0)
            player.pause()
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
        // 点击画面：视频 = 切换播放/暂停 + 切换返回箭头显隐；图片走 ImagePage 只切箭头。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = {
                    onToggleBack()
                    togglePlayPause()
                }),
        )
        // 中心播放按钮（仿抖音）：点击暂停后显示（灰底圆 + 白三角），恢复播放后隐藏。无手势，
        // 点击穿透到下方控制层。
        if (started && !isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/**
 * 视频底部进度条：细线样式（抖音式）。
 * 拖动/点按 = seek（松开提交）；按住（含长按/拖动中）= 上方显示「当前 / 总时长」；
 * 纵向滑动起点在进度条上时不消费，交给外层分页。
 */
@Composable
private fun FeedVideoProgressBar(
    holder: ExoPlayer?,
    fallbackDurationMs: Long,
    modifier: Modifier = Modifier,
) {
    // 播放器可能已被释放（翻页卸页竞态），读取一律 runCatching。
    val durationMs = (runCatching { holder?.duration }.getOrNull()?.takeIf { it > 0 } ?: fallbackDurationMs).coerceAtLeast(0L)
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }
    var showTime by remember { mutableStateOf(false) }

    // 播放位置轮询（拖动时暂停更新，避免回跳）。
    LaunchedEffect(holder) {
        while (true) {
            if (!dragging) {
                position = runCatching { holder?.currentPosition }.getOrNull()?.coerceAtLeast(0L) ?: 0L
            }
            delay(250)
        }
    }

    val displayPosition = if (dragging) dragValue else position
    val fraction = if (durationMs > 0) (displayPosition.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // 固定 48dp 槽高：上 20dp 为时长文字区（按住时显示，不占流式空间外的位置），
    // 下 28dp 为进度条（3dp 视觉线上下居中）——与图片位置横线的视觉线距底部等距。
    Column(modifier = modifier.height(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 按住/拖动时显示时长（长按查看时长，拖动时同步预览）。
            if (showTime) {
                Text(
                    text = "${formatPlayerDuration(displayPosition)} / ${formatPlayerDuration(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = overlayTextShadow),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(holder) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downTime = SystemClock.uptimeMillis()
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        var longPress = false
                        var lastFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // 松手：拖动/点按过才真正 seek（长按未拖动仅预览时长）。
                                if (dragging || !longPress) {
                                    val target = (lastFraction * durationMs).toLong()
                                    runCatching { holder?.seekTo(target) }
                                    position = target
                                }
                                dragging = false
                                showTime = false
                                if (dragging || longPress) change.consume()
                                break
                            }
                            if (!dragging) {
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                // 纵向起点：交给外层分页，不消费（否则上下切作品会被进度条挡住）。
                                if (abs(dy) > slop && abs(dy) > abs(dx)) {
                                    showTime = false
                                    break
                                }
                                // 横向越过阈值 = 进度条手势（消费，内层分页不接管）。
                                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                                    dragging = true
                                }
                            }
                            if (dragging || longPress) {
                                lastFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                if (dragging) {
                                    change.consume()
                                    dragValue = (lastFraction * durationMs).toLong()
                                }
                                showTime = true
                            } else if (SystemClock.uptimeMillis() - downTime >= LONG_PRESS_MS) {
                                // 长按阈值（未横向拖动）：显示「当前 / 总时长」，松手不 seek。
                                longPress = true
                                showTime = true
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 轨道 + 填充（细线，无滑块）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private const val LONG_PRESS_MS = 400L

/**
 * 抖音式位置横线：细横条，当前项高亮白色、其余半透明白；随素材数量自适应线宽。
 */
@Composable
private fun FeedPositionLines(
    index: Int,
    count: Int,
) {
    val gap = 5.dp
    // 总宽上限防溢出：数量越多线越细，最细 3dp。
    val lineWidth = ((260.dp - gap * (count - 1)) / count).coerceIn(3.dp, 20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .width(lineWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

/** 清空态的顶部返回按钮：只有白色箭头（无胶囊底、无其他组件）。 */
@Composable
private fun FeedBackArrow(
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
