package com.wechat.agent.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.model.Chat
import com.wechat.agent.ui.theme.WeChatGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    chats: List<Chat>,
    agentAvatar: String = "🤖",
    agentAvatarUri: String = "",
    onOpenAgentChat: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("通讯录", fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Add, contentDescription = "添加朋友", tint = WeChatGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = { bottomBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 搜索框占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text("搜索", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f))
            }

            // Agent 联系人（点击进入聊天）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAgentChat)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentAvatar(agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Agent 伴侣", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("24 小时在线的 AI 好友",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("发消息", style = MaterialTheme.typography.bodySmall, color = WeChatGreen)
            }

            Spacer(modifier = Modifier.height(4.dp))

            ContactPlaceholderRow(icon = Icons.Default.PersonAdd, title = "新的朋友", tint = Color(0xFFFA9D3B))
            ContactPlaceholderRow(icon = Icons.Outlined.Groups, title = "群聊", tint = WeChatGreen)
            ContactPlaceholderRow(icon = Icons.Default.Tag, title = "标签", tint = Color(0xFF576B95))
            ContactPlaceholderRow(icon = Icons.Default.ShoppingBag, title = "公众号", tint = Color(0xFF576B95))
            ContactPlaceholderRow(icon = Icons.Default.PhotoLibrary, title = "文件传输助手", tint = Color(0xFF888888))

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (chats.isNotEmpty()) "共 ${chats.size} 个会话" else "暂无会话，点击 Agent 伴侣开始聊天",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun AgentAvatar(agentAvatar: String, agentAvatarUri: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WeChatGreen),
        contentAlignment = Alignment.Center
    ) {
        if (agentAvatarUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                contentDescription = "",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(agentAvatar, fontSize = MaterialTheme.typography.titleLarge.fontSize)
        }
    }
}

@Composable
private fun ContactPlaceholderRow(icon: ImageVector, title: String, tint: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
