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

## v1.0.1-r28 编译修复（2026-09-30）

| # | 修复项 | 实现方法 |
|---|--------|---------|
| A | 记忆未生效根因（需求 2 补全） | `ChatViewModel` 的 `selectChat`/`sendMessage`/`sendImageMessage`/`openOrCreateChatWithAgent` 均按会话 `agentId` 调用 `memoryManager.setActiveAgent()`，确保回复时读取对应好友记忆库 |
| B | 动态删除弹窗参数缺失（需求 3 补全） | `MomentsScreen` 补充 `onDeletePost` 参数、删除模式状态与确认弹窗，`MomentPostCard` 补充 `showDelete/onDeleteClick` |
| C | 实验室入口参数缺失（需求 4 补全） | `SettingsScreen` 补充 `onOpenLab` 参数与 `Science` 图标 import |
| D | 模型测试链接弹窗缺失（需求 5 补全） | `ModelConfigScreen` 补充 `testing/testResult` 状态与结果弹窗（含“模型配置失败，请检查配置或网络”兜底）；`SettingsViewModel.testModelConnection` 失败返回空串触发失败文案 |

## 构建与发布说明

- 分支：`master`，JDK 21 / Gradle 8.11.1，GitHub Actions 构建 debug APK（artifact：WeChatAgent-APK）。
- 更新检查主源为 GitHub Releases API，备用源为仓库内 `update_info.json`；发布新版本时需同步更新该文件。
