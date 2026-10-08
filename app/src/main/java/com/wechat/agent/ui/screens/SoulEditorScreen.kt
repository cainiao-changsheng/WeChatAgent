package com.wechat.agent.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wechat.agent.data.SoulManager
import com.wechat.agent.data.SoulTemplate
import com.wechat.agent.ui.theme.WeChatGreen

/** 灵魂文件 soul.md 的应用内编辑器（Agent 模式核心人设，保存后即时生效）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulEditorScreen(
    agentId: String,
    agentName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val soulManager = remember { SoulManager.get(context.applicationContext) }
    var content by remember { mutableStateOf(soulManager.readSoul(agentId)) }
    val filePath = remember { soulManager.soulFile(agentId).absolutePath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("灵魂文件", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        soulManager.writeSoul(agentId, content)
                        Toast.makeText(context, "已保存，下次回复即时生效", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) { Text("保存", color = WeChatGreen, fontWeight = FontWeight.Bold) }
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "「$agentName」的人设来源，每次回复前都会实时读取并注入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "也可在文件管理器中打开编辑：\n$filePath",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().height(480.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("在此编写 soul.md…") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                content = SoulTemplate.DEFAULT
            }) {
                Text("重置为默认模板", color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}