package com.bocchi.tv.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.BrowseCategory
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.ui.EmptyHint
import com.bocchi.tv.desktop.ui.SelectableChip
import com.bocchi.tv.desktop.ui.VideoGrid
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分类浏览页。
 *
 * 功能：一级分类切换（电影/剧集/短剧/综艺/动漫/纪录片），二级分类按收录源数排序展示；
 *      切换任意筛选条件都会重新发起多源聚合请求。
 * 参数：onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
fun BrowseScreen(onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit) {
    var category by remember { mutableStateOf(BrowseCategory.MOVIE) }
    var subCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSub by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf(AggregateResult()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        subCategories = withContext(Dispatchers.IO) {
            AppGraph.videoRepository.loadSubCategories(category)
        }
    }

    LaunchedEffect(category, selectedSub) {
        isSearching = true
        result = AggregateResult()
        runCatching {
            AppGraph.videoRepository.browse(category, selectedSub).collect { snapshot ->
                result = snapshot
            }
        }
        isSearching = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            BrowseCategory.entries.forEach { item ->
                SelectableChip(
                    text = item.displayName,
                    selected = item == category,
                    onClick = {
                        if (item != category) {
                            category = item
                            selectedSub = null
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        if (subCategories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                SelectableChip(
                    text = "全部",
                    selected = selectedSub == null,
                    onClick = { selectedSub = null }
                )
                Spacer(modifier = Modifier.width(8.dp))
                subCategories.forEach { name ->
                    SelectableChip(
                        text = name,
                        selected = selectedSub == name,
                        onClick = { selectedSub = name }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "已完成 ${result.finishedSources}/${result.totalSources} 个源，共 ${result.items.size} 部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            result.isComplete && result.items.isEmpty() -> EmptyHint(text = "该分类下暂时没有内容，换个分类或稍后再试。")
            result.items.isEmpty() -> EmptyHint(text = "正在拉取各片源的分类内容…")
            else -> VideoGrid(items = result.items) { video ->
                onOpenDetail(video, video.matches.first())
            }
        }
    }
}
