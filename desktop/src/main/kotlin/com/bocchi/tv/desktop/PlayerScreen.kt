package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.player.VlcPlayerController
import com.bocchi.tv.desktop.ui.formatTime
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities

/**
 * 播放页。
 *
 * 功能：左侧为 VLC 画面与控制条，右侧为片源、线路与剧集；支持窗口全屏（F / 双击按钮 / Esc 退出）、
 *      竖屏短剧按原始画幅展示、空格暂停、左右方向键前后 10 秒，并定时写入播放历史实现续播。
 * 参数：video 聚合条目；initialMatch 初始片源；initialLineIndex 初始线路；initialEpisodeIndex 初始集数；
 *      initialPositionMs 起播位置；isFullscreen 是否处于全屏；onToggleFullscreen 切换全屏；onBack 返回。
 * 返回值：无。
 */
@Composable
fun PlayerScreen(
    video: AggregatedVideo,
    initialMatch: VideoSummary,
    initialLineIndex: Int = 0,
    initialEpisodeIndex: Int = 0,
    initialPositionMs: Long = 0L,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit
) {
    val controller = remember { VlcPlayerController() }
    val focusRequester = remember { FocusRequester() }

    var currentMatch by remember { mutableStateOf(initialMatch) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var engineError by remember { mutableStateOf<String?>(null) }
    var lineIndex by remember { mutableStateOf(initialLineIndex) }
    var episodeIndex by remember { mutableStateOf(initialEpisodeIndex) }
    var pendingSeek by remember { mutableStateOf(initialPositionMs) }
    var isPreparing by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var totalMs by remember { mutableStateOf(0L) }
    var volume by remember { mutableStateOf(80) }
    var autoNext by remember { mutableStateOf(true) }
    var videoAspect by remember { mutableStateOf<Float?>(null) }

    // 播放引擎初始化：失败时给出可读提示；监听器必须初始化成功后再注册。
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { controller.initialize() }
        if (result.available) {
            controller.setVolume(volume)
            controller.setOnFinished {
                if (!autoNext) return@setOnFinished
                val lines = detail?.lines ?: return@setOnFinished
                val episodes = lines.getOrNull(lineIndex)?.episodes ?: return@setOnFinished
                if (episodeIndex + 1 < episodes.size) {
                    SwingUtilities.invokeLater { episodeIndex += 1 }
                }
            }
        } else {
            engineError = result.message
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveHistory(controller.progress().first, controller.progress().second, currentMatch, detail, lineIndex, episodeIndex)
            controller.release()
        }
    }

    // 详情加载（切换片源时重新拉取）
    LaunchedEffect(currentMatch) {
        isLoading = true
        statusMessage = null
        detail = withContext(Dispatchers.IO) { AppGraph.videoRepository.loadDetail(currentMatch) }
        isLoading = false
        if (detail == null) statusMessage = "详情加载失败，请切换其他片源重试"
    }

    // 选中剧集变化时解析并播放
    LaunchedEffect(detail, lineIndex, episodeIndex) {
        val lines = detail?.lines ?: return@LaunchedEffect
        val episode = lines.getOrNull(lineIndex)?.episodes?.getOrNull(episodeIndex) ?: return@LaunchedEffect
        if (engineError != null) return@LaunchedEffect
        isPreparing = true
        statusMessage = "正在解析播放地址…"
        val mediaUrl = withContext(Dispatchers.IO) {
            AppGraph.videoRepository.resolvePlayUrl(episode.url)
        }
        isPreparing = false
        if (mediaUrl == null) {
            statusMessage = "该集地址无法解析为可播放的视频，请尝试切换线路"
        } else if (controller.play(mediaUrl)) {
            statusMessage = null
            val seekTo = pendingSeek
            if (seekTo > 0L) {
                delay(900)
                controller.seekToMillis(seekTo)
                pendingSeek = 0L
            }
        } else {
            statusMessage = "播放器启动失败，请确认已安装 VLC"
        }
    }

    // 进度轮询 + 画幅探测 + 定时写历史
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            val (current, total) = controller.progress()
            elapsedMs = current
            totalMs = total
            videoAspect = controller.videoSize()?.let { (w, h) ->
                if (h > 0) w.toFloat() / h.toFloat() else null
            } ?: videoAspect
            if (tick % 10 == 0 && total > 0) {
                saveHistory(current, total, currentMatch, detail, lineIndex, episodeIndex)
            }
            tick++
            delay(500)
        }
    }

    // 键盘快捷键：空格暂停/继续、左右方向键 ±10 秒、F 全屏、Esc 退出全屏
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val rootModifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Spacebar -> {
                    if (controller.isPlaying()) controller.pause() else controller.resume()
                    true
                }

                Key.DirectionLeft -> {
                    controller.seekToMillis(controller.progress().first - 10_000L)
                    true
                }

                Key.DirectionRight -> {
                    controller.seekToMillis(controller.progress().first + 10_000L)
                    true
                }

                Key.F -> {
                    onToggleFullscreen()
                    true
                }

                Key.Escape -> {
                    onBack()
                    true
                }

                else -> false
            }
        }
        .focusRequester(focusRequester)
        .focusable()

    val aspect = videoAspect
    val isPortrait = aspect != null && aspect < 1f

    if (isFullscreen) {
        // 采用「画面在上 + 控制条在下」的纵向布局：Swing/AWT 画布是重层级组件，
        // 若与 Compose 内容重叠会盖住控制条，因此这里不让二者重叠。
        Column(modifier = rootModifier) {
            VideoArea(
                controller = controller,
                engineError = engineError,
                isPreparing = isPreparing,
                statusMessage = statusMessage,
                isPortrait = isPortrait,
                aspect = aspect,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            ControlBar(
                isFullscreen = true,
                elapsedMs = elapsedMs,
                totalMs = totalMs,
                volume = volume,
                autoNext = autoNext,
                onToggleFullscreen = onToggleFullscreen,
                onBack = onBack,
                onSeek = { controller.seekTo(it) },
                onVolumeChange = {
                    volume = it
                    controller.setVolume(volume)
                },
                onToggleAutoNext = { autoNext = !autoNext },
                onTogglePlay = {
                    if (controller.isPlaying()) controller.pause() else controller.resume()
                },
                onPrev = { if (episodeIndex > 0) episodeIndex -= 1 },
                onNext = {
                    val episodes = detail?.lines?.getOrNull(lineIndex)?.episodes.orEmpty()
                    if (episodeIndex + 1 < episodes.size) episodeIndex += 1
                },
                modifier = Modifier.height(112.dp)
            )
        }
        return
    }

    Row(modifier = rootModifier) {
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            VideoArea(
                controller = controller,
                engineError = engineError,
                isPreparing = isPreparing,
                statusMessage = statusMessage,
                isPortrait = isPortrait,
                aspect = aspect,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            ControlBar(
                isFullscreen = false,
                elapsedMs = elapsedMs,
                totalMs = totalMs,
                volume = volume,
                autoNext = autoNext,
                onToggleFullscreen = onToggleFullscreen,
                onBack = onBack,
                onSeek = { controller.seekTo(it) },
                onVolumeChange = {
                    volume = it
                    controller.setVolume(volume)
                },
                onToggleAutoNext = { autoNext = !autoNext },
                onTogglePlay = {
                    if (controller.isPlaying()) controller.pause() else controller.resume()
                },
                onPrev = { if (episodeIndex > 0) episodeIndex -= 1 },
                onNext = {
                    val episodes = detail?.lines?.getOrNull(lineIndex)?.episodes.orEmpty()
                    if (episodeIndex + 1 < episodes.size) episodeIndex += 1
                },
                modifier = Modifier.height(112.dp)
            )
        }

        SidePanel(
            video = video,
            currentMatch = currentMatch,
            detail = detail,
            isLoading = isLoading,
            lineIndex = lineIndex,
            episodeIndex = episodeIndex,
            onBack = onBack,
            onToggleFullscreen = onToggleFullscreen,
            onSelectMatch = {
                if (it.uniqueKey != currentMatch.uniqueKey) {
                    currentMatch = it
                    episodeIndex = 0
                    pendingSeek = 0L
                }
            },
            onSelectLine = {
                lineIndex = it
                episodeIndex = 0
                pendingSeek = 0L
            },
            onSelectEpisode = { index ->
                if (index != episodeIndex) {
                    pendingSeek = 0L
                    episodeIndex = index
                }
            }
        )
    }
}

