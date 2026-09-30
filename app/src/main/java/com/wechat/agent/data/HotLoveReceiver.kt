package com.wechat.agent.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 热恋模式锁屏控制接收器。
 * 在 MainActivity 动态注册，监听锁屏/解锁广播：
 * 锁屏（SCREEN_OFF）自动暂停所选音乐 App，解锁（SCREEN_ON）自动恢复播放。
 * 仅在热恋模式开启且锁屏控制开关打开、且已选择音乐 App 时生效。
 */
class HotLoveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsManager.getInstance(context).getHotLoveSettingsSync()
        if (!settings.enabled || !settings.lockScreenPause) return
        if (settings.selectedMusicPackage.isBlank()) return

        val music = MusicController(context)
        if (!music.connectTo(settings.selectedMusicPackage)) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> music.pause()
            Intent.ACTION_SCREEN_ON -> music.play()
        }
    }
}
