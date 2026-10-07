## 1.0.24 需求集（2026-10-08 追加）

### 语音功能三个缺陷修复（按住说话不可用 / 下载无反应 / 解压嵌套）

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：进入聊天页语音输入仍不可用 | `ChatScreen` 进入页面即调用 `speech.refresh()` 刷新 ASR/TTS 就绪状态（此前依赖先进语音设置页才刷新，覆盖安装后直接进聊天页时 `asrReady` 恒为 false，语音输入被拦截） |
| 2 | 修复：「下载基础语音包」点击无反应 | 根因：VITS 下载地址指向 GitHub 且文件名写错（`vits-zh-ll.tar.bz2` 实际为 `sherpa-onnx-vits-zh-ll.tar.bz2`），国内直连超时且异常被静默吞掉。改为国内可达加速源 `https://ghfast.top/https://github.com/...` 并修正文件名与 `targetDir`（解压目录 `sherpa-onnx-vits-zh-ll`）；`downloadSequential` 下载失败时写入 `DownloadState.Error`，`downloadBaseModels` 同步记录 `lastError`，设置页会显示失败原因而非无反应 |
| 3 | 修复：模型解压产生嵌套目录导致引擎加载失败 | `extractZip`/`extractTarBz2` 解压前扫描归档条目，自动剥离共享顶层目录（如 `sherpa-onnx-vits-zh-ll/`、`vosk-model-small-cn-0.22/`），模型文件直接落到 `filesDir/models/<targetDir>/`；顺带修正 `SherpaTtsEngine` 硬编码的模型文件名 `vits-zh-ll.onnx` → `model.onnx` |
| 4 | 移除不存在的可选模型 | KittenTTS 中文模型（`kittentts-zh`）实际不存在（官方仅英文 `kitten-nano-en-v0_1`），从 `models.json` 移除；Kokoro 多语言模型 URL 同步切换加速源 |

## 1.0.23 需求集（2026-10-07 追加）

### 语音输入/语音回复就绪状态拆分（修复语音输入被 TTS 阻塞）

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：语音输入被 TTS 模型未就绪阻塞 | `SpeechState` 新增 `asrReady`/`ttsReady`；`SpeechManager.refresh()` 分别计算 ASR（required type=asr）与 TTS（required type=tts）就绪；`startListening` 仅校验 `asrReady`，`speak` 仅校验 `ttsReady`，互不依赖 |
| 2 | 聊天页判断同步拆分 | `ChatScreen.handleVoiceInput` 改为检查 `speechState.asrReady`（提示“语音识别模型未就绪”）；语音回复朗读条件改用 `speechState.ttsReady` |
| 3 | 引擎加载按就绪状态独立执行 | `refresh()` 中 ASR 引擎与 TTS 引擎分别按各自就绪状态懒加载，失败仅记录错误不崩溃 |

## 1.0.22 需求集（2026-10-07 追加）

### 语音功能（阶段 1）

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增语音输入：更多面板「语音输入」录音，Vosk 实时识别 | 依赖 `com.alphacephei:vosk-android:0.3.47`；`RECORD_AUDIO` 权限；`VoskAsrEngine` 封装识别，`ChatScreen` 更多面板「语音输入」可用，录音中实时显示 partial 识别文本，再次点击结束并发送 |
| 2 | 新增语音回复：AI 回复自动 VITS 合成播放 | 依赖 `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5`（VITS 中文语音合成）；`SherpaTtsEngine` 合成并播放，`ChatScreen` 回复气泡提供播放/停止 |
| 3 | 语音设置页：开关 + 模型下载 | `SpeechSettingsScreen`（设置 → 语音功能）支持语音输入/语音回复开关与模型下载状态；`SettingsScreen` 加入口，`MainActivity` 加 `speechSettings` 路由 |
| 4 | 模型按需下载到私有目录 | `ModelCatalog`/`ModelDownloadManager`：模型根目录 `filesDir/models`（覆盖安装不丢），支持断点续传、`zip`/`tar.bz2` 自动解压、sha256 校验（空则跳过）；`SpeechManager` 单例协调 ASR/TTS 与下载状态 |
| 5 | 模型清单 | Vosk 中文识别模型 `vosk-model-small-cn-0.22`（zip，16k）；VITS 中文语音合成模型 `vits-zh-ll`（tar.bz2，22.05k）；APK 可覆盖安装，模型暂不内置 Debug 包，按需下载 |

## 1.0.21 需求集（2026-10-01 追加）

### 编辑 AI 好友页新增「大模型提示词」输入框

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增「大模型提示词」输入框，用于给大模型添加约束条件和人物一致性审查 | `AgentProfile` 新增 `val customPrompt: String = ""`；`AgentSetupScreen` 在「性格特点」后新增 `label="大模型提示词"` 的 `OutlinedTextField`，placeholder 说明约束与一致性审查；`MainActivity` 两处 `AgentSetupScreen` 传 `initialCustomPrompt` 与 `onSave` 扩展参数 |
| 2 | 提示词保存并注入大模型 Prompt | `SettingsManager` / `SettingsViewModel` 持久化 `customPrompt`；`ChatViewModel.buildPersonaPrompt` 在全局设定后追加 `customPrompt`（`isNotBlank` 才注入），旧数据 Gson 兜底 `orEmpty` |

