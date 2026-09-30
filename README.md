# WeChatAgent

WeChat 风格的 AI 角色聊天 Android 应用。当前源码版本：**1.0.16**。

## 当前架构

- Kotlin + Jetpack Compose + Material 3
- ViewModel：聊天、动态、设置等页面状态
- Data 层：角色、记忆、情绪、动态、观察时间线等本地数据
- Retrofit + OkHttp：AI API 请求与 SSE 流式输出
- DataStore：普通应用设置
- Android Keystore：API Key 加密密钥

当前阶段暂不进行 Room、Repository/UseCase 大规模拆分；优先处理 P0/P1 安全性、稳定性和构建质量。

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

Debug APK 使用 Android/Gradle 默认 Debug 签名，源码仓库不再携带固定调试私钥。

正式 Release 发布应使用独立的正式签名密钥，并通过 CI Secret 注入，不能把正式私钥提交到仓库。

## 日志

应用日志保存在应用私有目录 `filesDir/logs/app.log`，内存保留最近 800 行，文件超过 2MB 时滚动截断。日志内容会进行基础凭据脱敏。

调试页的 GitHub Token 使用 Android Keystore 加密保存；上传日志是用户主动操作，上传前应确认日志内容适合提交到目标仓库。

## Roadmap

- P0：日志脱敏、API Key 安全存储、Release R8/资源压缩、CI Debug+Release 验证
- P1：关键网络异常统一处理、构建与文档完善
- 后续：ChatViewModel 拆分 UseCase/Repository、进一步引入 Room（本阶段不实施）
