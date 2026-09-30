package com.wechat.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.wechat.agent.data.AgentProfile
import com.wechat.agent.data.ObservationEntry
import com.wechat.agent.ui.components.CenteredTopBar
import com.wechat.agent.ui.theme.WeChatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "发现"页：顶部栏左侧为好友下拉菜单，选中好友后以「时间戳 + 行为记录」分列展示
 * 该好友的客观行为时间线（最新在上）。行为记录由大模型以"全知全能的观察者"视角生成，
 * 仅记录客观行为，不记录心理活动与想法。
 */
@Composable
fun DiscoverScreen(
    profiles: List<AgentProfile>,
    observations: List<ObservationEntry>,
    generating: Boolean = false,
    onRefresh: (String) -> Unit,
    onRecord: (String, String) -> Unit,
    onAutoRecord: (String, String) -> Unit = { _, _ -> },
    bottomBar: @Composable () -> Unit = {}
) {
    var selectedAgentId by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    // 默认选中第一个好友
    LaunchedEffect(profiles.map { it.id }) {
        if (selectedAgentId.isBlank() && profiles.isNotEmpty()) {
            selectedAgentId = profiles.first().id
        }
    }
    val selectedProfile = profiles.firstOrNull { it.id == selectedAgentId }

    // 切换好友时加载该好友的观察记录，并自动生成一条（受 ViewModel 最短间隔限制，避免重复）
    LaunchedEffect(selectedAgentId) {
        if (selectedAgentId.isNotBlank()) {
            onRefresh(selectedAgentId)
            selectedProfile?.let { onAutoRecord(it.id, it.name) }
        }
    }

    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            // 顶部栏：左侧好友下拉菜单 + 标题 + 右侧记录按钮（与其它导航页顶部栏对齐）
            CenteredTopBar(
                content = {},
                fullWidthContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧弹出式下拉菜单：好友列表
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { menuExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    selectedProfile?.name ?: "选择好友",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedProfile != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "好友列表",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                profiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(p.name, fontWeight = if (p.id == selectedAgentId) FontWeight.Medium else FontWeight.Normal)
                                        },
                                        onClick = {
                                            selectedAgentId = p.id
                                            menuExpanded = false
                                        }
                                    )
                                }
                                if (profiles.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("暂无好友", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                                        onClick = { menuExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Text("发现", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = { selectedProfile?.let { onRecord(it.id, it.name) } },
                            enabled = selectedProfile != null && !generating
                        ) {
                            if (generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("记录中", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("记录", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                profiles.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有好友，先去添加一位好友吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
                selectedProfile == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("请选择一位好友查看观察记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
                observations.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.width(40.dp).height(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("暂无 ${selectedProfile.name} 的观察记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        Text("点击右上角「记录」生成最新客观行为记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                "${selectedProfile.name} 的行为时间线",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(observations, key = { it.id }) { entry ->
                            ObservationRow(entry, timeFormat)
                        }
                    }
                }
            }
        }
    }
}

/** 单条观察记录：时间戳 + 行为分列展示，最新在上（列表已倒序）。 */
@Composable
private fun ObservationRow(
    entry: ObservationEntry,
    timeFormat: SimpleDateFormat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 时间戳列
        Text(
            timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = WeChatGreen,
            modifier = Modifier.widthIn(min = 76.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        // 行为记录列
        Text(
            entry.behavior,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