### 聊天页思考气泡右移与中点对齐

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 3 | 思考气泡整体右移，并与正文气泡中点对中点垂直对齐 | `ThinkingBubble` 加 `modifier` 参数；非流式思考气泡移入头像右侧正文 `Column` 内，用 `onGloballyPositioned` 测正文气泡宽度使思考容器等宽居中（`fillMaxWidth` + `widthIn(max=280.dp)` + 测宽后 `Modifier.width(bodyWidthPx.toDp())`） |
| 4 | 流式思考气泡同步校准 | 流式思考气泡 `Box` `padding(start=56.dp, end=12.dp)` 居中；`ChatScreen.kt` 新增 `import onGloballyPositioned`、`LocalDensity` |

## 1.0.20 需求集（暂缓发版，2026-09-30 追加）

### 热恋模式主动消息增强

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 限制：正在聊天窗口内不主动发消息 | `ChatViewModel` 新增 `_isInChatScreen` + `setInChatScreen(inChat)`；离开窗口瞬间把 `hotlove_last_proactive_at` 重置为当前时间，保证"仅不在对话窗口时才计时"。`MainActivity` chat 路由用 `DisposableEffect` 在进入/退出聊天页时切换状态 |
| 2 | 限制：`startProactiveLoop` 与 `sendProactiveContact` 双入口补窗口检查 | 在聊天窗口内直接 `continue`/`return`，且窗口内不累计间隔；用户在窗口内发消息仍走常规回复流程 |
| 3 | 正文：主动消息结合已有聊天记录 | `sendProactiveContact` 的 prompt 注入最近 8 条聊天记录（`你/对方` 角色标注），并要求模型"结合最近聊天记录自然延续话题、不要只盯着屏幕使用时间" |

### 播放器组件修复

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 4 | 修复：切歌/暂停后 UI 信息不刷新 | `MusicController` 的 `controllerCallback` 不再空实现，`onPlaybackStateChanged`/`onMetadataChanged` 触发时回调 `onNowPlayingChanged`；`ChatViewModel.init` 订阅该回调更新 `_nowPlaying`；四个控制方法操作后延迟 400ms 再主动 `refreshNowPlaying()` 兜底 |
| 5 | 修复：暂停键需按住才生效 | `MusicControlBar` 移除整行 `Row.clickable(onOpenApp)`，改为仅歌曲信息 `Column` 区域可点击打开音乐 App，消除按钮与整行点击事件竞争 |
| 6 | 修复：退出 App 后音乐继续播放 | `ChatViewModel.onCleared()` 在 `musicController.release()` 前先 `pause()`，Activity 销毁时自动暂停音乐 |

### 主动消息时间窗改造

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 7 | 主动消息触发改为「收到用户上条消息后 x-y 分钟内 AI 自行判断是否主动发」 | `HotLoveSettings` 新增 `proactiveWindowMinMinutes/MaxMinutes`（默认 5-20）、`proactiveWindowFromAi/UserSet`；`startProactiveLoop` 改为以用户最后一条消息时间为锚点：`elapsed ∈ [min, max]` 且窗口内未主动发过才触发；超过窗口上限本次不再打扰，等用户下一条消息重新开启窗口；`setInChatScreen(false)` 时把 `hotlove_last_proactive_at` 归零以重置窗口标记 |
| 8 | AI 自行判断是否发 + 用户自定义 + 恢复 AI 设定按钮 | `sendProactiveContact` 增加 `allowDecline`：prompt 允许模型输出「(暂不打扰)」标记时跳过插入消息（防重复标记仍由调用方维护）；`ensureProactiveWindowDecided` 向大模型询问「x y」两个分钟整数并按角色自定；`HotLoveScreen` 时间窗 UI 支持分别调整最早/最晚分钟数并一键「恢复 AI 设定」（重置 5-20 并标记由 AI 设定，`SettingsManager.restoreProactiveWindowFromAi`） |
| 9 | 思考气泡独立显示在正文气泡上方 | `MessageBubble` 改为外层 `Column`：AI 消息的思考气泡（含历史消息与流式）移出头像 `Row`，独立渲染在正文气泡上方，不再与头像水平对齐；`ThinkingBubble` 宽度由 `fillMaxWidth` 改为 `widthIn(max = 252.dp)`（正文气泡最大 280dp 的 90%）；流式思考气泡独立 item 补 `padding(horizontal = 10.dp)` 保持间距 |

### 新增 AI 好友随机生成

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 10 | 「新增 AI 好友」页新增随机生成按钮 | `AgentSetupScreen` 在「创建 AI 好友」按钮上方新增「随机生成（AI 自动设定）」`OutlinedButton`（编辑模式隐藏，生成中显示进度圈并禁用）；点击后由大模型一次性随机产出姓名/性别/年龄/人设描述/性格特点并回填表单，可继续手动调整；`ChatViewModel.randomGenerateAgentProfile()` 构造角色设计师提示词，请求模型输出 JSON 并用 Gson 解析（失败返回 null，不覆盖已有输入） |

