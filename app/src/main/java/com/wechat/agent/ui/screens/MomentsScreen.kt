package com.wechat.agent.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.model.MomentPost
import com.wechat.agent.ui.theme.WeChatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    agentAvatar: String = "🤖",
    agentAvatarUri: String = "",
    posts: List<MomentPost>,
    isLoading: Boolean,
    onBack: () -> Unit = {},
    showBack: Boolean = true,
    onGenerateNew: () -> Unit,
    onToggleLike: (String) -> Unit,
    onPostMoment: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val monthDayFormat = remember { SimpleDateFormat("MM月dd日", Locale.getDefault()) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("朋友圈", fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onGenerateNew, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = WeChatGreen
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "生成新动态",
                                tint = WeChatGreen
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = { bottomBar() }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 发布动态输入区
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("发一条动态…") },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onPostMoment(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        if (draft.isNotBlank()) WeChatGreen else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发布",
                        tint = if (draft.isNotBlank()) androidx.compose.ui.graphics.Color.White
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            if (posts.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📱", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("还没有动态", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("在上面输入内容发布，或点击右上角 ✨ 让Agent发一条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(posts.reversed(), key = { it.id }) { post ->
                        MomentPostCard(
                            post = post,
                            agentAvatar = agentAvatar,
                            agentAvatarUri = agentAvatarUri,
                            onToggleLike = onToggleLike,
                            timeFormat = timeFormat,
                            monthDayFormat = monthDayFormat
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun MomentPostCard(
    post: MomentPost,
    agentAvatar: String,
    agentAvatarUri: String,
    onToggleLike: (String) -> Unit,
    timeFormat: SimpleDateFormat,
    monthDayFormat: SimpleDateFormat
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val timeDisplay = if (now - post.timestamp > 24 * 60 * 60 * 1000) {
        monthDayFormat.format(Date(post.timestamp))
    } else {
        timeFormat.format(Date(post.timestamp))
    }
    val isUserPost = post.author == "我"

    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        if (isUserPost) MaterialTheme.colorScheme.primary else WeChatGreen
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isUserPost && agentAvatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                            contentDescription = "", modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            if (isUserPost) "👤" else agentAvatar,
                            fontSize = MaterialTheme.typography.titleLarge.fontSize
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.author,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUserPost) MaterialTheme.colorScheme.primary else WeChatGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(timeDisplay, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (post.mood.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "心情：${post.mood}",
                            style = MaterialTheme.typography.labelSmall,
                            color = WeChatGreen.copy(alpha = 0.7f)
                        )
                    }

                    // AI 评论
                    if (post.comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(WeChatGreen.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            post.comments.forEach { comment ->
                                Text(
                                    "AI 伴侣: $comment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleLike(post.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (post.liked) Icons.Filled.Favorite else {
                                    if (post.aiLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = "赞",
                                tint = when {
                                    post.liked -> MaterialTheme.colorScheme.error
                                    post.aiLiked -> WeChatGreen
                                    else -> WeChatGreen.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = post.likeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                post.liked -> MaterialTheme.colorScheme.error
                                post.aiLiked -> WeChatGreen
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        if (post.commentCount > 0) {
                            Text(
                                "💬 ${post.commentCount}条评论",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            "🌐",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    }
                }
            }
        }
    }
}
