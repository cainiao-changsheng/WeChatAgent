package com.wechat.agent.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wechat.agent.R
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 手机操控中枢：负责「约束 + 确认」两道安全闸。
 *
 * 约束：
 * 1. 高风险动作黑名单——命中即拒绝，绝不进入确认流程（转账/支付、短信、电话、删除/卸载、安装下载、
 *    密码/账号、系统设置修改等涉及敏感或不可逆的行为）；
 * 2. 白名单——仅允许 [ALLOWED] 里的安全导航动作，其余一律拒绝；
 * 3. 无障碍权限闸——未授权时直接提示引导授权。
 *
 * 确认：
 * 4. 白名单动作也不会立即执行，而是先发一条通知栏确认（同意/拒绝）；用户点「同意」后，
 *    由 [PhoneControlReceiver] 回调 [handleDecision] 才真正通过 [AgentAccessibilityService] 执行。
 */
object PhoneControl {

    const val CHANNEL_ID = "phone_control"
    const val ACTION_DECIDE = "com.wechat.agent.action.PHONE_CONTROL_DECIDE"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_APPROVED = "approved"

    private const val NOTIFICATION_ID = 2001
    private const val EXPIRE_MS = 2 * 60 * 1000L

    /** 白名单：允许的安全全局动作（actionKey -> 中文描述）。 */
    private val ALLOWED: Map<String, String> = mapOf(
        "home" to "回到桌面",
        "back" to "返回上一页",
        "recents" to "打开最近任务",
        "notifications" to "打开通知栏",
        "quick_settings" to "打开快捷设置",
        "lock_screen" to "锁屏"
    )

    /** 高风险关键词黑名单：命中任一即拒绝，绝不执行。 */
    private val HIGH_RISK_KEYWORDS: List<String> = listOf(
        "pay", "payment", "transfer", "转账", "支付", "付款", "红包",
        "sms", "text_message", "短信", "发消息",
        "call", "dial", "phone_call", "电话", "拨打",
        "delete", "remove", "uninstall", "卸载", "删除", "清空",
        "install", "安装", "download", "下载",
        "password", "密码", "credential", "账号", "登录", "验证码",
        "修改", "root", "权限", "factory", "格式化"
    )

    private data class Pending(val action: String, val description: String, val expireAt: Long)

    private val pending = ConcurrentHashMap<String, Pending>()

    fun isAccessibilityGranted(context: Context): Boolean =
        AgentAccessibilityService.isServiceEnabled(context)

    /**
     * Agent 请求执行一个手机操作。返回给模型的文案（模型据此继续与用户对话）。
     * 该方法**只进入确认流程，绝不直接执行**。
     */
    fun request(context: Context, rawAction: String): String {
        val action = rawAction.trim().lowercase()
        if (action.isEmpty()) return "未指定要执行的手机操作。"

        // 1) 高风险拦截
        val risky = HIGH_RISK_KEYWORDS.firstOrNull { action.contains(it) }
        if (risky != null) {
            return "已拒绝执行：该操作疑似高风险行为（命中敏感词「$risky」）。我不能擅自操作，请引导对方在自己的手机上手动处理。"
        }

        // 2) 白名单校验
        val description = ALLOWED[action]
            ?: return "不支持的手机操作「$rawAction」。目前仅支持：${ALLOWED.keys.joinToString("、")}。"

        // 3) 无障碍权限闸
        if (!isAccessibilityGranted(context)) {
            return "暂未授予「无障碍」权限，无法执行「$description」。请对方到 我 → 设置 → 无障碍手机操控 授权后再试。"
        }

        // 4) 进入确认流程
        val requestId = UUID.randomUUID().toString()
        pending[requestId] = Pending(action, description, System.currentTimeMillis() + EXPIRE_MS)
        postConfirmNotification(context, requestId, description)
        return "我已准备好「$description」，但需要你确认才敢动手。请到手机通知栏点「同意」或「拒绝」。"
    }

    /** 用户在通知栏做出选择后回调：同意则执行，拒绝或超时则放弃。 */
    fun handleDecision(context: Context, requestId: String, approved: Boolean) {
        val p = pending.remove(requestId) ?: return
        if (System.currentTimeMillis() > p.expireAt) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        if (!approved) return

        val service = AgentAccessibilityService.running
        if (service == null) {
            notifyResult(context, "无法执行「${p.description}」：无障碍服务未运行，请先到系统设置开启。")
            return
        }
        val ok = service.performSafeAction(p.action)
        notifyResult(context, if (ok) "已执行：${p.description}" else "「${p.description}」执行失败，请手动操作。")
    }

    // ========== 通知 ==========

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "手机操控确认",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI 执行手机操作前，需要你确认同意或拒绝"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun decideIntent(context: Context, requestId: String, approved: Boolean): PendingIntent {
        val intent = Intent(context, PhoneControlReceiver::class.java).apply {
            action = ACTION_DECIDE
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_APPROVED, approved)
        }
        // approve/reject 两个 PendingIntent 必须用不同的 requestCode，否则 FLAG_UPDATE_CURRENT 会互相覆盖
        val requestCode = requestId.hashCode() * 2 + if (approved) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun postConfirmNotification(context: Context, requestId: String, description: String) {
        try {
            ensureChannel(context)
            if (!canNotify(context)) return
            val approve = decideIntent(context, requestId, true)
            val reject = decideIntent(context, requestId, false)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("TA 想帮你「$description」")
                .setContentText("是否同意 AI 执行该操作？")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "同意", approve)
                .addAction(0, "拒绝", reject)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyResult(context: Context, text: String) {
        try {
            ensureChannel(context)
            if (!canNotify(context)) return
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentIntent = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("手机操控")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
        } catch (_: Exception) {
        }
    }
}