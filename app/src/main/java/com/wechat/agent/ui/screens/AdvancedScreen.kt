package com.wechat.agent.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.wechat.agent.data.AdvancedSettings
import com.wechat.agent.ui.components.CenteredTopBar
import com.wechat.agent.viewmodel.SettingsViewModel

/** 参考图配色：紫色强调 + 粉色按钮 */
private val AccentPurple = Color(0xFF9C6BFF)
private val AccentPink = Color(0xFFFF8FB1)
private val DeepPurpleText = Color(0xFF6A1B9A)
private val LightPinkBg = Color(0xFFFCE4EC)
private val RowBg = Color(0xFF23262F)
private val RowBgPressed = Color(0xFF2A2E3A)
private val SubText = Color(0xFF8A8FA3)

/**
 * “我 → 高级”设置页，按参考图（2508.jpg）“思考设置”的排版与功能实现。
 * 列表项：思考设置 / 流式输出 / 自定义请求参数 / 停用超时 / 添加自定义桌面图标 /
 * 深色模式 / 发送延时 / 多行文本自动分割；底部保存 / 测试 / 取消按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val initial by viewModel.advancedSettings.collectAsState()
    val apiUrl by viewModel.apiUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val modelName by viewModel.modelName.collectAsState()

    // 本地编辑状态（保存时一次性落库）
    var streamEnabled by remember { mutableStateOf(initial.streamEnabled) }
    var customParams by remember { mutableStateOf(initial.customParams) }
    var timeoutDisabled by remember { mutableStateOf(initial.timeoutDisabled) }
    var customIcon by remember { mutableStateOf(initial.customIcon) }
    var darkMode by remember { mutableStateOf(initial.darkMode) }
    var sendDelayMs by remember { mutableStateOf(initial.sendDelayMs) }
    var splitMessages by remember { mutableStateOf(initial.splitMessages) }

    // 跳转菜单子配置展开状态
    var thinkExpanded by remember { mutableStateOf(false) }
    var streamExpanded by remember { mutableStateOf(false) }
    var paramsExpanded by remember { mutableStateOf(false) }
    var darkExpanded by remember { mutableStateOf(false) }
    var delayExpanded by remember { mutableStateOf(false) }

    // 子项附加配置（不落库的次级选项）
    var thinkDisplay by remember { mutableStateOf(initial.streamEnabled) }
    var charByChar by remember { mutableStateOf(false) }
    var disableAnimation by remember { mutableStateOf(false) }

    var sendDelayText by remember { mutableStateOf(initial.sendDelayMs.toString()) }

    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(initial) {
        streamEnabled = initial.streamEnabled
        customParams = initial.customParams
        timeoutDisabled = initial.timeoutDisabled
        customIcon = initial.customIcon
        darkMode = initial.darkMode
        sendDelayMs = initial.sendDelayMs
        splitMessages = initial.splitMessages
        sendDelayText = initial.sendDelayMs.toString()
        thinkDisplay = initial.streamEnabled
    }

    Scaffold(
        topBar = {
            CenteredTopBar(
                content = { Text("思考设置", fontWeight = FontWeight.Medium) },
                showBack = true,
                onBack = onBack,
                containerColor = Color(0xFF181A20)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF181A20)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF181A20))
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. 思考设置
                ExpandableRow(
                    title = "思考设置",
                    subtitle = "配置思考过程显示和发送设置",
                    expanded = thinkExpanded,
                    onToggle = { thinkExpanded = !thinkExpanded }
                ) {
                    SubSwitchRow(
                        label = "启用思考过程显示",
                        checked = thinkDisplay,
                        onCheckedChange = { thinkDisplay = it }
                    )
                }

                // 2. 流式输出
                ExpandableRow(
                    title = "流式输出",
                    subtitle = "启用流式输出、逐字输出、动画禁用等选项",
                    expanded = streamExpanded,
                    onToggle = { streamExpanded = !streamExpanded }
                ) {
                    SubSwitchRow(
                        label = "启用流式输出",
                        checked = streamEnabled,
                        onCheckedChange = { streamEnabled = it }
                    )
                    SubSwitchRow(
                        label = "逐字输出",
                        checked = charByChar,
                        onCheckedChange = { charByChar = it }
                    )
                    SubSwitchRow(
                        label = "动画禁用",
                        checked = disableAnimation,
                        onCheckedChange = { disableAnimation = it }
                    )
                }

                // 3. 自定义请求参数
                ExpandableRow(
                    title = "自定义请求参数",
                    subtitle = "自定义请求体参数，解决平台独有参数或不一致问题（如智谱GLM的thinking.type）",
                    expanded = paramsExpanded,
                    onToggle = { paramsExpanded = !paramsExpanded }
                ) {
                    OutlinedTextField(
                        value = customParams,
                        onValueChange = { customParams = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        placeholder = { Text("例如：{\"thinking\":{\"type\":\"enabled\"}}", color = SubText) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE8E8F0)),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = Color(0xFF3A3F4B),
                            focusedContainerColor = Color(0xFF1E2129),
                            unfocusedContainerColor = Color(0xFF1E2129),
                            cursorColor = AccentPurple
                        ),
                        minLines = 2
                    )
                }

                // 4. 停用超时
                AdvancedSwitchRow(
                    title = "停用超时",
                    subtitle = "开启后，将停用默认10分钟超时设置",
                    checked = timeoutDisabled,
                    onCheckedChange = { timeoutDisabled = it }
                )

                // 5. 添加自定义桌面图标
                AdvancedActionRow(
                    title = "添加自定义桌面图标",
                    subtitle = if (customIcon) "已添加自定义桌面图标" else "点击在桌面创建本应用快捷方式",
                    onClick = {
                        val ok = runCatching { addShortcut(context) }.getOrDefault(false)
                        if (ok) {
                            customIcon = true
                            snackbarHostState.showSnackbar("已添加自定义桌面图标")
                        } else {
                            snackbarHostState.showSnackbar("当前环境不支持创建桌面快捷方式")
                        }
                    }
                )

                // 6. 深色模式
                ExpandableRow(
                    title = "深色模式",
                    subtitle = "自定义深色模式开关",
                    expanded = darkExpanded,
                    onToggle = { darkExpanded = !darkExpanded }
                ) {
                    SubSwitchRow(
                        label = "启用深色模式",
                        checked = darkMode,
                        onCheckedChange = { darkMode = it }
                    )
                }

                // 7. 发送延时
                ExpandableRow(
                    title = "发送延时",
                    subtitle = "设置发送消息与请求间的等待时间，节省Token消耗",
                    expanded = delayExpanded,
                    onToggle = { delayExpanded = !delayExpanded }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = sendDelayText,
                            onValueChange = { input ->
                                sendDelayText = input.filter { it.isDigit() }.take(5)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE8E8F0)),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = Color(0xFF3A3F4B),
                                focusedContainerColor = Color(0xFF1E2129),
                                unfocusedContainerColor = Color(0xFF1E2129),
                                cursorColor = AccentPurple
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("毫秒", style = MaterialTheme.typography.bodySmall, color = SubText)
                    }
                }

                // 8. 多行文本自动分割
                AdvancedSwitchRow(
                    title = "多行文本自动分割",
                    subtitle = "AI回复多行时自动拆分为多条消息；关闭则合并为单条“一问一答”",
                    checked = splitMessages,
                    onCheckedChange = { splitMessages = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 底部固定操作区：保存 / 测试（圆形边框）+ 取消（宽幅圆角）
            BottomActionBar(
                onSave = {
                    viewModel.saveAdvancedSettings(
                        AdvancedSettings(
                            streamEnabled = streamEnabled,
                            customParams = customParams.trim(),
                            timeoutDisabled = timeoutDisabled,
                            customIcon = customIcon,
                            darkMode = darkMode,
                            sendDelayMs = sendDelayText.toIntOrNull()?.coerceIn(0, 60000) ?: 0,
                            splitMessages = splitMessages
                        )
                    )
                    snackbarHostState.showSnackbar("高级设置已保存")
                    onBack()
                },
                onTest = {
                    testing = true
                    viewModel.testModelConnection(apiUrl, apiKey, modelName) { reply ->
                        testing = false
                        testResult = reply.ifBlank { "连接失败：请检查 API 地址 / Key / 模型名" }
                    }
                },
                onCancel = onBack
            )
        }
    }

    if (testResult != null) {
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text("测试结果") },
            text = {
                if (testing) {
                    Text("正在测试连接...")
                } else {
                    Text(
                        testResult ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE8E8F0)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { testResult = null }) { Text("确定", color = AccentPink) }
            }
        )
    }
}

/** 跳转菜单行：标题 + 副标题 + 右侧箭头，点击展开子配置 */
@Composable
private fun ExpandableRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowBg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8F0))
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SubText)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = SubText.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2129))
                    .padding(vertical = 4.dp)
            ) {
                content()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/** 纯开关行：标题 + 副标题 + 右侧 Switch */
@Composable
private fun AdvancedSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowBg)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8F0))
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SubText)
            }
        }
        PurpleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/** 点击动作行（添加桌面图标） */
