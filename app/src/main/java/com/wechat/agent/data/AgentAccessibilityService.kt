package com.wechat.agent.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * 无障碍手机操控服务：让 Agent「像人一样」执行安全全局导航动作。
 *
 * 安全边界：
 * - 只暴露 `performSafeAction` 里的白名单动作（回桌面/返回/最近任务/通知栏/快捷设置/锁屏）；
 * - 配置文件中 `canRetrieveWindowContent=false`，本服务**不读取任何屏幕内容**；
 * - 任何动作都不会直接执行——必须由 [PhoneControl] 先征得用户同意后，才通过实例调用 [performSafeAction]。
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: AgentAccessibilityService? = null

        /** 当前进程内已连接的服务实例（供确认通过后执行动作）。 */
        val running: AgentAccessibilityService?
            get() = instance

        /** 判断本应用的无障碍服务是否已在系统设置中被开启。 */
        fun isServiceEnabled(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    ?: return false
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
                    val si = info.resolveInfo.serviceInfo
                    si.packageName == context.packageName &&
                        si.name == AgentAccessibilityService::class.java.name
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无需读取任何事件内容，保持最小权限面。
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * 执行一个白名单内的安全全局动作。
     * @return 是否成功派发；不在此白名单内的 action 一律返回 false（绝不执行）。
     */
    fun performSafeAction(action: String): Boolean = when (action) {
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "lock_screen" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
        else -> false
    }
}