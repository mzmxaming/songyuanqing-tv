package com.bocchi.tv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.douban.DoubanSubject
import com.bocchi.tv.data.local.StoredHistory
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.ui.EmptyHint
import com.bocchi.tv.desktop.ui.RemotePoster
import com.bocchi.tv.desktop.ui.SectionTitle
import com.bocchi.tv.desktop.ui.VideoGrid
import com.bocchi.tv.desktop.ui.formatTime
import com.bocchi.tv.desktop.ui.toAggregatedVideo
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首页。
 *
 * 功能：展示「继续观看」（最近播放）与豆瓣热门推荐；点击推荐会以片名发起聚合搜索。
 * 参数：onOpenDetail 打开详情；onSearchTitle 以片名搜索。
 * 返回值：无。
 */
@Composable
fun HomeScreen(
    onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit,
    onSearchTitle: (String) -> Unit
) {
    var doubanItems by remember { mutableStateOf<List<DoubanSubject>>(emptyList()) }
    var doubanLoading by remember { mutableStateOf(false) }
    val doubanEnabled = remember { AppGraph.isDoubanEnabled() }
    val history = remember { AppGraph.historyStore.all().take(8) }

    LaunchedEffect(doubanEnabled) {
        if (!doubanEnabled) return@LaunchedEffect
        doubanLoading = true
        doubanItems = withContext(Dispatchers.IO) { AppGraph.doubanRepository.loadHotSubjects() }
        doubanLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isNotEmpty()) {
            SectionTitle(title = "继续观看")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(history, key = { it.vodKey }) { item ->
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .clickable {
                                onOpenDetail(item.toSummary().toAggregatedVideo(), item.toSummary())
                            }
                    ) {
                        RemotePoster(url = item.poster, width = 132.dp, height = 186.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val progressText = if (item.durationMs > 0) {
                            "${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}"
                        } else {
                            item.episodeName
                        }
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (doubanEnabled) {
            SectionTitle(title = "豆瓣热门") {
                if (doubanLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
                }
            }
            if (!doubanLoading && doubanItems.isEmpty()) {
                Text(
                    text = "豆瓣推荐暂不可用，可直接用上方搜索框找片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(doubanItems, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .clickable { onSearchTitle(item.title) }
                    ) {
                        RemotePoster(url = item.cover, width = 132.dp, height = 186.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.rate.isBlank()) "豆瓣热门" else "评分 ${item.rate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionTitle(title = "提示")
        Text(
            text = "共 ${AppGraph.sourceStore.enabled().size} 个片源参与搜索；可在「设置」里增减片源、配置解析接口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "搜索、分类、收藏与历史都在左侧导航里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 搜索结果页。
 *
 * 参数：keyword 关键词；result 聚合快照；isSearching 是否搜索中；errorMessage 错误提示；
 *      onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
fun SearchResultScreen(
    keyword: String,
    result: AggregateResult,
    isSearching: Boolean,
    errorMessage: String?,
    onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit
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

                result.totalSources > 0 -> Text(
                    text = "「$keyword」已完成 ${result.finishedSources}/${result.totalSources} 个源，共 ${result.items.size} 部影片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Text(
                    text = "正在搜索「$keyword」…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isSearching) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
            }
        }

        when {
            result.isComplete && result.items.isEmpty() -> EmptyHint(text = "没有找到相关影片。换个片名或简称再试试。")
            result.items.isEmpty() -> EmptyHint(text = "正在同时搜索多个片源…")
            else -> VideoGrid(items = result.items) { video ->
                onOpenDetail(video, video.matches.first())
            }
        }
    }
}