@Composable
private fun AdvancedActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE8E8F0))
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SubText)
            }
        }
        Icon(
            Icons.Default.AddToHomeScreen,
            contentDescription = null,
            tint = AccentPurple,
            modifier = Modifier.size(20.dp)
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/** 子配置中的开关行 */
@Composable
private fun SubSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFC6C9D4),
            modifier = Modifier.weight(1f)
        )
        PurpleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 紫色主题 Switch（贴合参考图） */
@Composable
private fun PurpleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AccentPurple,
            uncheckedThumbColor = Color(0xFF8A8FA3),
            uncheckedTrackColor = Color(0xFF3A3F4B),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

/** 底部操作区：保存 / 测试（圆形边框）+ 取消（宽幅圆角矩形） */
@Composable
private fun BottomActionBar(
    onSave: () -> Unit,
    onTest: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181A20))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RoundedBorderButton(
                text = "保存",
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
            RoundedBorderButton(
                text = "测试",
                onClick = onTest,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LightPinkBg)
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "取消",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = DeepPurpleText
            )
        }
    }
}

/** 圆形边框按钮（粉色文字/边框，贴合参考图） */
@Composable
private fun RoundedBorderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, AccentPink, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = AccentPink
        )
    }
}

/** 创建桌面快捷方式（支持时返回 true） */
private fun addShortcut(context: Context): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val shortcut = ShortcutInfoCompat.Builder(context, "agent_advanced_shortcut")
        .setShortLabel("AI 聊天")
        .setLongLabel("AI 聊天（自定义图标）")
        .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_edit))
        .setIntent(Intent(context, Class.forName("com.wechat.agent.MainActivity")).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    return true
}
