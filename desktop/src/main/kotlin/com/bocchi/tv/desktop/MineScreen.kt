package com.bocchi.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.local.StoredFavorite
import com.bocchi.tv.data.local.StoredHistory
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoSummary
import com.bocchi.tv.desktop.ui.EmptyHint
import com.bocchi.tv.desktop.ui.RemotePoster
import com.bocchi.tv.desktop.ui.SelectableChip
import com.bocchi.tv.desktop.ui.formatTime
import com.bocchi.tv.desktop.ui.toAggregatedVideo
import com.bocchi.tv.shared.AppGraph

/**
 * 我的页面。
 *
 * 功能：收藏与播放历史两个分页；点击条目进入详情，支持单条删除与整体清空。
 * 参数：onOpenDetail 打开详情。
 * 返回值：无。
 */
@Composable
fun MineScreen(onOpenDetail: (AggregatedVideo, VideoSummary) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf(AppGraph.favoriteStore.all()) }
    var histories by remember { mutableStateOf(AppGraph.historyStore.all()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableChip(text = "收藏（${favorites.size}）", selected = tab == 0, onClick = { tab = 0 })
            Spacer(modifier = Modifier.width(8.dp))
            SelectableChip(text = "历史（${histories.size}）", selected = tab == 1, onClick = { tab = 1 })
            Spacer(modifier = Modifier.weight(1f))
            if (tab == 0 && favorites.isNotEmpty()) {
                TextButton(onClick = {
                    AppGraph.favoriteStore.clear()
                    favorites = emptyList()
                }) { Text(text = "清空收藏") }
            }
            if (tab == 1 && histories.isNotEmpty()) {
                TextButton(onClick = {
                    AppGraph.historyStore.clear()
                    histories = emptyList()
                }) { Text(text = "清空历史") }
            }
        }

        when {
            tab == 0 && favorites.isEmpty() -> EmptyHint(text = "还没有收藏。在详情页点「收藏」即可加入。")
            tab == 1 && histories.isEmpty() -> EmptyHint(text = "还没有播放记录。")
            tab == 0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(favorites, key = { it.vodKey }) { item ->
                    LibraryRow(
                        title = item.title,
                        poster = item.poster,
                        subtitle = listOf(item.year, item.remarks).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = {
                            onOpenDetail(item.toSummary().toAggregatedVideo(), item.toSummary())
                        },
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
                        subtitle = buildSubtitle(item),
                        onClick = {
                            onOpenDetail(item.toSummary().toAggregatedVideo(), item.toSummary())
                        },
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
 * 构造历史记录的副标题。
 *
 * 参数：item —— 历史记录。
 * 返回值：String —— 形如「第3集 · 看到 00:37 / 2:02:19」。
 */
private fun buildSubtitle(item: StoredHistory): String {
    val parts = mutableListOf<String>()
    if (item.episodeName.isNotBlank()) parts += item.episodeName
    if (item.durationMs > 0) {
        parts += "看到 ${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}"
    }
    return parts.joinToString(" · ")
}

/**
 * 收藏/历史列表行。
 *
 * 参数：title 片名；poster 海报；subtitle 副标题；onClick 点击；onRemove 删除。
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
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemotePoster(url = poster, width = 64.dp, height = 90.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
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
