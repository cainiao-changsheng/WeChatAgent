# WeChatAgent v1.0.16 build-fix audit

## Baseline
This package is based on the user's uploaded `WeChatAgent-v1.0.14-source.zip` and contains the security/stability changes from the previous audited package.

## Build failure fixes applied
The GitHub Actions failure reported on the previous package was traced to these concrete source errors:

1. `AppLogger.kt`: the closing brace for the top-level `object AppLogger` was missing.
2. `RetrofitClient.kt`: `BuildConfig` was referenced but generated BuildConfig was not enabled. `buildFeatures.buildConfig = true` is now explicit.
3. `RetrofitClient.kt`: `userInfo` was incorrectly accessed on OkHttp `HttpUrl`. The check now uses `parsed.username` and `parsed.password`.

## Security/stability changes retained
- API Key stored with Android Keystore + AES-GCM; legacy DataStore plaintext is migrated only after encrypted write/read verification.
- GitHub debug token stored with Android Keystore + AES-GCM; legacy plaintext token is migrated only after verification.
- HTTP logging never records BODY; sensitive authorization/cookie headers are redacted.
- Release network configuration requires HTTPS; debug HTTP is restricted to localhost.
- API URL rejects embedded credentials, query parameters, and fragments.
- Update APK URLs require HTTPS and an exact allow-list of GitHub hosts; downloaded APKs are size-limited and checked for the ZIP/APK signature before installation.
- Release R8/resource shrinking remain enabled.
- Fixed debug keystore files are excluded/removed; Android backup is disabled.

## Static verification performed
- Compared source tree against the original uploaded v1.0.14 ZIP.
- Confirmed modified/new source files are present.
- Searched Kotlin compiler diagnostics for syntax/brace errors: none found.
- Confirmed no `BuildConfig`/`userInfo` source errors remain in the corrected code paths.
- Confirmed no `.p12`, `.jks`, `.keystore`, or APK artifacts are included in this source package.

## Not claimed
Android Gradle compilation was not executed in this environment because the Android SDK/Gradle distribution is unavailable offline. The authoritative compile check remains the GitHub Actions Gradle build.
