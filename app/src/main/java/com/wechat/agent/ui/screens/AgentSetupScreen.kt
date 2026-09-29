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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.ui.theme.WeChatGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgentSetupScreen(
    initialName: String,
    initialGender: String,
    initialAge: String,
    initialPersona: String,
    initialGlobalSettings: String,
    initialAvatar: String,
    initialAvatarUri: String,
    isEdit: Boolean,
    onSave: (
        name: String, gender: String, age: String, persona: String,
        globalSettings: String, avatar: String, avatarUri: String
    ) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var gender by remember { mutableStateOf(initialGender) }
    var age by remember { mutableStateOf(initialAge) }
    var persona by remember { mutableStateOf(initialPersona) }
    var globalSettings by remember { mutableStateOf(initialGlobalSettings) }
    var avatar by remember { mutableStateOf(initialAvatar) }
    var avatarUri by remember { mutableStateOf(initialAvatarUri) }

    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            avatarUri = it.toString()
            avatar = ""
        }
    }

    val avatarOptions = listOf(
        "🤖", "🦾", "🧠", "⚡", "🔥", "💎", "🌟", "🎯",
        "🐱", "🐶", "🦊", "🐼", "🐨", "🦄", "🐙", "👽",
        "😎", "🤓", "🧑‍💻", "🦸", "🧙", "🧚", "👑", "💃",
        "👩‍🎨", "👨‍🎤", "🌸", "🌙"
    )

    fun doSave() {
        val finalName = name.trim().ifEmpty { "AI伴侣" }
        onSave(finalName, gender.trim().ifEmpty { "女" }, age.trim().ifEmpty { "18" },
            persona, globalSettings, avatar, avatarUri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (isEdit) "编辑 AI 好友" else "新增 AI 好友", fontWeight = FontWeight.Medium)
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
            // 头像
            Text("头像", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("选择表情 或 点击头像从相册上传", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(WeChatGreen.copy(alpha = 0.15f))
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(avatarUri)).crossfade(true).build(),
                            contentDescription = "",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(avatar.ifEmpty { "🤖" }, fontSize = MaterialTheme.typography.displaySmall.fontSize)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "", tint = WeChatGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("从相册上传", style = MaterialTheme.typography.bodySmall, color = WeChatGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                avatarOptions.forEach { option ->
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(
                                if (avatar == option && avatarUri.isEmpty()) WeChatGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (avatar == option && avatarUri.isEmpty()) Modifier.border(2.dp, WeChatGreen, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            .clickable { avatar = option; avatarUri = "" },
                        contentAlignment = Alignment.Center
                    ) { Text(option, fontSize = MaterialTheme.typography.titleLarge.fontSize) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称") }, placeholder = { Text("给 AI 好友起个名字") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("性别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("女", "男").forEach { option ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (gender == option) WeChatGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (gender == option) Modifier.border(1.5.dp, WeChatGreen, RoundedCornerShape(20.dp))
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            )
                            .clickable { gender = option }
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option,
                            color = if (gender == option) WeChatGreen else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (gender == option) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = age, onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("年龄") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = persona, onValueChange = { persona = it },
                label = { Text("设定") },
                placeholder = { Text("描述 AI 好友的性格与说话风格，例如：温柔、善解人意") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = globalSettings, onValueChange = { globalSettings = it },
                label = { Text("全局设定") },
                placeholder = { Text("可留空。例如：TA 喜欢清晨发动态、晚上陪你聊天") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { doSave() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
            ) { Text(if (isEdit) "保存修改" else "创建 AI 好友", fontWeight = FontWeight.Medium) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
