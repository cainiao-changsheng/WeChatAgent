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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.ui.theme.WeChatGreen

/**
 * 编辑用户资料：自定义头像（默认头像选择 + 本地图片上传）与昵称。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    userAvatar: String,
    userAvatarUri: String,
    userNickname: String,
    onBack: () -> Unit,
    onSave: (avatar: String, avatarUri: String, nickname: String) -> Unit
) {
    val context = LocalContext.current
    var avatar by remember { mutableStateOf(userAvatar) }
    var avatarUri by remember { mutableStateOf(userAvatarUri) }
    var nickname by remember { mutableStateOf(userNickname) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            avatarUri = it.toString()
            avatar = ""
        }
    }

    val avatarOptions = listOf(
        "👤", "😀", "😎", "🤓", "🥰", "😇", "🤩", "😜",
        "🐱", "🐶", "🦊", "🐼", "🐨", "🦄", "🐙", "🐸",
        "🌻", "🌙", "⭐", "🔥", "🌈", "🍀", "🎈", "🎧",
        "👨‍💻", "👩‍🎨", "🦸", "🧙", "🤖", "👑", "💃", "🏆"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("编辑资料", fontWeight = FontWeight.Medium)
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
            // 当前头像
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(WeChatGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Uri.parse(avatarUri)).crossfade(true).build(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(avatar.ifEmpty { "👤" }, fontSize = 44.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 默认头像选择
            Text("选择默认头像", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                avatarOptions.forEach { option ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (avatar == option && avatarUri.isEmpty()) WeChatGreen.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 2.dp,
                                color = if (avatar == option && avatarUri.isEmpty()) WeChatGreen
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { avatar = option; avatarUri = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 从相册上传
            Button(
                onClick = {
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("从相册选择头像")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 昵称
            Text("昵称", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                placeholder = { Text("请输入昵称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(avatar, avatarUri, nickname.trim())
                },
                enabled = nickname.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
            ) {
                Text("保存", fontWeight = FontWeight.Medium)
            }
        }
    }
}
