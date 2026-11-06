package com.bocchi.tv.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.shared.AppGraph
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * 主题色板。
 *
 * 功能：与桌面端保持一致的深色观影配色。
 * 参数：无。
 * 返回值：ColorScheme —— Compose 主题配置。
 */
private val IosColorScheme = darkColorScheme(
    primary = Color(0xFFE5484D),
    onPrimary = Color.White,
    background = Color(0xFF14141A),
    onBackground = Color(0xFFE8E8ED),
    surface = Color(0xFF1C1C24),
    onSurface = Color(0xFFE8E8ED),
    surfaceVariant = Color(0xFF262630),
    onSurfaceVariant = Color(0xFF9A9AA8)
)

/**
 * iOS 端界面路由。
 *
 * 参数：Search 搜索页；Player 播放页。
 * 返回值：无（密封接口）。
 */
private sealed interface IosScreen {
    data object Search : IosScreen
    data class Player(val video: AggregatedVideo, val match: VideoSummary) : IosScreen
}

/**
 * iOS 应用根界面。
 *
 * 功能：承载搜索与播放两个页面；搜索采用流式聚合，边完成边刷新结果。
 * 参数：无。
 * 返回值：无。
 */
@Composable
fun IosApp() {
    var screen by remember { mutableStateOf<IosScreen>(IosScreen.Search) }
    var keyword by remember { mutableStateOf("") }
    var aggregate by remember { mutableStateOf(AggregateResult()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /**
     * 执行聚合搜索。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun runSearch() {
        val query = keyword.trim()
        if (query.isEmpty() || isSearching) return
        errorMessage = null
        aggregate = AggregateResult()
        isSearching = true
        scope.launch {
            runCatching {
                AppGraph.videoRepository.search(query).collect { snapshot -> aggregate = snapshot }
            }.onFailure { error ->
                errorMessage = "搜索失败：${error.message ?: "网络异常"}"
            }
            isSearching = false
        }
    }

    MaterialTheme(colorScheme = IosColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val current = screen) {
                is IosScreen.Search -> SearchScreen(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onSearch = ::runSearch,
                    isSearching = isSearching,
                    aggregate = aggregate,
                    errorMessage = errorMessage,
                    hasSearched = keyword.isNotBlank() && (aggregate.totalSources > 0 || isSearching),
                    onPlay = { video, match -> screen = IosScreen.Player(video, match) }
                )

                is IosScreen.Player -> PlayerScreen(
                    video = current.video,
                    initialMatch = current.match,
                    onBack = { screen = IosScreen.Search }
                )
            }
        }
    }
}

/**
 * 搜索页。
 *
 * 参数：keyword 关键词；onKeywordChange 输入回调；onSearch 触发搜索；isSearching 是否搜索中；
 *      aggregate 聚合快照；errorMessage 错误提示；hasSearched 是否已发起搜索；onPlay 点击回调。
 * 返回值：无。
 */
@Composable
internal fun SearchScreen(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    aggregate: AggregateResult,
    errorMessage: String?,
    hasSearched: Boolean,
    onPlay: (AggregatedVideo, VideoSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(56.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(text = "${AppGraph.sourceStore.enabled().size} 个片源同时搜索") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSearch, enabled = !isSearching) { Text(text = "搜索") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                aggregate.totalSources > 0 -> Text(
                    text = "已完成 ${aggregate.finishedSources}/${aggregate.totalSources} 个源，共 ${aggregate.items.size} 部",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            !hasSearched -> CenterHint(text = "输入片名开始搜索\n一次搜索，多个片源同时替你找")

            aggregate.isComplete && aggregate.items.isEmpty() -> CenterHint(
                text = "没有找到相关影片\n换个片名或简称再试试"
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(aggregate.items, key = { it.matches.first().uniqueKey }) { video ->
                    VideoCard(video = video, onClick = { onPlay(video, video.matches.first()) })
                }
            }
        }
    }
}

/**
 * 空状态提示。
 *
 * 参数：text 提示文案。
 * 返回值：无。
 */
@Composable
internal fun CenterHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 影片卡片。
 *
 * 参数：video 聚合条目；onClick 点击回调。
 * 返回值：无。
 */
@Composable
internal fun VideoCard(video: AggregatedVideo, onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Poster(url = video.poster, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${video.year.ifBlank { video.remarks }} · ${video.sourceCount} 源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 网络海报。
 *
 * 功能：用共享 HTTP 客户端下载图片，再用 Skia 解码；失败时显示占位色块。
 * 参数：url 图片地址；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
internal fun Poster(url: String, modifier: Modifier = Modifier) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        image = if (url.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching {
                    val bytes: ByteArray = AppGraph.httpClient.get(url) {
                        header("User-Agent", POSTER_USER_AGENT)
                    }.body()
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * 播放页。
 *
 * 功能：顶部为 AVPlayer 画面，底部为进度、片源、线路与选集；选中剧集后先解析地址再播放。
 * 参数：video 聚合条目；initialMatch 初始片源；initialLineIndex 初始线路；initialEpisodeIndex 初始集数；initialPositionMs 起播位置；onBack 返回回调。
 * 返回值：无。
 */
@Composable
internal fun PlayerScreen(
    video: AggregatedVideo,
    initialMatch: VideoSummary,
    initialLineIndex: Int = 0,
    initialEpisodeIndex: Int = 0,
    initialPositionMs: Long = 0L,
    onBack: () -> Unit
) {
    val controller = remember { IosPlayerController() }
    var currentMatch by remember { mutableStateOf(initialMatch) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var lineIndex by remember { mutableStateOf(initialLineIndex) }
    var episodeIndex by remember { mutableStateOf(initialEpisodeIndex) }
    var pendingSeek by remember { mutableStateOf(initialPositionMs) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var totalMs by remember { mutableStateOf(0L) }
    var volume by remember { mutableStateOf(80) }

    LaunchedEffect(Unit) {
        controller.setVolume(volume)
    }

    LaunchedEffect(currentMatch) {
        detail = withContext(Dispatchers.Default) {
            AppGraph.videoRepository.loadDetail(currentMatch)
        }
        if (detail == null) statusMessage = "详情加载失败，请切换其他片源重试"
    }

    LaunchedEffect(detail, lineIndex, episodeIndex) {
        val lines = detail?.lines ?: return@LaunchedEffect
        val episode = lines.getOrNull(lineIndex)?.episodes?.getOrNull(episodeIndex) ?: return@LaunchedEffect
        isPreparing = true
        statusMessage = "正在解析播放地址…"
        val mediaUrl = withContext(Dispatchers.Default) {
            AppGraph.videoRepository.resolvePlayUrl(episode.url)
        }
        isPreparing = false
        if (mediaUrl == null) {
            statusMessage = "该集地址无法解析为可播放的视频，请切换线路"
        } else if (controller.play(mediaUrl)) {
            statusMessage = null
            controller.setVolume(volume)
            val seekTo = pendingSeek
            if (seekTo > 0L) {
                delay(900)
                controller.seekToMillis(seekTo)
                pendingSeek = 0L
            }
        } else {
            statusMessage = "播放器启动失败"
        }
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            val (current, total) = controller.progress()
            elapsedMs = current
            totalMs = total
            if (tick % 10 == 0 && total > 0) {
                val episodeName = detail?.lines?.getOrNull(lineIndex)
                    ?.episodes?.getOrNull(episodeIndex)?.name.orEmpty()
                runCatching {
                    AppGraph.historyStore.record(
                        summary = currentMatch,
                        episodeName = episodeName,
                        episodeIndex = episodeIndex,
                        positionMs = current,
                        durationMs = total
                    )
                }
            }
            tick++
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(56.dp))
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            PlayerSurface(controller = controller, modifier = Modifier.fillMaxSize())
            if (isPreparing) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(elapsedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = if (totalMs > 0) elapsedMs.toFloat() / totalMs else 0f,
                    onValueChange = { controller.seekTo(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = formatTime(totalMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(text = "返回") }
                TextButton(onClick = {
                    if (controller.isPlaying()) controller.pause() else controller.resume()
                }) { Text(text = "播放 / 暂停") }
                Spacer(modifier = Modifier.weight(1f))
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
                    modifier = Modifier.width(110.dp)
                )
            }
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(video.year, video.typeName, video.remarks)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (video.matches.size > 1) {
                item {
                    Text(
                        text = "片源（${video.matches.size}）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
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
                            .clickable {
                                if (!selected) currentMatch = match
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            val lines = detail?.lines.orEmpty()
            if (lines.size > 1) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
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
                }
            }
            val episodes = lines.getOrNull(lineIndex)?.episodes.orEmpty()
            item {
                Text(
                    text = "选集（${episodes.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
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

/**
 * 把毫秒格式化为 mm:ss 或 h:mm:ss。
 *
 * 参数：millis —— 毫秒数。
 * 返回值：String —— 可展示的时间文本。
 */
internal fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/** 加载海报时使用的 UA，避免部分站点拒绝默认 UA。 */
private const val POSTER_USER_AGENT: String =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
