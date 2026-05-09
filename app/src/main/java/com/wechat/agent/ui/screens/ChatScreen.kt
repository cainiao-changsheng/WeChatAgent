package com.wechat.agent.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.MusicController
import com.wechat.agent.data.model.Message
import com.wechat.agent.data.model.MessageStatus
import com.wechat.agent.data.model.Role
import com.wechat.agent.ui.theme.DarkOtherBubble
import com.wechat.agent.ui.theme.DarkSelfBubble
import com.wechat.agent.ui.theme.OtherBubble
import com.wechat.agent.ui.theme.SelfBubble
import com.wechat.agent.ui.theme.WeChatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatTitle: String,
    messages: List<Message>,
    streamingContent: String,
    isLoading: Boolean,
    agentAvatar: String = "🤖",
    userAvatar: String = "👤",
    agentAvatarUri: String = "",
    userAvatarUri: String = "",
    moodText: String = "",
    nowPlaying: MusicController.NowPlaying = MusicController.NowPlaying(),
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onPlayMusic: () -> Unit = {},
    onPauseMusic: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    onOpenMusicApp: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF191919)
    val context = LocalContext.current

    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty() || streamingContent.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(0, messages.size))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chatTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (moodText.isNotEmpty()) {
                            Text(moodText, style = MaterialTheme.typography.labelSmall,
                                color = WeChatGreen.copy(alpha = 0.8f), maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMusicApp) {
                        Icon(Icons.Default.MusicNote, contentDescription = "打开音乐",
                            tint = WeChatGreen, modifier = Modifier.size(22.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },

        bottomBar = {
            Column {
                if (nowPlaying.title.isNotEmpty()) {
                    MusicControlBar(
                        nowPlaying = nowPlaying,
                        onPlay = onPlayMusic,
                        onPause = onPauseMusic,
                        onSkipNext = onSkipNext,
                        onSkipPrev = onSkipPrev,
                        onOpenApp = onOpenMusicApp
                    )
                }
                ChatInputBar(inputText = inputText, onInputChange = { inputText = it },
                    onSend = { if (inputText.isNotBlank()) { onSendMessage(inputText.trim()); inputText = "" } },
                    enabled = !isLoading)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            state = listState
        ) {
            if (messages.isEmpty() && streamingContent.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                        Text("发送一条消息开始对话 👋", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message, isDark = isDark,
                    agentAvatar = agentAvatar, userAvatar = userAvatar,
                    agentAvatarUri = agentAvatarUri, userAvatarUri = userAvatarUri)
            }
            if (streamingContent.isNotEmpty()) {
                item {
                    MessageBubble(
                        message = Message(content = streamingContent, role = Role.AGENT, status = MessageStatus.SENDING),
                        isDark = isDark, agentAvatar = agentAvatar, userAvatar = userAvatar,
                        agentAvatarUri = agentAvatarUri, userAvatarUri = userAvatarUri)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isDark: Boolean,
    agentAvatar: String = "🤖",
    userAvatar: String = "👤",
    agentAvatarUri: String = "",
    userAvatarUri: String = ""
) {
    val isUser = message.role == Role.USER
    val bubbleColor = when {
        isUser && isDark -> DarkSelfBubble
        isUser && !isDark -> SelfBubble
        !isUser && isDark -> DarkOtherBubble
        else -> OtherBubble
    }
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 })) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(WeChatGreen),
                    contentAlignment = Alignment.Center
                ) {
                    if (agentAvatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                            contentDescription = "", modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        Text(agentAvatar, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(bubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser && !isDark) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.status == MessageStatus.ERROR) {
                        Text("⚠", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(timeFormat.format(Date(message.timestamp)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (userAvatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(userAvatarUri)).crossfade(true).build(),
                            contentDescription = "", modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        Text(userAvatar, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText, onValueChange = onInputChange, modifier = Modifier.weight(1f),
            placeholder = { Text("输入消息...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeChatGreen, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend, enabled = enabled && inputText.isNotBlank(),
            modifier = Modifier.size(44.dp).clip(CircleShape).background(
                if (inputText.isNotBlank() && enabled) WeChatGreen else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送",
                tint = if (inputText.isNotBlank() && enabled) Color.White
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun MusicControlBar(
    nowPlaying: MusicController.NowPlaying,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onOpenApp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpenApp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = "",
            tint = WeChatGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nowPlaying.title.ifEmpty { "未知歌曲" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (nowPlaying.artist.isNotEmpty()) {
                Text(
                    text = nowPlaying.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onSkipPrev, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(20.dp))
        }
        IconButton(
            onClick = if (nowPlaying.isPlaying) onPause else onPlay,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                tint = WeChatGreen,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onSkipNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(20.dp))
        }
    }
}