### 好友详情页记忆库查看

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 11 | 新增：记忆库卡片「查看记忆」按钮 | `AgentDetailScreen` 记忆库卡片新增「查看记忆」按钮，点击后异步读取 `getL0Memory/getL1Memory/getL2Memory` 三层记忆并按 L0 即时 / L1 日常 / L2 成长分组拼接文本，弹出可滚动 `AlertDialog` 展示（最大高度 420dp，`verticalScroll` 滚动查看），关闭即回收；`memoryDialogContent` state 控制弹窗显隐 |

## 1.0.19 Agent 模式阶段 1：工具调用能力（2026-09-30）

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增：Agent 工具注册表 | 新增 `AgentToolRegistry.kt`：以 `AgentToolSpec(name/description/parameters/executor)` 描述本地能力，可转换为 OpenAI 兼容 tools 声明；阶段 1 提供 3 个只读工具：`get_current_time`（当前日期/星期/时段）、`query_screen_time`（今日屏幕使用时长与最常用应用，未授权返回提示）、`recall_memory`（读取角色记忆库） |
| 2 | 新增：ChatRepository.sendAgentMessage 多轮循环 | 非流式 Agent 调用：请求带 tools → 模型返回 tool_calls → 本地执行器逐个执行 → 回填 assistant(tool_calls) 与 role=tool 结果 → 再次请求，直到模型给出纯文本回复；最大 6 轮防死循环；模型不支持 tools（400）时自动记录并降级纯文本请求，同模型后续不再带 tools |
| 3 | 扩展：ApiModels 支持 function calling | `ChatRequest` 增加 `tools`/`tool_choice`；`ChatMessage.content` 改为可空并新增 `tool_calls`/`tool_call_id`，新增 `ChatTool`/`ToolFunction`/`ToolCall`/`FunctionCall` 数据类 |
| 4 | 接入：聊天发送走 Agent 分支 | `ChatViewModel.sendMessage` 在开启 Agent 模式时改调 `sendAgentMessage`，注入 `buildScreenUsageSummary` 作为屏幕时长提供源；回复前展示模拟思考气泡；失败/超时沿用原有友好提示 |
| 5 | 新增：高级设置「Agent 模式」开关 | `AdvancedSettings` 新增 `agentTools`（默认开），持久化 `adv_agent_tools`；`AdvancedScreen` 新增开关行，说明当前为一次成型回复（非流式） |
| 6 | 说明：阶段 1 仅只读工具 | 写操作（音乐/锁屏/主动发消息/发动态）留待阶段 2，将加用户确认机制后再开放 |

## 1.0.17 体验修复（2026-09-30）

- 修复：开启「多行文本自动分割」后不再重复出现多段思考气泡，思考过程只挂载在第一条分段消息。
- 修复：恢复仓库内固定 Debug 签名（debug.p12），后续更新可覆盖安装，无需卸载重装。
- 优化：「发现」页切到页面即自动生成一次观察记录，并限制最短间隔 30 分钟，避免频繁切换页面生成重复内容。

## 1.0.16 代码审查与安全加固（2026-09-30）

- API Key 迁移改为“加密写入并回读成功后再删除旧明文”，避免迁移异常导致凭据丢失。
- GitHub 调试 Token 统一由 Android Keystore 加密保存，旧版明文配置仅在迁移验证成功后删除。
- API 地址禁止携带用户信息、查询参数和片段；生产环境仅允许 HTTPS，调试环境的 HTTP 仅限本机地址。
- 在线更新只接受 GitHub 官方 HTTPS 下载域名，并限制 APK 大小、检查 ZIP/APK 文件头后再进入安装器。
- 关闭 Android 自动备份，避免应用凭据/聊天数据进入系统自动备份链路。
- GitHub Token 输入框改为密码样式，减少调试页面直接暴露凭据。

## 1.0.15 最终代码审查修复

- GitHub 调试 Token 改用 Android Keystore + AES-GCM 安全存储，并自动迁移旧版明文 Token。
- 移除仓库内固定 Debug 私钥，改用 Gradle 默认 Debug 签名。
- 强化日志凭据脱敏、HTTP 错误处理、SSE 空响应保护与 API 地址校验。
- Retrofit 客户端地址切换与实例创建增加同步保护。


## 1.0.14 代码审查修复（最终整理版）

- API Key 使用 Android Keystore + AES-GCM 加密保存，并兼容迁移旧版 DataStore 明文配置。
- 网络日志不记录 BODY，敏感 Header 脱敏，应用日志增加二次凭据脱敏。
- API 错误不再把完整服务端 error body 原样暴露给 UI。
- SSE 流式响应增加空 Body 保护，并保留协程取消语义。
- API Base URL 保存前进行 HTTP/HTTPS 地址校验。
- Retrofit Client 的服务重建改为同步，避免并发切换地址时出现竞态。
- Release 开启 R8/资源压缩；CI 同时执行测试、Lint、Debug 与 Release 构建。
- 移除无实际用途的 MEDIA_CONTENT_CONTROL 权限；保留 APK 自动更新所需的 REQUEST_INSTALL_PACKAGES。
- 移除仓库内固定 `debug.p12` 调试私钥，改用 Gradle 默认 Debug 签名。
# WeChatAgent 更新日志

