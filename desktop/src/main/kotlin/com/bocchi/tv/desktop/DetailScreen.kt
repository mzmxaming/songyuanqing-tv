package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.ui.EmptyHint
import com.bocchi.tv.desktop.ui.RemotePoster
import com.bocchi.tv.desktop.ui.SelectableChip
import com.bocchi.tv.shared.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 影片详情页。
 *
 * 功能：展示海报、基本信息、剧情简介与全部线路；支持收藏/取消收藏，并从任意线路的任意集数起播；
 *      若存在上次播放记录且集数一致，会自动从上次位置续播。
 * 参数：video 聚合条目；match 当前选中的源；onBack 返回；onPlay 起播回调（线路、集数、起播毫秒）。
 * 返回值：无。
 */
@Composable
fun DetailScreen(
    video: AggregatedVideo,
    match: VideoSummary,
    onBack: () -> Unit,
    onPlay: (lineIndex: Int, episodeIndex: Int, positionMs: Long) -> Unit
) {
    var detail by remember(match.uniqueKey) { mutableStateOf<VideoDetail?>(null) }
    var isLoading by remember(match.uniqueKey) { mutableStateOf(true) }
    var lineIndex by remember(match.uniqueKey) { mutableStateOf(0) }
    var isFavorite by remember(match.uniqueKey) { mutableStateOf(AppGraph.favoriteStore.contains(match.uniqueKey)) }
    val history = remember(match.uniqueKey) { AppGraph.historyStore.find(match.uniqueKey) }

    LaunchedEffect(match.uniqueKey) {
        isLoading = true
        detail = withContext(Dispatchers.IO) { AppGraph.videoRepository.loadDetail(match) }
        isLoading = false
    }

    val current = detail
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            TextButtonRow(onBack)
            EmptyHint(text = "详情加载失败，请返回换一个片源重试。")
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        RemotePoster(url = current.poster, width = 220.dp, height = 310.dp)
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = {
                    isFavorite = AppGraph.favoriteStore.toggle(match)
                }) {
                    Text(text = if (isFavorite) "已收藏" else "收藏")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButtonRow(onBack)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = listOf(current.year, current.area, current.typeName, current.lang, current.remarks)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (current.score.isNotBlank()) {
                Text(
                    text = "评分 ${current.score}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (current.actor.isNotBlank()) {
                Text(
                    text = "主演：${current.actor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = current.content.ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(90.dp).verticalScroll(rememberScrollState())
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (current.lines.size > 1) {
                Text(
                    text = "线路（${current.lines.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    current.lines.forEachIndexed { index, line ->
                        SelectableChip(
                            text = line.displayName,
                            selected = index == lineIndex,
                            onClick = { lineIndex = index }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            val episodes = current.lines.getOrNull(lineIndex)?.episodes.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "选集（${episodes.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (history != null) {
                    Text(
                        text = "上次看到 ${history.episodeName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    val resumeIndex = history?.episodeIndex ?: 0
                    val resumePosition = if (history != null) history.positionMs else 0L
                    onPlay(lineIndex, resumeIndex, resumePosition)
                }) {
                    Text(text = if (history != null) "继续播放" else "开始播放")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                episodes.forEachIndexed { index, episode ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val position = if (history?.episodeIndex == index) history.positionMs else 0L
                                onPlay(lineIndex, index, position)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = episode.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Text(
                text = "共 ${video.sourceCount} 个片源命中此片；播放页可随时切换片源与线路。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6E6E7A)
            )
        }
    }
}

/**
 * 返回按钮。
 *
 * 参数：onBack 返回回调。
 * 返回值：无。
 */
@Composable
private fun TextButtonRow(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack) { Text(text = "返回") }
}
