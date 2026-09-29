package com.wechat.agent.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.AgentProfile
import com.wechat.agent.data.model.Chat
import com.wechat.agent.ui.components.CenteredTopBar
import com.wechat.agent.ui.theme.WeChatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<Chat>,
    agentAvatar: String = "🤖",
    agentAvatarUri: String = "",
    profiles: List<AgentProfile> = emptyList(),
    onChatClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredChats = remember(chats, searchQuery, profiles) {
        if (searchQuery.isBlank()) chats
        else chats.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.contains(searchQuery, ignoreCase = true) ||
                (it.agentId.isNotBlank() && profiles.any { p -> p.id == it.agentId && p.name.contains(searchQuery, ignoreCase = true) })
        }
    }

    Scaffold(
        topBar = {
            CenteredTopBar(
                content = { Text("聊天", fontWeight = FontWeight.Medium) },
                fullWidthContent = if (searching) {
                    {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索聊天记录") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else null,
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy((-8).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) searchQuery = ""
                        }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searching) "取消搜索" else "搜索",
                                tint = WeChatGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Default.Add, contentDescription = "新建对话", tint = WeChatGreen,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        },
        bottomBar = { bottomBar() }
    ) { padding ->
        val displayList = filteredChats
        if (displayList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (searching) "未找到相关聊天记录" else "暂无对话", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (searching) "换个关键词试试" else "点击右上角 + 开始新对话", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(displayList, key = { it.id }) { chat ->
                    val chatTitle = chat.agentId.takeIf { it.isNotBlank() }
                        ?.let { aid -> profiles.firstOrNull { it.id == aid }?.name }
                        ?: chat.title
                    ChatListItem(
                        chat = chat,
                        titleOverride = chatTitle,
                        agentAvatar = agentAvatar,
                        agentAvatarUri = agentAvatarUri,
                        onClick = { onChatClick(chat.id) },
                        onLongClick = { showDeleteDialog = chat.id }
                    )
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog?.let { onDeleteChat(it) }; showDeleteDialog = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }
}

@Composable
fun ChatListItem(chat: Chat, agentAvatar: String = "🤖", agentAvatarUri: String = "",
                 titleOverride: String? = null,
                 onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val timeStr = if (now - chat.lastTime > 24 * 60 * 60 * 1000)
        dateFormat.format(Date(chat.lastTime)) else timeFormat.format(Date(chat.lastTime))

    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(WeChatGreen),
                contentAlignment = Alignment.Center
            ) {
                if (agentAvatarUri.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                        contentDescription = "", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop)
                } else {
                    Text(agentAvatar, fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titleOverride ?: chat.title,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeStr, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(chat.lastMessage.ifEmpty { "暂无消息" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 80.dp, end = 16.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    }
}
