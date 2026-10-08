package com.wechat.agent.data

import android.content.Context
import android.content.Intent
import com.wechat.agent.ScreenCaptureActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 屏幕截图中枢：在「Agent 工具调用」和「前台截图 Activity」之间桥接。
 *
 * 流程：
 * 1. [capture] 启动 [ScreenCaptureActivity]，由它弹出系统 MediaProjection 授权弹窗；
 * 2. 用户同意后截取一帧并落盘，Activity 通过 [complete] 回传文件绝对路径；
 * 3. [capture] 挂起等待结果，用户拒绝 / 超时 / 失败均返回 null。
 *
 * 安全边界：
 * - 每次截图都必须经过系统「屏幕录制/投射」授权弹窗，用户不点同意就永远拿不到画面，
 *   因此该弹窗本身就是「用户确认」闸，不额外静默截屏；
 * - 同一时间只允许一次截图请求，避免并发时结果互相覆盖。
 */
object ScreenCaptureManager {

    @Volatile
    private var pending: CompletableDeferred<String?>? = null

    /**
     * 请求一次屏幕截图。
     * @return 保存后的本地文件绝对路径（JPEG）；用户拒绝、超时或失败返回 null。
     */
    suspend fun capture(context: Context, timeoutMs: Long = 25_000L): String? {
        if (pending?.isActive == true) return null
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        return try {
            val launch = Intent(context.applicationContext, ScreenCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(launch)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (_: Exception) {
            null
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** 由 [ScreenCaptureActivity] 在截图完成 / 取消 / 失败后回调，唤醒挂起中的 [capture]。 */
    fun complete(path: String?) {
        pending?.apply {
            if (isActive) complete(path)
        }
    }
}