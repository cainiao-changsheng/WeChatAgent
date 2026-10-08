package com.wechat.agent.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 手机操控确认接收器：接收通知栏「同意/拒绝」按钮的回调。
 * 通过 [PhoneControl.handleDecision] 完成最终执行或取消，确保动作只在用户确认后才会发生。
 */
class PhoneControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PhoneControl.ACTION_DECIDE) return
        val requestId = intent.getStringExtra(PhoneControl.EXTRA_REQUEST_ID) ?: return
        val approved = intent.getBooleanExtra(PhoneControl.EXTRA_APPROVED, false)
        PhoneControl.handleDecision(context, requestId, approved)
    }
}