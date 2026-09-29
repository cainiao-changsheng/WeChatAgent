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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wechat.agent.ui.components.WeChatBottomBar
import com.wechat.agent.ui.screens.AgentDetailScreen
import com.wechat.agent.ui.screens.AgentSetupScreen
import com.wechat.agent.ui.screens.ChatListScreen
import com.wechat.agent.ui.screens.ChatScreen
import com.wechat.agent.ui.screens.ComposeMomentScreen
import com.wechat.agent.ui.screens.ContactsScreen
import com.wechat.agent.ui.screens.EditProfileScreen
import com.wechat.agent.ui.screens.MomentsScreen
import com.wechat.agent.ui.screens.MyProfileScreen
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "chatList"
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
    val agentName by settingsViewModel.agentName.collectAsState()
    val userNickname by settingsViewModel.userNickname.collectAsState()
    val agentGender by settingsViewModel.agentGender.collectAsState()
    val agentAge by settingsViewModel.agentAge.collectAsState()
    val agentPersona by settingsViewModel.agentPersona.collectAsState()
    val agentGlobalSettings by settingsViewModel.agentGlobalSettings.collectAsState()
    val nowPlaying by chatViewModel.nowPlaying.collectAsState()
    val momentPosts by chatViewModel.momentsPosts.collectAsState()

    fun openChatWithAgent() {
        if (chats.isNotEmpty()) {
            chatViewModel.selectChat(chats.first().id)
            navController.navigate("chat/${chats.first().id}")
        } else {
            navController.navigate("chat/${chatViewModel.createNewChat()}")
        }
    }

    NavHost(navController = navController, startDestination = "chatList") {
        composable("chatList") {
            ChatListScreen(
                chats = chats, agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                onChatClick = { chatId -> chatViewModel.selectChat(chatId); navController.navigate("chat/$chatId") },
                onNewChat = { navController.navigate("chat/${chatViewModel.createNewChat()}") },
                onDeleteChat = { chatViewModel.deleteChat(it) },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("contacts") {
            ContactsScreen(
                chats = chats, agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                agentName = agentName,
                onOpenAgentDetail = { navController.navigate("agentDetail") },
                onNewFriendClick = { navController.navigate("agentSetup") },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("agentSetup") {
            AgentSetupScreen(
                isEdit = false,
                initialName = agentName,
                initialGender = agentGender,
                initialAge = agentAge,
                initialPersona = agentPersona,
                initialGlobalSettings = agentGlobalSettings,
                initialAvatar = agentAvatar,
                initialAvatarUri = agentAvatarUri,
                onBack = { navController.popBackStack() },
                onSave = { name, gender, age, persona, global, avatar, avatarUri ->
                    settingsViewModel.saveAgentProfile(name, gender, age, persona, global)
                    if (avatar.isNotEmpty()) settingsViewModel.saveAvatar(avatar, userAvatar)
                    if (avatarUri.isNotEmpty()) settingsViewModel.saveAvatarUri(avatarUri, userAvatarUri)
                    navController.popBackStack()
                }
            )
        }

        composable("agentSetupEdit") {
            AgentSetupScreen(
                isEdit = true,
                initialName = agentName,
                initialGender = agentGender,
                initialAge = agentAge,
                initialPersona = agentPersona,
                initialGlobalSettings = agentGlobalSettings,
                initialAvatar = agentAvatar,
                initialAvatarUri = agentAvatarUri,
                onBack = { navController.popBackStack() },
                onSave = { name, gender, age, persona, global, avatar, avatarUri ->
                    settingsViewModel.saveAgentProfile(name, gender, age, persona, global)
                    if (avatar.isNotEmpty()) settingsViewModel.saveAvatar(avatar, userAvatar)
                    if (avatarUri.isNotEmpty()) settingsViewModel.saveAvatarUri(avatarUri, userAvatarUri)
                    navController.popBackStack()
                }
            )
        }

        composable("agentDetail") {
            AgentDetailScreen(
                agentName = agentName,
                agentGender = agentGender,
                agentAge = agentAge,
                agentPersona = agentPersona,
                agentGlobalSettings = agentGlobalSettings,
                agentAvatar = agentAvatar,
                agentAvatarUri = agentAvatarUri,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("agentSetupEdit") },
                onSendMessage = { openChatWithAgent() }
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
                onSendImage = { chatViewModel.sendImageMessage(it) },
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
                agentName = agentName,
                userAvatar = userAvatar, userAvatarUri = userAvatarUri,
                posts = momentPosts, isLoading = false,
                showBack = false,
                onBack = { navController.popBackStack() },
                onComposeMoment = { navController.navigate("composeMoment") },
                onToggleLike = { chatViewModel.toggleLike(it) },
                onAddComment = { postId, comment -> chatViewModel.addComment(postId, comment) },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("composeMoment") {
            ComposeMomentScreen(
                onBack = { navController.popBackStack() },
                onPublish = { content, imageUri ->
                    chatViewModel.postUserMoment(content, imageUri)
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            MyProfileScreen(
                userAvatar = userAvatar, userAvatarUri = userAvatarUri,
                userNickname = userNickname,
                showBack = false,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("editProfile") },
                onOpenMoments = { navigateToTab(navController, "moments") },
                onOpenSettings = { navController.navigate("settingsDetail") },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("settingsDetail") {
            SettingsScreen(
                viewModel = settingsViewModel,
                showBack = true,
                onBack = { navController.popBackStack() }
            )
        }

        composable("editProfile") {
            EditProfileScreen(
                userAvatar = userAvatar,
                userAvatarUri = userAvatarUri,
                userNickname = userNickname,
                onBack = { navController.popBackStack() },
                onSave = { avatar, avatarUri, nickname ->
                    if (avatar.isNotEmpty()) settingsViewModel.saveAvatar(agentAvatar, avatar)
                    if (avatarUri.isNotEmpty()) settingsViewModel.saveAvatarUri(agentAvatarUri, avatarUri)
                    if (nickname.isNotBlank()) settingsViewModel.saveUserNickname(nickname)
                    navController.popBackStack()
                }
            )
        }
    }
}

private fun navigateToTab(navController: androidx.navigation.NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