/**
 * 写入播放历史。
 *
 * 参数：positionMs 当前位置；durationMs 总时长；match 当前片源；detail 详情；lineIndex 线路；episodeIndex 集数。
 * 返回值：无。
 */
private fun saveHistory(
    positionMs: Long,
    durationMs: Long,
    match: VideoSummary,
    detail: VideoDetail?,
    lineIndex: Int,
    episodeIndex: Int
) {
    if (durationMs <= 0L) return
    val episodeName = detail?.lines?.getOrNull(lineIndex)?.episodes?.getOrNull(episodeIndex)?.name.orEmpty()
    runCatching {
        AppGraph.historyStore.record(
            summary = match,
            episodeName = episodeName,
            episodeIndex = episodeIndex,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }
}

/**
 * 视频画面区域。
 *
 * 参数：controller 播放器；engineError 引擎错误；isPreparing 是否解析中；statusMessage 状态提示；
 *      isPortrait 是否竖屏画幅；aspect 画幅比例；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
private fun VideoArea(
    controller: VlcPlayerController,
    engineError: String?,
    isPreparing: Boolean,
    statusMessage: String?,
    isPortrait: Boolean,
    aspect: Float?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            engineError != null -> Text(
                text = engineError,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )

            isPortrait -> SwingPanel(
                factory = { controller.canvas },
                modifier = Modifier.fillMaxHeight().aspectRatio(aspect ?: (9f / 16f))
            )

            else -> SwingPanel(
                factory = { controller.canvas },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isPreparing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * 播放控制条。
 *
 * 参数：见各字段；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
private fun ControlBar(
    isFullscreen: Boolean,
    elapsedMs: Long,
    totalMs: Long,
    volume: Int,
    autoNext: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleAutoNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isFullscreen) Color(0xCC000000) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTime(elapsedMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = if (totalMs > 0) elapsedMs.toFloat() / totalMs else 0f,
                onValueChange = onSeek,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text(
                text = formatTime(totalMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(modifier = Modifier.height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, modifier = Modifier.width(88.dp)) { Text(text = "返回") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onPrev) { Text(text = "上一集") }
            TextButton(onClick = onTogglePlay) { Text(text = "播放 / 暂停") }
            TextButton(onClick = onNext) { Text(text = "下一集") }
            TextButton(onClick = onToggleFullscreen) {
                Text(text = if (isFullscreen) "退出全屏" else "全屏")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "音量",
                style = MaterialTheme.typography.labelSmall,
                color = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(120.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (autoNext) "自动连播：开" else "自动连播：关",
                style = MaterialTheme.typography.labelSmall,
                color = if (autoNext) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onToggleAutoNext)
            )
            if (isFullscreen) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "空格 暂停 · ←→ 快进退 · F 全屏",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9A9AA8)
                )
            }
        }
    }
}

/**
 * 右侧片源 / 线路 / 选集面板。
 *
 * 参数：见各字段。
 * 返回值：无。
 */
@Composable
private fun SidePanel(
    video: AggregatedVideo,
    currentMatch: VideoSummary,
    detail: VideoDetail?,
    isLoading: Boolean,
    lineIndex: Int,
    episodeIndex: Int,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectMatch: (VideoSummary) -> Unit,
    onSelectLine: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack, modifier = Modifier.width(88.dp)) { Text(text = "返回") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onToggleFullscreen) { Text(text = "全屏") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = listOf(video.year, video.typeName, video.remarks)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            return@Column
        }

        if (video.matches.size > 1) {
            Text(
                text = "片源（${video.matches.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.height(96.dp)) {
                itemsIndexed(video.matches) { _, match ->
                    val selected = match.uniqueKey == currentMatch.uniqueKey
                    Text(
                        text = match.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                            )
                            .clickable { onSelectMatch(match) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val lines = detail?.lines.orEmpty()
        if (lines.size > 1) {
            Text(
                text = "线路（${lines.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEachIndexed { index, line ->
                    Text(
                        text = line.lineName.ifBlank { "线路${index + 1}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == lineIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == lineIndex) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable { onSelectLine(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val episodes = lines.getOrNull(lineIndex)?.episodes.orEmpty()
        Text(
            text = "选集（${episodes.size}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(episodes) { index, episode ->
                val selected = index == episodeIndex
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        )
                        .clickable { onSelectEpisode(index) }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                )
            }
        }
    }
}
