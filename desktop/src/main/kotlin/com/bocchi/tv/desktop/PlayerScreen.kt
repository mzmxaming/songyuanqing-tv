package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.player.VlcPlayerController
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放页。
 *
 * 功能：左侧为 VLC 播放区域与播放控制条，右侧为数据源、线路与剧集列表。
 *      选中剧集后先把采集地址解析为可播放地址，再交给播放器；一集播完自动接下一集，
 *      最后一集播完停止。播放失败会给出可读提示，而不是停在黑屏。
 * 参数：video 聚合条目；initialMatch 初始选中的数据源命中；onBack 返回搜索页回调。
 * 返回值：无。
 */
@Composable
fun PlayerScreen(
    video: AggregatedVideo,
    initialMatch: VideoSummary,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = remember { VlcPlayerController() }
    val listState = rememberLazyListState()

    var currentMatch by remember { mutableStateOf(initialMatch) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var engineError by remember { mutableStateOf<String?>(null) }
    var lineIndex by remember { mutableStateOf(0) }
    var episodeIndex by remember { mutableStateOf(0) }
    var isPreparing by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var totalMs by remember { mutableStateOf(0L) }
    var volume by remember { mutableStateOf(80) }
    var autoNext by remember { mutableStateOf(true) }

    // 播放引擎初始化：失败时把原因显示在画面区域，避免白屏无提示。
    // 播放结束监听必须等播放器创建成功后再注册，否则监听器会挂到 null 上而失效。
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { controller.initialize() }
        if (result.available) {
            controller.setVolume(volume)
            controller.setOnFinished {
                if (!autoNext) return@setOnFinished
                val lines = detail?.lines ?: return@setOnFinished
                val currentEpisodes = lines.getOrNull(lineIndex)?.episodes ?: return@setOnFinished
                if (episodeIndex + 1 < currentEpisodes.size) {
                    javax.swing.SwingUtilities.invokeLater {
                        episodeIndex += 1
                    }
                }
            }
        } else {
            engineError = result.message
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    // 加载详情（切换数据源时重新拉取）
    LaunchedEffect(currentMatch) {
        isLoading = true
        statusMessage = null
        detail = withContext(Dispatchers.IO) { AppGraph.videoRepository.loadDetail(currentMatch) }
        isLoading = false
        if (detail == null) {
            statusMessage = "详情加载失败，请切换其他片源重试"
        } else {
            lineIndex = 0
        }
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
        } else {
            statusMessage = "播放器启动失败，请确认已安装 VLC"
        }
    }

    // 轮询播放进度
    LaunchedEffect(Unit) {
        while (true) {
            val (current, total) = controller.progress()
            elapsedMs = current
            totalMs = total
            delay(500)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            // 画面区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    engineError != null -> Text(
                        text = engineError!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
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
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // 控制条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(elapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = if (totalMs > 0) elapsedMs.toFloat() / totalMs else 0f,
                        onValueChange = { controller.seekTo(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text(
                        text = formatTime(totalMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onBack, modifier = Modifier.width(88.dp)) {
                        Text(text = "返回")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        val (_, total) = controller.progress()
                        if (total > 0) controller.pause() else controller.resume()
                    }) {
                        Text(text = "播放 / 暂停")
                    }
                    TextButton(onClick = { controller.pause() }) { Text(text = "暂停") }
                    TextButton(onClick = { controller.resume() }) { Text(text = "继续") }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "音量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = {
                            volume = it.toInt()
                            controller.setVolume(volume)
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (autoNext) "自动连播：开" else "自动连播：关",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (autoNext) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { autoNext = !autoNext }
                    )
                }
            }
        }

        // 右侧：片源、线路与剧集
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
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

            // 片源切换
            val matches = video.matches
            if (matches.size > 1) {
                Text(
                    text = "片源（${matches.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(96.dp)) {
                    itemsIndexed(matches) { index, match ->
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
                                .clickable {
                                    if (!selected) {
                                        currentMatch = match
                                        episodeIndex = 0
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 线路切换
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
                                .clickable {
                                    lineIndex = index
                                    episodeIndex = 0
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 剧集列表
            val episodes = lines.getOrNull(lineIndex)?.episodes.orEmpty()
            Text(
                text = "选集（${episodes.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                            .clickable { episodeIndex = index }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

/**
 * 把毫秒格式化为 mm:ss 或 h:mm:ss。
 *
 * 参数：millis —— 毫秒数。
 * 返回值：String —— 可展示的时间文本。
 */
private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
