package com.bocchi.tv.ios

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.douban.DoubanSubject
import com.bocchi.tv.data.local.StoredFavorite
import com.bocchi.tv.data.local.StoredHistory
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.BrowseCategory
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.shared.AppGraph
import com.bocchi.tv.shared.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS 路由。
 *
 * 参数：Tabs 主标签页；Search 搜索结果；Detail 详情；Player 播放。
 * 返回值：无（密封接口）。
 */
private sealed interface IosRoute {
    data object Tabs : IosRoute
    data class Search(val keyword: String) : IosRoute
    data class Detail(val video: AggregatedVideo, val match: VideoSummary) : IosRoute
    data class Player(
        val video: AggregatedVideo,
        val match: VideoSummary,
        val lineIndex: Int = 0,
        val episodeIndex: Int = 0,
        val positionMs: Long = 0L
    ) : IosRoute
}

/**
 * iOS 根界面。
 *
 * 功能：底部四个标签（首页 / 分类 / 我的 / 设置），并在其上叠加搜索结果、详情与播放页，
 *      与桌面端保持一致的功能结构。
 * 参数：无。
 * 返回值：无。
 */
@Composable
fun IosRoot() {
    var tab by remember { mutableStateOf(0) }
    var route by remember { mutableStateOf<IosRoute>(IosRoute.Tabs) }

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFE5484D),
        onPrimary = Color.White,
        background = Color(0xFF14141A),
        onBackground = Color(0xFFE8E8ED),
        surface = Color(0xFF1C1C24),
        onSurface = Color(0xFFE8E8ED),
        surfaceVariant = Color(0xFF262630),
        onSurfaceVariant = Color(0xFF9A9AA8)
    )) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val current = route) {
                is IosRoute.Tabs -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (tab) {
                            0 -> HomeTab(
                                onSearch = { keyword -> route = IosRoute.Search(keyword) },
                                onOpenDetail = { video, match -> route = IosRoute.Detail(video, match) }
                            )

                            1 -> BrowseTab(
                                onOpenDetail = { video, match -> route = IosRoute.Detail(video, match) }
                            )

                            2 -> MineTab(
                                onOpenDetail = { video, match -> route = IosRoute.Detail(video, match) }
                            )

                            else -> SettingsTab()
                        }
                    }
                    BottomBar(current = tab, onSelect = { tab = it })
                }

                is IosRoute.Search -> SearchRoute(
                    keyword = current.keyword,
                    onBack = { route = IosRoute.Tabs },
                    onOpenDetail = { video, match -> route = IosRoute.Detail(video, match) }
                )

                is IosRoute.Detail -> DetailRoute(
                    video = current.video,
                    match = current.match,
                    onBack = { route = IosRoute.Tabs },
                    onPlay = { line, episode, position ->
                        route = IosRoute.Player(current.video, current.match, line, episode, position)
                    }
                )

                is IosRoute.Player -> PlayerScreen(
                    video = current.video,
                    initialMatch = current.match,
                    initialLineIndex = current.lineIndex,
                    initialEpisodeIndex = current.episodeIndex,
                    initialPositionMs = current.positionMs,
                    onBack = { route = IosRoute.Detail(current.video, current.match) }
                )
            }
        }
    }
}

/**
 * 底部标签栏。
 *
 * 参数：current 当前标签；onSelect 切换回调。
 * 返回值：无。
 */
