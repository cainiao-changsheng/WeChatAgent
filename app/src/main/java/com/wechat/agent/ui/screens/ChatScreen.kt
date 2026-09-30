package com.wechat.agent.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.EmojiManager
import com.wechat.agent.data.EmojiSticker
import com.wechat.agent.data.ImageCacheHelper
import com.wechat.agent.data.MusicController
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.model.Message
import com.wechat.agent.data.model.MessageStatus
import com.wechat.agent.data.model.Role
import com.wechat.agent.ui.components.CenteredTopBar
import com.wechat.agent.ui.theme.DarkOtherBubble
import com.wechat.agent.ui.theme.DarkSelfBubble
import com.wechat.agent.ui.theme.OtherBubble
import com.wechat.agent.ui.theme.SelfBubble
import com.wechat.agent.ui.theme.WeChatGreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatTitle: String,
    messages: List<Message>,
    streamingContent: String,
    streamingReasoning: String = "",
    isLoading: Boolean,
    agentAvatar: String = "🤖",
    userAvatar: String = "👤",
    agentAvatarUri: String = "",
    userAvatarUri: String = "",
    moodText: String = "",
    nowPlaying: MusicController.NowPlaying = MusicController.NowPlaying(),
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (String) -> Unit,
    onPlayMusic: () -> Unit = {},
    onPauseMusic: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    onOpenMusicApp: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onTypingChange: (Boolean) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var plusMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF191919)
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context.applicationContext) }
    val advSettings by settingsManager.advancedSettings.collectAsState()
    val emojiManager = remember { EmojiManager(context) }
    var stickers by remember { mutableStateOf(emojiManager.getAllStickers()) }
    var showAddStickerDialog by remember { mutableStateOf(false) }
    var newStickerName by remember { mutableStateOf("") }
    var newStickerShortcut by remember { mutableStateOf("") }
    var pendingStickerPath by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendImage(uri.toString())
        }
    }

    // 新增表情：先选相册图片并缓存到内部存储，再弹窗输入备注名/快捷名
    val stickerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val cached = ImageCacheHelper.cacheToInternal(context, uri.toString())
            if (cached != null) {
                pendingStickerPath = cached
                newStickerName = ""
                newStickerShortcut = ""
                showAddStickerDialog = true
            }
        }
    }

    // 导入表情包：选择 zip（参考格式 custom_stickers.json + 图片），解压并逐个添加
    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    emojiManager.importFromZip(input)
                }
            }.getOrNull()
            stickers = emojiManager.getAllStickers()
            if (result != null) {
                Toast.makeText(
                    context,
                    "导入完成：成功 ${result.first} 个，跳过 ${result.second} 个",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "导入失败，请选择格式正确的表情包 zip", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 导出表情包：选择保存位置，写入参考格式 zip（custom_stickers.json + 图片）
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    emojiManager.exportToZip(output)
                } ?: false
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (ok) "表情包已导出" else "导出失败（暂无表情可导出）",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(messages.size, streamingContent, streamingReasoning) {
        if (messages.isNotEmpty() || streamingContent.isNotEmpty() || streamingReasoning.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(0, messages.size))
        }
    }

    Scaffold(
        topBar = {
            CenteredTopBar(
                content = {
                    Row(
                        modifier = Modifier.clickable(onClick = onAvatarClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WeChatGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            if (agentAvatarUri.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                                    contentDescription = "好友头像",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(agentAvatar, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(chatTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isLoading) {
                                Text("对方正在输入中", style = MaterialTheme.typography.labelSmall,
                                    color = WeChatGreen.copy(alpha = 0.8f), maxLines = 1)
                            } else if (moodText.isNotEmpty()) {
                                Text(moodText, style = MaterialTheme.typography.labelSmall,
                                    color = WeChatGreen.copy(alpha = 0.8f), maxLines = 1)
                            }
                        }
                    }
                },
                showBack = true,
                onBack = onBack
            )
        },

        bottomBar = {
            Column(
                modifier = Modifier.imePadding()
            ) {
                // 输入框上方联想：输入包含表情备注名/快捷名时展示对应图片，点击即发送
                val matchedStickers = remember(inputText, stickers) {
                    if (inputText.isBlank()) emptyList()
                    else stickers.filter {
                        inputText.contains(it.name) ||
                            (it.shortcut.isNotEmpty() && inputText.contains(it.shortcut))
                    }
                }
                AnimatedVisibility(visible = matchedStickers.isNotEmpty()) {
                    StickerSuggestionRow(
                        stickers = matchedStickers,
                        onPick = { sticker ->
                            onSendImage(sticker.imagePath)
                            inputText = ""
                            onTypingChange(false)
                        }
                    )
                }
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
                ChatInputBar(
                    inputText = inputText,
                    onInputChange = {
                        inputText = it
                        onTypingChange(it.isNotEmpty())
                        if (it.isNotEmpty()) showEmojiPanel = false
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                            onTypingChange(false)
                        }
                    },
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onToggleEmoji = { showEmojiPanel = !showEmojiPanel; plusMenuExpanded = false },
                    emojiSelected = showEmojiPanel,
                    plusMenuExpanded = plusMenuExpanded,
                    onPlusMenuChange = { plusMenuExpanded = it },
                    enabled = true // 回复期间不锁定输入：发新消息即打断当前生成（sendMessage 会 cancel 旧 streamingJob）
                )
                AnimatedVisibility(visible = showEmojiPanel) {
                    EmojiPanel(
                        stickers = stickers,
                        onStickerClick = { sticker ->
                            onSendImage(sticker.imagePath)
                            showEmojiPanel = false
                        },
                        onAddSticker = {
                            showEmojiPanel = false
                            stickerPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onImportStickers = {
                            importZipLauncher.launch(arrayOf("application/zip"))
                        },
                        onExportStickers = {
                            exportZipLauncher.launch("stickers_${System.currentTimeMillis()}.zip")
                        },
                        onRemoveSticker = { sticker ->
                            emojiManager.removeSticker(sticker.name)
                            stickers = emojiManager.getAllStickers()
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            state = listState
        ) {
            if (messages.isEmpty() && streamingContent.isEmpty() && streamingReasoning.isEmpty()) {
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
                    agentAvatarUri = agentAvatarUri, userAvatarUri = userAvatarUri,
                    autoCollapseThinking = advSettings.autoCollapseThinking)
            }
            if (advSettings.thinkDisplay && streamingReasoning.isNotEmpty()) {
                item {
                    // 流式思考气泡：与正文气泡相同的起点偏移（头像 36dp + 间距 8dp），水平中点对齐正文区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ThinkingBubble(
                            reasoning = streamingReasoning,
                            autoCollapsed = advSettings.autoCollapseThinking,
                            isDark = isDark
                        )
                    }
                }
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

    if (showAddStickerDialog) {
        AlertDialog(
            onDismissRequest = { showAddStickerDialog = false; pendingStickerPath = "" },
            title = { Text("新增表情") },
            text = {
                Column {
                    if (pendingStickerPath.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(File(pendingStickerPath)).crossfade(true).build(),
                            contentDescription = "表情预览",
                            modifier = Modifier.size(width = 96.dp, height = 96.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = newStickerName,
                        onValueChange = { newStickerName = it },
                        placeholder = { Text("备注名称（聊天输入此名称时展示图片）") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newStickerShortcut,
                        onValueChange = { newStickerShortcut = it },
                        placeholder = { Text("快捷名称（可选，更短的关键词）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (emojiManager.addSticker(pendingStickerPath, newStickerName, newStickerShortcut)) {
                        stickers = emojiManager.getAllStickers()
                    }
                    pendingStickerPath = ""
                    newStickerName = ""
                    newStickerShortcut = ""
                    showAddStickerDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddStickerDialog = false
                    pendingStickerPath = ""
                    newStickerName = ""
                    newStickerShortcut = ""
                }) { Text("取消") }
            }
        )
    }
}

/** 流式期间的"思考过程"气泡：深色圆角卡片，标题行可点击展开/折叠。
 *  autoCollapsed 为 true（高级设置"思考完成自动折叠气泡"开启）时默认折叠为一行摘要。
 *  宽度固定为正文气泡最大宽度（280dp）的 90%（252dp），独立显示在正文气泡上方。 */
@Composable
private fun ThinkingBubble(
    reasoning: String,
    autoCollapsed: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var collapsed by remember { mutableStateOf(autoCollapsed) }
    val bubbleBg = if (isDark) Color(0xFF262A35) else Color(0xFFF0F1F5)
    val titleColor = if (isDark) Color(0xFFC6C9D4) else Color(0xFF8A8FA3)
    val bodyColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF3A3F4B)
    Column(
        modifier = modifier
            .widthIn(max = 252.dp)
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bubbleBg)
            .clickable { collapsed = !collapsed }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧠 思考过程", style = MaterialTheme.typography.labelMedium, color = titleColor,
                modifier = Modifier.weight(1f))
            Text(
                if (collapsed) "已深度思考 ${reasoning.length} 字 ▾" else "收起 ▴",
                style = MaterialTheme.typography.labelSmall,
                color = titleColor.copy(alpha = 0.7f)
            )
        }
        if (!collapsed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor
            )
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
    userAvatarUri: String = "",
    autoCollapseThinking: Boolean = false
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
    // 大模型回复中的表情标记（[名称] / 表情:名称）→ 解析为本地图片展示
    val stickerManager = remember { EmojiManager(context) }
    val stickerList = remember { stickerManager.getAllStickers() }
    val parsed = if (isUser) null else remember(message.content, stickerList) {
        parseStickerContent(message.content, stickerList)
    }
    // 记录正文气泡实际宽度（像素），用于思考气泡水平中点与正文气泡中点对齐
    var bodyWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 })) {
        // AI 消息：思考气泡与正文气泡上下排列，二者水平中点对齐（思考气泡整体位于头像右侧的正文区域上方）
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                if (!isUser && message.thinking.orEmpty().isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 280.dp)
                            .then(if (bodyWidthPx > 0) Modifier.width(with(density) { bodyWidthPx.toDp() }) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        ThinkingBubble(reasoning = message.thinking.orEmpty(), autoCollapsed = autoCollapseThinking, isDark = isDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (message.imageUri.isNotEmpty()) {
                    val imageData: Any = if (message.imageUri.startsWith("/")) File(message.imageUri) else Uri.parse(message.imageUri)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(imageData).crossfade(true).build(),
                        contentDescription = "图片消息",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 140.dp, height = 140.dp)
                            .clip(RoundedCornerShape(
                                topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (!isUser && parsed != null && parsed.images.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.images.forEach { s ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(File(s.imagePath)).crossfade(true).build(),
                                contentDescription = s.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if ((if (isUser) message.content else parsed?.text ?: message.content).isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                if (coordinates.size.width > 0) bodyWidthPx = coordinates.size.width
                            }
                            .clip(RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp, bottomEnd = 16.dp))
                            .background(bubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (isUser) message.content else parsed?.text ?: message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser && !isDark) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface)
                    }
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
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onToggleEmoji: () -> Unit,
    emojiSelected: Boolean,
    plusMenuExpanded: Boolean,
    onPlusMenuChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
    ) {
        // ➕ 展开的更多面板（参考图：2 行 × 4 列，深色圆角按钮）
        AnimatedVisibility(
            visible = plusMenuExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            PlusMenuPanel(
                onDismiss = { onPlusMenuChange(false) },
                onPickImage = { onPlusMenuChange(false); onPickImage() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleEmoji, enabled = enabled) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "表情",
                    tint = if (emojiSelected) WeChatGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = inputText, onValueChange = onInputChange, modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeChatGreen, unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (inputText.isBlank()) {
                IconButton(
                    onClick = { onPlusMenuChange(!plusMenuExpanded) },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else {
                IconButton(
                    onClick = onSend, enabled = enabled,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        if (enabled) WeChatGreen else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送",
                        tint = if (enabled) Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/** ➕ 更多面板：深色底 2×4 网格按钮，参考图（2515.jpg）排版；仅“相册”可用（发送图片）。 */
@Composable
private fun PlusMenuPanel(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit
) {
    val items = listOf(
        "相册" to Icons.Default.Image,
        "拍摄" to Icons.Default.PhotoCamera,
        "位置" to Icons.Default.LocationOn,
        "语音输入" to Icons.Default.Mic,
        "收藏" to Icons.Default.Star,
        "个人名片" to Icons.Default.Person,
        "文件" to Icons.Default.Folder,
        "音乐" to Icons.Default.MusicNote
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A30))
            .padding(vertical = 10.dp)
    ) {
        items.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                rowItems.forEach { (label, icon) ->
                    val isAlbum = label == "相册"
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = isAlbum) {
                                if (isAlbum) onPickImage() else onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isAlbum) WeChatGreen.copy(alpha = 0.28f)
                                    else Color(0xFF3A3A42)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isAlbum) WeChatGreen else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            label,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiPanel(
    stickers: List<EmojiSticker>,
    onStickerClick: (EmojiSticker) -> Unit,
    onAddSticker: () -> Unit,
    onImportStickers: () -> Unit,
    onExportStickers: () -> Unit,
    onRemoveSticker: (EmojiSticker) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("表情", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onImportStickers) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("导入", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onExportStickers) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("导出", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onAddSticker) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("新增表情", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无表情，点击右上角新增", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                gridItems(stickers) { sticker ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStickerClick(sticker) }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(File(sticker.imagePath)).crossfade(true).build(),
                            contentDescription = sticker.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemoveSticker(sticker) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "删除表情",
                                tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            sticker.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 输入框上方联想条：横向展示命中的表情图片，点击即发送。 */
@Composable
private fun StickerSuggestionRow(
    stickers: List<EmojiSticker>,
    onPick: (EmojiSticker) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("表情", style = MaterialTheme.typography.labelSmall,
            color = WeChatGreen.copy(alpha = 0.8f))
        stickers.forEach { sticker ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(sticker) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(sticker.imagePath)).crossfade(true).build(),
                    contentDescription = sticker.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                )
                Text(sticker.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1)
            }
        }
    }
}

/** 解析大模型回复中的表情标记：[名称] 或 表情:名称，返回清理后的文本与命中的表情列表。 */
private data class StickerParsed(val text: String, val images: List<EmojiSticker>)

private fun parseStickerContent(content: String, stickers: List<EmojiSticker>): StickerParsed {
    var text = content
    val found = mutableListOf<EmojiSticker>()
    stickers.forEach { s ->
        val tokens = mutableListOf("[${s.name}]", "表情:${s.name}")
        if (s.shortcut.isNotEmpty()) {
            tokens.add("[${s.shortcut}]")
            tokens.add("表情:${s.shortcut}")
        }
        tokens.forEach { token ->
            if (text.contains(token)) {
                text = text.replace(token, "")
                if (!found.any { it.name == s.name }) found.add(s)
            }
        }
    }
    return StickerParsed(text.trim(), found)
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
        // 仅歌曲信息区域可点击打开音乐 App，避免与播放控制按钮的事件竞争
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenApp),
            verticalArrangement = Arrangement.Center
        ) {
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
