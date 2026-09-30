package com.wechat.agent.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

class MusicController(private val context: Context) {

    data class NowPlaying(
        val title: String = "",
        val artist: String = "",
        val isPlaying: Boolean = false,
        val appName: String = ""
    )

    private var mediaController: MediaController? = null
    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    /** 媒体会话状态/元数据变化时回调当前播放信息（供 UI 实时刷新，如切歌后更新歌名）。 */
    var onNowPlayingChanged: ((NowPlaying) -> Unit)? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            onNowPlayingChanged?.invoke(getNowPlaying())
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            onNowPlayingChanged?.invoke(getNowPlaying())
        }
    }

    fun connect(): Boolean {
        val controllers = sessionManager.getActiveSessions(
            ComponentName(context, NotificationListener::class.java)
        )
        if (controllers.isNotEmpty()) {
            mediaController = controllers[0]
            mediaController?.registerCallback(controllerCallback)
            return true
        }
        return false
    }

    /** 按用户选择的音乐 App 包名连接当前活跃媒体会话；无匹配返回 false。 */
    fun connectTo(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val controllers = sessionManager.getActiveSessions(
            ComponentName(context, NotificationListener::class.java)
        )
        val target = controllers.firstOrNull { it.packageName == packageName } ?: return false
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = target
        mediaController?.registerCallback(controllerCallback)
        return true
    }

    fun getNowPlaying(): NowPlaying {
        val ctrl = mediaController ?: return NowPlaying()
        val metadata = ctrl.metadata ?: return NowPlaying()
        return NowPlaying(
            title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING,
            appName = ctrl.packageName ?: ""
        )
    }

    fun play() {
        mediaController?.transportControls?.play()
    }

    fun pause() {
        mediaController?.transportControls?.pause()
    }

    fun skipNext() {
        mediaController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        mediaController?.transportControls?.skipToPrevious()
    }

    fun openMusicApp(packageName: String = "") {
        try {
            if (packageName.isNotEmpty()) {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }
            val musicApps = listOf(
                "com.tencent.qqmusic",
                "com.netease.cloudmusic",
                "com.kugou.android",
                "com.spotify.music",
                "com.apple.android.music"
            )
            for (pkg in musicApps) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://music.163.com")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun searchSong(query: String) {
        try {
            val encoded = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://music.163.com/#/search/m/?s=$encoded")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun release() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null
    }
}

class NotificationListener : android.service.notification.NotificationListenerService() {
    override fun onListenerConnected() {}
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {}
}
