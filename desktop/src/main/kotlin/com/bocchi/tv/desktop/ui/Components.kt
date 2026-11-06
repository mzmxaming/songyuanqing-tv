package com.bocchi.tv.desktop.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO

/** 全局深色主题色板（与安卓版观影风格一致）。 */
val AppColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFE5484D),
    onPrimary = Color.White,
    background = Color(0xFF14141A),
    onBackground = Color(0xFFE8E8ED),
    surface = Color(0xFF1C1C24),
    onSurface = Color(0xFFE8E8ED),
    surfaceVariant = Color(0xFF262630),
    onSurfaceVariant = Color(0xFF9A9AA8)
)

/** 加载海报时使用的 UA，避免部分站点拒绝默认 Java UA。 */
private const val POSTER_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

/**
 * 区块标题 + 可选右侧操作。
 *
 * 参数：title 标题；trailing 右侧内容；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 空状态提示。
 *
 * 参数：text 提示文案；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 影片网格。
 *
 * 参数：items 聚合条目；onClick 点击回调；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
fun VideoGrid(
    items: List<AggregatedVideo>,
    modifier: Modifier = Modifier,
    onClick: (AggregatedVideo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(items, key = { it.matches.first().uniqueKey }) { video ->
            VideoCard(video = video, onClick = { onClick(video) })
        }
    }
}

/**
 * 影片卡片。
 *
 * 参数：video 聚合条目；onClick 点击回调。
 * 返回值：无。
 */
@Composable
fun VideoCard(video: AggregatedVideo, onClick: () -> Unit) {
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
 * 功能：异步加载并解码图片，失败或为空时显示占位色块，不阻塞列表渲染。
 * 参数：url 图片地址；width 期望宽度；height 期望高度；modifier 布局修饰。
 * 返回值：无。
 */
@Composable
fun RemotePoster(url: String, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        image = if (url.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URI(url).toURL().openConnection().apply {
                        setRequestProperty("User-Agent", POSTER_USER_AGENT)
                        if (url.contains("doubanio.com")) {
                            setRequestProperty("Referer", "https://movie.douban.com/")
                        }
                        connectTimeout = 8_000
                        readTimeout = 8_000
                    }
                    connection.getInputStream().use { stream ->
                        ImageIO.read(stream)?.toComposeImageBitmap()
                    }
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
 * 可点击标签（分类、二级分类、线路等）。
 *
 * 参数：text 文案；selected 是否选中；onClick 点击回调。
 * 返回值：无。
 */
@Composable
fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * 把单条摘要包装成聚合条目，供收藏/历史列表复用同一套卡片与详情入口。
 *
 * 参数：无（扩展函数）。
 * 返回值：AggregatedVideo —— 仅含一个源的条目。
 */
fun VideoSummary.toAggregatedVideo(): AggregatedVideo = AggregatedVideo(
    title = title,
    poster = poster,
    remarks = remarks,
    year = year,
    typeName = typeName,
    score = score,
    matches = listOf(this)
)

/**
 * 把毫秒格式化为 mm:ss 或 h:mm:ss。
 *
 * 参数：millis —— 毫秒数。
 * 返回值：String —— 可展示的时间文本。
 */
fun formatTime(millis: Long): String {
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
