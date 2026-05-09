package com.wechat.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wechat.agent.ui.screens.ChatListScreen
import com.wechat.agent.ui.screens.ChatScreen
import com.wechat.agent.ui.screens.MomentsScreen
import com.wechat.agent.ui.screens.SettingsScreen
import com.wechat.agent.ui.theme.WeChatAgentTheme
import com.wechat.agent.viewmodel.ChatViewModel
import com.wechat.agent.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeChatAgentTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val chats by chatViewModel.chats.collectAsState()
    val currentMessages by chatViewModel.currentMessages.collectAsState()
    val streamingContent by chatViewModel.streamingContent.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val moodText by chatViewModel.moodText.collectAsState()
    val agentAvatar by settingsViewModel.agentAvatar.collectAsState()
    val userAvatar by settingsViewModel.userAvatar.collectAsState()
    val agentAvatarUri by settingsViewModel.agentAvatarUri.collectAsState()
    val userAvatarUri by settingsViewModel.userAvatarUri.collectAsState()
    val nowPlaying by chatViewModel.nowPlaying.collectAsState()
    val momentPosts by chatViewModel.momentsPosts.collectAsState()

    NavHost(navController = navController, startDestination = "chatList") {
        composable("chatList") {
            ChatListScreen(
                chats = chats, agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                onChatClick = { chatId -> chatViewModel.selectChat(chatId); navController.navigate("chat/$chatId") },
                onNewChat = { navController.navigate("chat/${chatViewModel.createNewChat()}") },
                onDeleteChat = { chatViewModel.deleteChat(it) },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToMoments = { navController.navigate("moments") }
            )
        }

        composable("chat/{chatId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val chat = chats.find { it.id == id }
            ChatScreen(
                chatTitle = chat?.title ?: "对话", messages = currentMessages,
                streamingContent = streamingContent, isLoading = isLoading,
                agentAvatar = agentAvatar, userAvatar = userAvatar,
                agentAvatarUri = agentAvatarUri, userAvatarUri = userAvatarUri,
                moodText = moodText, nowPlaying = nowPlaying,
                onBack = { navController.popBackStack() },
                onSendMessage = { chatViewModel.sendMessage(it) },
                onPlayMusic = { chatViewModel.playMusic() },
                onPauseMusic = { chatViewModel.pauseMusic() },
                onSkipNext = { chatViewModel.skipNextMusic() },
                onSkipPrev = { chatViewModel.skipPrevMusic() },
                onOpenMusicApp = { chatViewModel.openMusicApp() }
            )
        }

        composable("moments") {
            MomentsScreen(
                agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                posts = momentPosts, isLoading = false,
                onBack = { navController.popBackStack() },
                onGenerateNew = { chatViewModel.generateMomentsPost() },
                onToggleLike = { chatViewModel.toggleLike(it) }
            )
        }

        composable("settings") {
            SettingsScreen(viewModel = settingsViewModel, onBack = { navController.popBackStack() })
        }
    }
}
