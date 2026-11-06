package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO

/**
 * 应用主题色板。
 *
 * 功能：统一的深色配色，接近观影场景，长时间浏览不刺眼。
 * 参数：无。
 * 返回值：ColorScheme —— Compose 主题配置。
 */
private val AppColorScheme = darkColorScheme(
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
 * 界面路由。
 *
 * 参数：Search 搜索与结果；Player 播放页。
 * 返回值：无（密封接口）。
 */
private sealed interface AppScreen {
    data object Search : AppScreen
    data class Player(val video: AggregatedVideo, val match: VideoSummary) : AppScreen
}

/**
 * 桌面应用主界面。
 *
 * 功能：承载顶部搜索栏与内容区，在「搜索结果 / 播放页」之间切换；搜索结果采用流式刷新，
 *      每完成一个数据源就更新一次，因此用户不必等全部源返回。
 * 参数：无。
 * 返回值：无。
 */
@Composable
fun DesktopApp() {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Search) }
    var keyword by remember { mutableStateOf("") }
    var aggregate by remember { mutableStateOf(AggregateResult()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /**
     * 执行搜索。
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
                AppGraph.videoRepository.search(query).collect { snapshot ->
                    aggregate = snapshot
                }
            }.onFailure { error ->
                errorMessage = "搜索失败：${error.message ?: "网络异常"}"
            }
            isSearching = false
        }
    }

    MaterialTheme(colorScheme = AppColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onSearch = ::runSearch,
                    enabled = !isSearching
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val current = screen) {
                        is AppScreen.Search -> SearchContent(
                            aggregate = aggregate,
                            isSearching = isSearching,
                            errorMessage = errorMessage,
                            hasSearched = keyword.isNotBlank() && (aggregate.totalSources > 0 || isSearching),
                            onPlay = { video, match -> screen = AppScreen.Player(video, match) }
                        )

                        is AppScreen.Player -> PlayerScreen(
                            video = current.video,
                            initialMatch = current.match,
                            onBack = { screen = AppScreen.Search }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部搜索栏。
 *
 * 参数：keyword 当前关键词；onKeywordChange 输入回调；onSearch 触发搜索；enabled 是否可交互。
 * 返回值：无。
 */
@Composable
private fun TopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "松源情·TV",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(24.dp))
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.width(420.dp),
            singleLine = true,
            placeholder = { Text(text = "输入片名，同时搜索 ${AppGraph.sourceStore.enabled().size} 个片源") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        Spacer(modifier = Modifier.width(12.dp))
        Button(onClick = onSearch, enabled = enabled) {
            Text(text = "搜索")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "APP 设计开发：陈光明",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 搜索结果区。
 *
 * 参数：aggregate 聚合快照；isSearching 是否搜索中；errorMessage 错误提示；
 *      hasSearched 是否已发起过搜索；onPlay 点击条目回调。
 * 返回值：无。
 */
@Composable
private fun SearchContent(
    aggregate: AggregateResult,
    isSearching: Boolean,
    errorMessage: String?,
    hasSearched: Boolean,
    onPlay: (AggregatedVideo, VideoSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                isSearching || aggregate.totalSources > 0 -> Text(
                    text = "已完成 ${aggregate.finishedSources}/${aggregate.totalSources} 个源，共 ${aggregate.items.size} 部影片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        when {
            !hasSearched -> EmptyHint(text = "输入片名开始搜索。一次搜索，多个片源同时替你找。")

            aggregate.isComplete && aggregate.items.isEmpty() -> EmptyHint(
                text = "没有找到相关影片。换个片名或简称再试试。"
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
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
private fun VideoCard(video: AggregatedVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        RemotePoster(
            url = video.poster,
            width = 152.dp,
            height = 214.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = video.year.ifBlank { video.remarks },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${video.sourceCount} 个源",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 网络海报。
 *
 * 功能：异步加载海报图片，失败或为空时显示占位色块，不阻塞列表渲染。
 * 参数：url 图片地址；width 期望宽度；height 期望高度；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
fun RemotePoster(url: String, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffectPoster(url) { image = it }

    val shape = RoundedCornerShape(8.dp)
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(width, height).clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(width, height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

/**
 * 异步加载海报的副作用。
 *
 * 功能：在 IO 线程用标准库下载并解码图片，避免 UI 线程阻塞；URL 变化时重新加载。
 * 参数：url 图片地址；onLoaded 加载完成回调（失败时为 null）。
 * 返回值：无。
 */
@Composable
private fun LaunchedEffectPoster(url: String, onLoaded: (ImageBitmap?) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isBlank()) {
            onLoaded(null)
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URI(url).toURL().openConnection().apply {
                    setRequestProperty("User-Agent", POSTER_USER_AGENT)
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                connection.getInputStream().use { stream ->
                    ImageIO.read(stream)?.toComposeImageBitmap()
                }
            }.getOrNull()
        }
        onLoaded(decoded)
    }
}

/** 加载海报时使用的 UA，避免部分站点拒绝默认 Java UA。 */
private const val POSTER_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
