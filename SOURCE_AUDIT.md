# WeChatAgent 1.0.16 Source Audit

基线：GitHub `cainiao-changsheng/WeChatAgent` 的公开 master，以及用户上传的 v1.0.14 源码包。

## 本次实际修改

- `app/build.gradle.kts`：版本号升至 1.0.16；Release 保持 R8/资源压缩；不再携带固定 Debug 私钥。
- `app/src/main/AndroidManifest.xml`：关闭 Android 自动备份，避免应用私有数据进入系统自动备份链路。
- `app/src/main/java/com/wechat/agent/data/SettingsManager.kt`：API Key 使用 Keystore 加密存储；旧 DataStore 明文只有在加密写入并回读成功后才删除。
- `app/src/main/java/com/wechat/agent/data/SecureApiKeyStore.kt`：AES-GCM + Android Keystore，并使用同步提交保证迁移结果可验证。
- `app/src/main/java/com/wechat/agent/data/SecureGitHubTokenStore.kt`：新增 GitHub 调试 Token 的 Keystore 存储及旧版明文迁移。
- `app/src/main/java/com/wechat/agent/ui/screens/LogScreen.kt`：移除直接读写明文 Token；输入框改为密码显示。
- `app/src/main/java/com/wechat/agent/data/network/RetrofitClient.kt`：禁止 API 地址携带用户信息、查询参数和 fragment；生产环境仅 HTTPS；HTTP 仅限 Debug 本机地址；HTTP BODY 日志保持关闭。
- `app/src/main/java/com/wechat/agent/data/UpdateChecker.kt`：更新下载仅接受 GitHub 官方 HTTPS 域名，限制 APK 最大体积，并检查 APK/ZIP 文件头后才进入安装器。
- `UPDATE_LOG.md`、`README.md`、`update_info.json`、`ChangelogScreen.kt`：同步 1.0.16 版本说明。

## 安全检查

- 未发现 `.p12`、`.jks`、`.keystore` 文件。
- 未发现 `ghp_`/GitHub PAT 硬编码。
- 未发现 HTTP BODY 日志级别。
- 未发现 API Key 明文写入 DataStore 的保存路径；旧字段只用于一次性迁移。
- GitHub Token 仅保留旧版字段名作为迁移兼容代码，不再直接保存新 Token。

## 构建说明

本源码包包含项目 Gradle 配置和 `gradle/wrapper/gradle-wrapper.properties`，但当前交付环境没有 Android SDK/Gradle Wrapper JAR，因此本次不能声称已经完成本地 APK 编译验证。
建议上传后首先执行：

`./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

