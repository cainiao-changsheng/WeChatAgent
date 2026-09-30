# WeChatAgent 更新日志

> 本文档记录每次版本更新的内容与实现方法，仅供开发追溯，不打包进 App。

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

## 构建与发布说明

- 分支：`master`，JDK 21 / Gradle 8.11.1，GitHub Actions 构建 debug APK（artifact：WeChatAgent-APK）。
- 更新检查主源为 GitHub Releases API，备用源为仓库内 `update_info.json`；发布新版本时需同步更新该文件。