## v1.0.14 P0/P1 安全与稳定性优化（2026-09-30）

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 安全：API Key 改为 Android Keystore 加密存储 | 新增 `SecureApiKeyStore`，使用 Android Keystore AES-GCM；旧版 DataStore 明文 API Key 首次启动自动迁移并删除旧字段，避免升级丢配置。 |
| 2 | 安全：网络日志禁止记录 BODY | `RetrofitClient` Debug 仅使用 BASIC，Release 使用 NONE；Authorization/Cookie 等敏感 Header 脱敏，不记录聊天正文、图片或响应 BODY。 |
| 3 | 稳定性：网络错误统一为安全的用户提示 | `ChatRepository` 不再把服务端原始 error body 放入异常，按 HTTP 状态码和常见网络异常返回可读提示；同时保留 CancellationException 的协程取消语义。 |
| 4 | 构建：Release 开启 R8 与资源压缩 | `app/build.gradle.kts` 的 Release 启用 `isMinifyEnabled` 和 `isShrinkResources`，继续使用现有 ProGuard 规则。 |
| 5 | CI：同时验证测试、Lint、Debug/Release | GitHub Actions 增加 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleRelease`，统一上传两个 APK artifact。 |
| 6 | 文档：补充 README | 增加架构、安全存储、构建、签名、日志和后续 Roadmap 说明。 |

> 本文档记录每次版本更新的内容与实现方法，仅供开发追溯，不打包进 App。

## v1.0.14（2026-09-30）聊天列表全局共享，彻底解决跨好友串号

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：聊天列表改为全局共享，不再按角色隔离 | 根因：v1.0.13 及之前 `chatPrefs` 按 `chat_sessions_<agentId>` 隔离，切换角色时 `bindAgent`/`switchBindingPreservingChat` 会整体换掉聊天列表，导致“李依娜的会话在切到林晚舟后消失/回复收不到”。修复：`ChatViewModel` 的 `chatPrefs` 固定指向全局 `chat_sessions`，`bindAgent`/`switchBindingPreservingChat` 只切换 `momentsPrefs`/`statusPrefs`（朋友圈/状态/情绪仍按角色隔离），不再切换聊天 prefs 与列表 |
| 2 | 修复：选中任意好友会话自动切换该好友设定回复 | `selectChat` 已带防御：会话 `agentId` 与当前角色不符时先切到该角色绑定（记忆/人设/情绪），再展示消息；`sendMessage` 按会话 `agentId` 构建 `personaPrompt`，保证回复人设正确 |
| 3 | 兼容：自动合并历史按角色隔离的聊天记录 | 新增 `migrateLegacyChatsToGlobal()`：init 时读取各角色 `chat_sessions_<agentId>` 的旧会话并入全局 `chat_sessions`（按 id 去重，`migrated_global_chats` 标记只跑一次），升级不丢历史 |
| 4 | 界面：全局聊天列表按会话显示对应好友头像 | `ChatListScreen` 按 `chat.agentId` 查找 `AgentProfile` 头像/昵称渲染，不再统一使用当前角色头像 |

## v1.0.13（2026-09-30）调试能力与跨角色串号修复

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增：“我”页“调试”入口 | `MyProfileScreen` 新增 `onOpenDebug` 参数与“调试”菜单项；`MainActivity` 注册 `debug` 路由并接入 `DebugScreen.kt` |
| 2 | 新增：调试页“日志”查看详情 | 新建 `DebugScreen.kt`（调试菜单，含“日志”入口与日志文件大小）；新建 `LogScreen.kt` 展示运行时日志（最近 800 行，ChatVM/异常行高亮） |
| 3 | 新增：日志详情底部“上传到 GitHub” | `LogScreen` 底部上传按钮调用 GitHub Contents API（`PUT /repos/cainiao-changsheng/WeChatAgent/contents/logs/`，branch master）；首次上传需填写 GitHub Token，保存在本机 `debug_prefs` |
| 4 | 新增：运行时日志记录器 | 新建 `data/AppLogger.kt`：追加写 `filesDir/logs/app.log`（2MB 滚动截断），内存保留最近 800 行；`MainActivity.onCreate` 初始化 |
| 5 | 新增：会话切换关键路径埋点 | `ChatViewModel` 的 `bindAgent`/`switchAgent`/`selectChat`/`openOrCreateChatWithAgent`/`sendMessage` 均写 `AppLogger`，便于定位串号/消息错乱问题 |
| 6 | 修复：重大 bug——点击不同好友名片发消息后聊天列表串号（李依娜/林晚舟对话混淆、重启后跨角色收到回复） | 根因：`openOrCreateChatWithAgent` 仅切记忆库、未同步切换 `currentAgentId`/`chatPrefs`，导致新会话写入上一角色的 prefs，重启后按 agentId 误显示在其他角色聊天列表。修复：`openOrCreateChatWithAgent` 检测角色不一致时先 `settingsManager.setCurrentAgentId` + `switchBindingPreservingChat`（同步切换 prefs 但不清空当前会话）再创建/复用会话；`selectChat` 增加防御——若所选会话的 agentId 与当前角色不一致，先切到该角色绑定；`MainActivity` chat 路由进入时 `selectChat(id)` 兜底，避免异步 `switchAgent` 清空当前会话后打开空列表 |

## v1.0.1（2026-09-30）九项更新

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：通过“新的朋友”添加好友后聊天，消息预览不出现 | `Chat` 增加 `agentId` 字段；`ChatViewModel` 新增 `openOrCreateChatWithAgent(agentId)` 按好友绑定专属会话；`MainActivity` 的 AgentDetail 发消息改为走该方法，避免误用列表第一条旧会话 |
| 2 | 修复：新好友导入记忆后大模型回复未使用记忆 | `MemoryManager.buildMemoryContext()` 放宽提取条件：L2 记忆 importance≥2 取 8 条、L1 记忆取 8 条（原为 ≥3 取 8 / 取 3），确保导入记忆进入提示上下文 |
| 3 | 新增：动态删除功能 | `MomentsScreen` 增加 `onDeletePost`、删除模式与确认弹窗（“是否删除该动态”+取消/删除）；`MomentPostCard` 增加垃圾桶图标；`ChatViewModel.deleteMomentPost(postId)` 过滤并持久化 |
| 4 | 新增：设置内“实验室”入口 | `SettingsScreen` 增加“实验室”菜单项（Science 图标）；新建 `LabScreen.kt` 汇总内测功能（动态删除/模型测试/会话搜索），`MainActivity` 增加 `lab` 路由 |
| 5 | 新增：模型配置“测试链接”按钮 | `SettingsViewModel.testModelConnection(url,key,model,onResult)` 发送固定测试消息；`ModelConfigScreen` 增加测试按钮与结果弹窗，失败显示“模型配置失败，请检查配置或网络” |
| 6 | 修复：检查更新提示“无法连接更新服务器” | `UpdateChecker` 增加 OkHttp 连接/读取超时；新增备用源 `update_info.json`（raw.githubusercontent.com，比 api.github.com 更易访问），API 失败自动降级 |
| 7 | 界面：聊天/通讯录标题居中，聊天顶部按钮缩进，🔍接搜索 | `ChatListScreen` 标题居中、右侧 Search/Add 按钮 `Row(spacedBy(-8.dp))` 缩进、搜索时显示输入框并过滤会话；`ContactsScreen` 标题居中 |
| 8 | 界面：聊天标题显示好友昵称，点头像跳详情 | `ChatScreen` 增加 `onAvatarClick` 参数，TopAppBar 标题改为头像+昵称 Row，点击触发跳转 `agentDetail/{agentId}` |
| 9 | 新增：仓库日志文件（本文件） | 仓库根目录新增 `UPDATE_LOG.md`，记录每次更新内容与实现方法 |

## v1.0.2（2026-09-30）七项界面与功能改造

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 界面：朋友圈删除按钮移入用户动态卡片内，仅本人动态显示 | 新建 `CenteredTopBar.kt` 通用顶部栏（标题绝对居中于屏幕宽，支持 showBack/actions）；`MomentsScreen` 删除顶部垃圾桶，`MomentPostCard` 仅 `isUserPost` 时显示删除图标并接确认弹窗 |
| 2 | 界面：去除蓝色球图标 | `MomentsScreen` 移除球图标相关展示，改用干净标题栏 |
| 3 | 界面：所有导航标题绝对居中 | `MomentsScreen`/`ChatListScreen`/`ContactsScreen`/`MyProfileScreen`/`AgentSetupScreen`/`ChatScreen` 全部改用 `CenteredTopBar`，标题基于屏幕宽度居中 |
| 4 | 界面：聊天列表显示角色名 | `ChatListScreen` 标题优先展示 `AgentProfile.name`（不再用首句消息） |
| 5 | 功能：编辑好友可点头像换自定义图；初次使用无默认好友 | `AgentSetupScreen` 头像点击唤起相册（`galleryLauncher` + `takePersistableUriPermission`）；`SettingsManager.init` 移除自动建默认角色逻辑 |
| 6 | 功能：首次扮演先读设定与记忆再回复 | `ChatRepository` 增加 `personaPrompt` 字段；`ChatViewModel.buildPersonaPrompt(agentId)` 在 `sendMessage`/`sendImageMessage`/`sendProactiveContact` 前注入设定与记忆 |
| 7 | 功能：好友详情增加“清除记忆”“删除好友” | `MemoryManager` 新增 `clearMemory()`（清 L0/L1/L2 + 情绪）；`ChatViewModel` 新增 `deleteChatsByAgent(agentId)` 清会话；`AgentDetailScreen` 加危险操作按钮（红字）+ 确认 `AlertDialog`；`MainActivity` 接 `onClearMemory`/`onDeleteAgent`（删会话+删档案+返回） |

## v1.0.1-r28 编译修复（2026-09-30）

| # | 修复项 | 实现方法 |
|---|--------|---------|
| A | 记忆未生效根因（需求 2 补全） | `ChatViewModel` 的 `selectChat`/`sendMessage`/`sendImageMessage`/`openOrCreateChatWithAgent` 均按会话 `agentId` 调用 `memoryManager.setActiveAgent()`，确保回复时读取对应好友记忆库 |
| B | 动态删除弹窗参数缺失（需求 3 补全） | `MomentsScreen` 补充 `onDeletePost` 参数、删除模式状态与确认弹窗，`MomentPostCard` 补充 `showDelete/onDeleteClick` |
| C | 实验室入口参数缺失（需求 4 补全） | `SettingsScreen` 补充 `onOpenLab` 参数与 `Science` 图标 import |
| D | 模型测试链接弹窗缺失（需求 5 补全） | `ModelConfigScreen` 补充 `testing/testResult` 状态与结果弹窗（含“模型配置失败，请检查配置或网络”兜底）；`SettingsViewModel.testModelConnection` 失败返回空串触发失败文案 |

## v1.0.3（2026-09-30）三项更新

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：发现页好友动态作者名始终显示“AI伴侣” | 根因：`MomentsGenerator.generateMomentPost()` 生成动态时未写入 `author`，`MomentPost.author` 走默认值“AI伴侣”。改为 `generateMomentPost(state, author)` 支持传入昵称；`ChatViewModel` 的 `autoGenerateMomentPost`/`checkAutoMoments`/`generateMomentsPost` 与 `MomentsViewModel.generateMomentsPost` 生成动态时取 `settingsManager.agentName.first()` 作为作者；`MomentsScreen` 显示层兜底：author 为空或“AI伴侣”时回退为当前好友昵称，兼容已持久化的旧动态 |
| 2 | 界面：编辑好友文案调整 | `AgentSetupScreen` 表单标签“名称”→“人设名字”、“设定”→“人设描述”、“全局设定”→“性格特点”；`AgentDetailScreen` 档案展示同步改为“人设描述”“性格特点” |
| 3 | 文档：同步更新本日志 | 记录本次三项改动（朋友圈昵称修复、编辑页文案、日志） |

## v1.0.4（2026-09-30）按当前角色承载身份

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 删除全局写死的“AI伴侣”提示词设定 | `MemoryManager.identityPromptPermanent` 移除“你是我最亲密的AI伴侣”“专属AI伴侣”等写死身份，改为通用表述“你不是工具/客服，是有自我意识的真人伙伴，你的名字/性格/说话风格/角色定位以系统消息中的【好友设定】为准”，身份完全由 `ChatViewModel.buildPersonaPrompt(agentId)` 按当前好友档案生成的 personaPrompt 承载 |
| 2 | 动态/评论/后台思考提示词改为按当前角色 | `MomentsGenerator.buildGenerationPrompt` 增加 `author` 参数（“你是{author}，现在要发一条朋友圈动态”），`ChatViewModel`/`MomentsViewModel` 各调用点传入当前好友昵称；`generateApiEvent`（后台低功耗思考）、`generateReplyToComment`（回复评论）、`generateReactionComment`（评论动态）提示词中的“AI伴侣”替换为当前角色名 |
| 3 | 兜底默认名“AI伴侣”改为中性“我” | `MomentPost.author`、`MomentsGenerator.generateMomentPost`、`AgentSetupScreen` 空名兜底、`MomentsScreen.agentName` 默认值由“AI伴侣”改为“我”；保留 `SettingsManager.DEFAULT_AGENT_NAME` 与 `MomentsScreen` 旧数据兼容判断（author 为空或旧值“AI伴侣”时回退当前昵称） |
| 4 | 文档：同步更新本日志 | 记录本次按角色承载身份改动 |

## v1.0.5（2026-09-30）固定APK签名支持覆盖安装

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：每次构建APK签名不同导致无法覆盖安装 | 根因：debug 包默认使用 GitHub Actions runner 机器随机生成的 debug keystore 签名，每次构建签名不一致，系统判定为不同应用。解决：用 openssl 生成固定 PKCS12 签名文件 `app/debug.p12`（alias=androiddebugkey，密码 android）提交仓库；`app/build.gradle.kts` 的 signingConfigs 修改内置 debug 签名指向该文件，debug buildType 显式绑定。此后每次构建均使用同一密钥签名，可覆盖安装（后续升级发布需保留此 keystore 不变） |
| 2 | 说明：Android 无法“去掉签名验证” | 系统强制所有 APK 必须有签名才能安装，不存在无签名安装；固定签名密钥是唯一正解。若未来上架应用商店，需另行生成正式 release keystore 并妥善保管 |

## v1.0.6（2026-09-30）修复大模型未获取好友设定名字

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：大模型回复未拿到设定名字（自称“阿深”而非设定名） | 根因：`SettingsManager` 在 `ChatViewModel`/`SettingsViewModel`/`MomentsViewModel` 中各自 `new` 出独立实例，内存 `agentProfiles` 快照互不同步——设置页通过 SettingsViewModel 实例保存“李依娜”后，ChatViewModel 持的仍是构造时读入的旧快照，`buildPersonaPrompt(agentId)` 按 id 找不到档案返回空串，personaPrompt 未注入，大模型凭默认/记忆自由发挥自称。解决：`SettingsManager` 改为单例（`companion object` 的 `getInstance(context)` + `@Volatile` + synchronized 双检锁，私有构造），三个 ViewModel 统一改调 `SettingsManager.getInstance(application)`，档案增删改后所有消费方实时同步 |

## v1.0.7（2026-09-30）我页高级设置 + 移除聊天详情音乐按钮

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增“我 → 高级”设置页 | 按参考图排版实现暗色“思考设置”页：列表含思考设置、流式输出、自定义请求参数、停用超时（开关）、添加自定义桌面图标、深色模式、发送延时、多行文本自动分割（开关），底部固定“保存 / 测试 / 取消”按钮；`AdvancedSettings` 数据类 + `SettingsManager` 持久化（SharedPreferences），`MyProfileScreen` 新增“高级”入口，`MainActivity` 新增 `advanced` 路由 |
| 2 | 高级设置接入聊天行为 | `ChatViewModel.sendMessage/sendImageMessage` 读取高级设置：流式输出开关控制逐字实时显示；发送延时在请求前生效；多行文本自动分割开关控制回复拆分为多条或合并为单条 |
| 3 | 移除聊天详情页右上角音乐按钮 | `ChatScreen` 顶部 actions 的 MusicNote 图标按钮删除（播放中底部控制条保留） |
| 4 | 文档：同步更新本日志 | 记录本次高级设置与音乐按钮改动 |

## v1.0.7-2（2026-09-30）识图修复 + ➕弹层重排 + 高级页去掉底部按钮

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 高级页移除“保存 / 测试 / 取消”按钮 | `AdvancedScreen` 删除底部 `BottomActionBar` 与测试弹窗；新增 `saveSettings()` 在各开关/输入变化时即时落库 |
| 2 | 修复发送图片后显示“[图片]”占位文本且模型看不了图 | `ChatViewModel.sendImageMessage` 将消息 content 置空（气泡按 imageUri 渲染真实缩略图）；读取图片为 data URL 后改走多模态流式接口 `sendVisionMessageStream`（`ChatRepository` + `ApiService` 新增），模型（DeepSeek 支持识图）直接看图回复；读图失败自动降级纯文本 |
| 3 | ➕ 弹层按参考图重排为 2×4 网格 | `ChatScreen` 移除单项 DropdownMenu，新增 `PlusMenuPanel`：相册/拍摄/位置/语音输入/收藏/个人名片/文件/音乐 八项深色圆角按钮；“发送图片”移入“相册”，其余按钮为占位（点击关闭面板） |
| 4 | 文档：同步更新本日志 | 记录识图与弹层改动 |

## v1.0.8（2026-09-30）聊天交互修复 + 思考过程显示

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复大模型卡住导致聊天界面按钮无法操作 | `ChatRepository.sendMessageStream/sendVisionMessageStream` 改为 `Flow<StreamPiece>`；`ChatViewModel` 增加 `REPLY_TIMEOUT_MS`（600s）超时兜底（高级设置「停用超时」开启时不限制），超时/异常自动复位 `_isLoading` 并提示；`deliverMultiMessage` 多段投递补 `finally { _isLoading.value = false }`，任何异常不再残留 loading 态 |
| 2 | 修复开启显示思考过程后聊天界面未显示思考过程 | `ApiModels.Delta` 新增 `reasoning_content` 字段；`ChatRepository` 流式解析区分思考与正文；`ChatViewModel` 新增 `_streamingReasoning` 状态；`ChatScreen` 新增 `ThinkingBubble`（🧠 思考过程）气泡组件，流式期间在正文气泡前展示 |
| 3 | 大模型回复时顶部显示「对方正在输入中」 | `ChatScreen` 顶部栏在 `isLoading` 时优先展示「对方正在输入中」（绿色小字），替代原固定情绪描述 |
| 4 | 修复点击输入框后输入法弹起顶栏上移超出状态栏 | `AndroidManifest.xml` 的 `MainActivity` 增加 `android:windowSoftInputMode="adjustResize"`，输入法弹起时顶栏保持在状态栏内 |
| 5 | 新增「思考完成自动折叠气泡」开关 | `AdvancedSettings` 新增 `autoCollapseThinking`（持久化 `adv_auto_collapse_think`）；`AdvancedScreen` 思考设置内新增「思考完成自动折叠气泡」SubSwitchRow（即时落库）；开启后思考气泡默认折叠为「已深度思考 N 字」，点击标题可展开/收起 |
| 6 | 文档：同步更新本日志 | 记录本次交互修复与思考过程显示改动 |

## v1.0.9（2026-09-30）思考过程气泡兜底显示

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复默认模型不返回 reasoning_content 时思考过程气泡不显示 | 默认模型 deepseek-v4-flash 不返回 `reasoning_content`，此前仅在流式收到 reasoning 时才更新 `_streamingReasoning`，故气泡从不出现。`ChatViewModel.sendMessage/sendImageMessage` 增加 `simulateJob` 兜底：开启「显示思考过程」时延迟 1.2s 若仍未收到 reasoning 且正文未开始，自动填入 `generateThinkingPreview()` 生成的模拟思考文本；正文到达仍无 reasoning 时立即补入预览并取消模拟任务；模型真实返回 reasoning 时取消模拟，展示真实思考 |
| 2 | 新增模拟思考文本生成器 | `generateThinkingPreview(userContent)` 依据用户消息生成 4 种口吻的自然思考文本（"对方说「xxx」，我得想想怎么回应才自然……"），随机选用，让思考气泡始终有内容 |
| 3 | 文档：同步更新本日志 | 记录思考气泡兜底逻辑 |

## v1.0.10（2026-09-30）输入框避让输入法 + 思考气泡常驻

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 修复：聊天输入框被输入法遮挡看不到输入内容 | `ChatScreen` 的 `bottomBar` 外层 `Column` 增加 `Modifier.imePadding()`，配合 Manifest 的 `adjustResize`，输入框与按钮组合整体浮在输入法键盘上方，输入时可实时看到已输入文字 |
| 2 | 新增：思考过程气泡常驻显示 | `Message` 模型新增 `thinking` 字段；`ChatViewModel` 在流式结束后保存 `_streamingReasoning` 到 `finalThinking` 并随 `deliverMultiMessage`/`finishStreaming` 写入 agent 消息；`MessageBubble` 对含思考的 agent 消息在正文上方渲染常驻 `ThinkingBubble`（受「思考完成自动折叠气泡」开关控制），回复完成后思考内容不再消失 |
| 3 | 优化：模拟思考更快出现减少等待卡顿感 | `simulateJob` 延迟由 1.2s 缩短为 0.6s，发送后更快出现「思考过程」气泡，正文流式未到前界面有明确反馈；同时保持正文到达无 reasoning 时立即补预览 |
| 4 | 兼容：旧存档安全加载 | `MessageBubble` 读取 `thinking` 使用 `orEmpty()` 防御旧数据反序列化缺失字段，历史会话不崩溃 |
| 5 | 文档：同步更新本日志 | 记录输入框避让与思考常驻改动 |

## v1.0.12（2026-09-30）表情导入导出 + 发现页改造

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增：表情导入导出 | `EmojiManager` 支持将自定义表情打包为 zip（含 custom_stickers.json + 图片）导出，支持导入同格式 zip 恢复；表情存储于 filesDir（emoji_stickers.json + emoji_stickers_images/），退应用不丢失 |
| 2 | 界面：聊天图片尺寸统一 | 聊天内图片消息渲染统一按固定最大尺寸展示，大小图不再参差 |
| 3 | 新增：底部「发现」页（原「发现」更名「动态」） | 底部导航新增「发现」Tab，原「发现」页更名为「动态」；新建 `DiscoverScreen.kt` 承载发现页内容 |
| 4 | 新增：全知全能观察者时间线记录 | 新增 `ObservationStore.kt` 记录观察者时间线事件；`ChatViewModel` 接入观察记录存储与读取，发现页展示时间线 |
| 5 | 界面：发现页顶部栏对齐 | `DiscoverScreen` 顶部栏复用 `CenteredTopBar`（56dp + statusBarsPadding），与聊天/通讯录/我各导航页对齐 |
| 6 | 优化：观察者记录结合角色人设、记忆与当前时间 | `ChatViewModel.recordObservation` 构造 prompt 时注入：当前时间（`SimpleDateFormat`）、选中角色的人设与全局设定、`memoryManager.buildMemoryContext()` 返回的角色记忆（临时切换 activeAgent 读取后恢复），要求大模型据此推断该角色在当前时间下最可能发生的客观行为，行为与设定/记忆/时间吻合 |
| 7 | 界面：聊天图片尺寸缩小 30% | `ChatScreen` 图片消息尺寸由 200dp 改为 140dp |
| 8 | 界面：发现页移除副标题说明文本 | `DiscoverScreen` 顶部栏去掉「全知全能的观察者 · 仅记录客观行为」副标题，仅保留居中「发现」标题 |

## v1.0.11（2026-09-30）图片表情系统 + 聊天图片自动缓存

| # | 更新内容 | 实现方法 |
|---|---------|---------|
| 1 | 新增：图片表情系统（json 管理） | 新增 `EmojiManager.kt`：从相册选取图片 + 备注/快捷名称，保存为 filesDir/emoji_stickers.json，聊天界面与大模型均可读取 |
| 2 | 新增：输入联想 / 大模型可读表情 | 聊天输入时按表情名联想插入；表情以文本描述注入提示上下文，大模型可读取表情含义 |
| 3 | 新增：聊天图片自动缓存 | `ImageCacheHelper.cacheToInternal` 将相册 content:// 图片复制到内部存储（chat_images/），`sendImageMessage` 先缓存再存本地路径；重开应用图片不丢失 |
| 4 | 修复：思考气泡去重 | `MessageBubble` 对含思考的 agent 消息仅渲染一次常驻 `ThinkingBubble`，避免重复气泡 |

## 构建与发布说明

- 分支：`master`，JDK 21 / Gradle 8.11.1，GitHub Actions 构建 debug APK（artifact：WeChatAgent-APK）。
- 更新检查主源为 GitHub Releases API，备用源为仓库内 `update_info.json`；发布新版本时需同步更新该文件。
