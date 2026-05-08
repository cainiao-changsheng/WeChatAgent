package com.wechat.agent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.ui.theme.WeChatGreen
import com.wechat.agent.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val apiUrl by viewModel.apiUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val agentAvatar by viewModel.agentAvatar.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val agentAvatarUri by viewModel.agentAvatarUri.collectAsState()
    val userAvatarUri by viewModel.userAvatarUri.collectAsState()

    var urlInput by remember(apiUrl) { mutableStateOf(apiUrl) }
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var modelInput by remember(modelName) { mutableStateOf(modelName) }
    var selectedAgentAvatar by remember(agentAvatar) { mutableStateOf(agentAvatar) }
    var selectedUserAvatar by remember(userAvatar) { mutableStateOf(userAvatar) }
    var agentPhotoUri by remember(agentAvatarUri) { mutableStateOf(agentAvatarUri) }
    var userPhotoUri by remember(userAvatarUri) { mutableStateOf(userAvatarUri) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(apiUrl, agentAvatar, userAvatar, agentAvatarUri, userAvatarUri) {}

    val agentGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            agentPhotoUri = it.toString()
            selectedAgentAvatar = ""
        }
    }

    val userGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            userPhotoUri = it.toString()
            selectedUserAvatar = ""
        }
    }

    val agentPhotoOptions = listOf(
        "🤖", "🦾", "🧠", "⚡", "🔥", "💎", "🌟", "🎯",
        "🐱", "🐶", "🦊", "🐼", "🐨", "🦄", "🐙", "👽",
        "😎", "🤓", "🧑‍💻", "🦸", "🧙", "🧚", "👑"
    )

    val userPhotoOptions = listOf(
        "👤", "😊", "🙂", "😎", "🤩", "🥳", "🤠", "👦",
        "👧", "👨", "👩", "🧑", "👶", "🧒", "🧔", "👱",
        "🐱", "🐶", "🐰", "🐻", "🦊", "🐸", "🐵", "🐯"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {

            Text("🤖 AI Agent 头像", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("选择表情 或 从相册选取照片", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(WeChatGreen.copy(alpha = 0.15f))
                        .clickable { agentGalleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (agentPhotoUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(agentPhotoUri)).crossfade(true).build(),
                            contentDescription = "",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(selectedAgentAvatar, fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("当前头像", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "", modifier = Modifier.size(16.dp),
                            tint = WeChatGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("点击头像从相册选取", style = MaterialTheme.typography.bodySmall,
                            color = WeChatGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                agentPhotoOptions.forEach { avatar ->
                    AvatarOption(avatar = avatar, selected = selectedAgentAvatar == avatar && agentPhotoUri.isEmpty(),
                        onClick = { selectedAgentAvatar = avatar; agentPhotoUri = "" })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveAvatarUri(agentPhotoUri, userPhotoUri); viewModel.saveAvatar(selectedAgentAvatar, selectedUserAvatar);
                    snackbarHostState.showSnackbar("头像已保存") },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
            ) { Text("保存头像", fontWeight = FontWeight.Medium) }

            Spacer(modifier = Modifier.height(20.dp))

            Text("👤 用户头像", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable { userGalleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (userPhotoUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(userPhotoUri)).crossfade(true).build(),
                            contentDescription = "",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(selectedUserAvatar, fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("当前头像", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "", modifier = Modifier.size(16.dp), tint = WeChatGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("点击头像从相册选取", style = MaterialTheme.typography.bodySmall, color = WeChatGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                userPhotoOptions.forEach { avatar ->
                    AvatarOption(avatar = avatar, selected = selectedUserAvatar == avatar && userPhotoUri.isEmpty(),
                        onClick = { selectedUserAvatar = avatar; userPhotoUri = "" })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ API 配置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(WeChatGreen.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("DeepSeek", style = MaterialTheme.typography.labelSmall, color = WeChatGreen, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("已预填 DeepSeek API，只需输入 Key", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(value = urlInput, onValueChange = { urlInput = it },
                        label = { Text("API 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it },
                        label = { Text("API 密钥") }, placeholder = { Text("sk-...") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = modelInput, onValueChange = { modelInput = it },
                        label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        viewModel.saveSettings(urlInput, keyInput, modelInput)
                        viewModel.saveAvatarUri(agentPhotoUri, userPhotoUri)
                        viewModel.saveAvatar(selectedAgentAvatar, selectedUserAvatar)
                        snackbarHostState.showSnackbar("全部设置已保存")
                    }, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)) {
                        Text("保存全部设置", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 使用说明", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 前往 platform.deepseek.com 获取 API Key\n" +
                        "2. API 地址和模型已预填，输入 Key 即可\n" +
                        "3. 顶部可选表情头像或从相册选照片\n" +
                        "4. Agent 有真人记忆：好感度、情绪状态会随聊天变化\n" +
                        "5. 每次对话后Agent会后台复盘，越聊越懂你\n\n" +
                        "兼容 OpenAI / Azure / 硅基流动等接口",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AvatarOption(avatar: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(if (selected) WeChatGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(2.dp, WeChatGreen, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(avatar, fontSize = MaterialTheme.typography.titleLarge.fontSize) }
}
