# WeChatAgent

微信风格的 AI 角色陪伴聊天 Android 应用。当前源码版本：**1.0.24**。

## 功能特性

- **四个底部标签页**：聊天、通讯录、动态、我（「发现」功能已暂时停用）。
- **多 AI 角色**：每个角色拥有独立的形象（头像/昵称/性别/年龄）、人设、全局设定与自定义提示词；支持新建、编辑、删除与随机生成，切换角色时聊天、记忆库、朋友圈等数据一并切换。
- **AI 聊天**：SSE 流式输出、深度思考（reasoning）展示、图片消息、输入状态提示，支持多发打断。
- **本地语音**：语音输入（Vosk 离线中文识别，按住/点按说话）与 AI 回复自动朗读（sherpa-onnx 离线中文 TTS）。
- **动态（朋友圈）**：AI 生成动态，支持点赞、评论；用户可发布动态（可选图片），点赞/评论/删除均即时持久化。
- **热恋模式（实验）**：锁屏控制、音乐播放器控制、AI 后台主动发消息（可配置时间窗与间隔）。
- **权限（热恋模式内，安全/隐私知情后开启）**：屏幕使用时间（使用情况访问）与通知使用权，用于读取屏幕使用时长、控制音乐播放；主动发消息时间窗可由大模型按角色自定，用户可随时手动修改或关闭。
- **实验室**：处于测试阶段的新功能入口，含模型配置、热恋模式等。
- **其他**：模型配置、更新检查、变更日志、日志查看、调试页、记忆库自动备份。

## 技术架构

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose：单 Activity 多路由导航
- ViewModel：`ChatViewModel`、`SettingsViewModel`、`MomentsViewModel`
- Data 层：多角色档案、记忆、情绪、朋友圈、观察时间线、生活模拟/决策引擎等本地数据
- Retrofit + OkHttp + Gson：AI API 请求与 SSE 流式输出
- DataStore + SharedPreferences：普通设置与用户数据
- Android Keystore：API Key、GitHub Token 的 AES-GCM 加密存储
- Coil：图片加载与缓存
- Vosk Android + sherpa-onnx：本地语音识别与合成
- Apache Commons Compress：模型压缩包（zip / tar.bz2）解压

## 语音功能

- 语音输入使用 Vosk 中文小模型（约 42MB），语音回复使用 sherpa-onnx VITS 中文 TTS（约 127MB），可选 Kokoro 多语言模型。
- 模型在「设置 → 语音功能」中「下载基础语音包」，统一存储到应用私有目录 `filesDir/models`，支持断点续传、SHA256 校验、zip / tar.bz2 解压；覆盖安装 APK 不会丢失，无需重复下载。
- 语音输入需授予 `RECORD_AUDIO` 录音权限，并在「高级设置」中开启「语音输入」开关。

## API 配置与安全

API 地址和模型名称保存在 DataStore。API Key 不再以明文保存在 DataStore：

1. Android Keystore 生成并保存 AES 密钥；
2. API Key 使用 AES-GCM 加密；
3. 加密后的密文保存在应用私有 SharedPreferences；
4. 从旧版本升级时，会自动把旧 DataStore 中的明文 API Key 迁移到安全存储，并删除旧字段。

HTTP 日志在 Debug 模式只记录 BASIC 请求信息，Release 模式关闭网络日志；Authorization/Cookie 等敏感请求头会被脱敏，网络 BODY 永不写入日志。

## 构建

环境：JDK 21、Gradle 8.11.1、Android Gradle Plugin 8.7.3。

本地：

```bash
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

仓库当前 CI 会自动生成 Gradle Wrapper，然后依次执行单元测试、Lint、Debug APK 和 Release APK 构建。

## 签名说明

Debug 与 Release 构建目前都使用仓库内的 `debug.p12`（标准 Android 调试证书），以保证 CI 构建出的 APK 签名完整、可直接安装，避免未签名导致解析失败。

正式 Release 发布应使用独立的正式签名密钥，并通过 CI Secret 注入，不能把正式私钥提交到仓库。

## 日志

应用日志保存在应用私有目录 `filesDir/logs/app.log`，内存保留最近 800 行，文件超过 2MB 时滚动截断。日志内容会进行基础凭据脱敏。

调试页的 GitHub Token 使用 Android Keystore 加密保存；上传日志是用户主动操作，上传前应确认日志内容适合提交到目标仓库。

## Roadmap

- 已完成：日志脱敏、API Key 安全存储、Release R8/资源压缩、CI Debug+Release 构建、本地语音输入/语音回复、多 AI 角色、动态与观察时间线。
- 进行中：关键网络异常统一处理、构建与文档完善。
- 后续：ChatViewModel 拆分 UseCase/Repository、进一步引入 Room（本阶段不实施）。