@Composable
private fun BottomBar(current: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("首页", "分类", "我的", "设置")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                color = if (index == current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * 首页标签。
 *
 * 参数：onSearch 以关键词搜索；onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
private fun HomeTab(
    onSearch: (String) -> Unit,
    onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var doubanItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val doubanEnabled = remember { AppGraph.isDoubanEnabled() }
    val history = remember { AppGraph.historyStore.all().take(8) }

    LaunchedEffect(doubanEnabled) {
        if (!doubanEnabled) return@LaunchedEffect
        loading = true
        doubanItems = withContext(Dispatchers.Default) { AppGraph.doubanRepository.loadHotSubjects() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(56.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(text = "${AppGraph.sourceStore.enabled().size} 个片源同时搜索") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (keyword.isNotBlank()) onSearch(keyword.trim())
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { if (keyword.isNotBlank()) onSearch(keyword.trim()) }) {
                Text(text = "搜索")
            }
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "继续观看",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history, key = { it.vodKey }) { item ->
                    Column(
                        modifier = Modifier.width(108.dp).clickable {
                            onOpenDetail(item.toSummary().toAggregated(), item.toSummary())
                        }
                    ) {
                        Poster(url = item.poster, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.durationMs > 0) {
                                "${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}"
                            } else {
                                item.episodeName
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (doubanEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "豆瓣热门",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(doubanItems, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.width(108.dp).clickable { onSearch(item.title) }
                    ) {
                        Poster(url = item.cover, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.rate.isBlank()) "热门" else "评分 ${item.rate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!loading && doubanItems.isEmpty()) {
                Text(
                    text = "豆瓣推荐暂不可用，可直接搜索片名。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "共 ${AppGraph.sourceStore.enabled().size} 个片源参与搜索；可在「设置」里增减片源、配置解析接口。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 搜索结果路由。
 *
 * 参数：keyword 关键词；onBack 返回；onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
private fun SearchRoute(
    keyword: String,
    onBack: () -> Unit,
    onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit
) {
    var result by remember { mutableStateOf(AggregateResult()) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(keyword) {
        isSearching = true
        runCatching {
            AppGraph.videoRepository.search(keyword).collect { result = it }
        }.onFailure { error = "搜索失败：${it.message ?: "网络异常"}" }
        isSearching = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(modifier = Modifier.height(52.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(text = "返回") }
            Text(
                text = "「$keyword」${result.finishedSources}/${result.totalSources} 源 · ${result.items.size} 部",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp)
        }
        when {
            error != null -> Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )

            result.isComplete && result.items.isEmpty() -> CenterHint(text = "没有找到相关影片")
            result.items.isEmpty() -> CenterHint(text = "正在同时搜索多个片源…")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(result.items, key = { it.matches.first().uniqueKey }) { video ->
                    VideoCard(video = video, onClick = { onOpenDetail(video, video.matches.first()) })
                }
            }
        }
    }
}

/**
 * 分类标签。
 *
 * 参数：onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
private fun BrowseTab(onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit) {
    var category by remember { mutableStateOf(BrowseCategory.MOVIE) }
    var subCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSub by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf(AggregateResult()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        subCategories = withContext(Dispatchers.Default) {
            AppGraph.videoRepository.loadSubCategories(category)
        }
    }
    LaunchedEffect(category, selectedSub) {
        isSearching = true
        result = AggregateResult()
        runCatching {
            AppGraph.videoRepository.browse(category, selectedSub).collect { result = it }
        }
        isSearching = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(52.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BrowseCategory.entries.forEach { item ->
                IosChip(item.displayName, item == category) {
                    if (item != category) {
                        category = item
                        selectedSub = null
                    }
                }
            }
        }
        if (subCategories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IosChip("全部", selectedSub == null) { selectedSub = null }
                subCategories.forEach { name ->
                    IosChip(name, selectedSub == name) { selectedSub = name }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已完成 ${result.finishedSources}/${result.totalSources} 个源，共 ${result.items.size} 部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp)
        }
        when {
            result.isComplete && result.items.isEmpty() -> CenterHint(text = "该分类下暂时没有内容")
            result.items.isEmpty() -> CenterHint(text = "正在拉取各片源的分类内容…")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(result.items, key = { it.matches.first().uniqueKey }) { video ->
                    VideoCard(video = video, onClick = { onOpenDetail(video, video.matches.first()) })
                }
            }
        }
    }
}

/**
 * 我的标签（收藏 / 历史）。
 *
 * 参数：onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
private fun MineTab(onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var favorites by remember { mutableStateOf(AppGraph.favoriteStore.all()) }
    var histories by remember { mutableStateOf(AppGraph.historyStore.all()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(modifier = Modifier.height(52.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IosChip("收藏（${favorites.size}）", tab == 0) { tab = 0 }
            Spacer(modifier = Modifier.width(6.dp))
            IosChip("历史（${histories.size}）", tab == 1) { tab = 1 }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (tab == 0) {
                    AppGraph.favoriteStore.clear()
                    favorites = emptyList()
                } else {
                    AppGraph.historyStore.clear()
                    histories = emptyList()
                }
            }) { Text(text = "清空") }
        }
        when {
            tab == 0 && favorites.isEmpty() -> CenterHint(text = "还没有收藏，详情页点「收藏」即可加入")
            tab == 1 && histories.isEmpty() -> CenterHint(text = "还没有播放记录")
            tab == 0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(favorites, key = { it.vodKey }) { item ->
                    LibraryRow(
                        title = item.title,
                        poster = item.poster,
                        subtitle = listOf(item.year, item.remarks).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { onOpenDetail(item.toSummary().toAggregated(), item.toSummary()) },
                        onRemove = {
                            AppGraph.favoriteStore.remove(item.vodKey)
                            favorites = AppGraph.favoriteStore.all()
                        }
                    )
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(histories, key = { it.vodKey }) { item ->
                    LibraryRow(
                        title = item.title,
                        poster = item.poster,
                        subtitle = buildHistorySubtitle(item),
                        onClick = { onOpenDetail(item.toSummary().toAggregated(), item.toSummary()) },
                        onRemove = {
                            AppGraph.historyStore.remove(item.vodKey)
                            histories = AppGraph.historyStore.all()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 历史副标题。
 *
 * 参数：item 历史记录。
 * 返回值：String —— 集数与进度。
 */
private fun buildHistorySubtitle(item: StoredHistory): String {
    val parts = mutableListOf<String>()
    if (item.episodeName.isNotBlank()) parts += item.episodeName
    if (item.durationMs > 0) parts += "看到 ${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}"
    return parts.joinToString(" · ")
}

/**
 * 收藏/历史行。
 *
 * 参数：item 记录（取标题与海报）；subtitle 副标题；onClick 点击；onRemove 删除。
 * 返回值：无。
 */
@Composable
private fun LibraryRow(
    title: String,
    poster: String,
    subtitle: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Poster(url = poster, modifier = Modifier.width(48.dp).height(68.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRemove) { Text(text = "删除") }
    }
}

/**
 * 设置标签。
 *
 * 参数：无。
 * 返回值：无。
 */
@Composable
private fun SettingsTab() {
    var sources by remember { mutableStateOf(AppGraph.sourceStore.all()) }
    var douban by remember { mutableStateOf(AppGraph.isDoubanEnabled()) }
    var parseApi by remember { mutableStateOf(AppGraph.getParseApiUrl()) }
    var parseError by remember { mutableStateOf(AppGraph.validateParseApi(AppGraph.getParseApiUrl())) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        Text(
            text = "数据源（${sources.count { it.enabled }}/${sources.size} 已启用）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = source.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = source.api,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { checked ->
                        AppGraph.sourceStore.setEnabled(source.key, checked)
                        sources = AppGraph.sourceStore.all()
                    }
                )
            }
        }
        TextButton(onClick = {
            AppGraph.sourceStore.restoreBuiltins()
            sources = AppGraph.sourceStore.all()
            message = "已恢复全部内置源"
        }) { Text(text = "恢复内置源") }

        Text(
            text = "播放解析接口",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = parseApi,
            onValueChange = {
                parseApi = it
                parseError = AppGraph.validateParseApi(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = parseError != null,
            placeholder = { Text(text = "https://example.com/parse?url={url}") }
        )
        if (parseError != null) {
            Text(
                text = parseError!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(onClick = {
            val error = AppGraph.validateParseApi(parseApi)
            parseError = error
            if (error == null) {
                AppGraph.setParseApiUrl(parseApi)
                message = "解析接口已保存"
            }
        }) { Text(text = "保存解析接口") }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "首页展示豆瓣热门", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = douban, onCheckedChange = {
                douban = it
                AppGraph.setDoubanEnabled(it)
            })
        }
        TextButton(onClick = {
            AppGraph.clearCaches()
            message = "缓存已清除"
        }) { Text(text = "清除缓存") }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${AppInfo.APP_NAME}  ${AppInfo.VERSION}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = AppInfo.DEVELOPER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "免责声明：本客户端仅提供聚合检索与播放能力，不存储、不制作、不传播任何视频内容；" +
                "内容均来自第三方公开采集接口，请遵守当地法律法规使用。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 详情路由。
 *
 * 参数：video 聚合条目；match 当前源；onBack 返回；onPlay 起播。
 * 返回值：无。
 */
@Composable
private fun DetailRoute(
    video: AggregatedVideo,
    match: VideoSummary,
    onBack: () -> Unit,
    onPlay: (line: Int, episode: Int, position: Long) -> Unit
) {
    var detail by remember(match.uniqueKey) { mutableStateOf<VideoDetail?>(null) }
    var loading by remember(match.uniqueKey) { mutableStateOf(true) }
    var lineIndex by remember(match.uniqueKey) { mutableStateOf(0) }
    var favorite by remember(match.uniqueKey) { mutableStateOf(AppGraph.favoriteStore.contains(match.uniqueKey)) }
    val history = remember(match.uniqueKey) { AppGraph.historyStore.find(match.uniqueKey) }

    LaunchedEffect(match.uniqueKey) {
        loading = true
        detail = withContext(Dispatchers.Default) { AppGraph.videoRepository.loadDetail(match) }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(text = "返回") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                favorite = AppGraph.favoriteStore.toggle(match)
            }) { Text(text = if (favorite) "已收藏" else "收藏") }
        }

        val current = detail
        if (loading) {
            CenterHint(text = "正在加载详情…")
            return@Column
        }
        if (current == null) {
            CenterHint(text = "详情加载失败，请返回换一个片源")
            return@Column
        }

        Row {
            Poster(url = current.poster, modifier = Modifier.width(110.dp).height(156.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = listOf(current.year, current.area, current.typeName, current.remarks)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (current.score.isNotBlank()) {
                    Text(
                        text = "评分 ${current.score}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = current.content.ifBlank { "暂无简介" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        if (current.lines.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                current.lines.forEachIndexed { index, line ->
                    IosChip(line.displayName, index == lineIndex) { lineIndex = index }
                }
            }
        }

        val episodes = current.lines.getOrNull(lineIndex)?.episodes.orEmpty()
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "选集（${episodes.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (history != null) {
                Text(
                    text = "上次看到 ${history.episodeName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                onPlay(
                    lineIndex,
                    history?.episodeIndex ?: 0,
                    history?.positionMs ?: 0L
                )
            }) { Text(text = if (history != null) "继续播放" else "开始播放") }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(220.dp)
        ) {
            items(episodes.size) { index ->
                val episode = episodes[index]
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val position = if (history?.episodeIndex == index) history.positionMs else 0L
                            onPlay(lineIndex, index, position)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 轻量标签。
 *
 * 参数：text 文案；selected 是否选中；onClick 点击。
 * 返回值：无。
 */
@Composable
private fun IosChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/**
 * 把摘要包装成聚合条目。
 *
 * 参数：无（扩展函数）。
 * 返回值：AggregatedVideo —— 单源条目。
 */
private fun VideoSummary.toAggregated(): AggregatedVideo = AggregatedVideo(
    title = title,
    poster = poster,
    remarks = remarks,
    year = year,
    typeName = typeName,
    score = score,
    matches = listOf(this)
)
