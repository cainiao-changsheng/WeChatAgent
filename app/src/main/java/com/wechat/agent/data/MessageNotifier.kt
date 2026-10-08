package com.wechat.agent.data

import android.Manifest
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

/**
 * 后台主动消息系统通知：热恋模式下 Agent 在后台主动发起聊天时，
 * 通过通知栏提醒用户（点击回到应用），避免消息只写入内存而用户无感知。
 * 仅在取得通知权限（Android 13+ 的 POST_NOTIFICATIONS）后才会展示，未授权时静默跳过。
 */
object MessageNotifier {

    const val PROACTIVE_CHANNEL_ID = "proactive_messages"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                PROACTIVE_CHANNEL_ID,
                "主动消息",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "对方（AI 恋人）在后台主动发来的消息提醒"
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun notifyProactiveMessage(context: Context, title: String, content: String) {
        try {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, PROACTIVE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }
}