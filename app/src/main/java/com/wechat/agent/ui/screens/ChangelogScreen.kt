package com.wechat.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wechat.agent.ui.theme.WeChatGreen

/**
 * 更新日志页：展示历史版本更新内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("更新日志", fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val changelog = listOf(
                ChangelogEntry(
                    version = "v1.1.0",
                    date = "2026-09-29",
                    items = listOf(
                        "新增「更新日志」入口，可查看历史版本更新内容",
                        "通讯录「新的朋友」支持添加多个 AI 角色，角色之间互不覆盖，各自拥有独立记忆库文件",
                        "创建 AI 好友界面移除头像旁无用的「从相册上传」图标按钮",
                        "修复记忆库导入 json 文件提示无法识别格式的问题，兼容纯字符串数组格式",
                        "朋友圈动态支持 AI 依据图片与文字内容回复，用户与 AI 可连续嵌套回复直至一方停止",
                        "记忆库支持自动备份：可自定义备份间隔、是否覆盖旧备份、退出聊天窗口时备份"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.1",
                    date = "2026-09-28",
                    items = listOf(
                        "底部 Tab 改为聊天、通讯录、发现、我",
                        "「新的朋友」可跳转创建 AI 好友",
                        "角色详情页新增记忆库卡片，支持导出 json / md 与导入",
                        "相机支持拍摄照片发布朋友圈",
                        "朋友圈支持点赞与评论",
                        "我的页面支持资料编辑",
                        "设置页拆分模型配置与检查更新，支持在线更新覆盖安装",
                        "输入检测：用户正在输入时 AI 不主动发消息"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.0",
                    date = "2026-09-20",
                    items = listOf(
                        "微信风格界面升级，全新 AI 聊天体验",
                        "支持记忆导入导出与生活模拟",
                        "朋友圈、聊天输入栏等基础功能完成"
                    )
                )
            )

            changelog.forEachIndexed { index, entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WeChatGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Circle, contentDescription = null,
                                    tint = WeChatGreen, modifier = Modifier.size(10.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(entry.version, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(entry.date, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        entry.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("·", style = MaterialTheme.typography.bodyMedium,
                                    color = WeChatGreen, modifier = Modifier.padding(end = 6.dp))
                                Text(item, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
                if (index < changelog.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>
)
