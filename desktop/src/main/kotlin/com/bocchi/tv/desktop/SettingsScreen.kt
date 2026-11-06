package com.bocchi.tv.desktop

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.tv.data.source.VideoSource
import com.bocchi.tv.desktop.ui.SectionTitle
import com.bocchi.tv.shared.AppGraph
import com.bocchi.tv.shared.AppInfo

/**
 * 设置页。
 *
 * 功能：数据源开关与增删改、播放解析接口、首页豆瓣推荐开关、缓存清理与关于信息。
 * 参数：无。
 * 返回值：无。
 */
@Composable
fun SettingsScreen() {
    var sources by remember { mutableStateOf(AppGraph.sourceStore.all()) }
    var doubanEnabled by remember { mutableStateOf(AppGraph.isDoubanEnabled()) }
    var parseApi by remember { mutableStateOf(AppGraph.getParseApiUrl()) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<VideoSource?>(null) }
    var creating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        SectionTitle(title = "数据源（${sources.count { it.enabled }}/${sources.size} 已启用）")

        sources.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = source.enabled,
                    onCheckedChange = { checked ->
                        AppGraph.sourceStore.setEnabled(source.key, checked)
                        sources = AppGraph.sourceStore.all()
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (AppGraph.sourceStore.isBuiltin(source.key)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "内置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = source.api,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { editing = source }) { Text(text = "编辑") }
                TextButton(onClick = {
                    AppGraph.sourceStore.remove(source.key)
                    sources = AppGraph.sourceStore.all()
                    message = "已删除「${source.name}」"
                }) { Text(text = "删除") }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { creating = true }) { Text(text = "新增自定义源") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = {
                AppGraph.sourceStore.restoreBuiltins()
                sources = AppGraph.sourceStore.all()
                message = "已恢复全部内置源"
            }) { Text(text = "恢复内置源") }
            Spacer(modifier = Modifier.weight(1f))
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle(title = "播放地址解析接口")
        Text(
            text = "直链会直接播放；遇到网页型地址时用该接口解析。留空表示不使用外部解析。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = parseApi,
                onValueChange = {
                    parseApi = it
                    parseError = AppGraph.validateParseApi(it)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = parseError != null,
                placeholder = { Text(text = "https://example.com/parse?url={url}") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val error = AppGraph.validateParseApi(parseApi)
                parseError = error
                if (error == null) {
                    AppGraph.setParseApiUrl(parseApi)
                    message = "解析接口已保存"
                }
            }) { Text(text = "保存") }
        }
        if (parseError != null) {
            Text(
                text = parseError!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        SectionTitle(title = "首页")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "展示豆瓣热门推荐", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = doubanEnabled,
                onCheckedChange = {
                    doubanEnabled = it
                    AppGraph.setDoubanEnabled(it)
                }
            )
        }

        SectionTitle(title = "缓存")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "清除解析结果与分类表缓存（收藏、历史与设置不受影响）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                AppGraph.clearCaches()
                message = "缓存已清除"
            }) { Text(text = "清除缓存") }
        }

        SectionTitle(title = "关于")
        Text(text = "${AppInfo.APP_NAME}  ${AppInfo.VERSION}", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = AppInfo.DEVELOPER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "免责声明：本客户端仅提供聚合检索与播放能力，不存储、不制作、不传播任何视频内容；" +
                "所有内容均来自第三方公开采集接口，请自行判断合法性并在遵守当地法律法规的前提下使用。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (editing != null) {
        SourceEditorDialog(
            initial = editing!!,
            title = "编辑数据源",
            onDismiss = { editing = null },
            onConfirm = { name, api, searchParam ->
                val ok = AppGraph.sourceStore.update(
                    editing!!.copy(name = name, api = api, searchParam = searchParam)
                )
                if (ok) {
                    sources = AppGraph.sourceStore.all()
                    editing = null
                    message = "已保存"
                }
                ok
            }
        )
    }

    if (creating) {
        SourceEditorDialog(
            initial = VideoSource(key = "", name = "", api = "", searchParam = "wd"),
            title = "新增自定义源",
            onDismiss = { creating = false },
            onConfirm = { name, api, searchParam ->
                val ok = AppGraph.sourceStore.addCustom(name, api, searchParam)
                if (ok) {
                    sources = AppGraph.sourceStore.all()
                    creating = false
                    message = "已新增「$name」"
                }
                ok
            }
        )
    }
}

/**
 * 数据源编辑弹窗。
 *
 * 参数：initial 初始值；title 标题；onDismiss 关闭；onConfirm 保存（返回 false 表示校验失败，弹窗保持）。
 * 返回值：无。
 */
@Composable
private fun SourceEditorDialog(
    initial: VideoSource,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, api: String, searchParam: String) -> Boolean
) {
    var name by remember { mutableStateOf(initial.name) }
    var api by remember { mutableStateOf(initial.api) }
    var searchParam by remember { mutableStateOf(initial.searchParam.ifBlank { "wd" }) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = api,
                    onValueChange = { api = it },
                    label = { Text(text = "采集接口地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchParam,
                    onValueChange = { searchParam = it },
                    label = { Text(text = "搜索参数名（默认 wd）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    name.isBlank() -> "名称不能为空"
                    !api.startsWith("http") -> "接口地址需要以 http:// 或 https:// 开头"
                    else -> null
                }
                if (error == null) {
                    if (!onConfirm(name, api, searchParam)) {
                        error = "保存失败：接口地址可能与其他源重复"
                    }
                }
            }) { Text(text = "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}
