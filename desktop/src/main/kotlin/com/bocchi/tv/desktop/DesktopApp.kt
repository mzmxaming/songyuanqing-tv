package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.ui.AppColorScheme
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.launch

/**
 * 界面路由。
 *
 * 参数：Home 首页推荐；Browse 分类浏览；Mine 收藏与历史；Settings 设置；
 *      SearchResult 搜索结果；Detail 影片详情；Player 播放页。
 * 返回值：无（密封接口）。
 */
sealed interface AppScreen {
    data object Home : AppScreen
    data object Browse : AppScreen
    data object Mine : AppScreen
    data object Settings : AppScreen
    data class SearchResult(val keyword: String) : AppScreen
    data class Detail(val video: AggregatedVideo, val match: VideoSummary) : AppScreen
    data class Player(
        val video: AggregatedVideo,
        val match: VideoSummary,
        val lineIndex: Int = 0,
        val episodeIndex: Int = 0,
        val startPositionMs: Long = 0L
    ) : AppScreen
}

/**
 * 桌面应用主界面。
 *
 * 功能：左侧导航（首页 / 分类 / 我的 / 设置）+ 顶部搜索栏 + 内容区；
 *      播放页可切换窗口全屏，全屏时隐藏导航与顶栏以获得沉浸体验。
 * 参数：windowState —— 窗口状态，用于切换全屏。
 * 返回值：无。
 */
@Composable
fun DesktopApp(windowState: WindowState) {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var keyword by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf(AggregateResult()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
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
        searchError = null
        searchResult = AggregateResult()
        isSearching = true
        screen = AppScreen.SearchResult(query)
        scope.launch {
            runCatching {
                AppGraph.videoRepository.search(query).collect { snapshot -> searchResult = snapshot }
            }.onFailure { error ->
                searchError = "搜索失败：${error.message ?: "网络异常"}"
            }
            isSearching = false
        }
    }

    /**
     * 切换窗口全屏。
     *
     * 参数：fullscreen —— 是否进入全屏。
     * 返回值：无。
     */
    fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        // 说明：这里切换的是应用内沉浸布局（隐藏导航/顶栏/侧栏），画面充满整个窗口。
        // 不调用系统级全屏，是为了避免各平台窗口管理器在切换动画期间导致互操作层重新布局、
        // 画面与控件短暂错位。
        if (!fullscreen) {
            windowState.placement = WindowPlacement.Floating
        }
    }

    MaterialTheme(colorScheme = AppColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (screen is AppScreen.Player && isFullscreen) {
                PlayerScreen(
                    video = (screen as AppScreen.Player).video,
                    initialMatch = (screen as AppScreen.Player).match,
                    initialLineIndex = (screen as AppScreen.Player).lineIndex,
                    initialEpisodeIndex = (screen as AppScreen.Player).episodeIndex,
                    initialPositionMs = (screen as AppScreen.Player).startPositionMs,
                    isFullscreen = true,
                    onToggleFullscreen = { setFullscreen(false) },
                    onBack = {
                        setFullscreen(false)
                        screen = AppScreen.Home
                    }
                )
                return@Surface
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onSearch = ::runSearch,
                    enabled = !isSearching
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    SideNav(
                        current = screen,
                        onNavigate = { target -> screen = target }
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val current = screen) {
                            is AppScreen.Home -> HomeScreen(
                                onOpenDetail = { video, match ->
                                    screen = AppScreen.Detail(video, match)
                                },
                                onSearchTitle = { title ->
                                    keyword = title
                                    runSearch()
                                }
                            )

                            is AppScreen.Browse -> BrowseScreen(
                                onOpenDetail = { video, match ->
                                    screen = AppScreen.Detail(video, match)
                                }
                            )

                            is AppScreen.SearchResult -> SearchResultScreen(
                                keyword = current.keyword,
                                result = searchResult,
                                isSearching = isSearching,
                                errorMessage = searchError,
                                onOpenDetail = { video, match ->
                                    screen = AppScreen.Detail(video, match)
                                }
                            )

                            is AppScreen.Detail -> DetailScreen(
                                video = current.video,
                                match = current.match,
                                onBack = { screen = AppScreen.SearchResult(current.video.title) },
                                onPlay = { lineIndex, episodeIndex, positionMs ->
                                    screen = AppScreen.Player(
                                        video = current.video,
                                        match = current.match,
                                        lineIndex = lineIndex,
                                        episodeIndex = episodeIndex,
                                        startPositionMs = positionMs
                                    )
                                }
                            )

                            is AppScreen.Player -> PlayerScreen(
                                video = current.video,
                                initialMatch = current.match,
                                initialLineIndex = current.lineIndex,
                                initialEpisodeIndex = current.episodeIndex,
                                initialPositionMs = current.startPositionMs,
                                isFullscreen = false,
                                onToggleFullscreen = { setFullscreen(true) },
                                onBack = { screen = AppScreen.Detail(current.video, current.match) }
                            )

                            is AppScreen.Mine -> MineScreen(
                                onOpenDetail = { video, match ->
                                    screen = AppScreen.Detail(video, match)
                                }
                            )

                            is AppScreen.Settings -> SettingsScreen()
                        }
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
        Button(onClick = onSearch, enabled = enabled) { Text(text = "搜索") }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "APP 设计开发：陈光明",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 左侧导航栏。
 *
 * 参数：current 当前页面；onNavigate 导航回调。
 * 返回值：无。
 */
@Composable
private fun SideNav(current: AppScreen, onNavigate: (AppScreen) -> Unit) {
    val items = listOf(
        Triple("首页", AppScreen.Home, AppScreen.Home),
        Triple("分类", AppScreen.Browse, AppScreen.Browse),
        Triple("我的", AppScreen.Mine, AppScreen.Mine),
        Triple("设置", AppScreen.Settings, AppScreen.Settings)
    )
    Column(
        modifier = Modifier
            .width(112.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 10.dp)
    ) {
        items.forEach { (label, target, marker) ->
            val selected = when {
                current is AppScreen.Player || current is AppScreen.Detail ->
                    marker == AppScreen.Home
                current is AppScreen.SearchResult -> marker == AppScreen.Home
                else -> current == marker
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onNavigate(target) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
