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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.model.MomentPost
import com.wechat.agent.ui.components.CenteredTopBar
import com.wechat.agent.ui.theme.WeChatGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    agentAvatar: String = "🤖",
    agentAvatarUri: String = "",
    agentName: String = "AI伴侣",
    userAvatar: String = "👤",
    userAvatarUri: String = "",
    posts: List<MomentPost>,
    isLoading: Boolean,
    onBack: () -> Unit = {},
    showBack: Boolean = true,
    onComposeMoment: () -> Unit,
    onToggleLike: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    onDeletePost: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val monthDayFormat = remember { SimpleDateFormat("MM月dd日", Locale.getDefault()) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenteredTopBar(
                content = { Text("朋友圈", fontWeight = FontWeight.Medium) },
                showBack = showBack,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onComposeMoment) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "发动态",
                            tint = WeChatGreen
                        )
                    }
                }
            )
        },
        bottomBar = { bottomBar() }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (posts.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📱", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("还没有动态", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击右上角相机发布动态，AI 好友会来互动",
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
                            agentName = agentName,
                            userAvatar = userAvatar,
                            userAvatarUri = userAvatarUri,
                            onToggleLike = onToggleLike,
                            onAddComment = onAddComment,
                            timeFormat = timeFormat,
                            monthDayFormat = monthDayFormat,
                            showDelete = true,
                            onDeleteClick = { pendingDelete = post.id }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { postId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除动态") },
            text = { Text("是否删除该动态？删除后不可恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeletePost(postId)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun MomentPostCard(
    post: MomentPost,
    agentAvatar: String,
    agentAvatarUri: String,
    agentName: String,
    userAvatar: String,
    userAvatarUri: String,
    onToggleLike: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    timeFormat: SimpleDateFormat,
    monthDayFormat: SimpleDateFormat,
    showDelete: Boolean = false,
    onDeleteClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val timeDisplay = if (now - post.timestamp > 24 * 60 * 60 * 1000) {
        monthDayFormat.format(Date(post.timestamp))
    } else {
        timeFormat.format(Date(post.timestamp))
    }
    val isUserPost = post.author == "我"
    val displayAuthor = if (post.author.isBlank() || post.author == "AI伴侣") agentName else post.author
    var showComments by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }

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
                    val displayUri = if (isUserPost) userAvatarUri else agentAvatarUri
                    val displayAvatar = if (isUserPost) userAvatar else agentAvatar
                    if (displayUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(displayUri)).crossfade(true).build(),
                            contentDescription = "", modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            displayAvatar.ifEmpty { if (isUserPost) "👤" else "🤖" },
                            fontSize = MaterialTheme.typography.titleLarge.fontSize
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            displayAuthor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUserPost) MaterialTheme.colorScheme.primary else WeChatGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(timeDisplay, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        if (showDelete && isUserPost) {
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除该动态",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (post.content.isNotEmpty()) {
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 动态图片
                    if (post.imageUri.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(post.imageUri)).crossfade(true).build(),
                            contentDescription = "动态图片",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (post.mood.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "心情：${post.mood}",
                            style = MaterialTheme.typography.labelSmall,
                            color = WeChatGreen.copy(alpha = 0.7f)
                        )
                    }

                    // 评论列表（点击展开）
                    if (post.comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(WeChatGreen.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            post.comments.takeLast(if (showComments) 20 else 3).forEach { raw ->
                                val sep = raw.indexOf("::")
                                val author = if (sep >= 0) raw.substring(0, sep)
                                    .ifEmpty { if (post.author == "我") agentName else post.author } else agentName
                                val text = if (sep >= 0) raw.substring(sep + 2) else raw
                                Text(
                                    "$author: $text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                            if (post.comments.size > 3 && !showComments) {
                                Text(
                                    "查看全部 ${post.comments.size} 条评论",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WeChatGreen,
                                    modifier = Modifier.clickable { showComments = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 点赞 + 评论
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleLike(post.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (post.liked || post.aiLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
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

                        Text(
                            "💬 ${post.commentCount}条评论",
                            style = MaterialTheme.typography.labelSmall,
                            color = WeChatGreen.copy(alpha = 0.8f),
                            modifier = Modifier.clickable { showComments = !showComments }
                        )

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // 评论输入
                    if (showComments) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = commentDraft,
                                onValueChange = { commentDraft = it },
                                placeholder = { Text("评论…") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (commentDraft.isNotBlank()) {
                                        onAddComment(post.id, commentDraft.trim())
                                        commentDraft = ""
                                    }
                                },
                                enabled = commentDraft.isNotBlank(),
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(
                                    if (commentDraft.isNotBlank()) WeChatGreen
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送评论",
                                    tint = if (commentDraft.isNotBlank()) androidx.compose.ui.graphics.Color.White
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
