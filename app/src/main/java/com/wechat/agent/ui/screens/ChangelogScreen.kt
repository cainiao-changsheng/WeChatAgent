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
                    version = "v1.0.12",
                    date = "2026-09-30",
                    items = listOf(
                        "表情支持导入导出（zip / JSON）",
                        "聊天图片尺寸统一展示",
                        "底部新增「发现」页，原「发现」更名「动态」",
                        "发现页支持全知全能观察者时间线记录",
                        "发现页顶部栏与其它导航页对齐"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.11",
                    date = "2026-09-30",
                    items = listOf(
                        "新增图片表情系统，表情以 JSON 管理",
                        "聊天输入支持表情联想，大模型可读取表情含义",
                        "聊天图片自动缓存，退出重开不丢失",
                        "修复思考气泡重复显示问题"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.10",
                    date = "2026-09-30",
                    items = listOf(
                        "修复聊天输入框被输入法遮挡的问题",
                        "思考过程气泡常驻显示，回复完成不再消失",
                        "模拟思考更快出现，减少等待卡顿"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.9",
                    date = "2026-09-30",
                    items = listOf(
                        "修复默认模型无思考内容时气泡不显示，增加模拟思考兜底"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.8",
                    date = "2026-09-30",
                    items = listOf(
                        "修复大模型卡住时聊天界面按钮无法操作的问题，增加超时兜底与状态复位",
                        "开启「显示思考过程」后聊天界面正确展示思考过程气泡，可点击展开 / 收起",
                        "大模型回复时顶部显示「对方正在输入中」",
                        "修复输入法弹起时顶栏上移超出状态栏的问题",
                        "新增「思考完成自动折叠气泡」开关（我 → 高级 → 思考设置）"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.7-2",
                    date = "2026-09-30",
                    items = listOf(
                        "高级页移除「保存 / 测试 / 取消」按钮，设置改动即时生效",
                        "修复发送图片后显示「[图片]」占位文本的问题，AI 可直接查看图片内容并回复",
                        "聊天「+」弹层升级为 2×4 网格：相册 / 拍摄 / 位置 / 语音输入 / 收藏 / 个人名片 / 文件 / 音乐，发送图片移入「相册」"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.7",
                    date = "2026-09-30",
                    items = listOf(
                        "新增「我 → 高级」设置页：流式输出、自定义请求参数、停用超时、自定义桌面图标、深色模式、发送延时、多行文本自动分割",
                        "移除聊天详情页右上角音乐按钮"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.6",
                    date = "2026-09-30",
                    items = listOf(
                        "修复大模型不按好友设定名字自称的问题"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.5",
                    date = "2026-09-30",
                    items = listOf(
                        "固定 APK 签名，支持直接覆盖安装新版本"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.4",
                    date = "2026-09-30",
                    items = listOf(
                        "删除写死的「AI 伴侣」身份，AI 的名字 / 性格 / 说话风格完全按你创建的角色设定来"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.3",
                    date = "2026-09-30",
                    items = listOf(
                        "修复发现页动态作者名显示为「AI伴侣」的问题，改显真实角色名",
                        "编辑好友文案调整：人设名字 / 人设描述 / 性格特点"
                    )
                ),
                ChangelogEntry(
                    version = "v1.0.2",
                    date = "2026-09-30",
                    items = listOf(
                        "朋友圈删除按钮移入动态卡片内",
                        "所有页面标题居中",
                        "聊天列表显示角色名",
                        "编辑好友可点头像更换自定义图片",
                        "首次使用不再预置默认好友",
                        "首次扮演先读取设定与记忆再回复",
                        "好友详情支持清除记忆、删除好友"
                    )
                ),
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
